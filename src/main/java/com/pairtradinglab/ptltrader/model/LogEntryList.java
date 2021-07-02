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
import java.util.LinkedList;
import java.util.List;
import com.google.common.eventbus.*;
import com.pairtradinglab.ptltrader.events.LogEvent;
import net.jcip.annotations.*;

@ThreadSafe
public class LogEntryList extends AbstractModelObject {
	private final static int MAX_ENTRIES = 200;
	private final LinkedList<LogEntry> entries = new LinkedList<LogEntry>();

	public List<LogEntry> getEntries() {
		List<LogEntry> out;
		synchronized(entries) {
			out = (List<LogEntry>) entries.clone();
		}
		return out;
	}
	
	public void addEntry(LogEntry x) {
		synchronized(entries) {
			entries.addFirst(x);
			if (entries.size()>MAX_ENTRIES) entries.removeLast();
			firePropertyChange("entries", null, (List<LogEntry>) entries.clone());
		}
		
	}

	public void removeEntry(LogEntry x) {
		synchronized(entries) {
			entries.remove(x);
			firePropertyChange("entries", null, (List<LogEntry>) entries.clone());
		}
		
	}
	
	@Subscribe
	public void onLogEvent(LogEvent le) {
		addEntry(LogEntry.createFromLogEvent(le));
	}

}
