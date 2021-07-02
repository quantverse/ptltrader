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
package com.pairtradinglab.ptltrader.trading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Joiner;
import com.google.common.eventbus.EventBus;
import com.ib.client.CommissionReport;
import com.ib.client.EClientSocket;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.TickType;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.SupportedFeatures;
import com.pairtradinglab.ptltrader.events.AccountConnected;
import com.pairtradinglab.ptltrader.events.BeaconFlash;
import com.pairtradinglab.ptltrader.events.GlobalPortfolioUpdateRequest;
import com.pairtradinglab.ptltrader.events.LogEvent;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;
import com.pairtradinglab.ptltrader.trading.events.ClosePositionRequest;
import com.pairtradinglab.ptltrader.trading.events.Disconnected;
import com.pairtradinglab.ptltrader.trading.events.Error;
import com.pairtradinglab.ptltrader.trading.events.ExecutionEvent;
import com.pairtradinglab.ptltrader.trading.events.GenericTick;
import com.pairtradinglab.ptltrader.trading.events.HistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.ManualInterventionRequested;
import com.pairtradinglab.ptltrader.trading.events.OpenPositionRequest;
import com.pairtradinglab.ptltrader.trading.events.OrderStatus;
import com.pairtradinglab.ptltrader.trading.events.PairDataFailure;
import com.pairtradinglab.ptltrader.trading.events.PairDataReady;
import com.pairtradinglab.ptltrader.trading.events.PairStateUpdated;
import com.pairtradinglab.ptltrader.trading.events.PortfolioUpdate;
import com.pairtradinglab.ptltrader.trading.events.ResumeRequest;
import com.pairtradinglab.ptltrader.trading.events.StrategyPlUpdated;
import com.pairtradinglab.ptltrader.trading.events.Tick;
import com.pairtradinglab.ptltrader.trading.events.TransactionEvent;
import com.pairtradinglab.ptltrader.ActiveCores;

import net.jcip.annotations.*;

/**
 * Pair Trading Engine
 * @author carloss
 * This class is designed to run in thread confinement.
 * Only methods permitted to call from other threads are:
 * setPtModel, getPtModel, hasActiveOrPendingPosition
 *
 */
@NotThreadSafe
public class ConfinedEngine {
	private static final double MIN_DAYTRADING_EQUITY = 25000; //in USD
	private static final int RETRY_HISTORICAL_MINUTES = 11;
	private static final int WAIT_RECOVERABLE = 120; // wait 2 mins for recoverable error
	private static final int AFTER_FILL_POS_SYNC_LOCK = 300; // in seconds
	private static final int COOLDOWN_AFTER_CLOSE = 120; // wait 2 mins to open new position after position is closed
	
	private static final int EXCHANGE_OPEN_HOUR = 9;
	private static final int EXCHANGE_OPEN_MINUTE = 30;
	private static final int EXCHANGE_CLOSE_HOUR = 16;
	private static final int EXCHANGE_CLOSE_MINUTE = 0;
	
	private static final int MAX_HIST_PRICE_AGE = 5; // max age of last hist price in days
	private static final double PRICE_MOVE_RATIO_LIMIT = 1.99; // this number must be >1
	
	private static int SLEEP_AFTER_POST = 50; // in ms
	
	// dependencies to be injected
	private volatile PairTradingModel ptmodel; // not final so it could be replaced!
	private final PairStrategy strategy;
	private final LoggerFactory loggerFactory;
	private final MarketDataProvider marketDataProvider;
	private final EventBus bus;
	private final PairDataProviderFactory pairDataProviderFactory;
	private final Map<String, SimpleWrapper> ibWrapperMap;
	private final Set<String> connectedAccounts;
	private final ActiveCores activeCores;
	private final ActivityDetector activityDetector;
	
	// locally resolved dependencies
	private final Logger l;
	private final PairDataProvider provider;
	
	private ContractExt c1;
	private ContractExt c2;
	
	private boolean s1Shortable = false;
	private boolean s2Shortable = false;
	
	private boolean started = false;
	
	private final String label;
	
	
	private int pos1=0; // current position of stock 1 (qty)
	private int pos2=0; // current position of stock 2 (qty)
	private volatile int opening1=0; //(qty)
	private volatile int opening2=0; //(qty)
	private int closing1=0; //(qty)
	private int closing2=0; //(qty)
	private volatile int position=0; // current confirmed pair position (-1, 0, 1)
	
	private Order o1=null; // pending order of stock 1
	private Order o2=null; // pending order of stock 2
	
	private HashMap<Integer, HashSet<String>> executions = new HashMap<Integer, HashSet<String>>();
	private HashMap<String, Integer> executionMap = new HashMap<String, Integer>();
	
	private int openOrderId1 = 0;
	private int openOrderId2 = 0;
	private int closeOrderId1 = 0;
	private int closeOrderId2 = 0;
	
	private HistoryEntry openHistoryEntry = null;
	private HistoryEntry closeHistoryEntry = null;
	private HistoryEntry openHistoryEntryLastSent = null;
	private HistoryEntry closeHistoryEntryLastSent = null;
	
	private TransactionEvent openTransaction1 = null;
	private TransactionEvent openTransaction2 = null;
	private TransactionEvent closeTransaction1 = null;
	private TransactionEvent closeTransaction2 = null;
	
	// for internal testing
	private TransactionEvent openTransactionLastSent1 = null;
	private TransactionEvent openTransactionLastSent2 = null;
	private TransactionEvent closeTransactionLastSent1 = null;
	private TransactionEvent closeTransactionLastSent2 = null;
	
	private double positionPl = 0;
	
	private DateTime blocked = null; // pair gets blocked if errors happen while sending orders
	
	private PortfolioUpdate lastPortfUpdate1 = null;
	private PortfolioUpdate lastPortfUpdate2 = null;
	private boolean ready = false; // the instance gets ready as long as PortfolioUpdates are received first time
	
	// historical data
	
	private DateTime lastDataObtained = null;
	private DateTime lastDataRequested = null;
	private int dataRequestId=0;
	
	private CoreStatus lastStat=null;
	
	private String lastExitReason="";
	
	private DateTime lastRecoverableError1=null;
	private boolean recoverableError1=false;
	private DateTime lastRecoverableError2=null;
	private boolean recoverableError2=false;
	
	// slippage tracking
	private DateTime lastOrderPlaced1;
	private DateTime lastOrderPlaced2;
	private DateTime lastOrderFilled1;
	private DateTime lastOrderFilled2;
	
	// cooldown
	private DateTime lastPositionClosed=null;
	
	// hist data tracking
	private DateTime lastHistPriceDate1 = null;
	private double lastHistPrice1 = 0.0;
	private DateTime lastHistPriceDate2 = null;
	private double lastHistPrice2 = 0.0;
	
	private DateTime lastResumed = null;
	
	private boolean lastHistDataRequestFailed = false;
	private boolean unsupportedStrategyFeatures = false;
	
	private final String exchange1;
	private final String exchange2;
	
	public ConfinedEngine(PairTradingModel ptmodel, PairStrategy strategy,
			Map<String, SimpleWrapper> ibWrapperMap, LoggerFactory loggerFactory,
			MarketDataProvider marketDataProvider,
			EventBus bus, PairDataProviderFactory pairDataProviderFactory, Set<String> connectedAccounts, ActiveCores activeCores, ActivityDetector activityDetector) {
		super();
		this.ptmodel = ptmodel;
		this.strategy = strategy;
		this.ibWrapperMap = ibWrapperMap;
		this.loggerFactory = loggerFactory;
		this.marketDataProvider = marketDataProvider;
		this.bus = bus;
		this.pairDataProviderFactory = pairDataProviderFactory;
		this.connectedAccounts = connectedAccounts;
		this.activeCores = activeCores;
		this.activityDetector = activityDetector;
		
		
		c1 = ContractExt.createFromGoogleSymbol(strategy.getStock1(), strategy.getTradeAs1() == PairStrategy.TRADE_AS_CFD);
		c2 = ContractExt.createFromGoogleSymbol(strategy.getStock2(), strategy.getTradeAs2() == PairStrategy.TRADE_AS_CFD);
		
		String r1[] = strategy.getStock1().split(":");
		String r2[] = strategy.getStock2().split(":");
		
		exchange1=r1[0];
		exchange2=r2[0];
		
		// resolve other dependencies
		label = c1.m_symbol.replace(" ", ".")+"_"+c2.m_symbol.replace(" ", ".");
		l = loggerFactory.createLogger(label);

		// for pair data provider factory we always use STK contracts
		ContractExt stkc1 = ContractExt.createFromGoogleSymbol(strategy.getStock1(), false);
		ContractExt stkc2 = ContractExt.createFromGoogleSymbol(strategy.getStock2(), false);
		provider = pairDataProviderFactory.createForContracts(stkc1, stkc2);
		
	}
	
