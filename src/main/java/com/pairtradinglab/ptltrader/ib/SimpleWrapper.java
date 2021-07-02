/**
 * 	This file is part of PTL Trader.
 *
 * 	Copyright © 2011-2021 Quantverse OÜ. All Rights Reserved.
 *
 *  PTL Trader is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  PTL Trader is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with PTL Trader. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pairtradinglab.ptltrader.ib;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.picocontainer.Startable;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.TickType;
import com.ib.client.UnderComp;
import com.ib.client.EClientSocket;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.UUID;

import com.ning.http.client.Response;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.pairtradinglab.ptltrader.Application;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.events.AccountConnected;
import com.pairtradinglab.ptltrader.events.BeaconFlash;
import com.pairtradinglab.ptltrader.events.GlobalPortfolioUpdateRequest;
import com.pairtradinglab.ptltrader.events.IbConnectionFailed;
import com.pairtradinglab.ptltrader.events.IbConnectionRequest;
import com.pairtradinglab.ptltrader.events.LogEvent;
import com.pairtradinglab.ptltrader.model.*;
import com.pairtradinglab.ptltrader.trading.*;
import com.pairtradinglab.ptltrader.trading.events.*;
import com.pairtradinglab.ptltrader.trading.events.Error;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.jcip.annotations.*;

/**
 * SimpleWrapper object implements IB callbacks
 * @author carloss
 * This class is thread safe except for methods implemented from EWrapper, which may be called
 * from EReader thread only!
 *
 */
@NotThreadSafe
public class SimpleWrapper extends AbstractModelObject implements EWrapper, Startable {
	private final String uid = UUID.randomUUID().toString();
	
	// dependencies to be injected
	private final Status status;
	private final PortfolioList portfolioList;
	private final AccountList accountList;
	private final EventBus bus;
	private final Logger logger;
	private final Map<String, SimpleWrapper> ibWrapperMap;
	private final RuntimeParams runtimeParams;
	private final Set<String> connectedAccounts;
	
	
	private volatile int ibClientId = 0;
	private volatile String ibHost = "";
	private volatile int ibPort = 0;
	private volatile String ibFaAccount = "";
	
	private final Object connectionStateLock = new Object();
	@GuardedBy("connectionStateLock")
	private boolean connecting = false;
	
	
	final BlockingQueue<HistoricalDataRequest> histRequestQueue = new LinkedBlockingQueue<>(1024);
	final Thread histRequestQueueWorker = new Thread(new Runnable() {
		
		@Override
		public void run() {
			while (true) {
                try {
                	HistoricalDataRequest hrq = histRequestQueue.take();
                    logger.trace("processing queued historical request for "+hrq.contract.m_symbol);
                    hrq.execute(getIbSocket());
                    
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                	Thread.currentThread().interrupt();
                    //System.out.println("Interrupted via InterruptedIOException");
                    break;
                }
                
            }
			//System.out.println("Shutting down thread "+Thread.currentThread().getName()); //@DEBUG
			
		}
	}, "hist-worker");
	
	private static final int IB_CONNECTION_RETRY_RATE = 45;
	public static final int MIN_IB_API_VERSION = 66;
	
	private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(1);
	final Runnable apiConnectionRetryHandler = new Runnable() {
        public void run() { 
        	logger.info("attempting to reconnect to Interactive Brokers API");
        	bus.post(new LogEvent(String.format("Attempting to reconnect to IB API...")));
        	connectedAccounts.clear();
        	status.setIbConnecting(true);
        	synchronized(connectionStateLock) {
        		connecting = true;
        	}
    		getIbSocket().eConnect(getIbHost(), getIbPort(), getIbClientId());
    		if (getIbSocket().isConnected()) {
    			if (getIbSocket().serverVersion()<MIN_IB_API_VERSION) {
    				connecting = false;
    				logger.error("connection failed: please upgrade your IB API at least to version "+MIN_IB_API_VERSION+" (detected: ."+getIbSocket().serverVersion()+")");
    				getIbSocket().eDisconnect();
    				status.setIbConnected(false);
    				return;
    			}
    			if (retryHandle!=null) retryHandle.cancel(false); // cancel retry scheduler
    			bus.post(new LogEvent(String.format("Re-connected to IB, server version: %d",  getIbSocket().serverVersion())));
    			
    		} else {
    			connecting = false;
    			logger.warn(String.format("IB connection failed, retry in %d seconds", IB_CONNECTION_RETRY_RATE));
    			status.setIbConnected(false);
    			status.setIbConnecting(false);
    		}
        }
    };
    
