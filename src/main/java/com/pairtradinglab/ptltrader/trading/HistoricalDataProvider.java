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
import java.util.List;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.ib.client.*;

import com.google.common.eventbus.*;
import com.pairtradinglab.ptltrader.ib.HistoricalDataRequest;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.Settings;
import com.pairtradinglab.ptltrader.trading.events.Error;

import net.jcip.annotations.*;

/**
 * HistoricalDataProvider
 * @author carloss
 * 
 * This class is not entirely thread safe.
 * addRecord(): called from EReader thread
 * onError(): called from eventBus
 * the rest is called from ConfinedEngine dedicated thread
 */
@NotThreadSafe
public class HistoricalDataProvider {
	// dependencies for DI
	private final List<SimpleWrapper> ibWrapperList;
	private final Settings settings;
	private final Contract contract;
	private final Logger l;
	
	// local dependencies
	private final SimpleWrapper ibWrapper;
	
	private final DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyyMMdd");
	
	private volatile int reqid=0;
	private int lastreqidused=0;
	
	private TreeMap<DateTime, Double> data = new TreeMap<DateTime, Double>();
	
	private volatile IDataProviderOwner owner=null;
	
	private final Object lock = new Object();
	
	
	public HistoricalDataProvider(List<SimpleWrapper> ibWrapperList,
			Settings settings, Contract contract, Logger l) {
		super();
		this.ibWrapperList = ibWrapperList;
		this.settings = settings;
		this.contract = contract;
		this.l = l;
		
		// according to settings, we must decide what IB connection we are going to use for this data provider
		// at this moment, we just pick the primary one
		this.ibWrapper = ibWrapperList.get(0);
	}


	/**
	 * Called from ConfinedEngine thread only
	 */
	public int allocRequestId() {
		reqid = ibWrapper.getNextReqId();
		return reqid;
	}
	
	/**
	 * Called from ConfinedEngine thread only
	 */
	public void requestData() {
		if (reqid==lastreqidused) throw new IllegalArgumentException("allocate new request id");
		lastreqidused=reqid;
		
		ibWrapper.hmapPut(reqid, this);
		synchronized(lock) {
			data.clear();
		}
		
		l.debug(String.format("requesting historical data for %s, request id = %d", contract.m_symbol, reqid));
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyyMMdd 00:00:00");
		HistoricalDataRequest req = new HistoricalDataRequest(reqid, contract, DateTime.now().toString(fmt), "1 Y", "1 day");
		ibWrapper.executeHistoricalDataRequestQueued(req);
		
	}
	
	/**
	 * Called from EReader thread only
	 */
	public void addRecord(String datetime, double price) {
		if (datetime.startsWith("finished")) {
			// request done, we can unregister object and fire event
			l.debug(String.format("successfuly retrieved all data for %s, request id = %d, samples = %d", contract.m_symbol, reqid, data.size()));
			ibWrapper.hmapRemove(reqid);
			if (owner!=null) owner.notifyDataReady(reqid, this);
		} else {
			synchronized(lock) {
				data.put(DateTime.parse(datetime, formatter), price);
			}
		}
	}
	
	public TreeMap<DateTime, Double> getData() {
		synchronized(lock) {
			return (TreeMap<DateTime, Double>) data.clone();
		}
	}

	public IDataProviderOwner getOwner() {
		return owner;
	}

	public void setOwner(IDataProviderOwner owner) {
		this.owner = owner;
	}
	
	/**
	 * Called from EventBus threads
	 */
	@Subscribe
	public void onError(Error e) {
		if (!e.ibWrapperUid.equals(ibWrapper.getUid())) return; // ignore error of other connections!
		if (e.id>0 && e.id==reqid && ibWrapper.hmapCheckExists(reqid)) {
			l.warn(String.format("historical data request failed for request #%d", reqid));
			ibWrapper.hmapRemove(reqid);
			if (owner!=null) owner.notifyRequestFailed(reqid, this, e.code, e.message);
		}
	}
	
	public boolean isConnected() {
		return ibWrapper.getIbSocket().isConnected();
	}

}
