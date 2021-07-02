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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.picocontainer.Startable;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.pairtradinglab.ptltrader.events.BeaconFlash;
import com.pairtradinglab.ptltrader.events.MonitorEvent;
import com.pairtradinglab.ptltrader.model.Status;
import com.pairtradinglab.ptltrader.trading.HistoricalDataProvider;
import com.pairtradinglab.ptltrader.trading.events.ManualInterventionRequested;
import com.pairtradinglab.ptltrader.trading.events.ResumeRequest;

public class SystemMonitor implements Startable {
	private final Logger logger;
	private final Status status;
	private final EventBus bus;
	private final RuntimeParams runtimeParams;
	private Map<String, ManualInterventionRequested> interventions = Collections.synchronizedMap(new HashMap<String, ManualInterventionRequested>());
	private String compName = "unknown";

	public SystemMonitor(Status status, EventBus bus,
			RuntimeParams runtimeParams, Logger logger) {
		super();
		this.status = status;
		this.bus = bus;
		this.runtimeParams = runtimeParams;
		this.logger = logger;
	}

	@Override
	public void start() {
		//compName = System.getenv("COMPUTERNAME");
		//System.out.println(compName);
		try	{
		    InetAddress addr;
		    addr = InetAddress.getLocalHost();
		    compName = addr.getHostName();
		    
		} catch (UnknownHostException ex) {
			 
		}
		bus.register(this);
		logger.info("SystemMonitor started, hostname: "+compName);
		
	}

	@Override
	public void stop() {
		bus.unregister(this);
		
	}
	
	@Subscribe
	public void onBeaconFlash(BeaconFlash event) {
		int stat = 0;
		if (!status.isIbConnected()) stat+=MonitorEvent.STATUS_IB_NOT_CONNECTED;
		if (!interventions.isEmpty()) stat+=MonitorEvent.STATUS_INTERVENTIONS_PENDING;
		MonitorEvent e = new MonitorEvent(event.getDatetime(), compName, runtimeParams.getProfile(), stat, interventions.values());
		bus.post(e);
	}
	
	@Subscribe
	public void onManualInterventionRequested(ManualInterventionRequested event) {
		interventions.put(event.strategyUid, event);
	}
	
	@Subscribe
	public void onResumeRequest(ResumeRequest event) {
		interventions.remove(event.strategyUid);
	
	}

}