    ScheduledFuture<?> retryHandle = null;
	
	public int getIbClientId() {
		return ibClientId;
	}
	public void setIbClientId(int ibClientId) {
		int oldval=this.ibClientId;
		this.ibClientId = ibClientId;
		firePropertyChange("ibClientId", oldval, this.ibClientId);
		Preferences prefs = getPreferences();
		prefs.putInt("ibClientId", ibClientId);
	}
	public String getIbHost() {
		return ibHost;
	}
	public void setIbHost(String ibHost) {
		String oldval = this.ibHost;
		this.ibHost = ibHost;
		firePropertyChange("ibHost", oldval, this.ibHost);
		Preferences prefs = getPreferences();
		prefs.put("ibHost", ibHost);
	}
	public int getIbPort() {
		return ibPort;
	}
	public void setIbPort(int ibPort) {
		int oldval=this.ibPort;
		this.ibPort = ibPort;
		firePropertyChange("ibPort", oldval, this.ibPort);
		Preferences prefs = getPreferences();
		prefs.putInt("ibPort", ibPort);
	}
	
	public String getIbFaAccount() {
		return ibFaAccount;
	}
	
	public void setIbFaAccount(String ibFaAccount) {
		String oldval = this.ibFaAccount;
		this.ibFaAccount = ibFaAccount;
		firePropertyChange("ibFaAccount", oldval, this.ibFaAccount);
		Preferences prefs = getPreferences();
		prefs.put("ibFaAccount", ibFaAccount);
	}

	// for maintaining historical data requests
	private Map<Integer, HistoricalDataProvider> hmap = Collections.synchronizedMap(new HashMap<Integer, HistoricalDataProvider>());
	
	public void hmapPut(int i, HistoricalDataProvider hp) {
		hmap.put(i, hp);
	}
	
	public void hmapRemove(int i) {
		hmap.remove(i);
	}
	
	public boolean hmapCheckExists(int i) {
		return hmap.containsKey(i);
	}
	
	// for maintaining market data subscriptions
	private Map<Integer,String> marketDataReqMap = Collections.synchronizedMap(new HashMap<Integer, String>());
	
	public void clearMarketDataReqMap() {
		marketDataReqMap.clear();
	}
	
	public void getMarketDataReqMapCopy(Collection<? extends Entry<Integer, String>> out) {
		marketDataReqMap.entrySet().addAll(out);
	}
	
	public void putToMarketDataReqMap(int i, String v) {
		marketDataReqMap.put(i, v);
	}
	
	public String getSymbolFromMarketDataReqMap(int i) {
		return marketDataReqMap.get(i);
	}
	
	
	// for maintaining order IDs
	private final ReentrantLock nextIdLock = new ReentrantLock(); // used for lock getNextId->sendOrder phase
	private final AtomicInteger nextId = new AtomicInteger(1);
	
	public void lockOrderId() {
		nextIdLock.lock();
	}
	
	public void unlockOrderId() {
		nextIdLock.unlock();
	}
	
	private EClientSocket ibSocket = new EClientSocket(this);
	
	// for maintaining request Ids
	private final AtomicInteger nextReqId = new AtomicInteger(1000000);
	
	
	public SimpleWrapper(Status status, PortfolioList portfolioList,
			AccountList accountList, EventBus bus, LoggerFactory loggerFactory, Map<String, SimpleWrapper> ibWrapperMap, RuntimeParams runtimeParams, Set<String> connectedAccounts) {
		super();
		this.status = status;
		this.portfolioList = portfolioList;
		this.accountList = accountList;
		this.bus = bus;
		this.logger = loggerFactory.createLogger(this.getClass().getSimpleName());
		this.ibWrapperMap = ibWrapperMap;
		this.runtimeParams = runtimeParams;
		this.connectedAccounts = connectedAccounts;
		
		attachDisconnectHook(this);
	}
	

	@Override
	public void error(Exception e) {
		// TODO Auto-generated method stub
		logger.error(e.getClass().getSimpleName(), e);

	}

	@Override
	public void error(String str) {
		// TODO Auto-generated method stub
		logger.error(str);

	}

