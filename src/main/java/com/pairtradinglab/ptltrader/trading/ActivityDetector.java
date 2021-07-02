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

import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.picocontainer.Startable;

import net.jcip.annotations.*;

import com.pairtradinglab.ptltrader.events.AccountConnected;
import com.pairtradinglab.ptltrader.trading.events.Disconnected;
import com.pairtradinglab.ptltrader.trading.events.Tick;
import com.google.common.eventbus.*;
import com.ib.client.TickType;

@ThreadSafe
public class ActivityDetector implements Startable {
	private final ConcurrentHashMap<String,DateTime> map = new ConcurrentHashMap<String,DateTime>();
	private final EventBus bus;
	private final Logger l;
	private final MarketDataProvider marketDataProvider;
	
	public static final String NYSEARCA_SYMBOL = "NYSEARCA:SPY";
	public static final String NYSE_SYMBOL = "NYSE:BAC";
	public static final String NASDAQ_SYMBOL = "NASDAQ:QQQ";
	public static final String NYSEMKT_SYMBOL = "NYSEMKT:NGD";
	
	public static final int MAX_DELAY = 1800; // in seconds
	

	public ActivityDetector(EventBus bus, MarketDataProvider marketDataProvider, Logger l) {
		super();
		this.bus = bus;
		this.marketDataProvider = marketDataProvider;
		this.l = l;
		
	}

	@Override
	public void start() {
		l.debug("starting exchange activity detector");
		bus.register(this);
		
	}

	@Override
	public void stop() {
		l.debug("stopping exchange activity detector");
		bus.unregister(this);
		
	}
	
	@Subscribe
	public void onAccountConnected(AccountConnected ev) {
		
		// subscribe to market data
		if (marketDataProvider.isConnected()) {
			marketDataProvider.subscribeData(NYSEARCA_SYMBOL, "detect_nysearca");
			marketDataProvider.subscribeData(NYSE_SYMBOL, "detect_nyse");
			marketDataProvider.subscribeData(NYSEMKT_SYMBOL, "detect_nysemkt");
			marketDataProvider.subscribeData(NASDAQ_SYMBOL, "detect_nasdaq");
			
		}
		
	}
	
	@Subscribe
	public void onDisconnected(Disconnected ev) {
		
	}
	
	@Subscribe
	public void onTick(Tick t) {
		if (t.type != TickType.LAST) return; // we are interested only in last prices at this place
		
		switch(t.symbol) {
			case NYSE_SYMBOL:
				map.put("NYSE", DateTime.now());
				//System.out.println("NYSE active");
				break;
			case NYSEARCA_SYMBOL:
				map.put("NYSEARCA", DateTime.now());
				//System.out.println("NYSEARCA active");
				break;
			case NYSEMKT_SYMBOL:
				map.put("NYSEMKT", DateTime.now());
				//System.out.println("NYSEMKT active");
				break;
			case NASDAQ_SYMBOL:
				map.put("NASDAQ", DateTime.now());
				//System.out.println("NASDAQ active");
				break;
		}
		
	}
	
	public boolean isExchangeActive(String exchange) {
		DateTime dt = map.get(exchange);
		if (dt==null) return false;
		Duration d = new Duration(dt, DateTime.now());
		//System.out.println(String.format("ex act %s: %d", exchange, d.getMillis()));
		return d.getStandardSeconds()<MAX_DELAY;
	}
	
	

}