	public void handleMessage(ControlMessage message) {
		Duration d = new Duration(message.timestamp, DateTime.now());
		//debug(message.type+" "+d);
		long ms = d.getMillis();
		
		// reject all messages older than 30 seconds
		if (ms>30000) return;
		
		switch(message.type) {
			case ControlMessage.TYPE_TICK:
				// reject all ticks older than 2 seconds
				if (ms<=2000) onTick((Tick) message.data);
				break;
			case ControlMessage.TYPE_GENERIC_TICK:
				// reject all ticks older than 5 seconds
				if (ms<=5000) onGenericTick((GenericTick) message.data);;
				break;
			case ControlMessage.TYPE_EXECUTION:
				onExecutionEvent((ExecutionEvent) message.data);
				break;
			case ControlMessage.TYPE_COMMISSION:
				onCommissionReport((CommissionReport) message.data);
				break;
			case ControlMessage.TYPE_PORTFOLIO_UPDATE:
				onPortfolioUpdate((PortfolioUpdate) message.data);
				break;
			case ControlMessage.TYPE_BEACON:
				onBeaconFlash((BeaconFlash) message.data);
				break;
			case ControlMessage.TYPE_ERROR:
				onError((Error) message.data);
				break;
			case ControlMessage.TYPE_ORDER_STATUS:
				onOrderStatus((OrderStatus) message.data);
				break;
			case ControlMessage.TYPE_PAIR_DATA_READY:
				onPairDataReady((PairDataReady) message.data);
				break;
			case ControlMessage.TYPE_PAIR_DATA_FAILURE:
				onPairDataFailure((PairDataFailure) message.data);
				break;
			case ControlMessage.TYPE_START:
				start();
				break;
			case ControlMessage.TYPE_STOP:
				stop(true);
				break;
			case ControlMessage.TYPE_ACCOUNT_CONNECTED:
				onAccountConnected((AccountConnected) message.data);
				break;
			case ControlMessage.TYPE_RESUME_REQUEST:
				onResumeRequest((ResumeRequest) message.data);
				break;
			case ControlMessage.TYPE_CLOSE_POSITION_REQUEST:
				onClosePositionRequest((ClosePositionRequest) message.data);
				break;
			case ControlMessage.TYPE_OPEN_POSITION_REQUEST:
				onOpenPositionRequest((OpenPositionRequest) message.data);
				break;
		
		}
		
	}
	
	
	// use this method if IB API is not yet connected
	protected void start() {
		if (started) return;
		debug("starting trading engine");
		if (ptmodel instanceof PairTradingModelDummy) error(String.format("model %s is not supported in this version of PTL Trader, disabling strategy", strategy.getModel()));
		
		// check strategy features
		for(String f : strategy.getFeatures()) {
			if (!SupportedFeatures.strategyFeatures.contains(f)) {
				error(String.format("feature %s is not supported, disabling strategy", f));
				unsupportedStrategyFeatures = true;
			}
		}
		
		provider.start();
		started=true;
		
		reportStatus(CoreStatus.PENDING);
		strategy.setZscoreBid(0);
        strategy.setZscoreAsk(0);
		strategy.setProfitPotential(0);
		if (ptmodel instanceof PairTradingModelRatio) strategy.setRsi(0);
		
		o1=null;
		o2=null;
		opening1=0;
		closing1=0;
		opening2=0;
		closing2=0;
		position=0;
		pos1=0;
		pos2=0;
		position=0;
			
		recoverableError1=false;
		lastRecoverableError1=null;
		recoverableError2=false;
		lastRecoverableError2=null;
		
		updateStrategyStatus();
		
		// we handle IB connection only if our account is present in connected accounts set
		if (connectedAccounts.contains(strategy.getPortfolio().getAccountCode())) handleIbConnection();
		
	}
	
	protected void stop(boolean interruptCurrentThread) {
		if (!started) return;
		debug("stopping trading engine");
		started=false;
		reportStatus(CoreStatus.NONE);
		strategy.setStatus(PairStrategy.STATUS_INACTIVE);
		
		// unsubscribe from market data (if applicable)
		marketDataProvider.unsubscribeData(strategy.getStock1(), strategy.getUid());
		marketDataProvider.unsubscribeData(strategy.getStock2(), strategy.getUid());
		provider.stop();
		activeCores.unregisterCore(strategy.getUid());
		
		if (interruptCurrentThread) {
			Thread.currentThread().interrupt();
		}
	}
	
	private SimpleWrapper getWrapper() {
		String acode = strategy.getPortfolio().getAccountCode();
		if (acode.isEmpty()) return null;
		return ibWrapperMap.get(acode);
	}
	
	private EClientSocket getSocket() {
		SimpleWrapper wrapper = getWrapper();
		if (wrapper==null) return null;
		return wrapper.getIbSocket();
	}
	
	private void info(String s) {
		l.info(s);
		bus.post(new LogEvent(label+": "+s));
	}
	
	private void warn(String s) {
		l.warn(s);
		bus.post(new LogEvent(label+": "+s));
	}
	
	private void error(String s) {
		l.error(s);
		bus.post(new LogEvent(label+": "+s));
	}
	
	private void trace(String s) {
		l.trace(s);
	}
	
	private void debug(String s) {
		l.debug(s);
	}
	
	protected void onBeaconFlash(BeaconFlash bf) {
		// called every minute by timer
		// we execute trade logic here as well
		if (started) {
			if (recoverableError1 || recoverableError2) handleRecoverableError();
			tradeLogic(true);
			
		}
		
	}
	
	
	private void handleRecoverableError() {
		if (recoverableError1 && lastRecoverableError1!=null) {
			Duration d=new Duration(lastRecoverableError1, DateTime.now());
			if (d.getStandardSeconds()>=WAIT_RECOVERABLE) handleRecoverableErrorTimeout(1);
		}
		if (recoverableError2 && lastRecoverableError2!=null) {
			Duration d=new Duration(lastRecoverableError2, DateTime.now());
			if (d.getStandardSeconds()>=WAIT_RECOVERABLE) handleRecoverableErrorTimeout(2);
		}
		
	}
	
	private void handleRecoverableErrorTimeout(int leg) {
		EClientSocket es = getSocket();
		if (es==null) return;
		warn("recoverable error timeout, cancelling order and liquidating position for leg #"+leg);
		if (leg==1) {
			if (o1!=null && opening1!=0) {
				// cancel the order for leg #1
				debug("cancelling order #"+o1.m_orderId);
				es.cancelOrder(o1.m_orderId);
				if (o2!=null && opening2!=0) {
					// cancel order for leg #2 too (if pending)
					debug("cancelling order #"+o2.m_orderId);
					es.cancelOrder(o2.m_orderId);
					opening2=0;
					o2=null;
				} else if (pos2!=0) {
					// close the position
					closePair(0, "recoverable error timeout");
				}
				opening1=0;
				o1=null;
				strategy.releasePositionLock();
			}
			recoverableError1=false;
			lastRecoverableError1=null;
		} else if (leg==2) {
			if (o2!=null && opening2!=0) {
				// cancel the order for leg #2
				debug("cancelling order #"+o2.m_orderId);
				es.cancelOrder(o2.m_orderId);
				if (o1!=null && opening1!=0) {
					// cancel order for leg #1 too (if pending)
					debug("cancelling order #"+o1.m_orderId);
					es.cancelOrder(o1.m_orderId);
					opening1=0;
					o1=null;
				} else if (pos1!=0) {
					// close the position
					closePair(0, "recoverable error timeout");
				}
				opening2=0;
				o2=null;
				strategy.releasePositionLock();
			}
			recoverableError2=false;
			lastRecoverableError2=null;
			
		}
	}
	
	
	protected void onTick(Tick t) {
		if (t.symbol.equals(strategy.getStock1())) {
			MarketRates mrates1 = ptmodel.getMr1();
			switch(t.type) {
			case TickType.BID:
				mrates1.setBid(t.price);
				break;
			case TickType.ASK:
				mrates1.setAsk(t.price);
				break;
			case TickType.LAST:
				mrates1.setLast(t.price);
				break;
			}
			if (started) tradeLogic(false);
		} else if (t.symbol.equals(strategy.getStock2())) {
			MarketRates mrates2 = ptmodel.getMr2();
			switch(t.type) {
			case TickType.BID:
				mrates2.setBid(t.price);
				break;
			case TickType.ASK:
				mrates2.setAsk(t.price);
				break;
			case TickType.LAST:
				mrates2.setLast(t.price);
				break;
			}
			if (started) tradeLogic(false);
		}
		
	}
	
	protected void onGenericTick(GenericTick t) {
		//debug(String.format("gen tick type %d of %s value %f (%s %s)", t.type, t.symbol, t.value, strategy.getStock1(), strategy.getStock2()));
		if (t.symbol.equals(strategy.getStock1())) {
			switch(t.type) {
			case TickType.SHORTABLE:
				s1Shortable=(t.value>2.5);
				debug("sym1 shortable: " + s1Shortable);
				break;
			}
		} else if (t.symbol.equals(strategy.getStock2())) {
			switch(t.type) {
			case TickType.SHORTABLE:
				s2Shortable=(t.value>2.5);
				debug("sym2 shortable: " + s2Shortable);
				break;
			}
		}
		
		
	}
	
	protected void onClosePositionRequest(ClosePositionRequest cpr) {
		if (!cpr.getStrategyUid().equals(strategy.getUid())) return; // not related to our instance
		EClientSocket es = getSocket();
		if (es==null) return;
		// user wants to close position manually
		if (!es.isConnected()) {
			return; // not connected
		}
		if (pos1!=0 || pos2!=0) {
			info("manually closing position(s) on user's request");
			closePair(0, "requested by user");
		}
		
		
	}
	
	protected void onOpenPositionRequest(OpenPositionRequest opr) {
		if (!opr.strategyUid.equals(strategy.getUid())) return; // not related to our instance
		if (opr.direction!=OpenPositionRequest.DIRECTION_LONG && opr.direction!=OpenPositionRequest.DIRECTION_SHORT) return;
		
		EClientSocket es = getSocket();
		if (es==null) return;
		
		if (!es.isConnected()) {
			return; // not connected
		}
		handleOpenPositionRequest(opr.direction);
	}
	
	private void handleOpenPositionRequest(int direction) {
		if (pos1!=0 || pos2!=0) {
			error("unable to manually open position: position already exists");
			return;
		}
		
		if (opening1!=0 || opening2!=0 || closing1!=0 || closing2!=0) {
			error("unable to manually open position: transient state");
			return;
		}
		
		info("opening position manually, direction="+direction);
		openPosition(direction, true);
		
	}
	
	private void requestManualIntervention(String reason) {
		warn("pair blocked for auto execution, reason: "+reason);
		blocked=DateTime.now();
		updateStrategyStatus();
		bus.post(new ManualInterventionRequested(DateTime.now(), strategy.getUid(), strategy.getPortfolio().getAccountCode(), reason));
	}
	
	
	protected void onError(Error e) {
		SimpleWrapper w = getWrapper();
		if (w==null) return;
		if (e.code>=1000) return;
		if (!w.getUid().equals(e.ibWrapperUid)) return; // ignore error of alien IB connection
		int on=0;
		if (o1!=null && e.id==o1.m_orderId) on=1;
		else if (o2!=null && e.id==o2.m_orderId) on=2;
				
		if (on>0) {
			handleOrderError(e);
		}
		
	}
	