	@Override
	public void error(int id, int errorCode, String errorMsg) {
		Error err = new Error(uid, id, errorCode, errorMsg);
		bus.post(err);
		
		if (errorCode>=1100 && errorCode<2100) {
			// system warning
			logger.warn(String.format("id: %d code: %d msg: %s", id, errorCode, errorMsg));
			bus.post(new LogEvent(String.format("IB Warning: id: %d code: %d msg: %s", id, errorCode, errorMsg)));
			
		} else if (errorCode>=2100) {
			// warning/notice message
			logger.info(String.format("id: %d code: %d msg: %s", id, errorCode, errorMsg));
			bus.post(new LogEvent(String.format("IB Notice: id: %d code: %d msg: %s", id, errorCode, errorMsg)));
			onMaybeConnected();
			
		} else {
			// real error from IB API
			logger.error(String.format("id: %d code: %d msg: %s", id, errorCode, errorMsg));
			bus.post(new LogEvent(String.format("IB Error: id: %d code: %d msg: %s", id, errorCode, errorMsg)));
			
			// process special error codes
			switch(errorCode) {
			case 326:
				// connection actually failed
				status.setIbConnected(false);
				break;
			}
		}

	}

	@Override
	public void connectionClosed() {
		// TODO Auto-generated method stub
		synchronized(connectionStateLock) {
			connecting = false;
		}
		logger.warn("API connection lost");
		bus.post(new Disconnected(uid));
		status.setIbConnected(false);
		
		retryHandle = retryScheduler.scheduleAtFixedRate(apiConnectionRetryHandler, IB_CONNECTION_RETRY_RATE, IB_CONNECTION_RETRY_RATE, TimeUnit.SECONDS);

	}

	@Override
	public void tickPrice(int tickerId, int field, double price,
			int canAutoExecute) {
		//logger.info(String.format("TICK #%d: field %d price %f canexec %d", tickerId, field, price, canAutoExecute));
		
		// resolve tickerId
		String symbol = marketDataReqMap.get(tickerId);
		if (symbol!=null) {
			// send event to event bus
			Tick tev = new Tick(symbol, field, price, canAutoExecute);
			bus.post(tev);
		}
		
		
		
		
	}

	@Override
	public void tickSize(int tickerId, int field, int size) {
		// TODO Auto-generated method stub
		String symbol = marketDataReqMap.get(tickerId);
		if (symbol!=null) {
			//logger.info(String.format("TICK for %s: field %d size %d", symbol, field, size));
			TickSize tev = new TickSize(symbol, field, size);
			bus.post(tev);
		}

	}

	@Override
	public void tickOptionComputation(int tickerId, int field,
			double impliedVol, double delta, double optPrice,
			double pvDividend, double gamma, double vega, double theta,
			double undPrice) {
		// TODO Auto-generated method stub

	}

	@Override
	public void tickGeneric(int tickerId, int tickType, double value) {
		//logger.debug(String.format("GTICK #%d: ticktype %d value %f", tickerId, tickType, value));
		
		String symbol = marketDataReqMap.get(tickerId);
		if (symbol!=null) {
			//logger.debug(String.format("GTICK #%d: resolved to %s", tickerId, symbol));
			GenericTick ev = new GenericTick(symbol, tickType, value);
			bus.post(ev);
		}

	}

	@Override
	public void tickString(int tickerId, int tickType, String value) {
		// TODO Auto-generated method stub

	}

	@Override
	public void tickEFP(int tickerId, int tickType, double basisPoints,
			String formattedBasisPoints, double impliedFuture, int holdDays,
			String futureExpiry, double dividendImpact, double dividendsToExpiry) {
		// TODO Auto-generated method stub

	}

	@Override
	public void orderStatus(int orderId, String status, int filled,
			int remaining, double avgFillPrice, int permId, int parentId,
			double lastFillPrice, int clientId, String whyHeld) {
		
		//logger.info(String.format("OSTAT: oid %d status %s filled %d remaining %d price %f permid %d parentid %d lastprice %f clid %d held %s", 
		//		orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld));
		
		OrderStatus os = new OrderStatus(orderId, status, filled, remaining, avgFillPrice, lastFillPrice, whyHeld);
		bus.post(os);
		

	}

