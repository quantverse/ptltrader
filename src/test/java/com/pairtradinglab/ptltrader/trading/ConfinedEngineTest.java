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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.eventbus.EventBus;
import com.ib.client.CommissionReport;
import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.TickType;
import com.pairtradinglab.ptltrader.ActiveCores;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.LoggerFactoryImpl;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.StringXorProcessor;
import com.pairtradinglab.ptltrader.events.AccountConnected;
import com.pairtradinglab.ptltrader.events.BeaconFlash;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;
import com.pairtradinglab.ptltrader.model.Settings;
import com.pairtradinglab.ptltrader.trading.events.Error;
import com.pairtradinglab.ptltrader.trading.events.ExecutionEvent;
import com.pairtradinglab.ptltrader.trading.events.GenericTick;
import com.pairtradinglab.ptltrader.trading.events.OrderStatus;
import com.pairtradinglab.ptltrader.trading.events.PortfolioUpdate;
import com.pairtradinglab.ptltrader.trading.events.Tick;

public class ConfinedEngineTest {

	private DateTimeZone tz = DateTimeZone.forID("Europe/Prague");
	private ConfinedEngine ce;
	private PairStrategy ps;
	private Portfolio pf;
	private final EventBus bus = new EventBus("bus_master_test");
	private PairTradingModelKalmanAuto ptm;
	private MarketRates mr1;
	private MarketRates mr2;
	private EClientSocket es;
	
	private MarketDataProvider mdp = mock(MarketDataProvider.class);
	private Map<String, SimpleWrapper> wrapperMap = mock(Map.class);
	private SimpleWrapper wrapperMock;
	private RuntimeParams runtimeMock;
	private ActivityDetector detectorMock;
	
	private int orderId;
	private final PairTradingModelKalmanAutoState testModelState = new PairTradingModelKalmanAutoState(13);
    private final PairTradingModelKalmanAutoState testModelState2= new PairTradingModelKalmanAutoState(19);
	
