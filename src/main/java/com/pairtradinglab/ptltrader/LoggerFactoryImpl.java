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

import java.io.File;
import java.io.IOException;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

public class LoggerFactoryImpl implements LoggerFactory {
	private static volatile boolean configured = false;
	
	private final RuntimeParams runtimeParams;
	
	public LoggerFactoryImpl(RuntimeParams runtimeParams) {
		super();
		this.runtimeParams = runtimeParams;
	}



	@Override
	public Logger createLogger(String name) {
		if (!configured) {
			synchronized(LoggerFactoryImpl.class) {
				if (!configured) {
					String home = System.getProperty("user.home")+ File.separator + "Application Data";
					String logfile = home + File.separator + "PTLTrader" + File.separator + runtimeParams.getProfile() + ".log";
					Logger root = Logger.getRootLogger();
					PatternLayout layout = new PatternLayout("%d{ISO8601} [%t] %p %c %x - %m%n");
					ConsoleAppender ca = new ConsoleAppender(layout);
					root.setLevel(Level.DEBUG);
					root.addAppender(ca);
					try {
						RollingFileAppender ap = new RollingFileAppender(layout, logfile, false);
						ap.setMaxFileSize("10000000");
						ap.setMaxBackupIndex(5);
						root.addAppender(ap);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					configured=true;
				}
			}
		}
		return Logger.getLogger(name);
	}

}
