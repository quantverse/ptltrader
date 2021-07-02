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

import java.util.ArrayList;
import java.util.Arrays;
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

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.picocontainer.DefaultPicoContainer;
import org.picocontainer.MutablePicoContainer;

import java.util.List;

public class HistoricalDataProviderTest {
	private MutablePicoContainer pico;
	private SimpleWrapper sw;
	private EClientSocket socket;
	private EventBus bus;
	private RuntimeParams runtimeMock = mock(RuntimeParams.class);

	@Before
	public void setUp() throws Exception {
		sw = mock(SimpleWrapper.class);
		socket = mock(EClientSocket.class);
		bus = new EventBus("test-bus");
		
		pico = new DefaultPicoContainer();
		pico.addComponent(HistoricalDataProviderFactoryImpl.class);
		pico.addComponent(StringXorProcessor.class);
		pico.addComponent(runtimeMock);
		//LoggerFactory lf = new LoggerFactoryImpl();
		//Logger l = lf.createLogger(HistoricalDataProviderTest.class.getSimpleName());
		Logger l = mock(Logger.class);
		pico.addComponent(l);
		pico.addComponent(Settings.class);
		pico.addComponent(sw);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGetHistoricalDataSuccess() {
		when(sw.getIbSocket()).thenReturn(socket);
		when(sw.getUid()).thenReturn("ABCD");
		when(sw.getNextReqId()).thenReturn(10000001);
		
		
		HistoricalDataProviderFactory hdf = (HistoricalDataProviderFactory) pico.getComponent(HistoricalDataProviderFactory.class);
		ContractExt c = ContractExt.createFromGoogleSymbol("NYSE:A", false);
		final HistoricalDataProvider hdp = hdf.createForContract(c);
		
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
				        	 
				        	 //System.out.println(args[0]);
				        	 if ((int) args[0]==10000001) {
				        		 hdp.addRecord("20130101", 10);
				        		 hdp.addRecord("20130103", 12);
				        		 hdp.addRecord("20130102", 11);
				        		 hdp.addRecord("finished-20120111  00:00:00-20130111  00:00:00", 0);
				        	 }
				        	
				        	 
				         }
				         return null;
				     }
				 }).when(socket).reqHistoricalData(anyInt(), any(Contract.class), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), (List<TagValue>) any());
		
		
		int rid = hdp.allocRequestId();
		assertTrue(rid>10000000);
		hdp.requestData();
		
		verify(sw).hmapPut(10000001, hdp);
		
		TreeMap<DateTime, Double> res = hdp.getData();
		//System.out.println(res);
		List<Double> prices = new ArrayList<>(res.values());
		assertArrayEquals(new Double[]{10.0, 11.0, 12.0}, prices.toArray());
		// TODO also check keys
	}
	
	
	
	@Test
	public void testGetHistoricalDataFailure() {
		when(sw.getIbSocket()).thenReturn(socket);
		when(sw.getNextReqId()).thenReturn(10000001);
		when(sw.hmapCheckExists(10000001)).thenReturn(true);
		when(sw.getUid()).thenReturn("ABCD");
		
		HistoricalDataProviderFactory hdf = (HistoricalDataProviderFactory) pico.getComponent(HistoricalDataProviderFactory.class);
		ContractExt c = ContractExt.createFromGoogleSymbol("NYSE:A", false);
		final HistoricalDataProvider hdp = hdf.createForContract(c);
		
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
				        	 
				        	 
				        	 bus.post(new Error("ABCD", (int) args[0], 200, "No security definition has been found for the request"));
				        	 
				         }
				         return null;
				     }
				 }).when(socket).reqHistoricalData(anyInt(), any(Contract.class), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), (List<TagValue>) any());
		
		
		
		bus.register(hdp);
		
		int rid = hdp.allocRequestId();
		assertTrue(rid>10000000);
		hdp.requestData();
		TreeMap<DateTime, Double> res = hdp.getData();
		//System.out.println(res);
		assertEquals("{}", res.toString());
		
		
	}

}