	private void handleOrderError(Error e) {
		int on=0;
		if (o1!=null && e.id==o1.m_orderId) on=1;
		else if (o2!=null && e.id==o2.m_orderId) on=2;
		
		if (on==0) return;
		
		boolean recoverable=false;
		switch(e.code) {
		case Error.ERRC_ORDER_HELD:
			recoverable=true;
			break;
		
		}
		
		if (recoverable) {
			warn(String.format("recoverable problem detected with order #%d: %s", e.id, e.message));
			// we mark down the time and wait for some time
			if (on==1) {
				lastRecoverableError1=DateTime.now();
				recoverableError1=true;
			} else if (on==2) {
				lastRecoverableError2=DateTime.now();
				recoverableError2=true;
			}
			
		} else {
			warn(String.format("unrecoverable problem detected with order #%d: %s", e.id, e.message));
			// problem is not recoverable, let's assume it failed badly
			if (on==1) o1=null;
			else if (on==2) o2=null;
			strategy.releasePositionLock();
			// if there is
			if ((on==1 && opening1!=0) || (on==2 && opening2!=0)) {
				// leg opening failed
				if (on==1 && opening1!=0) opening1=0;
				if (on==2 && opening2!=0) opening2=0;
				// if there is any position, close it
				if (pos1!=0 || pos2!=0) closePair(0, "closing incomplete pair because of opening error");
				requestManualIntervention("failed to open position");
				
				
			} else if ((on==1 && closing1!=0) || (on==2 && closing2!=0)) {
				// leg closing failed
				if (on==1 && closing1!=0) closing1=0;
				if (on==2 && closing2!=0) closing2=0;
				requestManualIntervention("failed to close position");
				
			}
			
			recoverableError1=false;
			lastRecoverableError1=null;
			recoverableError2=false;
			lastRecoverableError2=null;
			
		}
		
	}
	
	protected void onOrderStatus(OrderStatus os) {
		if ((o1!=null && os.id==o1.m_orderId) || (o2!=null && os.id==o2.m_orderId)) {
			handleOrderStatus(os);
		}
		
	}
	
	private void handleOrderStatus(OrderStatus os) {
		if (o1!=null && os.id==o1.m_orderId) {
			// relevant to order #1
			if ("Filled".equals(os.status) && os.remaining==0) {
				o1=null; // destroy order
				
				// mark fill
				lastOrderFilled1 = DateTime.now();
				
				debug(String.format("order #%d has been completely filled, opening1=%d closing1=%d", os.id, opening1, closing1));
				// order has been completely filled
				recoverableError1=false;
				lastRecoverableError1=null;
				if (opening1!=0) {
					pos1=opening1;
					opening1=0;
					if (openTransaction1!=null) {
						openTransaction1.setFillConfirmed(true);
						checkTransactionEventAndPostWhenReady(1, false);
					}
					if (blocked!=null) {
						// this opening leg was filled after pair was blocked, we need attempt to close the position
						closePair(0, "closing leg after error");
					} else {
						checkNewPosition();
					}
				} else if (closing1!=0) {
					pos1=0;
					closing1=0;
					if (closeTransaction1!=null) {
						closeTransaction1.setFillConfirmed(true);
						checkTransactionEventAndPostWhenReady(1, true);
					}
					checkClosedPosition();
				}
				
				
			}
		} else if (o2!=null && os.id==o2.m_orderId) {
			// relevant to order #2
			if ("Filled".equals(os.status) && os.remaining==0) {
				o2=null; // destroy order
				
				// mark fill
				lastOrderFilled2 = DateTime.now();
				
				debug(String.format("order #%d has been completely filled, opening2=%d closing2=%d", os.id, opening2, closing2));
				// order has been completely filled
				recoverableError2=false;
				lastRecoverableError2=null;
				if (opening2!=0) {
					pos2=opening2;
					opening2=0;
					if (openTransaction2!=null) {
						openTransaction2.setFillConfirmed(true);
						checkTransactionEventAndPostWhenReady(2, false);
					}
					if (blocked!=null) {
						// this opening leg was filled after pair was blocked, we need attempt to close the position
						closePair(0, "closing leg after error");
					} else {
						checkNewPosition();
					}
				} else if (closing2!=0) {
					pos2=0;
					closing2=0;
					if (closeTransaction2!=null) {
						closeTransaction2.setFillConfirmed(true);
						checkTransactionEventAndPostWhenReady(2, true);
					}
					checkClosedPosition();
				}
				
			}
			
		}
		
	}
	
	protected void onExecutionEvent(ExecutionEvent ee) {
		SimpleWrapper w = getWrapper();
		if (w==null) return;
		if (
				ee.getReqId()!=-1
				|| ee.getExecution().m_clientId!=w.getIbClientId()
				|| !ee.getExecution().m_acctNumber.equals(strategy.getPortfolio().getAccountCode())
		) return;
		
		
		handleExecutionEvent(ee);
		
	}
	
	private void handleExecutionEvent(ExecutionEvent ee) {
		Execution ex = ee.getExecution();
		if (!executions.containsKey(ex.m_orderId)) return;
		
		// now we now, the execution belong to this object
		// let's add it to the map
		executions.get(ex.m_orderId).add(ex.m_execId);
		executionMap.put(ex.m_execId, ex.m_orderId);
		
		debug(String.format("handling execution event %s for order %d", ex.m_execId, ex.m_orderId));
		
		if (ex.m_orderId == openOrderId1) {
			if (openTransaction1!=null) {
				openTransaction1.add2CumVal(ex.m_avgPrice * (double) ex.m_shares);
				openTransaction1.markExecuted(ex.m_shares);
			}
		} else if (ex.m_orderId == openOrderId2) {
			if (openTransaction2!=null) {
				openTransaction2.add2CumVal(ex.m_avgPrice * (double) ex.m_shares);
				openTransaction2.markExecuted(ex.m_shares);
			}
			
		} else if (ex.m_orderId == closeOrderId1) {
			if (closeTransaction1!=null) {
				closeTransaction1.add2CumVal(ex.m_avgPrice * (double) ex.m_shares);
				closeTransaction1.markExecuted(ex.m_shares);
			}
			
		} else if (ex.m_orderId == closeOrderId2) {
			if (closeTransaction2!=null) {
				closeTransaction2.add2CumVal(ex.m_avgPrice * (double) ex.m_shares);
				closeTransaction2.markExecuted(ex.m_shares);
			}
			
		}
		
		
		
	}
	
	protected void onCommissionReport(CommissionReport cr) {
		boolean close = false;
		int onum = 0;
		
		if (!executionMap.containsKey(cr.m_execId)) return; // no match!
		int oid = executionMap.get(cr.m_execId);
		
		debug(String.format("handling commission report event %s for order %d", cr.m_execId, oid));
		
		// remove exec id from all lists
		executions.get(oid).remove(cr.m_execId);
		executionMap.remove(cr.m_execId);
		
		boolean execsDone = executions.get(oid).isEmpty();
		
		if (oid == openOrderId1) {
			if (openTransaction1 != null) {
				openTransaction1.add2Commissions(cr.m_commission);
				// mark transaction execConfirmed if all executions have been processed
			
				if (execsDone && openTransaction1.getToExecute()==0) openTransaction1.setExecConfirmed(true);
			}
			if (openHistoryEntry != null) {
				openHistoryEntry.commissions+=cr.m_commission;
			}
			checkTransactionEventAndPostWhenReady(1, false);
			
		} else if (oid == openOrderId2) {
			if (openTransaction2 != null) {
				openTransaction2.add2Commissions(cr.m_commission);
				// mark transaction execConfirmed if all executions have been processed
			
				if (execsDone && openTransaction2.getToExecute()==0) openTransaction2.setExecConfirmed(true);
			}
			if (openHistoryEntry != null) {
				openHistoryEntry.commissions+=cr.m_commission;
			}
			checkTransactionEventAndPostWhenReady(2, false);
			
			
		} else if (oid == closeOrderId1) {
			if (closeTransaction1 != null) {
				closeTransaction1.add2Commissions(cr.m_commission);
				closeTransaction1.add2RealizedPl(cr.m_realizedPNL);
				// mark transaction execConfirmed if all executions have been processed
			
				if (execsDone && closeTransaction1.getToExecute()==0) closeTransaction1.setExecConfirmed(true);
			}
			if (closeHistoryEntry != null) {
				closeHistoryEntry.commissions+=cr.m_commission;
				closeHistoryEntry.realizedPl+=cr.m_realizedPNL;
			}
			checkTransactionEventAndPostWhenReady(1, true);
			
			
		} else if (oid == closeOrderId2) {
			if (closeTransaction2 != null) {
				closeTransaction2.add2Commissions(cr.m_commission);
				closeTransaction2.add2RealizedPl(cr.m_realizedPNL);
				// mark transaction execConfirmed if all executions have been processed
			
				if (execsDone && closeTransaction2.getToExecute()==0) closeTransaction2.setExecConfirmed(true);
			}
			if (closeHistoryEntry != null) {
				closeHistoryEntry.commissions+=cr.m_commission;
				closeHistoryEntry.realizedPl+=cr.m_realizedPNL;
			}
			checkTransactionEventAndPostWhenReady(2, true);
			
			
		}
		
	}
	
