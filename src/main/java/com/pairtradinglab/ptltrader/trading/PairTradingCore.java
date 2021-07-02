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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedTransferQueue;

import com.ib.client.*;
import com.google.common.eventbus.*;

import org.apache.log4j.Logger;
import org.picocontainer.Startable;

import com.pairtradinglab.ptltrader.ActiveCores;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.events.AccountConnected;
import com.pairtradinglab.ptltrader.events.BeaconFlash;
import com.pairtradinglab.ptltrader.events.LogEvent;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.trading.events.*;
import com.pairtradinglab.ptltrader.trading.events.Error;

public class PairTradingCore implements Startable {
	
	// dependencies to be injected
	private final PairStrategy strategy;
	private final EventBus bus;
	private final Map<String, SimpleWrapper> ibWrapperMap;
	private final ActiveCores activeCores;
	
	private final ConfinedEngine ce;
	
	// locally resolved dependencies
	private final Logger l;
	
	private ContractExt c1;
	private ContractExt c2;
	
	private boolean started = false;
	
	private final String label;
	
	private final LinkedTransferQueue<ControlMessage> messages = new LinkedTransferQueue<ControlMessage>();
	private final Thread cthread;
		
	
	public PairTradingCore(PairTradingModel ptmodel, PairStrategy strategy,
			Map<String, SimpleWrapper> ibWrapperMap, LoggerFactory loggerFactory,
			MarketDataProvider marketDataProvider,
			EventBus bus, PairDataProviderFactory pairDataProviderFactory, Set<String> connectedAccounts, ActiveCores activeCores, ActivityDetector activityDetector) {
		super();
		this.strategy = strategy;
		this.ibWrapperMap = ibWrapperMap;
		this.bus = bus;
		
		c1 = ContractExt.createFromGoogleSymbol(strategy.getStock1(), strategy.getTradeAs1() == PairStrategy.TRADE_AS_CFD);
		c2 = ContractExt.createFromGoogleSymbol(strategy.getStock2(), strategy.getTradeAs2() == PairStrategy.TRADE_AS_CFD);
		
		// resolve other dependencies
		label = c1.m_symbol.replace(" ", ".")+"_"+c2.m_symbol.replace(" ", ".");
		l = loggerFactory.createLogger(label);
		this.activeCores = activeCores;
		
		// initialize confined engine object
		ce = new ConfinedEngine(ptmodel, strategy, ibWrapperMap, loggerFactory, marketDataProvider, bus, pairDataProviderFactory, connectedAccounts, activeCores, activityDetector);
		
		cthread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				while (true) {
	                try {
	                	ControlMessage message = messages.take();
	                    //trace("processing message "+message.type);
	                    ce.handleMessage(message);
	                    
	                    //Thread.sleep(1000);
	                } catch (InterruptedException e) {
	                	Thread.currentThread().interrupt();
	                    //System.out.println("Interrupted via InterruptedIOException");
	                    break;
	                }
	                
	            }
				//System.out.println("Shutting down thread "+Thread.currentThread().getName()); //@DEBUG
				
			}
		}, strategy.getPortfolio().getAccountCode()+ "_" + label);
		
	}
	
	private void msg(ControlMessage m) {
		messages.put(m);
	}
	
	
	// use this method if IB API is not yet connected
	public synchronized void start() {
		if (started) return;
		debug("starting PTL core");
		bus.register(this);
		
		messages.clear();
		msg(new ControlMessage(ControlMessage.TYPE_START, null));
		activeCores.registerCore(strategy.getUid(), label);
		cthread.start();
		
		started=true;
		
		
	}
	
	public synchronized void stop() {
		if (!started) return;
		debug("stopping PTL core");
		bus.unregister(this);
		msg(new ControlMessage(ControlMessage.TYPE_STOP, null));
		started=false;
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
	
	@Subscribe
	public void onBeaconFlash(BeaconFlash bf) {
		// called every minute by timer
		// we execute trade logic here as well
		if (started) {
			msg(new ControlMessage(ControlMessage.TYPE_BEACON, bf));
		}
		
	}
	
	@Subscribe
	public void onTick(Tick t) {
		if (t.symbol.equals(strategy.getStock1()) || t.symbol.equals(strategy.getStock2())) {
			msg(new ControlMessage(ControlMessage.TYPE_TICK, t));
		}
	}
	
	@Subscribe
	public void onGenericTick(GenericTick t) {
		if (t.symbol.equals(strategy.getStock1()) || t.symbol.equals(strategy.getStock2())) {
			msg(new ControlMessage(ControlMessage.TYPE_GENERIC_TICK, t));
		}
	}
	
	@Subscribe
	public void onClosePositionRequest(ClosePositionRequest cpr) {
		if (!cpr.getStrategyUid().equals(strategy.getUid())) return; // not related to our instance
		EClientSocket es = getSocket();
		if (es==null) return;
		// user wants to close position manually
		if (!es.isConnected()) {
			return; // not connected
		}
		msg(new ControlMessage(ControlMessage.TYPE_CLOSE_POSITION_REQUEST, cpr));
		
	}
	
	@Subscribe
	public void onOpenPositionRequest(OpenPositionRequest opr) {
		if (!opr.strategyUid.equals(strategy.getUid())) return; // not related to our instance
		if (opr.direction!=OpenPositionRequest.DIRECTION_LONG && opr.direction!=OpenPositionRequest.DIRECTION_SHORT) return;
		
		EClientSocket es = getSocket();
		if (es==null) return;
		
		if (!es.isConnected()) {
			return; // not connected
		}
		msg(new ControlMessage(ControlMessage.TYPE_OPEN_POSITION_REQUEST, opr));
	}
	
	
	
	
	@Subscribe
	public void onError(Error e) {
		SimpleWrapper w = getWrapper();
		if (w==null) return;
		if (e.code>=1000) return;
		if (!w.getUid().equals(e.ibWrapperUid)) return; // ignore error of alien IB connection
		msg(new ControlMessage(ControlMessage.TYPE_ERROR, e));
		
	}
	
	@Subscribe
	public void onOrderStatus(OrderStatus os) {
		msg(new ControlMessage(ControlMessage.TYPE_ORDER_STATUS, os));
		
	}
	
	
	
	@Subscribe
	public void onExecutionEvent(ExecutionEvent ee) {
		SimpleWrapper w = getWrapper();
		if (w==null) return;
		if (
				ee.getReqId()!=-1
				|| ee.getExecution().m_clientId!=w.getIbClientId()
				|| !ee.getExecution().m_acctNumber.equals(strategy.getPortfolio().getAccountCode())
		) return;
		
		msg(new ControlMessage(ControlMessage.TYPE_EXECUTION, ee));
	}
	
	
	
	@Subscribe
	public synchronized void onCommissionEvent(CommissionEvent ce) {
		msg(new ControlMessage(ControlMessage.TYPE_COMMISSION, ce.getCommReport()));
		
	}
	
	
	
	
	@Subscribe
	public void onPortfolioUpdate(PortfolioUpdate pu) {
		if (!strategy.getPortfolio().getAccountCode().equals(pu.accountCode)) return; // different account
		if (c1.equalsInSymbolTypeCurrency(pu.contract) || c2.equalsInSymbolTypeCurrency(pu.contract)) {
			msg(new ControlMessage(ControlMessage.TYPE_PORTFOLIO_UPDATE, pu));
		}
		
	}
	
	
	
	@Subscribe
	public void onPairDataReady(PairDataReady pdr) {
		msg(new ControlMessage(ControlMessage.TYPE_PAIR_DATA_READY, pdr));
		
	}
	
	@Subscribe
	public void onPairDataFailure(PairDataFailure pdf) {
		msg(new ControlMessage(ControlMessage.TYPE_PAIR_DATA_FAILURE, pdf));
		
	}
	
	
	@Subscribe
	public void onAccountConnected(AccountConnected ev) {
		if (ev.code.equals(strategy.getPortfolio().getAccountCode())) {
			msg(new ControlMessage(ControlMessage.TYPE_ACCOUNT_CONNECTED, ev));
		}
	}
	
	@Subscribe
	public void onDisconnected(Disconnected ev) {
		
	}
	
	public boolean hasActiveOrPendingPosition() {
		return ce.hasActiveOrPendingPosition();
	}


	public void setPtmodel(PairTradingModel ptmodel) {
		//@FIXME additional logic for replacing model!
		ce.setPtmodel(ptmodel);
	}
	
	@Subscribe
	public void onResumeRequest(ResumeRequest event) {
		msg(new ControlMessage(ControlMessage.TYPE_RESUME_REQUEST, event));
	}
	
}