	@Before
	public void setUp() throws Exception {
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		orderId=1;
		
		runtimeMock = mock(RuntimeParams.class);
		
		detectorMock = mock(ActivityDetector.class);
		when(detectorMock.isExchangeActive(anyString())).thenReturn(true);
		
		Set<String> connectedAccounts = new HashSet<String>();
		
		LoggerFactory lf = new LoggerFactoryImpl(runtimeMock);
		Logger l = lf.createLogger(ConfinedEngineTest.class.getSimpleName());
		es = mock(EClientSocket.class);
		when(es.isConnected()).thenReturn(true);
		wrapperMock = mock(SimpleWrapper.class);
		
		when(wrapperMock.getIbClientId()).thenReturn(1);
		when(wrapperMock.getIbSocket()).thenReturn(es);
		when(wrapperMock.getUid()).thenReturn("");
		when(wrapperMap.get(any())).thenReturn(wrapperMock);
		when(wrapperMock.getNextId()).then(new Answer() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				return orderId++;
			}
			
		});
		
		pf = new Portfolio(bus, null, lf, "abcd");
		pf.setAccountCode("xxx");
		pf.setEquity(30000);
		
		ps = new PairStrategy("xyz", pf, "NYSE:A", "NASDAQ:B", 0, 0, bus, null);
		ps.injectCore(mock(PairTradingCore.class));
        ps.setModelState(testModelState2);
		pf.addPairStrategy(ps);
		
		mr1 = mock(MarketRates.class);
		mr2 = mock(MarketRates.class);
		
		ptm = mock(PairTradingModelKalmanAuto.class); // to test over the LockableStateModel interface
		when(ptm.isPricesInitialized()).thenReturn(true);
		when(ptm.getMr1()).thenReturn(mr1);
		when(ptm.getMr2()).thenReturn(mr2);
		when(ptm.getCurrentState()).thenReturn(testModelState);
		when(ptm.calcLegQtys(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(new Answer() {

			public Object answer(InvocationOnMock invocation) {
				Object[] args = invocation.getArguments();
				double out1 = (double) args[0] / ((double)args[2] * (double)args[3] + (double)args[1] * (double)args[3]);
				double out2 = out1 * (double)args[3] / (double)args[4];
				return new QtyQty((int) Math.floor(out1), (int) Math.floor(out2));
			}
		});

		
		ce = new ConfinedEngine(ptm, ps, wrapperMap, lf, mdp, bus, new PairDataProviderFactoryImpl(bus, new HistoricalDataProviderFactoryImpl(mock(List.class), new Settings(runtimeMock, new StringXorProcessor()), l)), connectedAccounts, mock(ActiveCores.class), detectorMock);
		ce.setLastDataObtained(DateTime.now());
		
	}

	@After
	public void tearDown() throws Exception {
		//System.out.println("teardown");
		ce.stop(false);
	}
	
	

	@Test
	public void testEntryHoursEnabled() {
		// friday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 15, 59, 59, 0, tz).getMillis());
		assertFalse(ce.entryHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		assertTrue(ce.entryHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 21, 49, 49, 0, tz).getMillis());
		assertTrue(ce.entryHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 21, 50, 0, 0, tz).getMillis());
		assertFalse(ce.entryHoursEnabled());
		
		//saturday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 12, 16, 0, 0, 1, tz).getMillis());
		assertFalse(ce.entryHoursEnabled());
		
		//sunday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 13, 16, 0, 0, 1, tz).getMillis());
		assertFalse(ce.entryHoursEnabled());
		
		// check DST
		// monday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 3, 11, 14, 59, 59, 0, tz).getMillis());
		assertFalse(ce.entryHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 3, 11, 15, 0, 0, 1, tz).getMillis());
		assertTrue(ce.entryHoursEnabled());
		
		// monday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 4, 1, 15, 59, 59, 0, tz).getMillis());
		assertFalse(ce.entryHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 4, 1, 16, 0, 0, 1, tz).getMillis());
		assertTrue(ce.entryHoursEnabled());
	}
	
	@Test
	public void testTradingHoursEnabled() {
		// friday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 15, 59, 59, 0, tz).getMillis());
		assertTrue(ce.tradingHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		assertTrue(ce.tradingHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 21, 49, 49, 0, tz).getMillis());
		assertTrue(ce.tradingHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 21, 50, 0, 0, tz).getMillis());
		assertTrue(ce.tradingHoursEnabled());
		
		//saturday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 12, 16, 0, 0, 1, tz).getMillis());
		assertFalse(ce.tradingHoursEnabled());
		
		//sunday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 13, 16, 0, 0, 1, tz).getMillis());
		assertFalse(ce.tradingHoursEnabled());
		
		
	}

	@Test
	public void testExitHoursEnabled() {
		// friday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 15, 59, 59, 0, tz).getMillis());
		assertFalse(ce.exitHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		assertTrue(ce.exitHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 21, 57, 49, 0, tz).getMillis());
		assertTrue(ce.exitHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 21, 58, 0, 0, tz).getMillis());
		assertFalse(ce.exitHoursEnabled());
		
		//saturday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 12, 16, 0, 0, 1, tz).getMillis());
		assertFalse(ce.exitHoursEnabled());
		
		//sunday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 13, 16, 0, 0, 1, tz).getMillis());
		assertFalse(ce.exitHoursEnabled());
		
		// check DST
		// monday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 3, 11, 14, 59, 59, 0, tz).getMillis());
		assertFalse(ce.exitHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 3, 11, 15, 0, 0, 1, tz).getMillis());
		assertTrue(ce.exitHoursEnabled());
		
		// monday
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 4, 1, 15, 59, 59, 0, tz).getMillis());
		assertFalse(ce.exitHoursEnabled());
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 4, 1, 16, 0, 0, 1, tz).getMillis());
		assertTrue(ce.exitHoursEnabled());
	}

	
	private void setupSocketMockFriendlyShift() {
		
		// configure EClientSocket mock behavior
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         //System.out.println("called with arguments: " + args[0]);
				         
				         if (args[2] instanceof Order && args[1] instanceof Contract) {
				        	 Contract c = (Contract) args[1];
				        	 Order o = (Order) args[2];
				        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
				        	 
				        	 
					         DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 2, 1, tz).getMillis());
				        	 
				        	 
				        	 // fire Filled event
				        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
				        	 ce.onOrderStatus(os);
				        	 
				         }
				         return null;
				     }
				 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
		
		
		
	}
	
private void setupSocketMockFriendlyExecComm1() {
		
		// configure EClientSocket mock behavior
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         //System.out.println("called with arguments: " + args[0]);
				         
				         if (args[2] instanceof Order && args[1] instanceof Contract) {
				        	 Contract c = (Contract) args[1];
				        	 Order o = (Order) args[2];
				        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
				        	 
				        	 // send execution event
				        	 String exUid = UUID.randomUUID().toString();
				        	 Execution ex = new Execution();
				        	 ex.m_orderId = o.m_orderId;
				        	 ex.m_clientId = 1;
				        	 ex.m_execId = exUid;
				        	 ex.m_acctNumber="xxx";
				        	 ex.m_shares = o.m_totalQuantity;
				        	 if (o.m_orderId==1 || o.m_orderId==3) ex.m_avgPrice = 15.0;
				        	 else if (o.m_orderId==2 || o.m_orderId==4) ex.m_avgPrice = 20.0;
				        	 ExecutionEvent ee = new ExecutionEvent(-1, c, ex);
				        	 ce.onExecutionEvent(ee);
				        	 
				        	 // fire Filled event
				        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
				        	 ce.onOrderStatus(os);
				        	 
				        	 // send commission event
				        	 CommissionReport commReport = new CommissionReport();
				        	 commReport.m_execId = exUid;
				        	 commReport.m_currency = "USD";
				        	 commReport.m_commission = 1.0;
				        	 commReport.m_realizedPNL = 22.0;
				        	 ce.onCommissionReport(commReport);
				        	 
				        	 
				        	 
				         }
				         return null;
				     }
				 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
		
		
		
	}