	private void checkTransactionEventAndPostWhenReady(int num, boolean isClose) {
		TransactionEvent ev = null;
		if (num==1) {
			if (isClose) {
				ev = closeTransaction1;
				
			} else {
				ev = openTransaction1;
				
			}
		} else if (num==2) {
			if (isClose) {
				ev = closeTransaction2;
				
			} else {
				ev = openTransaction2;
				
			}
		}
		if (ev==null || !ev.isExecConfirmed() || !ev.isFillConfirmed()) return; // does not exist or not ready
		
		if (num==1) {
			postTransactionEvent1(ev);
			if (isClose) {
				closeTransactionLastSent1 = ev;
				closeTransaction1=null;
				if (closeHistoryEntry!=null) closeHistoryEntry.leg1Ready=true;
			} else {
				openTransactionLastSent1 = ev;
				openTransaction1=null;
				if (openHistoryEntry!=null) openHistoryEntry.leg1Ready=true;
				
			}
		} else {
			postTransactionEvent2(ev);
			if (isClose) {
				closeTransactionLastSent2 = ev;
				closeTransaction2=null;
				if (closeHistoryEntry!=null) closeHistoryEntry.leg2Ready=true;
			} else {
				openTransactionLastSent2 = ev;
				openTransaction2=null;
				if (openHistoryEntry!=null) openHistoryEntry.leg2Ready=true;
				
			}
		}
		
		if (isClose) {
			if (closeHistoryEntry!=null && closeHistoryEntry.leg1Ready && closeHistoryEntry.leg2Ready) postCloseHistoryEntry();
		} else {
			if (openHistoryEntry!=null && openHistoryEntry.leg1Ready && openHistoryEntry.leg2Ready) postOpenHistoryEntry();
		}
		
		
		
	}
	
	private void postTransactionEvent1(TransactionEvent ev) {
		if (ev!=null) {
			Duration d;
			if (lastOrderPlaced1!=null && lastOrderFilled1!=null)  d = new Duration(lastOrderPlaced1, lastOrderFilled1);
			else d=null;
			
			ev.setDatetime(DateTime.now());
			ev.setFillTime(d);
			bus.post(ev);
			
		}
	}
	
	private void postTransactionEvent2(TransactionEvent ev) {
		if (ev!=null) {
			Duration d;
			if (lastOrderPlaced2!=null && lastOrderFilled2!=null)  d = new Duration(lastOrderPlaced2, lastOrderFilled2);
			else d=null;
			
			ev.setDatetime(DateTime.now());
			ev.setFillTime(d);
			bus.post(ev);
			ev=null;
			
		}
	}
	
	private void postOpenHistoryEntry() {
		//debug("posting open history entry before check");
		
		
		if (openHistoryEntry!=null) {
			openHistoryEntry.datetime=DateTime.now();
			
			debug("posting open history entry");
			bus.post(openHistoryEntry);
			openHistoryEntryLastSent=openHistoryEntry;
			openHistoryEntry=null;
			
			
			lastOrderPlaced1=null; lastOrderFilled1=null; lastOrderPlaced2=null; lastOrderFilled2=null;
		}
		
	}
	
	private void postCloseHistoryEntry() {
		
		//debug("posting close history entry before check");
		if (closeHistoryEntry!=null) {
			
			closeHistoryEntry.datetime=DateTime.now();
			if (strategy.getLastOpenEquity()>=1) closeHistoryEntry.realizedPlPerc=100*closeHistoryEntry.realizedPl/strategy.getLastOpenEquity();
			// post history event
			debug("posting close history entry");
			bus.post(closeHistoryEntry);
			closeHistoryEntryLastSent=closeHistoryEntry;
			closeHistoryEntry=null;
			
			
			lastOrderPlaced1=null; lastOrderFilled1=null; lastOrderPlaced2=null; lastOrderFilled2=null;
			
		}
		
	}
	
	
	protected void onPortfolioUpdate(PortfolioUpdate pu) {
		if (!strategy.getPortfolio().getAccountCode().equals(pu.accountCode)) return; // different account
		if (c1.equalsInSymbolTypeCurrency(pu.contract) && opening1==0 && closing1==0) {
			handlePortfolioUpdate(pu);
		} else if (c2.equalsInSymbolTypeCurrency(pu.contract) && opening2==0 && closing2==0) {
			handlePortfolioUpdate(pu);
		}
		
	}
	
	private void handlePortfolioUpdate(PortfolioUpdate pu) {
		if (c1.equalsInSymbolTypeCurrency(pu.contract) && opening1==0 && closing1==0) {
			// portfolio update is relevant for the first leg
			lastPortfUpdate1=pu;
			boolean allowSync = true;
			if (lastOrderFilled1!=null) {
				Duration d = new Duration(lastOrderFilled1, DateTime.now());
				if (d.getStandardSeconds()<AFTER_FILL_POS_SYNC_LOCK) allowSync=false;
			}
			if (pu.qty!=pos1) {
				// different qty at IB than in this object
				if (pos1==0) {
					if (allowSync) {
						info(String.format("accepting existing position for %s,  qty=%d", pu.contract.m_symbol, pu.qty));
						// fully accept the position from IB
						pos1 = pu.qty;
						if (pos2 != 0 && position == 0) {
							position = (pos1 > 0) ? 1 : -1;
							info(String.format("new pair position accepted, direction = %d", position));
							//manager.reportNewPosition(strategy, true);
							strategy.setStatus((position == 1) ? PairStrategy.STATUS_LONG : PairStrategy.STATUS_SHORT);

							// we need to lock the model if needed! we NEVER manipulate with the state itself here, we just accept it!!!!
							if (ptmodel instanceof LockableStateModel && strategy.getModelState() != null) {
								// lock the model!
								try {
									info(String.format("locking model state to: %s", strategy.getModelState().toString()));
									((LockableStateModel) ptmodel).lockState(strategy.getModelState());
								} catch (RuntimeException e) {
									error(String.format("failed to lock model state to %s: %s", strategy.getModelState().toString(), e.getMessage()));
								}
							}
						}
					}
					
				} else if (ready && pu.qty==0 && pos1!=0) {
					// position was closed externally!
					info(String.format("position closed externally for %s", pu.contract.m_symbol));
					pos1=0;
					if (pos2==0 && position!=0) {
						info("pair position closed externally, state accepted");
						lastPositionClosed=DateTime.now(); // start cooldown timer
						position=0;
						//manager.removePosition(strategy);
                        if (ptmodel instanceof LockableStateModel) {
                            // unlock the model in any case
                            // we don't manipulate the state here
                            try {
                                ((LockableStateModel) ptmodel).unlockState();
                            } catch (RuntimeException e) {
                                error(String.format("failed to unlock model state: %s", e.getMessage()));
                            }
                        }
						updateStrategyStatus();
					}
				} else if (ready && pos1*pu.qty>0) {
					if (allowSync) {
						// both pos1 and pu.qty are non zero and have same sign = possible split event or manual correction? we accept new qty
						info(String.format("split or manual correction detected on %s, syncing qty from %d to %d", pu.contract.m_symbol, pos1, pu.qty));
						pos1=pu.qty;
					}
				} else {
					warn(String.format("position mismatch %s (IB says %d, internal state=%d)", pu.contract.m_symbol, pu.qty, pos1));
					requestManualIntervention("position mismatch");
				}
			}
			processPortfolioUpdate();
		} else if (c2.equalsInSymbolTypeCurrency(pu.contract) && opening2==0 && closing2==0) {
			// portfolio update is relevant for the second leg
			lastPortfUpdate2=pu;
			boolean allowSync = true;
			if (lastOrderFilled2!=null) {
				Duration d = new Duration(lastOrderFilled2, DateTime.now());
				if (d.getStandardSeconds()<AFTER_FILL_POS_SYNC_LOCK) allowSync=false;
			}
			if (pu.qty!=pos2) {
				// different qty at IB than in this object
				if (pos2==0) {
                    if (allowSync) {
                        info(String.format("accepting existing position for %s,  qty=%d", pu.contract.m_symbol, pu.qty));
                        // fully accept the position from IB
                        pos2 = pu.qty;
                        if (pos1 != 0 && position == 0) {
                            position = (pos1 > 0) ? 1 : -1;
                            info(String.format("new pair position accepted, direction = %d", position));
                            //manager.reportNewPosition(strategy, true);
                            strategy.setStatus((position == 1) ? PairStrategy.STATUS_LONG : PairStrategy.STATUS_SHORT);
                            // we need to lock the model if needed! we NEVER manipulate with the state itself here, we just accept it!!!!
                            if (ptmodel instanceof LockableStateModel && strategy.getModelState() != null) {
                                // lock the model!
                                try {
                                    info(String.format("locking model state to: %s", strategy.getModelState().toString()));
                                    ((LockableStateModel) ptmodel).lockState(strategy.getModelState());
                                } catch (RuntimeException e) {
                                    error(String.format("failed to lock model state to %s: %s", strategy.getModelState().toString(), e.getMessage()));
                                }
                            }
                        }
                    }
					
				} else if (ready && pu.qty==0 && pos2!=0) {
					// position was closed externally!
					info(String.format("position closed externally for %s", pu.contract.m_symbol));
					pos2=0;
					if (pos1==0 && position!=0) {
						info("pair position closed externally, state accepted");
						lastPositionClosed=DateTime.now(); // start cooldown timer
						position=0;
						//manager.removePosition(strategy);
                        if (ptmodel instanceof LockableStateModel) {
                            // unlock the model in any case
                            // we don't manipulate the state here
                            try {
                                ((LockableStateModel) ptmodel).unlockState();
                            } catch (RuntimeException e) {
                                error(String.format("failed to unlock model state: %s", e.getMessage()));
                            }
                        }
						updateStrategyStatus();
					}
				} else if (ready && pos2*pu.qty>0) {
					if (allowSync) {
						// both pos2 and pu.qty are non zero and have same sign = possible split event or manual correction? we accept new qty
						info(String.format("split or manual correction detected on %s, syncing qty from %d to %d", pu.contract.m_symbol, pos2, pu.qty));
						pos2=pu.qty;
					}
				} else {
					warn(String.format("position mismatch %s (IB says %d, internal state=%d)", pu.contract.m_symbol, pu.qty, pos2));
					requestManualIntervention("position mismatch");
				}
			}
			processPortfolioUpdate();
		}
	}
	
	/**
	 * Returns true in black hour.
	 * Black hour = midnight EST, where IB performs maintenance and account data are not reliable
	 * @return
	 */
	private boolean isBlackHour() {
		return DateTime.now().withZone(DateTimeZone.forID("America/New_York")).getHourOfDay()==0;
	}
	
