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
package com.pairtradinglab.ptltrader.model;
import org.joda.time.*;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.pairtradinglab.ptltrader.events.LogEvent;
import net.jcip.annotations.*;

@Immutable
public class LogEntry extends AbstractModelObject {
	private final DateTime datetime;
	private final String datetimeFormatted;
	private final String message;
	
	public LogEntry(DateTime datetime, String message) {
		super();
		this.datetime = datetime;
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm:ss a");
		//DateTimeFormatter fmt = DateTimeFormat.shortDateTime();
		this.datetimeFormatted=this.datetime.toString(fmt);
		this.message = message;
	}

	public DateTime getDatetime() {
		return datetime;
	}

	public String getMessage() {
		return message;
	}
	
	public String getDatetimeFormatted() {
		return datetimeFormatted;
	}

	public static LogEntry createFromLogEvent(LogEvent le) {
		return new LogEntry(le.datetime, le.message);
	}
	
	
}