private void setupSocketMockFriendlyExecComm2() {
	
	// configure EClientSocket mock behavior
	doAnswer(
			new Answer() {
			     public Object answer(InvocationOnMock invocation) {
			         Object[] args = invocation.getArguments();
			         Object mock = invocation.getMock();
			         //System.out.println("called with arguments: " + args[0]);
			         
			         if (args[2] instanceof Order && args[1] instanceof Contract) {
			        	 Contract c = (Contract) args[1];
			        	 Order o = (Order) args[2];
			        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
			        	 
			        	 // send execution event
			        	 String exUid = UUID.randomUUID().toString();
			        	 Execution ex = new Execution();
			        	 ex.m_orderId = o.m_orderId;
			        	 ex.m_clientId = 1;
			        	 ex.m_execId = exUid;
			        	 ex.m_acctNumber="xxx";
			        	 ex.m_shares = o.m_totalQuantity;
			        	 if (o.m_orderId==1 || o.m_orderId==3) ex.m_avgPrice = 15.0;
			        	 else if (o.m_orderId==2 || o.m_orderId==4) ex.m_avgPrice = 20.0;
			        	 ExecutionEvent ee = new ExecutionEvent(-1, c, ex);
			        	 ce.onExecutionEvent(ee);
			        	 
			        	// send commission event
			        	 CommissionReport commReport = new CommissionReport();
			        	 commReport.m_execId = exUid;
			        	 commReport.m_currency = "USD";
			        	 commReport.m_commission = 1.0;
			        	 commReport.m_realizedPNL = 22.0;
			        	 ce.onCommissionReport(commReport);
			        	 
			        	 // fire Filled event
			        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
			        	 ce.onOrderStatus(os);
			        	 
			        	 
			        	 
			        	 
			        	 
			         }
			         return null;
			     }
			 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
	
	
	
}

private void setupSocketMockFriendlyExecComm3() {
	
	// configure EClientSocket mock behavior
	doAnswer(
			new Answer() {
			     public Object answer(InvocationOnMock invocation) {
			         Object[] args = invocation.getArguments();
			         Object mock = invocation.getMock();
			         //System.out.println("called with arguments: " + args[0]);
			         
			         if (args[2] instanceof Order && args[1] instanceof Contract) {
			        	 Contract c = (Contract) args[1];
			        	 Order o = (Order) args[2];
			        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
			        	 
			        	// fire Filled event
			        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
			        	 ce.onOrderStatus(os);
			        	 
			        	 // send execution event
			        	 String exUid = UUID.randomUUID().toString();
			        	 Execution ex = new Execution();
			        	 ex.m_orderId = o.m_orderId;
			        	 ex.m_clientId = 1;
			        	 ex.m_execId = exUid;
			        	 ex.m_acctNumber="xxx";
			        	 ex.m_shares = o.m_totalQuantity;
			        	 if (o.m_orderId==1 || o.m_orderId==3) ex.m_avgPrice = 15.0;
			        	 else if (o.m_orderId==2 || o.m_orderId==4) ex.m_avgPrice = 20.0;
			        	 ExecutionEvent ee = new ExecutionEvent(-1, c, ex);
			        	 ce.onExecutionEvent(ee);
			        	 
			        	// send commission event
			        	 CommissionReport commReport = new CommissionReport();
			        	 commReport.m_execId = exUid;
			        	 commReport.m_currency = "USD";
			        	 commReport.m_commission = 1.0;
			        	 commReport.m_realizedPNL = 22.0;
			        	 ce.onCommissionReport(commReport);
			        	 
			        	 
			        	 
			         }
			         return null;
			     }
			 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
	
	
	
}

private void setupSocketMockFriendlyExecComm4() {
	
	// configure EClientSocket mock behavior
	doAnswer(
			new Answer() {
			     public Object answer(InvocationOnMock invocation) {
			         Object[] args = invocation.getArguments();
			         Object mock = invocation.getMock();
			         //System.out.println("called with arguments: " + args[0]);
			         
			         if (args[2] instanceof Order && args[1] instanceof Contract) {
			        	 Contract c = (Contract) args[1];
			        	 Order o = (Order) args[2];
			        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
			        	 
			        	// fire Filled event
			        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
			        	 ce.onOrderStatus(os);
			        	 
			        	 // send execution event
			        	 String exUid = UUID.randomUUID().toString();
			        	 Execution ex = new Execution();
			        	 ex.m_orderId = o.m_orderId;
			        	 ex.m_clientId = 1;
			        	 ex.m_execId = exUid;
			        	 ex.m_acctNumber="xxx";
			        	 ex.m_shares = o.m_totalQuantity-10;
			        	 if (o.m_orderId==1 || o.m_orderId==3) ex.m_avgPrice = 15.0;
			        	 else if (o.m_orderId==2 || o.m_orderId==4) ex.m_avgPrice = 20.0;
			        	 ExecutionEvent ee = new ExecutionEvent(-1, c, ex);
			        	 ce.onExecutionEvent(ee);
			        	 
			        	// send commission event
			        	 CommissionReport commReport = new CommissionReport();
			        	 commReport.m_execId = exUid;
			        	 commReport.m_currency = "USD";
			        	 commReport.m_commission = 0.9;
			        	 commReport.m_realizedPNL = 22.0;
			        	 ce.onCommissionReport(commReport);
			        	 
			        	// send execution event
			        	 exUid = UUID.randomUUID().toString();
			        	 ex = new Execution();
			        	 ex.m_orderId = o.m_orderId;
			        	 ex.m_clientId = 1;
			        	 ex.m_execId = exUid;
			        	 ex.m_acctNumber="xxx";
			        	 ex.m_shares = 10;
			        	 if (o.m_orderId==1 || o.m_orderId==3) ex.m_avgPrice = 15.0;
			        	 else if (o.m_orderId==2 || o.m_orderId==4) ex.m_avgPrice = 20.0;
			        	 ee = new ExecutionEvent(-1, c, ex);
			        	 ce.onExecutionEvent(ee);
			        	 
			        	// send commission event
			        	 commReport = new CommissionReport();
			        	 commReport.m_execId = exUid;
			        	 commReport.m_currency = "USD";
			        	 commReport.m_commission = 0.1;
			        	 commReport.m_realizedPNL = 22.0;
			        	 ce.onCommissionReport(commReport);
			        	 
			        	 
			        	 
			         }
			         return null;
			     }
			 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
	
	
	
}
	
	
	private void setupSocketMockFriendly() {
		
		// configure EClientSocket mock behavior
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         //System.out.println("called with arguments: " + args[0]);
				         
				         if (args[2] instanceof Order && args[1] instanceof Contract) {
				        	 Contract c = (Contract) args[1];
				        	 Order o = (Order) args[2];
				        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
				        	 
				        	 // fire Filled event
				        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
				        	 ce.onOrderStatus(os);
				        	 
				         }
				         return null;
				     }
				 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
		
		
		
	}
	
	
	private void testRegularFlow(int testDirection) {
		
		setupSocketMockFriendlyShift();
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(testDirection); // simulate long signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		
		ce.start();
		
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected((new AccountConnected("xxx")));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		// check internal state of core object (new long position should be opened)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		if (testDirection==1) {
			assertEquals(200, ce.getPos1());
			assertEquals(-150, ce.getPos2());
		} else {
			assertEquals(-200, ce.getPos1());
			assertEquals(150, ce.getPos2());
		}
		assertEquals(testDirection, ce.getPosition());
		assertEquals("2013-01-11T16:00:02.001+01:00", ce.getLastPositionOpened().withZone(DateTimeZone.forID("Europe/Prague")).toString());
        assertEquals(testModelState, ps.getModelState());
		verify(ptm).lockState(testModelState);
		//assertEquals(1, am.countPositions());
		//assertEquals(0, am.countPending());
		
		// ok lets try to close the position now
		when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
		// fire some price event to execute the logic chain
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		verify(ptm).storeReversalState();
        verify(ptm).unlockState();
        assertNull(ps.getModelState());
		
		//assertEquals(0, am.countPositions());
		//assertEquals(0, am.countPending());
		
		
		
	}
	
	@Test
	public void testRegularFlowLong() {
		testRegularFlow(PairTradingModel.SIGNAL_LONG);
		
	}
	
	
	@Test
	public void testRegularFlowShort() {
		testRegularFlow(PairTradingModel.SIGNAL_SHORT);
	}
	
	@Test
	public void testErrorFlow1() {
		EClientSocket es = mock(EClientSocket.class);
		
		// set time to trading hours
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		
		// configure EClientSocket mock behavior
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         //System.out.println("called with arguments: " + args[0]);
				         
				         if (args[2] instanceof Order && args[1] instanceof Contract) {
				        	 Contract c = (Contract) args[1];
				        	 Order o = (Order) args[2];
				        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
				        	 
				        	 // shift execution by 1 seconds
				        	 DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 2, 1, tz).getMillis());
				        	 
				        	 // long order is filled, short order fails
				        	 if (o.m_totalQuantity==50) {
				        		// fire Filled event
					        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
					        	 ce.onOrderStatus(os);
					        	 //System.out.println("success, posted");
				        		 
				        	 } else {
				        		 // fire error event
				        		 Error ev = new Error("", o.m_orderId, Error.ERRC_ORDER_REJECTED, "rejected as test");
				        		 ce.onError(ev);
				        		 
				        	 }
				        	 
				        	 
				        	 
				         }
				         return null;
				     }
				 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
		
		
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(PairTradingModel.SIGNAL_SHORT); // simulate long signal opening
		
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		
		
		ce.start();
		
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		//assertEquals(0, am.countPositions());
		//assertEquals(0, am.countPending());
		
		
	}
	
	@Test
	public void testAcceptAndClosePosition() {
		setupSocketMockFriendlyShift();
		
		// set time to trading hours
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		
		
		// configure PairTradingModel mock behavior
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		
		
		ce.start();
		
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
	
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", 66, ContractExt.createFromGoogleSymbol("NYSE:A", false), 20, 2000));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", -50, ContractExt.createFromGoogleSymbol("NASDAQ:B", false), 20, -2000));
		
		
		// fire malformed IB events to confuse the core
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", 0, ContractExt.createFromGoogleSymbol("NYSE:A", false), 0, 0));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", 0, ContractExt.createFromGoogleSymbol("NASDAQ:B", false), 0, 0));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", 66, ContractExt.createFromGoogleSymbol("NYSE:A", false), 20, 2000));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", -50, ContractExt.createFromGoogleSymbol("NASDAQ:B", false), 20, -2000));
		
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		// check internal state of core object (new long position should be opened)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		
		assertEquals(66, ce.getPos1());
		assertEquals(-50, ce.getPos2());
		
		assertEquals(1, ce.getPosition());
		//assertEquals("2013-01-11T16:00:02.001+01:00", ptc.getLastPositionOpened().toString());
		
		//assertEquals(1, am.countPositions());
		//assertEquals(0, am.countPending());

        // verify if model got locked
        verify(ptm, times(2)).lockState(testModelState2);

		// ok lets try to close the position now
		when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
		// fire some price event to execute the logic chain
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		//assertEquals(0, am.countPositions());
		//assertEquals(0, am.countPending());
        verify(ptm, times(2)).unlockState();
        assertNull(ps.getModelState());

	}

	@Test
	public void testAcceptAndClosePositionIbPortfUpdateFault1() {
		setupSocketMockFriendlyShift();

		// set time to trading hours
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());

		// configure PairTradingModel mock behavior
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);

		ce.start();

		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());

		ce.onAccountConnected(new AccountConnected("xxx"));

		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", 66, ContractExt.createFromGoogleSymbol("NYSE:A", false), 20, 2000));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", -50, ContractExt.createFromGoogleSymbol("NASDAQ:B", false), 20, -2000));


		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));


		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);

		// check internal state of core object (new long position should be opened)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());

		assertEquals(66, ce.getPos1());
		assertEquals(-50, ce.getPos2());

		assertEquals(1, ce.getPosition());

		// ok lets try to close the position now
		when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
		// fire some price event to execute the logic chain
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));

		// fuck up the stock #1 (will cause the status to go to BLOCKED before the issue was fixed)
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", 2, ContractExt.createFromGoogleSymbol("NYSE:A", false), 0, 0));

		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		assertEquals(PairStrategy.STATUS_ACTIVE, ps.getStatus());
	}


    @Test
    public void testAcceptAndClosePositionIbPortfUpdateFault2() {
        setupSocketMockFriendlyShift();

        // set time to trading hours
        DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());

        // configure PairTradingModel mock behavior
        when(mr1.getAsk()).thenReturn(15.0);
        when(mr1.getBid()).thenReturn(15.0);
        when(mr2.getAsk()).thenReturn(20.0);
        when(mr2.getBid()).thenReturn(20.0);

        ce.start();

        assertEquals(0, ce.getClosing1());
        assertEquals(0, ce.getClosing2());
        assertEquals(0, ce.getOpening1());
        assertEquals(0, ce.getOpening2());
        assertEquals(0, ce.getPos1());
        assertEquals(0, ce.getPos2());
        assertEquals(0, ce.getPosition());

        ce.onAccountConnected(new AccountConnected("xxx"));

        // fire dummy events to make pair ready
        ce.onPortfolioUpdate(new PortfolioUpdate("xxx", 66, ContractExt.createFromGoogleSymbol("NYSE:A", false), 20, 2000));
        ce.onPortfolioUpdate(new PortfolioUpdate("xxx", -50, ContractExt.createFromGoogleSymbol("NASDAQ:B", false), 20, -2000));


        // fire shortable events
        ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
        ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
        // fire some price events
        ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));


        // check if model setAsk1 method was called (via eventbus processing)
        verify(mr1).setAsk(15);

        // check internal state of core object (new long position should be opened)
        assertEquals(0, ce.getClosing1());
        assertEquals(0, ce.getClosing2());
        assertEquals(0, ce.getOpening1());
        assertEquals(0, ce.getOpening2());

        assertEquals(66, ce.getPos1());
        assertEquals(-50, ce.getPos2());

        assertEquals(1, ce.getPosition());

        // ok lets try to close the position now
        when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
        // fire some price event to execute the logic chain
        // fire some price events
        ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));

        // fuck up the stock #2 (will cause the status to go to BLOCKED before the issue was fixed)
        ce.onPortfolioUpdate(new PortfolioUpdate("xxx", 2, ContractExt.createFromGoogleSymbol("NYSE:B", false), 0, 0));

        // check internal state of core object (position should be closed)
        assertEquals(0, ce.getClosing1());
        assertEquals(0, ce.getClosing2());
        assertEquals(0, ce.getOpening1());
        assertEquals(0, ce.getOpening2());
        assertEquals(0, ce.getPos1());
        assertEquals(0, ce.getPos2());
        assertEquals(0, ce.getPosition());
        assertEquals(PairStrategy.STATUS_ACTIVE, ps.getStatus());
    }
	
	@Test
	public void testPdtRule() {
		pf.setEquity(10000);
		pf.setPdtEnable(Portfolio.PDT_ENABLE_25K);
		setupSocketMockFriendly();
		
		// set time to trading hours
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(PairTradingModel.SIGNAL_LONG); // simulate long signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		ce.setLastPositionOpened(new DateTime(2013, 1, 11, 15, 45, 0, 1, tz));
		
		ce.start();
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check internal state of core object (position should be closed)
		assertEquals(CoreStatus.ENTRY_PDT, ps.getCoreStatus());
		assertEquals(0, ce.getPosition());
		//assertEquals(0, am.countPositions());
		//assertEquals(0, am.countPending());
		
		// now increase equity to 25k and retry
		pf.setEquity(25000);
		
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check internal state of core object (position should be closed)
		assertEquals(1, ce.getPosition());
		//assertEquals(1, am.countPositions());
		//assertEquals(0, am.countPending());
		
		
	}
	
	
	@Test
	public void testReversalRule() {
		setupSocketMockFriendly();
		
		// set time to trading hours
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(PairTradingModel.SIGNAL_LONG); // simulate long signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		when(ptm.checkReversalCondition()).thenReturn(true);
		
		
		ce.setLastPositionClosed(new DateTime(2013, 1, 11, 15, 45, 0, 1, tz));
		
		ce.start();
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check internal state of core object (position should be closed)
		assertEquals(CoreStatus.ENTRY_REVERSAL_NOT_ALLOWED, ps.getCoreStatus());
		assertEquals(0, ce.getPosition());
		//assertEquals(0, am.countPositions());
		//assertEquals(0, am.countPending());
		
	}
	
	
	
	@Test
	public void testRecoverableFlow1() {
		
		// set time to trading hours
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		
		// configure EClientSocket mock behavior
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         //System.out.println("called with arguments: " + args[0]);
				         
				         if (args[2] instanceof Order && args[1] instanceof Contract) {
				        	 Contract c = (Contract) args[1];
				        	 Order o = (Order) args[2];
				        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
				        	 
				        	 // long order is filled, short order fails
				        	 if (o.m_totalQuantity==150) {
				        		// fire Filled event
					        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
					        	 ce.onOrderStatus(os);
					        	 //System.out.println("success, posted");
				        		 
				        	 } else {
				        		 // fire error event
				        		 Error ev = new Error("", o.m_orderId, Error.ERRC_ORDER_HELD, "order held");
				        		 ce.onError(ev);
				        		 
				        	 }
				        	 
				        	 
				        	 
				         }
				         return null;
				     }
				 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
		
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(PairTradingModel.SIGNAL_SHORT); // simulate short signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		ce.start();
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		
		// check internal state of core object
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(-200, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(150, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		//assertEquals(0, am.countPositions());
		//assertEquals(1, am.countPending());
		
		// now set the time 2 minutes to future and post beacon event, it should cancel the order and close existing position
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 2, 0, 1, tz).getMillis());
		ce.onBeaconFlash(new BeaconFlash(DateTime.now()));
		
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		//assertEquals(0, am.countPositions());
		//assertEquals(0, am.countPending());
		
		
	}
	
	
	@Test
	public void testRecoverableFlow2() {
		
		// set time to trading hours
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 0, 0, 1, tz).getMillis());
		
		// configure EClientSocket mock behavior
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         //System.out.println("called with arguments: " + args[0]);
				         
				         if (args[2] instanceof Order && args[1] instanceof Contract) {
				        	 Contract c = (Contract) args[1];
				        	 Order o = (Order) args[2];
				        	 //System.out.println(o.m_totalQuantity+" "+o.m_action);
				        	 
				        	 // long order is filled, short order fails
				        	 if (o.m_totalQuantity==200) {
				        		// fire Filled event
					        	 OrderStatus os = new OrderStatus(o.m_orderId, "Filled", o.m_totalQuantity, 0, 10, 10, "");
					        	 ce.onOrderStatus(os);
					        	 //System.out.println("success, posted");
				        		 
				        	 } else {
				        		 // fire error event
				        		 Error ev = new Error("", o.m_orderId, Error.ERRC_ORDER_HELD, "order held");
				        		 ce.onError(ev);
				        		 
				        	 }
				        	 
				        	 
				        	 
				         }
				         return null;
				     }
				 }).when(es).placeOrder(anyInt(), any(Contract.class), any(Order.class));
		
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(PairTradingModel.SIGNAL_SHORT); // simulate short signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		ce.start();
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		
		// check internal state of core object
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(150, ce.getOpening2());
		assertEquals(-200, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		//assertEquals(0, am.countPositions());
		//assertEquals(1, am.countPending());
		
		// now set the time 2 minutes to future and post beacon event, it should cancel the order and close existing position
		DateTimeUtils.setCurrentMillisFixed(new DateTime(2013, 1, 11, 16, 2, 0, 1, tz).getMillis());
		ce.onBeaconFlash(new BeaconFlash(DateTime.now()));
		
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		//assertEquals(0, am.countPositions());
		//assertEquals(0, am.countPending());
		
		
	}

	@Test
	public void testDoubleSlot() {
		int testDirection = 1;
		ps.setSlotOccupation(2);
		setupSocketMockFriendlyShift();
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(testDirection); // simulate long signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		
		ce.start();
		
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		// check internal state of core object (new long position should be opened)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		if (testDirection==1) {
			assertEquals(400, ce.getPos1());
			assertEquals(-300, ce.getPos2());
		} else {
			assertEquals(-133, ce.getPos1());
			assertEquals(100, ce.getPos2());
		}
		assertEquals(testDirection, ce.getPosition());
		
		//assertEquals(1, am.countPositions());
		//assertEquals(0, am.countPending());
		
		// ok lets try to close the position now
		when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
		// fire some price event to execute the logic chain
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		//assertEquals(0, am.countPositions());
		//assertEquals(0, am.countPending());
		
		
		
	}
	
	
	@Test	
	public void testExecCommOpenFlow1() {
		setupSocketMockFriendlyExecComm1();
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(1); // simulate long signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		
		ce.start();
		
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		// check internal state of core object (new long position should be opened)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		
		assertEquals(200, ce.getPos1());
		assertEquals(-150, ce.getPos2());
		
		assertEquals(1, ce.getPosition());
		
		// check transaction logs sent out
		assertNotNull(ce.getOpenTransactionLastSent1());
		assertEquals(1, ce.getOpenTransactionLastSent1().getCommissions(), 0.01);
		assertEquals(0, ce.getOpenTransactionLastSent1().getSlippage(), 0.01);
		assertNotNull(ce.getOpenTransactionLastSent2());
		assertEquals(1, ce.getOpenTransactionLastSent2().getCommissions(), 0.01);
		assertEquals(0, ce.getOpenTransactionLastSent2().getSlippage(), 0.01);
		
		// check history entry logs sent out
		assertNotNull(ce.getOpenHistoryEntryLastSent());
		assertEquals(2.0, ce.getOpenHistoryEntryLastSent().commissions, 0.01);
		
		
		// ok lets try to close the position now
		when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
		// fire some price event to execute the logic chain
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		// check transaction logs sent out
		assertNotNull(ce.getCloseTransactionLastSent1());
		assertEquals(1, ce.getCloseTransactionLastSent1().getCommissions(), 0.01);
		assertEquals(0, ce.getCloseTransactionLastSent1().getSlippage(), 0.01);
		assertEquals(1, ce.getCloseTransactionLastSent2().getCommissions(), 0.01);
		assertEquals(0, ce.getCloseTransactionLastSent2().getSlippage(), 0.01);
		
		// check history entry logs sent out
		assertNotNull(ce.getCloseHistoryEntryLastSent());
		assertEquals(2.0, ce.getCloseHistoryEntryLastSent().commissions, 0.01);
		
		
		
	}
	
	@Test	
	public void testExecCommOpenFlow2() {
		// comm event before fill event
		setupSocketMockFriendlyExecComm2();
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(1); // simulate long signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		
		ce.start();
		
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		// check internal state of core object (new long position should be opened)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		
		assertEquals(200, ce.getPos1());
		assertEquals(-150, ce.getPos2());
		
		assertEquals(1, ce.getPosition());
		
		// check transaction logs sent out
		assertNotNull(ce.getOpenTransactionLastSent1());
		assertEquals(1, ce.getOpenTransactionLastSent1().getCommissions(), 0.01);
		assertEquals(0, ce.getOpenTransactionLastSent1().getSlippage(), 0.01);
		assertNotNull(ce.getOpenTransactionLastSent2());
		assertEquals(1, ce.getOpenTransactionLastSent2().getCommissions(), 0.01);
		assertEquals(0, ce.getOpenTransactionLastSent2().getSlippage(), 0.01);
		
		// check history entry logs sent out
		assertNotNull(ce.getOpenHistoryEntryLastSent());
		assertEquals(2.0, ce.getOpenHistoryEntryLastSent().commissions, 0.01);
		
		
		
		// ok lets try to close the position now
		when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
		// fire some price event to execute the logic chain
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		// check transaction logs sent out
		assertNotNull(ce.getCloseTransactionLastSent1());
		assertEquals(1, ce.getCloseTransactionLastSent1().getCommissions(), 0.01);
		assertEquals(0, ce.getCloseTransactionLastSent1().getSlippage(), 0.01);
		assertEquals(1, ce.getCloseTransactionLastSent2().getCommissions(), 0.01);
		assertEquals(0, ce.getCloseTransactionLastSent2().getSlippage(), 0.01);
		
		// check history entry logs sent out
		assertNotNull(ce.getCloseHistoryEntryLastSent());
		assertEquals(2.0, ce.getCloseHistoryEntryLastSent().commissions, 0.01);
		
		
		
	}
	
	@Test	
	public void testExecCommOpenFlow3() {
		// fill - exec - comm flow
		setupSocketMockFriendlyExecComm3();
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(1); // simulate long signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		
		ce.start();
		
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		// check internal state of core object (new long position should be opened)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		
		assertEquals(200, ce.getPos1());
		assertEquals(-150, ce.getPos2());
		
		assertEquals(1, ce.getPosition());
		
		// check transaction logs sent out
		assertNotNull(ce.getOpenTransactionLastSent1());
		assertEquals(1, ce.getOpenTransactionLastSent1().getCommissions(), 0.01);
		assertEquals(0, ce.getOpenTransactionLastSent1().getSlippage(), 0.01);
		assertNotNull(ce.getOpenTransactionLastSent2());
		assertEquals(1, ce.getOpenTransactionLastSent2().getCommissions(), 0.01);
		assertEquals(0, ce.getOpenTransactionLastSent2().getSlippage(), 0.01);
		
		// check history entry logs sent out
		assertNotNull(ce.getOpenHistoryEntryLastSent());
		assertEquals(2.0, ce.getOpenHistoryEntryLastSent().commissions, 0.01);
		
		
		
		// ok lets try to close the position now
		when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
		// fire some price event to execute the logic chain
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		// check transaction logs sent out
		assertNotNull(ce.getCloseTransactionLastSent1());
		assertEquals(1, ce.getCloseTransactionLastSent1().getCommissions(), 0.01);
		assertEquals(0, ce.getCloseTransactionLastSent1().getSlippage(), 0.01);
		assertEquals(1, ce.getCloseTransactionLastSent2().getCommissions(), 0.01);
		assertEquals(0, ce.getCloseTransactionLastSent2().getSlippage(), 0.01);
		
		// check history entry logs sent out
		assertNotNull(ce.getCloseHistoryEntryLastSent());
		assertEquals(2.0, ce.getCloseHistoryEntryLastSent().commissions, 0.01);
		
		
		
	}
	
	@Test	
	public void testExecCommOpenFlow4() {
		// fill - exec incomplete - comm -exec - comm flow
		setupSocketMockFriendlyExecComm4();
		
		// configure PairTradingModel mock behavior
		when(ptm.entryLogic()).thenReturn(1); // simulate long signal opening
		when(mr1.getAsk()).thenReturn(15.0);
		when(mr1.getBid()).thenReturn(15.0);
		when(mr2.getAsk()).thenReturn(20.0);
		when(mr2.getBid()).thenReturn(20.0);
		when(ptm.getProfitPotential(anyDouble(), anyDouble(), anyDouble())).thenReturn(100.0);
		
		
		
		ce.start();
		
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		ce.onAccountConnected(new AccountConnected("xxx"));
		
		// fire dummy events to make pair ready
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NYSE:A", false)));
		ce.onPortfolioUpdate(new PortfolioUpdate("xxx", ContractExt.createFromGoogleSymbol("NASDAQ:B", false)));
		
		// fire shortable events
		ce.onGenericTick(new GenericTick("NYSE:A", TickType.SHORTABLE, 3));
		ce.onGenericTick(new GenericTick("NASDAQ:B", TickType.SHORTABLE, 3));
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		
		// check if model setAsk1 method was called (via eventbus processing)
		verify(mr1).setAsk(15);
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		//verify(es).placeOrder(anyInt(), isA(Contract.class), isA(Order.class));
		
		// check internal state of core object (new long position should be opened)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		
		assertEquals(200, ce.getPos1());
		assertEquals(-150, ce.getPos2());
		
		assertEquals(1, ce.getPosition());
		
		// check transaction logs sent out
		assertNotNull(ce.getOpenTransactionLastSent1());
		assertEquals(1, ce.getOpenTransactionLastSent1().getCommissions(), 0.01);
		assertEquals(0, ce.getOpenTransactionLastSent1().getSlippage(), 0.01);
		assertNotNull(ce.getOpenTransactionLastSent2());
		assertEquals(1, ce.getOpenTransactionLastSent2().getCommissions(), 0.01);
		assertEquals(0, ce.getOpenTransactionLastSent2().getSlippage(), 0.01);
		
		// check history entry logs sent out
		assertNotNull(ce.getOpenHistoryEntryLastSent());
		assertEquals(2.0, ce.getOpenHistoryEntryLastSent().commissions, 0.01);
		
		
		
		// ok lets try to close the position now
		when(ptm.exitLogic(ce.getPosition())).thenReturn(true);
		// fire some price event to execute the logic chain
		// fire some price events
		ce.onTick(new Tick("NYSE:A", TickType.ASK, 15, 1));
		
		// check internal state of core object (position should be closed)
		assertEquals(0, ce.getClosing1());
		assertEquals(0, ce.getClosing2());
		assertEquals(0, ce.getOpening1());
		assertEquals(0, ce.getOpening2());
		assertEquals(0, ce.getPos1());
		assertEquals(0, ce.getPos2());
		assertEquals(0, ce.getPosition());
		
		// check transaction logs sent out
		assertNotNull(ce.getCloseTransactionLastSent1());
		assertEquals(1, ce.getCloseTransactionLastSent1().getCommissions(), 0.01);
		assertEquals(0, ce.getCloseTransactionLastSent1().getSlippage(), 0.01);
		assertEquals(1, ce.getCloseTransactionLastSent2().getCommissions(), 0.01);
		assertEquals(0, ce.getCloseTransactionLastSent2().getSlippage(), 0.01);
		
		// check history entry logs sent out
		assertNotNull(ce.getCloseHistoryEntryLastSent());
		assertEquals(2.0, ce.getCloseHistoryEntryLastSent().commissions, 0.01);
		
		
		
	}
	

}