	private void processPortfolioUpdate() {
		//l.debug("process folio update");
		if (lastPortfUpdate1!=null && lastPortfUpdate2!=null) {
			//l.debug("position pl: "+lastPortfUpdate1.pl+" "+lastPortfUpdate2.pl);
			setPositionPl(lastPortfUpdate1.pl+lastPortfUpdate2.pl);
			if (position!=0 && !isBlackHour()) bus.post(new StrategyPlUpdated(DateTime.now(), strategy.getUid(), strategy.getPortfolio().getAccountCode(), lastPortfUpdate1.pl+lastPortfUpdate2.pl));
			if (!ready) {
				debug("ready after first portfolio updates");
				ready=true;
			}
		}
	}
	
	private void checkNewPosition() {
		//System.out.println("checkNewPosition");
		if (pos1!=0 && pos2!=0) {
			position=(pos1>0)?1:-1;
			info(String.format("new pair position completely opened, direction = %d", position));
			//manager.reportNewPosition(strategy);
			strategy.releasePositionLock();
			strategy.setStatus((position==1)?PairStrategy.STATUS_LONG:PairStrategy.STATUS_SHORT);
			setLastPositionOpened(DateTime.now());
			strategy.setLastOpenEquity(strategy.getPortfolio().getEquity());
			setPositionPl(0);

			if (ptmodel instanceof LockableStateModel) {
				// get model state and lock it
				try {
					AbstractPairTradingModelState mstate = ((LockableStateModel) ptmodel).getCurrentState();
					if (mstate!=null) ((LockableStateModel) ptmodel).lockState(mstate);
					strategy.setModelState(mstate);
				} catch (RuntimeException e) {
					error(String.format("failed to lock model state: %s", e.getMessage()));
					strategy.setModelState(null);
				}

			} else strategy.setModelState(null);
			
			bus.post(new PairStateUpdated(strategy, DateTime.now()));
			
		}
	}
	
	
	private void checkClosedPosition() {
		if (pos1==0 && pos2==0) {
			position=0;
			info("pair position completely closed");
			//manager.removePosition(strategy);
			//setLastPositionOpened(null); we cannot clear the flag here, because PDT rules would stop working!!!
			setPositionPl(0);
			updateStrategyStatus();
			
			lastPositionClosed=DateTime.now();

            if (ptmodel instanceof LockableStateModel && strategy.getModelState() != null) {
                // unlock model and reset the strategy state
                try {
                    ((LockableStateModel) ptmodel).unlockState();
                    strategy.setModelState(null);
                    bus.post(new PairStateUpdated(strategy, DateTime.now()));
                } catch (RuntimeException e) {
                    error(String.format("failed to unlock model state: %s", e.getMessage()));
                }
            }
		}
	}
	
	private void closePair(double zscore, String reason) {
		SimpleWrapper w = getWrapper();
		if (w==null) return; // no wrapper
		EClientSocket es = getSocket();
		if (es==null) return; // no socket
		if (!es.isConnected()) return; // not connected
		int qty1 = Math.abs(pos1);
		int qty2 = Math.abs(pos2);
		if (qty1>0 || qty2>0) {
			lastExitReason=reason;
			if (ptmodel.isPricesInitialized()) ptmodel.storeReversalState();
			info(String.format("transmitting orders for CLOSE pair position qty1=%d qty2=%d zscore=%f reason:%s", qty1, qty2, zscore, reason));
			
			closeHistoryEntry = new HistoryEntry(strategy.getUid(), strategy.getPortfolio().getAccountCode(), null, strategy.getStock1(), strategy.getStock2(), HistoryEntry.ACTION_CLOSED, zscore, reason);
		}
		
		MarketRates mrates1 = ptmodel.getMr1();
		MarketRates mrates2 = ptmodel.getMr2();
		
		if (qty1>0) {
			closeTransaction1 = new TransactionEvent(strategy.getUid(), strategy.getPortfolio().getAccountCode(), strategy.getStock1(), (pos1>0)?TransactionEvent.DIRECTION_SHORT:TransactionEvent.DIRECTION_LONG, qty1, (pos1>0)?mrates1.getBid():mrates1.getAsk());
			o1 = new Order();
			o1.m_orderType="MKT";
			o1.m_totalQuantity = qty1;
			debug("acquiring orderId lock");
			w.lockOrderId(); // lock order ID subsystem
			try {
				o1.m_orderId = w.getNextId();
				o1.m_clientId = w.getIbClientId();
				o1.m_action = (pos1>0)?"SELL":"BUY";
				o1.m_account = strategy.getPortfolio().getAccountCode();
				o1.m_orderRef = String.format("ptl close %s-%s", c1.m_symbol, c2.m_symbol);
				debug(String.format("transmit %s order symbol=%s qty=%d oid=%d account=%s", o1.m_action, c1.m_symbol, o1.m_totalQuantity, o1.m_orderId, o1.m_account));
				closing1=-pos1;
				closeOrderId1 = o1.m_orderId;
				executions.put(o1.m_orderId, new HashSet<String>());
				
				lastOrderPlaced1=DateTime.now();
				
				es.placeOrder(o1.m_orderId, c1, o1);
				Thread.sleep(SLEEP_AFTER_POST);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				debug("releasing orderId lock");
				w.unlockOrderId();
			}
		}
		
		if (qty2>0) {
			closeTransaction2 = new TransactionEvent(strategy.getUid(), strategy.getPortfolio().getAccountCode(), strategy.getStock2(), (pos2>0)?TransactionEvent.DIRECTION_SHORT:TransactionEvent.DIRECTION_LONG, qty2, (pos2>0)?mrates2.getBid():mrates2.getAsk());
			o2 = new Order();
			o2.m_orderType="MKT";
			o2.m_totalQuantity = qty2;
			debug("acquiring orderId lock");
			w.lockOrderId(); // lock order ID subsystem
			try {
				o2.m_orderId = w.getNextId();
				o2.m_clientId = w.getIbClientId();
				o2.m_action = (pos2>0)?"SELL":"BUY";
				o2.m_account = strategy.getPortfolio().getAccountCode();
				o2.m_orderRef = String.format("ptl close %s-%s", c1.m_symbol, c2.m_symbol);
				debug(String.format("transmit %s order symbol=%s qty=%d oid=%d account=%s", o2.m_action, c2.m_symbol, o2.m_totalQuantity, o2.m_orderId, o2.m_account));
				closing2=-pos2;
				closeOrderId2 = o2.m_orderId;
				executions.put(o2.m_orderId, new HashSet<String>());
				
				lastOrderPlaced2=DateTime.now();
				
				es.placeOrder(o2.m_orderId, c2, o2);
				Thread.sleep(SLEEP_AFTER_POST);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				debug("releasing orderId lock");
				w.unlockOrderId();
			}
		}
		
	}
	
	
	private boolean histDataReady() {
		if (lastDataObtained==null) {
			requestHistData();
			return false;
		}
		
		if (DateTime.now().withZone(strategy.getTimezone()).getDayOfMonth()!=lastDataObtained.withZone(strategy.getTimezone()).getDayOfMonth()) {
			debug("historical data require to update");
			requestHistData();
			return false;
		}
		// data are ready
		return true;
	}
	
	private void requestHistData() {
		if (!provider.isConnected()) return; // not connected
		if (lastDataRequested!=null) {
			Duration d = new Duration(lastDataRequested, DateTime.now());
			if (d.getStandardMinutes()<RETRY_HISTORICAL_MINUTES) return;
		}
		dataRequestId = provider.allocReqId();
		debug("requesting historical data, reqid = "+dataRequestId);
		lastDataRequested=DateTime.now();
		lastHistDataRequestFailed = false;
		provider.requestData();
		
	}
	
	// returns true if we are free to open position, false if pdt rules prevent that
	private boolean checkPdtRules() {
		if (strategy.getLastOpened()==null) return true; // when we don't have an idea when the last position was opened, we can't check anything
		int pdt = strategy.getPortfolioPdtEnable();
		if (pdt==Portfolio.PDT_DISABLE || (pdt==Portfolio.PDT_ENABLE_25K && strategy.getPortfolio().getEquity()<MIN_DAYTRADING_EQUITY)) {
			DateTime dt1 = DateTime.now().withZone(strategy.getTimezone());
			DateTime dt2 = strategy.getLastOpened().withZone(strategy.getTimezone()); 
			return (dt1.getDayOfMonth()!=dt2.getDayOfMonth() || dt1.getMonthOfYear()!=dt2.getMonthOfYear() || dt1.getYear()!=dt2.getYear());
		}
		return true;
	}
	
