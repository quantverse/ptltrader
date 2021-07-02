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
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.picocontainer.Startable;

import com.ib.client.*;
import com.pairtradinglab.ptltrader.trading.events.PairDataFailure;
import com.pairtradinglab.ptltrader.trading.events.PairDataReady;
import com.google.common.eventbus.*;
import net.jcip.annotations.*;

@ThreadSafe
public class PairDataProvider implements IDataProviderOwner, Startable {
	// DI dependencies
	private final EventBus bus;
	private final HistoricalDataProviderFactory hdpFactory;
	
	private HistoricalDataProvider p1;
	private HistoricalDataProvider p2;
	
	private int reqId1=0;
	private int reqId2=0;
	private int reqId=0;
	
	private TreeMap<DateTime, Double> data1 = null;
	private TreeMap<DateTime, Double> data2 = null;
	
	private TreeMap<DateTime, Double> dataOut1 = new TreeMap<DateTime, Double>();
	private TreeMap<DateTime, Double> dataOut2 = new TreeMap<DateTime, Double>();
	
	private DateTime dataStamp=null;
	

	public PairDataProvider(EventBus bus,
			HistoricalDataProviderFactory hdpFactory, Contract c1, Contract c2) {
		super();
		this.bus = bus;
		this.hdpFactory = hdpFactory;
		
		p1 = hdpFactory.createForContract(c1);
		p1.setOwner(this);
		p2 = hdpFactory.createForContract(c2);
		p2.setOwner(this);
		
	}

	/**
	 * Called from ConfinedEngine thread
	 */
	public synchronized int allocReqId() {
		data1=null;
		data2=null;
		
		reqId1 = p1.allocRequestId();
		reqId2 = p2.allocRequestId();
		
		reqId=reqId1;
		dataStamp=null;
		return reqId;
		
	}
	
	/**
	 * Called from ConfinedEngine thread
	 */
	public synchronized void requestData() {
		data1=null;
		data2=null;
		
		dataOut1.clear();
		dataOut2.clear();
		
		p1.requestData();
		p2.requestData();
		
		dataStamp=null;
		
	}


	/**
	 * Called from EReader thread
	 */
	@Override
	public synchronized void notifyDataReady(int rqid, HistoricalDataProvider provider) {
		//System.out.println(String.format("ready reqid %d rq1 %d rq2 %d", rqid, reqId1, reqId2));
		if (rqid==reqId1) {
			data1 = provider.getData();
			//System.out.println("d1: "+data1);
			
		} else if (rqid==reqId2) {
			data2 = provider.getData();
			//System.out.println("d2: "+data2);
			
		}
		
		if (data1!=null && data2!=null) {
			reqId1=0;
			reqId2=0;
			
			dataOut1.clear();
			dataOut2.clear();
			for (DateTime dt:data1.keySet()) {
				if (data2.get(dt)!=null) {
					dataOut1.put(dt, data1.get(dt));
					dataOut2.put(dt, data2.get(dt));
				}
			}
			
			dataStamp=DateTime.now();
			
			// setup and fire historical data completed event
			PairDataReady pdr=new PairDataReady(this.reqId, dataStamp);
			bus.post(pdr);
			
			reqId=0;
		}

	}

	/**
	 * Called from EventBus threads
	 */
	@Override
	public synchronized void notifyRequestFailed(int reqId, HistoricalDataProvider provider, int code, String message) {
		dataOut1.clear();
		dataOut2.clear();
		if (reqId==reqId1) {
			data1=null;
			reqId1=0;
		} else if (reqId==reqId2) {
			data2=null;
			reqId2=0;
		}
		bus.post(new PairDataFailure(this.reqId, code, message));
		
		
	}

	public synchronized TreeMap<DateTime, Double> getDataOut1() {
		return dataOut1;
	}

	public synchronized TreeMap<DateTime, Double> getDataOut2() {
		return dataOut2;
	}


	@Override
	public void start() {
		bus.register(p1);
		bus.register(p2);
		
	}


	@Override
	public void stop() {
		bus.unregister(p1);
		bus.unregister(p2);
		
	}


	protected HistoricalDataProvider getP1() {
		return p1;
	}


	protected HistoricalDataProvider getP2() {
		return p2;
	}
	
	public boolean isConnected() {
		return p1.isConnected() && p2.isConnected();
	}
	

}
