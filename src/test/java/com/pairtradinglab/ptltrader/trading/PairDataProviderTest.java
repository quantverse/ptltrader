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

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.TreeMap;

import com.google.common.eventbus.EventBus;
import com.ib.client.*;

import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.StringXorProcessor;
import com.pairtradinglab.ptltrader.ib.HistoricalDataRequest;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.Settings;
import com.pairtradinglab.ptltrader.trading.events.Error;

import static org.mockito.Mockito.*;

import org.joda.time.DateTime;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.picocontainer.DefaultPicoContainer;
import org.picocontainer.MutablePicoContainer;

import java.util.List;


public class PairDataProviderTest {

	private MutablePicoContainer pico;
	private SimpleWrapper sw;
	private EClientSocket socket;
	private EventBus bus;
	private int nextReqId;
	private RuntimeParams runtimeMock = mock(RuntimeParams.class);

	@Before
	public void setUp() throws Exception {
		nextReqId = 10000001;
		sw = mock(SimpleWrapper.class);
		socket = mock(EClientSocket.class);
		bus = new EventBus("test-bus");
		
		pico = new DefaultPicoContainer();
		pico.addComponent(HistoricalDataProviderFactoryImpl.class);
		pico.addComponent(StringXorProcessor.class);
		pico.addComponent(runtimeMock);
		//LoggerFactory lf = new LoggerFactoryImpl();
		//Logger l = lf.createLogger(PairDataProviderTest.class.getSimpleName());
		Logger l = mock(Logger.class);
		pico.addComponent(l);
		pico.addComponent(Settings.class);
		pico.addComponent(sw);
		pico.addComponent(PairDataProviderFactoryImpl.class);
		pico.addComponent(bus);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testRequestDataSuccess() {
		when(sw.getIbSocket()).thenReturn(socket);
		when(sw.getUid()).thenReturn("ABCD");
		when(sw.getNextReqId()).then(new Answer() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				return nextReqId++;
			}
			
		});
		PairDataProviderFactory pdpf = (PairDataProviderFactory) pico.getComponent(PairDataProviderFactory.class);
		
		ContractExt c1 = ContractExt.createFromGoogleSymbol("NYSE:A", false);
		ContractExt c2 = ContractExt.createFromGoogleSymbol("NYSE:B", false);
		
		final PairDataProvider pdr =pdpf.createForContracts(c1, c2);
		
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         
				         HistoricalDataRequest hrq = (HistoricalDataRequest) args[0];
				         socket.reqHistoricalData(hrq.reqid, hrq.contract, hrq.endDatetime, hrq.duration, hrq.barSize, hrq.whatToShow, hrq.useRTH, hrq.format, null);
				         
				         return null;
				     }
				 }).when(sw).executeHistoricalDataRequestQueued(any(HistoricalDataRequest.class));
		
		
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         
				         if (args[1] instanceof Contract) {
				        	 Contract c = (Contract) args[1];
				        	 
				        	 //System.out.println(args[0]+" "+c.m_symbol);
				        	 
			        		 //System.out.println("got provider object");
			        		 if ("A".equals(c.m_symbol)) {
				        		 pdr.getP1().addRecord("20130101", 10);
				        		 pdr.getP1().addRecord("20130103", 12);
				        		 pdr.getP1().addRecord("20130102", 11);
				        		 pdr.getP1().addRecord("finished-20120111  00:00:00-20130111  00:00:00", 0);
			        		 }
			        		 if ("B".equals(c.m_symbol)) {
			        			 pdr.getP2().addRecord("20130103", 22);
			        			 pdr.getP2().addRecord("20130102", 21);
			        			 pdr.getP2().addRecord("finished-20120111  00:00:00-20130111  00:00:00", 0);
			        		 }
				        		 
				        	 
				        	 
				         }
				         return null;
				     }
				 }).when(socket).reqHistoricalData(anyInt(), any(Contract.class), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), (List<TagValue>) any());
		
		
		int rid = pdr.allocReqId();
		assertTrue(rid>10000000);
		pdr.requestData();
		TreeMap<DateTime, Double> res1 = pdr.getDataOut1();
		//System.out.println(res1);
		TreeMap<DateTime, Double> res2 = pdr.getDataOut2();
		//System.out.println(res2);
		//assertEquals("{2013-01-02T00:00:00.000+01:00=11.0, 2013-01-03T00:00:00.000+01:00=12.0}", res1.toString());
		List<Double> prices1 = new ArrayList<>(res1.values());
		assertArrayEquals(new Double[]{11.0, 12.0}, prices1.toArray());
		// TODO also check keys
		//assertEquals("{2013-01-02T00:00:00.000+01:00=21.0, 2013-01-03T00:00:00.000+01:00=22.0}", res2.toString());
		List<Double> prices2 = new ArrayList<>(res2.values());
		assertArrayEquals(new Double[]{21.0, 22.0}, prices2.toArray());
		// TODO also check keys
		
	}
	
	
	@Test
	public void testRequestDataFailure() {
		when(sw.getIbSocket()).thenReturn(socket);
		when(sw.getNextReqId()).then(new Answer() {

			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				return nextReqId++;
			}
			
		});
		when(sw.hmapCheckExists(10000001)).thenReturn(true);
		when(sw.hmapCheckExists(10000002)).thenReturn(true);
		when(sw.getUid()).thenReturn("ABCD");
		PairDataProviderFactory pdpf = (PairDataProviderFactory) pico.getComponent(PairDataProviderFactory.class);
		
		ContractExt c1 = ContractExt.createFromGoogleSymbol("NYSE:A", false);
		ContractExt c2 = ContractExt.createFromGoogleSymbol("NYSE:B", false);
		
		final PairDataProvider pdr =pdpf.createForContracts(c1, c2);
		pdr.start();
		
		doAnswer(
				new Answer() {
				     public Object answer(InvocationOnMock invocation) {
				         Object[] args = invocation.getArguments();
				         Object mock = invocation.getMock();
				         
				         if (args[1] instanceof Contract) {
				        	 Contract c = (Contract) args[1];
				        	 
				        	 //System.out.println(args[0]+" "+c.m_symbol);
				        	 //System.out.println("got provider object");
			        		 if ("A".equals(c.m_symbol)) {
			        			 pdr.getP1().addRecord("20130101", 10);
			        			 pdr.getP1().addRecord("20130103", 12);
			        			 pdr.getP1().addRecord("20130102", 11);
			        			 pdr.getP1().addRecord("finished-20120111  00:00:00-20130111  00:00:00", 0);
			        		 }
			        		 if ("B".equals(c.m_symbol)) {
			        			 bus.post(new Error("ABCD", (int) args[0], 200, "No security definition has been found for the request"));
			        		 }
				        	 
				         }
				         return null;
				     }
				 }).when(socket).reqHistoricalData(anyInt(), any(Contract.class), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), (List<TagValue>) any());
		
		
		int rid = pdr.allocReqId();
		assertTrue(rid>10000000);
		pdr.requestData();
		TreeMap<DateTime, Double> res1 = pdr.getDataOut1();
		//System.out.println(res1);
		TreeMap<DateTime, Double> res2 = pdr.getDataOut2();
		//System.out.println(res2);
		assertEquals("{}", res1.toString());
		assertEquals("{}", res2.toString());
		
	}

}