	private void openPosition(int signal, boolean isManual) {
		SimpleWrapper w = getWrapper();
		if (w==null) return; // no wrapper
		EClientSocket es = getSocket();
		if (es==null) return; // no socket
		
		MarketRates mrates1 = ptmodel.getMr1();
		MarketRates mrates2 = ptmodel.getMr2();
		// check if the short stock is shortable
		if ((signal==PairTradingModel.SIGNAL_LONG && (s2Shortable || isManual)) || (signal==PairTradingModel.SIGNAL_SHORT && (s1Shortable || isManual))) {
			// check if account manager approves opening 
			if (strategy.acquirePositionLock()) {
				// account manager approved to open position
				double allocMargin = strategy.getPortfolio().allocateMargin(strategy.getSlotOccupation());
				if (signal==PairTradingModel.SIGNAL_LONG) {
					// open stock 1 long, stock2 short
					QtyQty qq = ptmodel.calcLegQtys(allocMargin, strategy.getMarginPerc1()/100, strategy.getMarginPerc2()/100, mrates1.getAsk(), mrates2.getBid());
					if (qq.qty1>=0 && qq.qty2>=0) {
						info(String.format("transmitting orders for LONG pair position alloc=%f qty1=%d qty2=%d", allocMargin, qq.qty1, qq.qty2));
						
						openHistoryEntry = new HistoryEntry(strategy.getUid(), strategy.getPortfolio().getAccountCode(), null, strategy.getStock1(), strategy.getStock2(), HistoryEntry.ACTION_OPENED_LONG, 
								ptmodel.getLastZscoreInvolved(), "");
						openTransaction1 = new TransactionEvent(strategy.getUid(), strategy.getPortfolio().getAccountCode(), strategy.getStock1(), TransactionEvent.DIRECTION_LONG, qq.qty1, mrates1.getAsk());
						
						// create and transmit orders
						o1 = new Order();
						o1.m_orderType="MKT";
						o1.m_totalQuantity = qq.qty1;
						debug("acquiring orderId lock");
						w.lockOrderId(); // lock order ID subsystem
						try {
							o1.m_orderId = w.getNextId();
							o1.m_clientId = w.getIbClientId();
							o1.m_action = "BUY";
							o1.m_account = strategy.getPortfolio().getAccountCode();
							o1.m_orderRef = String.format("ptl open long %s-%s", c1.m_symbol, c2.m_symbol);
							debug(String.format("transmit BUY order symbol=%s qty=%d oid=%d account=%s", c1.m_symbol, o1.m_totalQuantity, o1.m_orderId, o1.m_account));
							opening1=qq.qty1;
							openOrderId1 = o1.m_orderId;
							executions.put(o1.m_orderId, new HashSet<String>());
							
							lastOrderPlaced1=DateTime.now();
							
							es.placeOrder(o1.m_orderId, c1, o1);
							Thread.sleep(SLEEP_AFTER_POST);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} finally {
							debug("releasing orderId lock");
							w.unlockOrderId();
						}
						
						openTransaction2 = new TransactionEvent(strategy.getUid(), strategy.getPortfolio().getAccountCode(), strategy.getStock2(), TransactionEvent.DIRECTION_SHORT, qq.qty2, mrates2.getBid());
						o2 = new Order();
						o2.m_orderType="MKT";
						o2.m_totalQuantity = qq.qty2;
						debug("acquiring orderId lock");
						w.lockOrderId(); // lock order ID subsystem
						try {
							o2.m_orderId = w.getNextId();
							o2.m_clientId = w.getIbClientId();
							o2.m_action = "SELL";
							o2.m_account = strategy.getPortfolio().getAccountCode();
							o2.m_orderRef = String.format("ptl open long %s-%s", c1.m_symbol, c2.m_symbol);
							debug(String.format("transmit SELL order symbol=%s qty=%d oid=%d account=%s", c2.m_symbol, o2.m_totalQuantity, o2.m_orderId, o2.m_account));
							opening2=-qq.qty2;
							openOrderId2 = o2.m_orderId; 
							executions.put(o2.m_orderId, new HashSet<String>());
							
							lastOrderPlaced2=DateTime.now();
							
							
							es.placeOrder(o2.m_orderId, c2, o2);
							Thread.sleep(SLEEP_AFTER_POST);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} finally {
							debug("releasing orderId lock");
							w.unlockOrderId();
						}
					}
					
				} else if (signal==PairTradingModel.SIGNAL_SHORT) {
					// open stock 1 short, stock2 long
					QtyQty qq = ptmodel.calcLegQtys(allocMargin, strategy.getMarginPerc1()/100, strategy.getMarginPerc2()/100, mrates1.getBid(), mrates2.getAsk());
					if (qq.qty1>=0 && qq.qty2>=0) {
						info(String.format("transmitting orders for SHORT pair position alloc=%f qty1=%d qty2=%d", allocMargin, qq.qty1, qq.qty2));
						
						openHistoryEntry = new HistoryEntry(strategy.getUid(), strategy.getPortfolio().getAccountCode(), null, strategy.getStock1(), strategy.getStock2(), HistoryEntry.ACTION_OPENED_SHORT,
								ptmodel.getLastZscoreInvolved(), "");
						openTransaction1 = new TransactionEvent(strategy.getUid(), strategy.getPortfolio().getAccountCode(), strategy.getStock1(), TransactionEvent.DIRECTION_SHORT, qq.qty1, mrates1.getBid());
						// create and transmit orders
						o1 = new Order();
						o1.m_orderType="MKT";
						o1.m_totalQuantity = qq.qty1;
						debug("acquiring orderId lock");
						w.lockOrderId(); // lock order ID subsystem
						try {
							o1.m_orderId = w.getNextId();
							o1.m_clientId = w.getIbClientId();
							o1.m_action = "SELL";
							o1.m_account = strategy.getPortfolio().getAccountCode();
							o1.m_orderRef = String.format("ptl open short %s-%s", c1.m_symbol, c2.m_symbol);
							debug(String.format("transmit SELL order symbol=%s qty=%d oid=%d account=%s", c1.m_symbol, o1.m_totalQuantity, o1.m_orderId, o1.m_account));
							opening1=-qq.qty1;
							openOrderId1 = o1.m_orderId;
							executions.put(o1.m_orderId, new HashSet<String>());
							
							lastOrderPlaced1=DateTime.now();
							
							es.placeOrder(o1.m_orderId, c1, o1);
							Thread.sleep(SLEEP_AFTER_POST);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} finally {
							debug("releasing orderId lock");
							w.unlockOrderId();
						}
						
						openTransaction2 = new TransactionEvent(strategy.getUid(), strategy.getPortfolio().getAccountCode(), strategy.getStock2(), TransactionEvent.DIRECTION_LONG, qq.qty2, mrates2.getAsk());
						o2 = new Order();
						o2.m_orderType="MKT";
						o2.m_totalQuantity = qq.qty2;
						debug("acquiring orderId lock");
						w.lockOrderId(); // lock order ID subsystem
						try {
							o2.m_orderId = w.getNextId();
							o2.m_clientId = w.getIbClientId();
							o2.m_action = "BUY";
							o2.m_account = strategy.getPortfolio().getAccountCode();
							o2.m_orderRef = String.format("ptl open short %s-%s", c1.m_symbol, c2.m_symbol);
							debug(String.format("transmit BUY order symbol=%s qty=%d oid=%d account=%s", c2.m_symbol, o2.m_totalQuantity, o2.m_orderId, o2.m_account));
							opening2=qq.qty2;
							openOrderId2 = o2.m_orderId;
							executions.put(o2.m_orderId, new HashSet<String>());
							
							lastOrderPlaced2=DateTime.now();
							
							es.placeOrder(o2.m_orderId, c2, o2);
							Thread.sleep(SLEEP_AFTER_POST);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} finally {
							debug("releasing orderId lock");
							w.unlockOrderId();
						}
					}
					
				}
				
			} else {
				if (isManual) error("unable to open position manually: money management rules prevent opening");
				else reportStatus(CoreStatus.ENTRY_MANAGER);
			}
		} else reportStatus(CoreStatus.ENTRY_SHORTABILITY);
		
	}
	
	private void entryLogic(boolean fromBeacon) {
		// check if model is initialized
		if (!ptmodel.isPricesInitialized()) {
			reportStatus(CoreStatus.ENTRY_HIST_DATA);
			return; // historical data not ready
		}
		
		boolean belowMinExpectation = false;
		MarketRates mrates1 = ptmodel.getMr1();
		MarketRates mrates2 = ptmodel.getMr2();
		int mstatus1 = mrates1.getMarketStatus();
		int mstatus2 = mrates2.getMarketStatus();
		
		if (mstatus1 == MarketRates.MARKET_STATUS_OK && mstatus2 == MarketRates.MARKET_STATUS_OK) {
			double allocMargin = strategy.getPortfolio().allocateMargin(strategy.getSlotOccupation());

			double profitp = ptmodel.getProfitPotential(allocMargin, strategy.getMarginPerc1()/100, strategy.getMarginPerc2()/100);
			strategy.setProfitPotential(profitp);
			if (strategy.isMinExpectationEnabled()) {
				if (profitp<strategy.getMinExpectation()) {
					belowMinExpectation = true;
				}
			}
		}

        if (strategy.getTradingStatus()!=PairStrategy.TRADING_STATUS_ACTIVE || strategy.getPortfolioMasterStatus()!=Portfolio.MASTER_STATUS_ACTIVE) {
            reportStatus(CoreStatus.MAINTAIN_ONLY);
            return;
        }

		if (!tradingHoursEnabled() && !entryHoursEnabled()) {
			reportStatus(CoreStatus.ENTRY_HOURS);
			return;
		}
		
		if (!histDataReady()) {
			reportStatus(CoreStatus.ENTRY_HIST_DATA);
			return; // historical data not ready
		}
		
		if (checkPriceMoveRatioLimit()) return;
		
		if (!entryHoursEnabled()) {
			reportStatus(CoreStatus.ENTRY_HOURS);
			return;
		}
		
		// we don't have any position open, let's check entry rules
		// check exchange activity
		if (!activityDetector.isExchangeActive(exchange1) || !activityDetector.isExchangeActive(exchange2)) {
			reportStatus(CoreStatus.EXCHANGE_DEAD);
			return;
		}
		
		
		if (!checkPdtRules()) {
			reportStatus(CoreStatus.ENTRY_PDT);
			return;
		}
		
		if (strategy.isMinPriceEnabled()) {
			double mp = strategy.getMinPrice();
			if (
					mrates1.getAsk()<mp
					|| mrates2.getAsk()<mp
					|| mrates1.getBid()<mp
					|| mrates2.getBid()<mp
			) {
				reportStatus(CoreStatus.ENTRY_MIN_PRICE);
				return;
			}
		}
		
		// check cooldown period
		if (lastPositionClosed!=null) {
			Duration d = new Duration(lastPositionClosed, DateTime.now());
			if (d.getStandardSeconds()<COOLDOWN_AFTER_CLOSE) {
				reportStatus(CoreStatus.COOLDOWN);
				return;
			}
		}
		
		// check reversal rule
		if (!strategy.isAllowReversals() && lastPositionClosed!=null) {
			DateTime dt1 = DateTime.now().withZone(strategy.getTimezone());
			DateTime dt2 = lastPositionClosed.withZone(strategy.getTimezone());
			if (dt1.getDayOfMonth()==dt2.getDayOfMonth() && dt1.getMonthOfYear()==dt2.getMonthOfYear() && dt1.getYear()==dt2.getYear()) {
				if (ptmodel.checkReversalCondition()) {
					reportStatus(CoreStatus.ENTRY_REVERSAL_NOT_ALLOWED);
					return;
				}
			}
			
		}
		
		if (mstatus1 != MarketRates.MARKET_STATUS_OK || mstatus2 != MarketRates.MARKET_STATUS_OK) {
			// we don't attempt to continue if market status does not permit
			reportStatus(CoreStatus.MARKET_STATUS_NOT_OK);
			return;
			
		}
		
		if (belowMinExpectation) {
			reportStatus(CoreStatus.ENTRY_PROFIT_POTENTIAL);
			return;
		}
		
		// following code is executed for quote events only
		
		int signal = ptmodel.entryLogic();
		//System.out.println(signal);
		
		if (signal==PairTradingModel.SIGNAL_LONG && strategy.getAllowPositions()!=PairStrategy.ALLOW_POSITIONS_BOTH && strategy.getAllowPositions()!=PairStrategy.ALLOW_POSITIONS_LONG) {
			// opening long position is denied on this pair
			reportStatus(CoreStatus.ENTRY_SIGNAL);
			return;
		}
		
		if (signal==PairTradingModel.SIGNAL_SHORT && strategy.getAllowPositions()!=PairStrategy.ALLOW_POSITIONS_BOTH && strategy.getAllowPositions()!=PairStrategy.ALLOW_POSITIONS_SHORT) {
			// opening short position is denied on this pair
			reportStatus(CoreStatus.ENTRY_SIGNAL);
			return;
		}
		
		if (signal==PairTradingModel.SIGNAL_LONG || signal==PairTradingModel.SIGNAL_SHORT) {
			// model sends signal to open a position
			openPosition(signal, false);
			
		} else reportStatus(CoreStatus.ENTRY_SIGNAL);
		
		
	}
	
