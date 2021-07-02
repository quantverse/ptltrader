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
package com.pairtradinglab.ptltrader.trading.events;
import org.joda.time.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.pairtradinglab.ptltrader.CustomDatetimeUtcSerializer;
import com.pairtradinglab.ptltrader.events.ConfidentialEvent;
import com.pairtradinglab.ptltrader.events.ImportantEvent;

public class HistoryEntry implements ImportantEvent, ConfidentialEvent {
	public static final String ACTION_OPENED_LONG = "opened long";
	public static final String ACTION_OPENED_SHORT = "opened short";
	public static final String ACTION_CLOSED = "closed";
	public static final String ACTION_NONE = "";
	
	public final String strategyUid;
	public final String account;
	@JsonSerialize(using = CustomDatetimeUtcSerializer.class)
	public DateTime datetime;
	public final String stock1;
	public final String stock2;
	public final String action;
	public double realizedPl=0;
	public double realizedPlPerc=0;
	public double commissions=0;
	public final double zscore;
	public final String comment;
	@JsonIgnore
	public boolean leg1Ready=false;
	@JsonIgnore
	public boolean leg2Ready=false;
	
	
	public HistoryEntry(String strategyUid, String account, DateTime datetime, String stock1, String stock2,
			String action, double realizedPl, double realizedPlPerc,
			double commissions, double zscore, String comment) {
		super();
		this.strategyUid = strategyUid;
		this.account = account;
		this.datetime = datetime;
		this.stock1 = stock1;
		this.stock2 = stock2;
		this.action = action;
		this.realizedPl = realizedPl;
		this.realizedPlPerc = realizedPlPerc;
		this.commissions = commissions;
		this.zscore = zscore;
		this.comment = comment;
		
	}

	public HistoryEntry(String strategyUid, String account, DateTime datetime, String stock1, String stock2,
			String action, double zscore, String comment) {
		super();
		this.strategyUid = strategyUid;
		this.account = account;
		this.datetime = datetime;
		this.stock1 = stock1;
		this.stock2 = stock2;
		this.action = action;
		this.zscore = zscore;
		this.comment = comment;
		
	
	}
	
	
	
	
	

}
