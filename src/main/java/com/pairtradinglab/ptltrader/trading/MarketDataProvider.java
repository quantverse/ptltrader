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
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.picocontainer.Startable;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.Settings;
import com.pairtradinglab.ptltrader.trading.events.Disconnected;
import com.pairtradinglab.ptltrader.trading.events.Error;
import net.jcip.annotations.*;
import java.util.concurrent.atomic.AtomicInteger;

@ThreadSafe
public class MarketDataProvider implements Startable {
	// dependencies to inject
	private final List<SimpleWrapper> ibWrapperList;
	private final Settings settings;
	private final EventBus bus;
	private final Logger l;
	
    private final static AtomicInteger lastReqId = new AtomicInteger(20000000);
    
    @GuardedBy("lock")
    private HashMap<String, HashSet<String>> contractSet = new HashMap<String, HashSet<String>>();
    private final Object lock = new Object();
    
    public MarketDataProvider(List<SimpleWrapper> ibWrapperList,
			Settings settings, EventBus bus, LoggerFactory loggerFactory) {
		super();
		this.ibWrapperList = ibWrapperList;
		this.settings = settings;
		this.bus = bus;
		this.l = loggerFactory.createLogger(this.getClass().getSimpleName());
		
	}
    
    private SimpleWrapper getWrapper() {
    	return ibWrapperList.get(0);
    }

	public static int getNextReqId() {
		return lastReqId.incrementAndGet();
	}
    
    public void subscribeData(String symbol, String subscriberUid) {
    	int reqId;
    	synchronized(lock) {
    		if (contractSet.containsKey(symbol)) {
    			// already in subscribe list, we just add subscriber to the list
    			contractSet.get(symbol).add(subscriberUid);
    			return;
    		}
	    	reqId = getNextReqId();
	    	HashSet<String> set = new HashSet<String>();
	    	set.add(subscriberUid);
	    	contractSet.put(symbol, set);
	    	getWrapper().putToMarketDataReqMap(reqId, symbol);
	    	
    	}
    	l.info("subscribing for market data of "+symbol+", subscriber UID="+subscriberUid);
    	ContractExt c = ContractExt.createFromGoogleSymbol(symbol, false); // always use STK
    	getWrapper().getIbSocket().reqMktData(reqId, c, "236", false, null); // 236=also request SHORTABLE field
    }
    
    public void unsubscribeData(String symbol, String subscriberUid) {
    	int reqId;
    	synchronized(lock) {
    		if (!contractSet.containsKey(symbol)) return; // no subscription
    		if (!contractSet.get(symbol).contains(subscriberUid)) return; // unknown subscriber
    		
    		// remove subscriber
    		contractSet.get(symbol).remove(subscriberUid);
    		if (contractSet.get(symbol).size()>0) return; // there are still subscribers
    		
    		// no more subscribers, let's cancel the subscription
    		reqId=-1;
    		ArrayList<Entry<Integer, String>> entryset = new ArrayList<Entry<Integer, String>>();
    		getWrapper().getMarketDataReqMapCopy(entryset);
    		for (Entry<Integer, String> entry : entryset) {
    	        if (symbol.equals(entry.getValue())) {
    	        	reqId=entry.getKey();
    	            break;
    	        }
    	    }
    		
	    	if (reqId<=0) return; // not found
	    	
    	}
    	l.info("cancelling market data of "+symbol+", last subscriber was "+subscriberUid);
    	getWrapper().getIbSocket().cancelMktData(reqId);
    }
    
    
    private void resubscribeAll() {
    	// we need to resubscribe for all data we have
		l.warn("resubscribing to market data after reconnect");
		HashMap<Integer,String> subscribeList = new HashMap<Integer,String>();
		synchronized(lock) {
			getWrapper().clearMarketDataReqMap();
    		for (String sym:contractSet.keySet()) {
    			int reqId = getNextReqId();
    			getWrapper().putToMarketDataReqMap(reqId, sym);
    			subscribeList.put(reqId, sym);
    			
    		}
		}
		for (Entry<Integer, String> entry : subscribeList.entrySet()) {
			l.info("re-subscribing for market data of "+entry.getValue());
			ContractExt c = ContractExt.createFromGoogleSymbol(entry.getValue(), false); // always use STK here
			getWrapper().getIbSocket().reqMktData(entry.getKey(), c, "236", false, null); // 236=also request SHORTABLE field
		}
    	
    }
    
    @Subscribe
    public void onError(Error err) {
    	if (!getWrapper().getUid().equals(err.ibWrapperUid)) return; // not interested in alien errors
    	if (err.code==Error.ERRC_RECONNECT_DATA_LOST) {
    		// IB API reconnected after connection loss but not able to resubscribe for market data
    		resubscribeAll();
    		
    	} else if (err.id>=20000000 && getWrapper().getSymbolFromMarketDataReqMap(err.id)!=null) {
    		l.error("there was a problem in subscribing for data of "+getWrapper().getSymbolFromMarketDataReqMap(err.id));
    		//@FIXME - what to do here? retry?
    	}
    	
    }
    
    @Subscribe
    public void onDisconnected(Disconnected d) {
    	if (!getWrapper().getUid().equals(d.ibWrapperUid)) return; // not interested in alien disconnections
    	// we got disconnected from IB API - let's delete all data
    	l.warn("IB API disconnected - reseting market provider data");
    	synchronized(lock) {
    		contractSet.clear();
    		getWrapper().clearMarketDataReqMap();
    	}
    	
    	
    }

	@Override
	public void start() {
		bus.register(this);
		
	}

	@Override
	public void stop() {
		bus.unregister(this);
		
	}
	
	public boolean isConnected() {
		return getWrapper().getIbSocket().isConnected();
	}

}