	private void exitLogic(boolean fromBeacon) {
		if (!ptmodel.isPricesInitialized()) {
			reportStatus(CoreStatus.EXIT_HIST_DATA);
			return; // historical data not ready
		}
		
		MarketRates mrates1 = ptmodel.getMr1();
		MarketRates mrates2 = ptmodel.getMr2();
		int mstatus1 = mrates1.getMarketStatus();
		int mstatus2 = mrates2.getMarketStatus();
		
		strategy.setProfitPotential(0);
		
		if (!tradingHoursEnabled() && !exitHoursEnabled()) {
			reportStatus(CoreStatus.EXIT_HOURS);
			return;
		}
		
		if (!histDataReady()) {
			reportStatus(CoreStatus.EXIT_HIST_DATA);
			return; // historical data not ready
		}
		
		if (checkPriceMoveRatioLimit()) return;
		
		if (!exitHoursEnabled()) {
			reportStatus(CoreStatus.EXIT_HOURS);
			return;
		}
		
		if (mstatus1 != MarketRates.MARKET_STATUS_OK || mstatus2 != MarketRates.MARKET_STATUS_OK) {
			// we don't attempt to continue if market status does not permit
			reportStatus(CoreStatus.MARKET_STATUS_NOT_OK);
			return;
			
		}
		
		// check exchange activity
		if (!activityDetector.isExchangeActive(exchange1) || !activityDetector.isExchangeActive(exchange2)) {
			reportStatus(CoreStatus.EXCHANGE_DEAD);
			return;
		}
		
		// PDT protection
		if (!checkPdtRules()) {
			reportStatus(CoreStatus.EXIT_PDT);
			return;
		}
		
		// timeout logic
		if (strategy.getLastOpened()!=null && strategy.getMaxDays()>0 && strategy.isMaxDaysEnabled()) {
			Duration d = new Duration(strategy.getLastOpened(), DateTime.now());
			if (d.getStandardDays()>=strategy.getMaxDays()) {
				closePair(ptmodel.getLastZscoreInvolved(), "max days rule triggered");
				return;
			}
		}
		
		boolean close = ptmodel.exitLogic(position);
		if (!close) {
			reportStatus(CoreStatus.EXIT_SIGNAL);
			return;
		}
		
		// model send signal to close position
		
		// check the min P/L rule
		if (strategy.isMinPlToCloseEnabled() && positionPl<strategy.getMinPlToClose()) {
			// we cannot close position atm, we wait for P/L rule
			reportStatus(CoreStatus.EXIT_WAIT_PL_RULE);
			return;
		}
		
		// CLOSE PAIR
		closePair(ptmodel.getLastZscoreInvolved(), "exit signal triggered");
		
	}
	
	private void updateStrategyStatus() {
		if (blocked!=null || strategy.getTradingStatus()==PairStrategy.TRADING_STATUS_INACTIVE || strategy.getPortfolioMasterStatus()==Portfolio.MASTER_STATUS_INACTIVE) {
			strategy.setStatus(PairStrategy.STATUS_INACTIVE);
		} else {
			strategy.setStatus(PairStrategy.STATUS_ACTIVE);
		}
	}
	
	private boolean wasResumedTheSameDay(final DateTime dt) {
		if (lastResumed == null) return false;
		DateTime dt1 = dt.withZone(strategy.getTimezone());
		DateTime dt2 = lastResumed.withZone(strategy.getTimezone());
		
		return dt1.getDayOfYear() == dt2.getDayOfYear();
		
	}
	
	private boolean checkPriceMoveRatioLimit() {
		if (ptmodel.isPricesInitialized()) {
			double limit2 = 1 / PRICE_MOVE_RATIO_LIMIT;
			MarketRates mrates1 = ptmodel.getMr1();
			MarketRates mrates2 = ptmodel.getMr2();
			
			double last1 = mrates1.getLast();
			if (lastHistPriceDate1 != null && lastHistPrice1 > 0.00001 && last1 > 0.00001) {
				double ratio = last1 /  lastHistPrice1;
				if (ratio > PRICE_MOVE_RATIO_LIMIT || ratio < limit2) {
					
					if (!wasResumedTheSameDay(DateTime.now())) {
						
						requestManualIntervention("suspicious price ratio for stock 1: "+ratio);
						return true;
					}
					
				}
				
			}
			double last2 = mrates2.getLast();
			if (lastHistPriceDate2 != null && lastHistPrice2 > 0.00001 && last2 > 0.00001) {
				double ratio = last2 /  lastHistPrice2;
				if (ratio > PRICE_MOVE_RATIO_LIMIT || ratio < limit2) {
					if (!wasResumedTheSameDay(DateTime.now())) {
						
						requestManualIntervention("suspicious price ratio for stock 2: "+ratio);
						return true;
					}
					
				}
				
			}
		
		}
		return false;
		
	}
	
	
	private void tradeLogic(boolean fromBeacon) {
		if (!ready) {
			reportStatus(CoreStatus.NOT_READY);
			return;
		}
		
		if (fromBeacon) {
			strategy.updateDaysRemaining();
		}
		
		if (blocked!=null) {
			reportStatus(CoreStatus.BLOCKED);
			return;
		}
		
		if (ptmodel instanceof PairTradingModelDummy) {
			reportStatus(CoreStatus.UNSUPPORTED_MODEL);
			return;
		}
		
		if (unsupportedStrategyFeatures) {
			reportStatus(CoreStatus.UNSUPPORTED_FEATURES);
			return;
			
		}
		
		
		EClientSocket es = getSocket();
		
		if (es==null) {
			reportStatus(CoreStatus.SOCKET_DISCOVERY);
			return; // no socket
		}
		
		if (!es.isConnected()) {
			reportStatus(CoreStatus.NOT_CONNECTED);
			return; // not connected
		}
		
		if (lastHistDataRequestFailed) {
			requestHistData();
		}
		
		// here we display z-score and RSI immediately when we can
		if (ptmodel.isPricesInitialized()) {
			MarketRates mrates1 = ptmodel.getMr1();
			MarketRates mrates2 = ptmodel.getMr2();
			int mstatus1 = mrates1.getMarketStatus();
			int mstatus2 = mrates2.getMarketStatus();
			
			if (mstatus1 == MarketRates.MARKET_STATUS_OK && mstatus2 == MarketRates.MARKET_STATUS_OK) {
				strategy.setZscoreBid(ptmodel.getZScore(PairTradingModel.ZSCORE_BID));
                strategy.setZscoreAsk(ptmodel.getZScore(PairTradingModel.ZSCORE_ASK));
				if (ptmodel instanceof PairTradingModelRatio) strategy.setRsi(((PairTradingModelRatio) ptmodel).getRsi());
			}
			
		}
		
		if (o1!=null || o2!=null) {
			reportStatus(CoreStatus.TRANSIENT);
			return; // state in-between opening or closing
		}
		
		// this is the place we need to solve the Open and Close button availability logic
		boolean openAvailable = false, closeAvailable = false;
		if (tradingHoursEnabled() && activityDetector.isExchangeActive(exchange1) && activityDetector.isExchangeActive(exchange2)) {
			// trading hours are enabled and there is some activity present on the exchange
			
			// ok, we enable Open Position button if there is no position
			if (pos1==0 && pos2==0) openAvailable = true;
			// we enable Close Position button if we have position on one or both legs
			if (pos1!=0 || pos2!=0) closeAvailable = true;
		}
		strategy.setOpenable(openAvailable);
		strategy.setCloseable(closeAvailable);
		// end of manual Open Close availability logic 
		
		if ((pos1!=0 && pos2==0) || (pos1==0 && pos2!=0)) {
			reportStatus(CoreStatus.ONE_LEG);
			return; //only one position is open, wtf? rather don't do anything
		}
		
		if (strategy.getTradingStatus()==PairStrategy.TRADING_STATUS_INACTIVE || strategy.getPortfolioMasterStatus()==Portfolio.MASTER_STATUS_INACTIVE) {
			reportStatus(CoreStatus.INACTIVE);
			return;
		}
		
		if (pos1==0 && pos2==0) {
			updateStrategyStatus();
			entryLogic(fromBeacon);
			
		} else {
			// we have position opened, let's check exit rules
			exitLogic(fromBeacon);
		}
		
	}
	
	protected boolean tradingHoursEnabled() {
		DateTime dt=DateTime.now().withZone(strategy.getTimezone());
		if (dt.getDayOfWeek()==DateTimeConstants.SATURDAY || dt.getDayOfWeek()==DateTimeConstants.SUNDAY) return false;
		DateTime start = dt.withTime(EXCHANGE_OPEN_HOUR, EXCHANGE_OPEN_MINUTE, 0, 0);
		DateTime end = dt.withTime(EXCHANGE_CLOSE_HOUR, EXCHANGE_CLOSE_MINUTE, 0, 0);
		return (dt.isAfter(start) && dt.isBefore(end));
		
	}
	