	@Override
	public void openOrder(int orderId, Contract contract, Order order,
			OrderState orderState) {
		//logger.info(String.format("OO: oid %d symbol %s qty %d comm %f im %s mm %s status %s", orderId, contract.m_symbol, order.m_totalQuantity, orderState.m_commission, orderState.m_initMargin, orderState.m_maintMargin, orderState.m_status));

	}

	@Override
	public void openOrderEnd() {
		logger.info("OO: end");

	}
	
	/**
	 * Returns true in black hour.
	 * Black hour = midnight EST, where IB performs maintenance and account data are not reliable
	 * @return
	 */
	private boolean isBlackHour() {
		return DateTime.now().withZone(DateTimeZone.forID("America/New_York")).getHourOfDay()==0;
	}

	@Override
	public void updateAccountValue(String key, String value, String currency,
			String accountName) {
		//logger.info(String.format("update account value: %s = %s (%s) for %s", key, value, currency, accountName));
		
		if (!accountList.isLoaded()) {
			accountList.getAccounts().clear();
			accountList.setLoaded(true);
		}
		
		if ("AccountCode".equals(key)) {
			if (accountList.getAccountByCode(value)==null) {
				accountList.addAccount(new Account(value));
				if (accountList.getAccounts().size()==1) {
					// first account added to the list
					accountList.setActiveIndex(0);
				}
			}
			if (!value.isEmpty()) {
				if (!ibWrapperMap.containsKey(value)) {
					logger.info("new account discovered: "+value);
					ibWrapperMap.put(value, this);
				}
				if (!connectedAccounts.contains(value) && ibSocket.isConnected()) {
					logger.debug("new account connected: "+value);
					connectedAccounts.add(value);
					bus.post(new AccountConnected(value));
				}
			}
		} else {
			// this is other field than Account
			Account ac = accountList.getAccountByCode(accountName);
			if (ac!=null) {
				if ("EquityWithLoanValue".equals(key)) {
					
					if (isBlackHour()) {
						logger.trace(String.format("account equity for %s: %s (black hour)", accountName, value));
					} else {
						logger.trace(String.format("account equity for %s: %s", accountName, value));
						EquityChange ev=new EquityChange(DateTime.now(), accountName, Double.parseDouble(value));
						bus.post(ev);
					}
					ac.setCurrency(currency);
					ac.setEquityWithLoanValue(Double.parseDouble(value));
					
				} else if ("BuyingPower".equals(key)) {
					ac.setBuyingPower(Double.parseDouble(value));
					
				} else if ("TotalCashValue".equals(key)) {
					ac.setTotalCash(Double.parseDouble(value));
					
				} else if ("InitMarginReq".equals(key)) {
					ac.setInitialMargin(Double.parseDouble(value));
				} else if ("MaintMarginReq".equals(key)) {
					ac.setMaintenanceMargin(Double.parseDouble(value));
				} else if ("UnrealizedPnL".equals(key)) {
					ac.setUnrealizedPl(Double.parseDouble(value));
				} else if ("AvailableFunds".equals(key)) {
					ac.setAvailableFunds(Double.parseDouble(value));
				}
				
			}
			
		}
			
		
	}

	@Override
	public void updatePortfolio(Contract contract, int position,
			double marketPrice, double marketValue, double averageCost,
			double unrealizedPNL, double realizedPNL, String accountName) {
		//logger.debug(String.format("portfolio %s@%s (%s, %s): pos %d price %f value %f cost %f unreal %f real %f accnt %s", contract.m_symbol, contract.m_exchange, contract.m_currency, contract.m_secType, 
		//		position, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL, accountName));
		
		if (isBlackHour()) {
			logger.trace(String.format("black hour: portfolio %s@%s (%s, %s): pos %d price %f value %f cost %f unreal %f real %f accnt %s", contract.m_symbol, contract.m_exchange, contract.m_currency, contract.m_secType, 
				position, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL, accountName));
		}
		
		portfolioList.swUpdatePortfolio(contract, position, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL, accountName);
		
		PortfolioUpdate ev = new PortfolioUpdate(accountName, position, contract, (position==0)?0:unrealizedPNL, marketValue);
		bus.post(ev);

	}

	@Override
	public void updateAccountTime(String timeStamp) {
		// TODO Auto-generated method stub
		//logger.info(String.format("account time %s", timeStamp));

	}

