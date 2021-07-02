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
package com.pairtradinglab.ptltrader;

import org.picocontainer.Startable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.pairtradinglab.ptltrader.events.ConfidentialEvent;
import com.pairtradinglab.ptltrader.events.ImportantEvent;
import com.pairtradinglab.ptltrader.events.ImportantTestEvent;
import com.pairtradinglab.ptltrader.events.MonitorEvent;
import com.pairtradinglab.ptltrader.events.SerializedEvent;
import com.pairtradinglab.ptltrader.events.TestEvent;
import com.pairtradinglab.ptltrader.model.Settings;
import com.pairtradinglab.ptltrader.trading.events.EquityChange;
import com.pairtradinglab.ptltrader.trading.events.HistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.StrategyPlUpdated;
import com.pairtradinglab.ptltrader.trading.events.TransactionEvent;

public class AmqpProxy implements Startable {
	private final EventBus bus;
	private final Settings settings;
	
	

	public AmqpProxy(EventBus bus, Settings settings) {
		super();
		this.bus = bus;
		this.settings = settings;
	}

	@Override
	public void start() {
		bus.register(this);
		
	}

	@Override
	public void stop() {
		bus.unregister(this);
		
	}
	
	private void postSerialized(String json, String className, boolean important) {
		bus.post(new SerializedEvent(json, className, important));
	}
	
	private void serializeAndPost(Object o) {
		if (settings.isEnableConfidentialMode() && o instanceof ConfidentialEvent) {
			//System.out.println("event proxy discarded confidential event: "+o.getClass().getSimpleName());
			return;
		}
		ObjectMapper mapper = new ObjectMapper();
		try {
			String json = mapper.writeValueAsString(o);
			//System.out.println("proxy serialized event as "+json); //@DEBUG
			postSerialized(json, o.getClass().getSimpleName(), o instanceof ImportantEvent);
		} catch (JsonProcessingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	@Subscribe
	public void onTestEvent(TestEvent e) {
		serializeAndPost(e);
		
	}
	
	@Subscribe
	public void onImportantTestEvent(ImportantTestEvent e) {
		serializeAndPost(e);
	}
	
	@Subscribe
	public void onTransactionEvent(TransactionEvent e) {
		serializeAndPost(e);
	}
	
	@Subscribe
	public void onHistoryEntry(HistoryEntry e) {
		serializeAndPost(e);
	}

	@Subscribe
	public void onMonitorEvent(MonitorEvent e) {
		serializeAndPost(e);
	}

}
