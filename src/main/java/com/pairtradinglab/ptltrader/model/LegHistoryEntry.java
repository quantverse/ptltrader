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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.pairtradinglab.ptltrader.trading.events.TransactionEvent;
import net.jcip.annotations.*;

@Immutable
public class LegHistoryEntry extends AbstractModelObject {
	public static final String ACTION_BUY = "buy";
	public static final String ACTION_SELL = "sell";
	
	private final DateTime datetime;
	private final String datetimeS;
	private final String symbol;
	private final String action;
	private final int qty;
	private final double realizedPl;
	private final String realizedPlS;
	private final double commissions;
	private final String commissionsS;
	private final double price;
	private final String priceS;
	private final double value;
	private final String valueS;
	private final double slippage;
	private final String slippageS;
	private final Duration fillTime;
	private final String fillTimeS;
	private final String account;
	
	
	public LegHistoryEntry(DateTime datetime, String symbol, String action,
			int qty, double realizedPl, double commissions, double price,
			double value, double slippage, Duration fillTime, String account) {
		super();
		this.datetime = datetime;
		this.symbol = symbol;
		this.action = action;
		this.qty = qty;
		this.realizedPl = realizedPl;
		this.commissions = commissions;
		this.price = price;
		this.value = value;
		this.slippage = slippage;
		this.fillTime = fillTime;
		
		if (this.datetime==null) this.datetimeS="";
		else {
			DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm:ss a");
			this.datetimeS = this.datetime.toString(fmt);
		}
		
		this.realizedPlS = String.format("%.2f", this.realizedPl);
		this.commissionsS = String.format("%.2f", this.commissions);
		this.priceS = String.format("%.2f", this.price);
		this.valueS = String.format("%.2f", this.value);
		this.slippageS = String.format("%.2f", this.slippage);
		if (this.fillTime==null) this.fillTimeS=""; 
		else this.fillTimeS = String.format("%.3f", ((double) this.fillTime.getMillis())/1000.0);
		
		this.account = account;
	}


	public DateTime getDatetime() {
		return datetime;
	}


	public String getDatetimeS() {
		return datetimeS;
	}


	public String getSymbol() {
		return symbol;
	}


	public String getAction() {
		return action;
	}


	public int getQty() {
		return qty;
	}


	public double getRealizedPl() {
		return realizedPl;
	}


	public String getRealizedPlS() {
		return realizedPlS;
	}


	public double getCommissions() {
		return commissions;
	}


	public String getCommissionsS() {
		return commissionsS;
	}


	public double getPrice() {
		return price;
	}


	public String getPriceS() {
		return priceS;
	}


	public double getValue() {
		return value;
	}


	public String getValueS() {
		return valueS;
	}


	public double getSlippage() {
		return slippage;
	}


	public String getSlippageS() {
		return slippageS;
	}


	public Duration getFillTime() {
		return fillTime;
	}


	public String getFillTimeS() {
		return fillTimeS;
	}
	
	
	
	public String getAccount() {
		return account;
	}


	public static LegHistoryEntry createFromTransactionEvent(TransactionEvent ev) {
		return new LegHistoryEntry(ev.getDatetime(), ev.symbol,
				(ev.direction==TransactionEvent.DIRECTION_LONG)?LegHistoryEntry.ACTION_BUY:LegHistoryEntry.ACTION_SELL,
						ev.qty, ev.getRealizedPl(), ev.getCommissions(), ev.price, ev.value, ev.getSlippage(), ev.getFillTime(), ev.getAccount());
	}
	
	public static LegHistoryEntry createFromJsonNode(JsonNode r) {
		int direction = r.get("direction").asInt();
		double fillTime = r.get("fillTime").asDouble();
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
		DateTime dt = fmt.withZoneUTC().parseDateTime(r.get("dt").asText());
		 
		return new LegHistoryEntry(
				dt.withZone(DateTimeZone.getDefault()),
				r.get("ticker").asText(),
				(direction==TransactionEvent.DIRECTION_LONG)?LegHistoryEntry.ACTION_BUY:LegHistoryEntry.ACTION_SELL,
				r.get("qty").asInt(),
				r.get("realizedPl").asDouble(),
				r.get("commissions").asDouble(),
				r.get("price").asDouble(),
				r.get("value").asDouble(),
				r.get("slippage").asDouble(),
				Duration.millis((long) (fillTime*1000)),
				r.get("account").asText()
				
				);
	}
	

}
