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
import java.util.Iterator;

import java.util.LinkedList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.eventbus.Subscribe;
import com.pairtradinglab.ptltrader.trading.events.*;
import net.jcip.annotations.*;

@ThreadSafe
public class TradeHistory extends AbstractModelObject {
	private final LinkedList<TradeHistoryEntry> entries = new LinkedList<TradeHistoryEntry>();

	public List<TradeHistoryEntry> getEntries() {
		LinkedList<TradeHistoryEntry> out;
		synchronized(entries) {
			out = (LinkedList<TradeHistoryEntry>) entries.clone();
		}
		return out;
	}
	
	public void addEntry(TradeHistoryEntry x) {
		synchronized(entries) {
			entries.addFirst(x);
			firePropertyChange("entries", null, (LinkedList<TradeHistoryEntry>) entries.clone());
		}
		
	}
	
	public void addEntryLast(TradeHistoryEntry x) {
		synchronized(entries) {
			entries.addLast(x);
			firePropertyChange("entries", null, (LinkedList<TradeHistoryEntry>) entries.clone());
		}
		
	}

	public void removeEntry(TradeHistoryEntry x) {
		synchronized(entries) {
			entries.remove(x);
			firePropertyChange("entries", null, (LinkedList<TradeHistoryEntry>) entries.clone());
		}
		
	}
	
	@Subscribe
	public void onHistoryEntry(HistoryEntry he) {
		addEntry(TradeHistoryEntry.createFromHistoryEntry(he));
	}
	
	public void addItemsFromJson(JsonNode root) {
		Iterator<JsonNode> items = root.elements();
		while ( items.hasNext() ){
	    	JsonNode r = items.next();
	    	TradeHistoryEntry e = TradeHistoryEntry.createFromJsonNode(r);
	    	addEntryLast(e);
	    }
	}

}
