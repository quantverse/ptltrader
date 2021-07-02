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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.joda.time.*;
import org.picocontainer.Startable;

import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.pairtradinglab.ptltrader.events.BeaconFlash;

import static java.util.concurrent.TimeUnit.*;

// maintains every-minute execution
public class Beacon implements Startable {
	private final EventBus bus;
	
	
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("beacon-%d").build());
	final Runnable beacon = new Runnable() {
        public void run() { 
        	//System.out.println("BEACON "+DateTime.now()+" "+this); //@DEBUG
        	bus.post(new BeaconFlash(DateTime.now()));
        }
    };
    
    ScheduledFuture<?> beaconHandle = null;
    
    

    public Beacon(EventBus bus) {
		super();
		this.bus = bus;
	}

    @Override	
	public void start() {
    	DateTime dt1 = DateTime.now();
    	DateTime dt2 = dt1.withMillisOfSecond(0).withSecondOfMinute(0).plusMinutes(1);
    	Duration d = new Duration(dt1, dt2);
    	beaconHandle = scheduler.scheduleAtFixedRate(beacon, d.getMillis(), 60000, MILLISECONDS);
    }
    
    @Override
    public void stop() {
    	if (beaconHandle!=null) {
    		//System.out.println("beacon stop called");
    		beaconHandle.cancel(true);
    		scheduler.shutdownNow();
    	}
    }
}
