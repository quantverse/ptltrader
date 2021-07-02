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

import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.pairtradinglab.ptltrader.CustomDatetimeUtcSerializer;
import com.pairtradinglab.ptltrader.CustomDurationSerializer;
import com.pairtradinglab.ptltrader.events.ConfidentialEvent;
import com.pairtradinglab.ptltrader.events.ImportantEvent;

public class TransactionEvent implements ImportantEvent, ConfidentialEvent {
	public static final int DIRECTION_LONG = 1;
	public static final int DIRECTION_SHORT = -1;
	
	private final String strategyUid;
	private final String account;
	@JsonSerialize(using = CustomDatetimeUtcSerializer.class)
	private DateTime datetime=null;
	public final String symbol;
	public final int direction;
	public final int qty;
	private double realizedPl=0;
	private double commissions=0;
	public final double price;
	public final double value;
	
	@JsonSerialize(using = CustomDurationSerializer.class)
	private Duration fillTime=null;
	@JsonIgnore
	private double cumVal=0;
	@JsonIgnore
	private boolean fillConfirmed=false;
	@JsonIgnore
	private boolean execConfirmed=false;
	@JsonIgnore
	private int toExecute;
	
	
	public TransactionEvent(String strategyUid, String account, String symbol, int direction, int qty, double price) {
		super();
		this.strategyUid = strategyUid;
		this.account = account;
		this.symbol = symbol;
		this.direction = direction;
		this.qty = qty;
		this.price = price;
		this.value = price * (double) qty;
		this.toExecute = qty;
	}
	public DateTime getDatetime() {
		return datetime;
	}
	public void setDatetime(DateTime datetime) {
		this.datetime = datetime;
	}
	public double getRealizedPl() {
		return realizedPl;
	}
	public void setRealizedPl(double realizedPl) {
		this.realizedPl = realizedPl;
	}
	public double getCommissions() {
		return commissions;
	}
	public void setCommissions(double commissions) {
		this.commissions = commissions;
	}
	public Duration getFillTime() {
		return fillTime;
	}
	public void setFillTime(Duration fillTime) {
		this.fillTime = fillTime;
	}
	public double getCumVal() {
		return cumVal;
	}
	
	public void add2CumVal(double v) {
		cumVal+=v;
	}
	
	public void add2Commissions(double v) {
		commissions+=v;
	}
	
	public void add2RealizedPl(double v) {
		realizedPl+=v;
	}
	
	public double getSlippage() {
		if (direction==DIRECTION_LONG) {
			return value-cumVal;
		} else {
			return cumVal-value;
		}
		
	}
	
	
	public String getStrategyUid() {
		return strategyUid;
	}
	public String getAccount() {
		return account;
	}
	public boolean isFillConfirmed() {
		return fillConfirmed;
	}
	public void setFillConfirmed(boolean fillConfirmed) {
		this.fillConfirmed = fillConfirmed;
	}
	public boolean isExecConfirmed() {
		return execConfirmed;
	}
	public void setExecConfirmed(boolean execConfirmed) {
		this.execConfirmed = execConfirmed;
	}
	public int getToExecute() {
		return toExecute;
	}
	
	public void markExecuted(int qtyExecuted) {
		toExecute-=qtyExecuted;
		
	}
	
	
	

}