	@Override
	public void accountDownloadEnd(String accountName) {
		portfolioList.swAccountDownloadEnd(accountName);
		
		logger.info(String.format("download end for %s", accountName));

	}

	@Override
	public void nextValidId(int orderId) {
		logger.info(String.format("nextValidId: %d", orderId));
		onMaybeConnected();
		setNextId(orderId);

	}

	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub

	}

	@Override
	public void bondContractDetails(int reqId, ContractDetails contractDetails) {
		// TODO Auto-generated method stub

	}

	@Override
	public void contractDetailsEnd(int reqId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void execDetails(int reqId, Contract contract, Execution execution) {
		logger.debug(String.format("execution %d of %s @ %s oid %d time %s account %s price %f shares %d execid %s", reqId, contract.m_symbol, execution.m_exchange, execution.m_orderId, execution.m_time, execution.m_acctNumber, execution.m_avgPrice, execution.m_shares, execution.m_execId));
		
		bus.post(new ExecutionEvent(reqId, contract, execution));

	}

	@Override
	public void execDetailsEnd(int reqId) {
		logger.debug("exec details end for id="+reqId);

	}

	@Override
	public void updateMktDepth(int tickerId, int position, int operation,
			int side, double price, int size) {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateMktDepthL2(int tickerId, int position,
			String marketMaker, int operation, int side, double price, int size) {
		// TODO Auto-generated method stub

	}

	@Override
	public void updateNewsBulletin(int msgId, int msgType, String message,
			String origExchange) {
		// TODO Auto-generated method stub

	}

	@Override
	public void managedAccounts(String accountsList) {
		logger.info("managed accounts: " + accountsList);
		onMaybeConnected();
		// TODO Auto-generated method stub

	}

	@Override
	public void receiveFA(int faDataType, String xml) {
		// TODO Auto-generated method stub
		//logger.info(String.format("received FA response type %d", faDataType));
		//logger.info(xml);

	}

	@Override
	public void historicalData(int reqId, String date, double open,
			double high, double low, double close, int volume, int count,
			double WAP, boolean hasGaps) {
		
		//logger.info(String.format("HDATA: reqid %d date %s O:%f H:%f L:%f C:%f V:%d CNT:%d WAP:%f", reqId, date, open, high, low, close, volume, count, WAP));
		HistoricalDataProvider hdp = hmap.get(reqId);
		if (hdp!=null) {
			hdp.addRecord(date, close);
		}

	}

	@Override
	public void scannerParameters(String xml) {
		// TODO Auto-generated method stub

	}

	@Override
	public void scannerData(int reqId, int rank,
			ContractDetails contractDetails, String distance, String benchmark,
			String projection, String legsStr) {
		// TODO Auto-generated method stub

	}

	@Override
	public void scannerDataEnd(int reqId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high,
			double low, double close, long volume, double wap, int count) {
		// TODO Auto-generated method stub

	}

	@Override
	public void currentTime(long time) {
		// TODO Auto-generated method stub

	}

	@Override
	public void fundamentalData(int reqId, String data) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deltaNeutralValidation(int reqId, UnderComp underComp) {
		// TODO Auto-generated method stub

	}

	@Override
	public void tickSnapshotEnd(int reqId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void marketDataType(int reqId, int marketDataType) {
		// TODO Auto-generated method stub

	}

	@Override
	public void commissionReport(CommissionReport commissionReport) {
		logger.debug(String.format("comm report id=%s curr=%s comm=%f", commissionReport.m_execId, commissionReport.m_currency, commissionReport.m_commission));
		
		bus.post(new CommissionEvent(commissionReport));

	}
	
	
	
	public EClientSocket getIbSocket() {
		return ibSocket;
	}


	private static void attachDisconnectHook(final SimpleWrapper sw) {
	      Runtime.getRuntime().addShutdownHook(new Thread() {				
	         public void run() {
	            sw.getIbSocket().eDisconnect();
	         }
	      });			    	
	}
	
	public int getNextId() {
		return nextId.getAndIncrement();
	}

	public void setNextId(int nextId) {
		this.nextId.set(nextId);
		
	}
	public String getUid() {
		return uid;
	}
	
	public int getNextReqId() {
		return nextReqId.incrementAndGet();
		
	}
	
	@Override
	public void start() {
		histRequestQueueWorker.start();
		bus.register(this);
		
		// load preferences
		Preferences prefs = getPreferences();
		setIbClientId(prefs.getInt("ibClientId", 1));
		setIbHost(prefs.get("ibHost", "localhost"));
		setIbPort(prefs.getInt("ibPort", 7496));
		setIbFaAccount(prefs.get("ibFaAccount", ""));
		
	}
	@Override
	public void stop() {
		histRequestQueueWorker.interrupt();
		if (retryHandle!=null) retryHandle.cancel(true); 
		bus.unregister(this);
		
	}
	
	public void executeHistoricalDataRequestQueued(HistoricalDataRequest req) {
		histRequestQueue.add(req);
	}
	
	@Subscribe
	public void onGlobalPortfolioUpdateRequest(GlobalPortfolioUpdateRequest event) {
		if (!getIbSocket().isConnected()) return; // do nothing if not connected
		logger.debug("requesting update for all accounts");
		for (String ac: ibWrapperMap.keySet()) {
			if (ibWrapperMap.get(ac)==this) {
				logger.trace("requesting update for account: "+ac);
				getIbSocket().reqAccountUpdates(false, ac);
    			getIbSocket().reqAccountUpdates(true, ac);
			}
		}
		
	}
	
	private Preferences getPreferences() {
		String nodename = this.getClass().getName()+".1."+runtimeParams.getProfile();
		nodename = nodename.replace(".", "/");
		//System.out.println("settings loading prefs for "+nodename);
		Preferences prefs = Preferences.userRoot().node(nodename);
		return prefs;
	}
	
	// async connection handling
	public void ibConnectAsync() {
		bus.post(new IbConnectionRequest(uid));
	}
	
	@Subscribe
	public void onIbConnectionRequest(IbConnectionRequest cr) {
		if (!cr.uid.equals(uid)) return; // this is not ours!
		// we misuse the async event bus to handle IB connection request asynchronously
		logger.info("handling asynchronous connection to IB API for uid="+uid);
		synchronized(connectionStateLock) {
			connecting = true;
		}
		getIbSocket().eConnect(getIbHost(), getIbPort(), getIbClientId());
		if (getIbSocket().isConnected()) {
			logger.info("connected to IB API version "+getIbSocket().serverVersion());
			if (getIbSocket().serverVersion()<MIN_IB_API_VERSION) {
				connecting = false;
				logger.info("IB API disconnected");
				bus.post(new IbConnectionFailed(uid, IbConnectionFailed.REASON_API_VERSION, MIN_IB_API_VERSION, getIbSocket().serverVersion()));
				getIbSocket().eDisconnect();
				return;
			}
			
			
			
		} else {
			connecting = false;
			logger.error("failed to connect to IB API");
			bus.post(new IbConnectionFailed(uid, IbConnectionFailed.REASON_UNKNOWN, 0, 0));
		}
	}
	
	private void onMaybeConnected() {
		boolean justConnected = false;
		synchronized(connectionStateLock) {
			if (connecting) {
				connecting = false;
				justConnected = true;
			}
			
		}
		if (justConnected) {
			logger.info("IB connected for real");
			getIbSocket().reqAccountUpdates(true, getIbFaAccount());
			getIbSocket().reqIds(1);
			//getIbSocket().reqAccountSummary(1, "All", "AccountType,NetLiquidation,TotalCashValue");
			bus.post(new Connected(getUid()));
			
		}
	}
	
	@Override
	public void position(String account, Contract contract, int pos,
			double avgCost) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void positionEnd() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void accountSummary(int reqId, String account, String tag,
			String value, String currency) {
		// TODO Auto-generated method stub
		//logger.debug(String.format("reqId %d account %s tag %s val %s curr %s", reqId, account, tag, value, currency));
		
	}
	@Override
	public void accountSummaryEnd(int reqId) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void verifyMessageAPI(String apiData) {
		// TODO Auto-generated method stub
		logger.debug("verifyMessageAPI: "+apiData);
		
	}
	@Override
	public void verifyCompleted(boolean isSuccessful, String errorText) {
		// TODO Auto-generated method stub
		logger.debug(String.format("verifyCompleted: %b %s", isSuccessful, errorText));
		
	}
	@Override
	public void displayGroupList(int reqId, String groups) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void displayGroupUpdated(int reqId, String contractInfo) {
		// TODO Auto-generated method stub
		
	}
	
	
	

}
