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

import com.fasterxml.jackson.databind.JsonNode;
import com.pairtradinglab.ptltrader.trading.events.HistoryEntry;
import net.jcip.annotations.*;

@Immutable
public class TradeHistoryEntry extends AbstractModelObject {
	
	private final DateTime datetime;
	private final String stock1;
	private final String stock2;
	private final String action;
	private final double realizedPl;
	private final double realizedPlPercent;
	private final double commissions;
	private final double zscore;
	private final String comment;
	
	private final String realizedPlS;
	private final String realizedPlPercentS;
	private final String zscoreS;
	private final String commissionsS;
	private final String datetimeS;
	private final String account;
	
	public TradeHistoryEntry(DateTime datetime, String stock1, String stock2,
			String action, double realizedPl, double realizedPlPercent,
			double commissions, double zscore, String comment, String account) {
		super();
		this.datetime = datetime;
		this.stock1 = stock1;
		this.stock2 = stock2;
		this.action = action;
		this.realizedPl = realizedPl;
		this.realizedPlPercent = realizedPlPercent;
		this.commissions = commissions;
		this.zscore = zscore;
		this.comment = comment;
		
		this.realizedPlS=String.format("%.2f", realizedPl);
		this.realizedPlPercentS=String.format("%.3f", realizedPlPercent);
		this.zscoreS=String.format("%.3f", zscore);
		this.commissionsS=String.format("%.2f", commissions);
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm:ss a");
		//DateTimeFormatter fmt = DateTimeFormat.shortDateTime();
		this.datetimeS=datetime.toString(fmt);
		this.account = account;
		
	}

	public TradeHistoryEntry(DateTime datetime, String stock1, String stock2,
			String action, String account) {
		super();
		this.datetime = datetime;
		this.stock1 = stock1;
		this.stock2 = stock2;
		this.action = action;
		
		this.realizedPl = 0;
		this.realizedPlPercent = 0;
		this.commissions = 0;
		this.zscore = 0;
		this.comment = "";
		
		this.realizedPlS="";
		this.realizedPlPercentS="";
		this.zscoreS="";
		this.commissionsS="";
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd KK:mm:ss a");
		//DateTimeFormatter fmt = DateTimeFormat.shortDateTime();
		this.datetimeS=datetime.toString(fmt);
		this.account = account;
		
		
	}
	
	
	public static TradeHistoryEntry createFromHistoryEntry(HistoryEntry he) {
		
		return new TradeHistoryEntry(he.datetime, he.stock1, he.stock2, he.action, he.realizedPl, he.realizedPlPerc, he.commissions, he.zscore, he.comment, he.account);
	}

	public DateTime getDatetime() {
		return datetime;
	}

	public String getStock1() {
		return stock1;
	}

	public String getStock2() {
		return stock2;
	}

	public String getAction() {
		return action;
	}

	public double getRealizedPl() {
		return realizedPl;
	}

	public double getRealizedPlPercent() {
		return realizedPlPercent;
	}

	public double getCommissions() {
		return commissions;
	}

	public double getZscore() {
		return zscore;
	}

	public String getComment() {
		return comment;
	}

	public String getRealizedPlS() {
		return realizedPlS;
	}

	public String getRealizedPlPercentS() {
		return realizedPlPercentS;
	}

	public String getZscoreS() {
		return zscoreS;
	}

	public String getCommissionsS() {
		return commissionsS;
	}

	public String getDatetimeS() {
		return datetimeS;
	}

	public String getAccount() {
		return account;
	}

	/*
	 * "id": "4",
    "user_uid": "UCIptT8i-d4Wqba5",
    "strategy_uid": "USNvrwxVx7tDZV1p",
    "account": "DU122884",
    "dt": "2013-03-15 18:21:47",
    "action": "opened short",
    "realizedPl": "0",
    "realizedPlPerc": "0",
    "commissions": "3.29",
    "zscore": "2.1406782266343",
    "comment": "",
    "stock1": "NASDAQ:LHCG",
    "stock2": "NASDAQ:AFAM"
	 */
	
	public static TradeHistoryEntry createFromJsonNode(JsonNode r) {
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
		DateTime dt = fmt.withZoneUTC().parseDateTime(r.get("dt").asText());
		
		return new TradeHistoryEntry(
				dt.withZone(DateTimeZone.getDefault()),
				r.get("stock1").asText(),
				r.get("stock2").asText(),
				r.get("action").asText(),
				r.get("realizedPl").asDouble(),
				r.get("realizedPlPerc").asDouble(),
				r.get("commissions").asDouble(),
				r.get("zscore").asDouble(),
				r.get("comment").asText(),
				r.get("account").asText()
		);
	}

}