	protected boolean entryHoursEnabled() {
		DateTime dt=DateTime.now().withZone(strategy.getTimezone());
		if (dt.getDayOfWeek()==DateTimeConstants.SATURDAY || dt.getDayOfWeek()==DateTimeConstants.SUNDAY) return false;
		DateTime start = dt.withTime(strategy.getEntryStartHour(), strategy.getEntryStartMinute(), 0, 0);
		DateTime end = dt.withTime(strategy.getEntryEndHour(), strategy.getEntryEndMinute(), 0, 0);
		return (dt.isAfter(start) && dt.isBefore(end));
		
	}
	
	
	protected boolean exitHoursEnabled() {
		DateTime dt=DateTime.now().withZone(strategy.getTimezone());
		if (dt.getDayOfWeek()==DateTimeConstants.SATURDAY || dt.getDayOfWeek()==DateTimeConstants.SUNDAY) return false;
		DateTime start = dt.withTime(strategy.getExitStartHour(), strategy.getExitStartMinute(), 0, 0);
		DateTime end = dt.withTime(strategy.getExitEndHour(), strategy.getExitEndMinute(), 0, 0);
		return (dt.isAfter(start) && dt.isBefore(end));
		
	}

	protected void onPairDataReady(PairDataReady pdr) {
		if (dataRequestId!=0 && pdr.reqId==dataRequestId) {
			lastHistDataRequestFailed = false;
			// extract data to the model TODO
			
			TreeMap<DateTime, Double> p1 = provider.getDataOut1();
			TreeMap<DateTime, Double> p2 = provider.getDataOut2();
			if (p1.size()!=p2.size()) {
				// this should not happen, but if it does, we fail gracefuly here
				dataRequestId=0;
				requestManualIntervention("unable to get correct historical data");
				lastHistDataRequestFailed = true;
				return;
			}
			
			int sz = p1.size();
			if (sz <= 0) {
				// we got empty data for whatever reason!
				dataRequestId=0;
				requestManualIntervention("historical data set is empty");
				lastHistDataRequestFailed = true;
				return;
			}
			
			DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyyMMdd");
			lastHistPriceDate1 = p1.lastKey();
			lastHistPrice1 = p1.get(lastHistPriceDate1);
			lastHistPriceDate2 = p2.lastKey();
			lastHistPrice2 = p2.get(lastHistPriceDate2);
			
			int ls1 = (p1.size()>15) ? 15 : p1.size();
			List<Double> l1 = new ArrayList<Double>(p1.values()).subList(p1.size()-ls1, p1.size());
			debug(String.format("last %d prices (%s): %s", ls1, strategy.getStock1(), Joiner.on(" ").join(l1)));
			int ls2 = (p2.size()>15) ? 15 : p2.size();
			List<Double> l2 = new ArrayList<Double>(p2.values()).subList(p2.size()-ls2, p2.size());
			debug(String.format("last %d prices (%s): %s", ls2, strategy.getStock2(), Joiner.on(" ").join(l2)));
			
			info(String.format("got historical data: last entries are %f@%s / %f@%s", lastHistPrice1, lastHistPriceDate1.toString(fmt), lastHistPrice2, lastHistPriceDate2.toString(fmt)));
			
			if (!lastHistPriceDate1.equals(lastHistPriceDate2)) {
				// this should not happen, but if it does, we fail gracefuly here
				dataRequestId=0;
				requestManualIntervention("unable to get correct historical data");
				lastHistDataRequestFailed = true;
				return;
			}
			
			// check the last price point age
			Duration d = new Duration(lastHistPriceDate1, DateTime.now());
			if (d.getStandardDays() > MAX_HIST_PRICE_AGE) {
				dataRequestId=0;
				requestManualIntervention("historical data are obsolete");
				lastHistDataRequestFailed = true;
				return;
			}
			
			
			double prices1[] = new double[p1.size()];
			double prices2[] = new double[p2.size()];
			int i=0;
			for (DateTime k:p1.keySet()) {
				prices1[i]=p1.get(k);
				prices2[i]=p2.get(k);
				i++;
			}
			try {
				ptmodel.setPrices(prices1, prices2);
			} catch (RuntimeException e) {
				dataRequestId=0;
				requestManualIntervention("not enough data to calculate model");
				lastHistDataRequestFailed = true;
				return;
			}

			lastDataObtained=DateTime.now();
			dataRequestId=0;
			
			info("obtained historical data OK: "+ptmodel.getStatusInfo());
			
		}
	}
	
	protected void onPairDataFailure(PairDataFailure pdf) {
		if (dataRequestId!=0 && pdf.reqId==dataRequestId) {
			error(String.format("failed to retrieve historical data, retry in approx %d minutes", RETRY_HISTORICAL_MINUTES));
			dataRequestId=0;
			lastHistDataRequestFailed = true;
		}
		
	}
	
	
	private void handleIbConnection() {
		boolean rdy = histDataReady();
		
		// subscribe to market data
		if (marketDataProvider.isConnected()) {
			marketDataProvider.subscribeData(strategy.getStock1(), strategy.getUid());
			marketDataProvider.subscribeData(strategy.getStock2(), strategy.getUid());
		}
	}
	
//	@Subscribe
//	public void onConnected(Connected cev) {
//		// we handle here all connections, not just the bound one
//		handleIbConnection();
//	}
	
	protected void onAccountConnected(AccountConnected ev) {
		if (ev.code.equals(strategy.getPortfolio().getAccountCode())) {
			handleIbConnection();
		}
	}
	
	protected void onDisconnected(Disconnected ev) {
		
	}
	
	

	public DateTime getLastPositionOpened() {
		return strategy.getLastOpened();
	}

	protected void setLastPositionOpened(DateTime lastPositionOpened) {
		strategy.setLastOpened(lastPositionOpened);
	}

	public double getPositionPl() {
		return positionPl;
	}

	protected void setPositionPl(double positionPl) {
		this.positionPl = positionPl;
		strategy.setPl(this.positionPl);
		
	}

	public PairTradingModel getPtmodel() {
		return ptmodel;
	}
	

	public int getPos1() {
		return pos1;
	}

	public int getPos2() {
		return pos2;
	}

	public int getOpening1() {
		return opening1;
	}

	public int getOpening2() {
		return opening2;
	}

	public int getClosing1() {
		return closing1;
	}

	public int getClosing2() {
		return closing2;
	}

	public int getPosition() {
		return position;
	}
	
	
	private void reportStatus(CoreStatus cs) {
		if (lastStat==null || lastStat!=cs) {
			debug("changing core status to "+cs.name());
			strategy.setCoreStatus(cs);
			lastStat=cs;
		}
	}


	
	public boolean hasActiveOrPendingPosition() {
		return position!=0 || opening1!=0 || opening2!=0;
		
	}


	protected void setPtmodel(PairTradingModel ptmodel) {
		//@FIXME additional logic for replacing model!
		this.ptmodel = ptmodel;
	}

	// just for tests
	protected void setLastDataObtained(DateTime lastDataObtained) {
		this.lastDataObtained = lastDataObtained;
	}
	
	private void resetState() {
		debug("state reset");
		pos1=0; // current position of stock 1 (qty)
		pos2=0; // current position of stock 2 (qty)
		opening1=0; //(qty)
		opening2=0; //(qty)
		closing1=0; //(qty)
		closing2=0; //(qty)
		position=0; // current confirmed pair position (-1, 0, 1)
		
		o1=null; // pending order of stock 1
		o2=null; // pending order of stock 2
		
		//private final Object executionsLock = new Object();
		executions.clear();
		executionMap.clear();
		
		
		openHistoryEntry = null;
		closeHistoryEntry = null;
		
		
		openTransaction1 = null;
		openTransaction2 = null;
		closeTransaction1 = null;
		closeTransaction2 = null;
		
		positionPl = 0;
		
		blocked = null; // pair gets blocked if errors happen while sending orders
		
		lastPortfUpdate1 = null;
		lastPortfUpdate2 = null;
		ready = false; // the instance gets ready as long as PortfolioUpdates are received first time
		
		// historical data
		
		lastDataObtained = null;
		lastDataRequested = null;
		dataRequestId=0;
		
		lastExitReason="";
		
		lastRecoverableError1=null;
		recoverableError1=false;
		lastRecoverableError2=null;
		recoverableError2=false;
		
		lastHistPrice1 = 0.0;
		lastHistPrice2 = 0.0;
		lastHistPriceDate1 = null;
		lastHistPriceDate2 = null;
		
		lastResumed = null;
		
		SimpleWrapper w = getWrapper();
		if (w!=null && w.getIbSocket().isConnected()) reportStatus(CoreStatus.NOT_READY);
		else reportStatus(CoreStatus.NOT_CONNECTED);
		
	}
	
	protected void onResumeRequest(ResumeRequest event) {
		if (blocked==null) return;
		if (event.strategyUid.equals(strategy.getUid())) {
			info("attempting to resume automated trading");
			resetState();
			lastResumed = DateTime.now();
			bus.post(new GlobalPortfolioUpdateRequest());
		}
	}
	
	protected TransactionEvent getOpenTransactionLastSent1() {
		return openTransactionLastSent1;
	}


	protected TransactionEvent getOpenTransactionLastSent2() {
		return openTransactionLastSent2;
	}


	protected TransactionEvent getCloseTransactionLastSent1() {
		return closeTransactionLastSent1;
	}


	protected TransactionEvent getCloseTransactionLastSent2() {
		return closeTransactionLastSent2;
	}


	protected HistoryEntry getOpenHistoryEntryLastSent() {
		return openHistoryEntryLastSent;
	}


	protected HistoryEntry getCloseHistoryEntryLastSent() {
		return closeHistoryEntryLastSent;
	}

	protected DateTime getLastPositionClosed() {
		return lastPositionClosed;
	}

	protected void setLastPositionClosed(DateTime lastPositionClosed) {
		this.lastPositionClosed = lastPositionClosed;
	}
	
	

}
