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

import com.google.common.eventbus.Subscribe;
import com.pairtradinglab.ptltrader.trading.events.GenericTick;
import com.pairtradinglab.ptltrader.trading.events.Tick;
import com.pairtradinglab.ptltrader.trading.events.TickSize;
import com.ib.client.TickType;

import net.jcip.annotations.*;

@ThreadSafe
public class Position extends AbstractModelObject {
	public static final String STATUS_LONG = "long";
	public static final String STATUS_SHORT = "short";
	public static final String STATUS_NONE = "";
	
	public final String symbol;
	public final boolean isCfd;
	public final String secType;
	private volatile double last=0;
	private volatile double bid=0;
	private volatile double ask=0;
	private volatile int bidSize=0;
	private volatile int askSize=0;
	private volatile String status = STATUS_NONE;
	private volatile int qty=0;
	private volatile double pl=0;
	private volatile String plS="";
	private volatile double avgOpenPrice=0;
	private volatile String avgOpenPriceS="";
	private volatile double value=0;
	private volatile boolean shortable=false;
	        
	private volatile DateTime lastPortfolioUpdate=null;
	
	public Position(String symbol, boolean isCfd) {
		this.symbol = symbol;
		this.isCfd = isCfd;
		if (isCfd) {
			secType = "CFD";
		} else {
			secType = "STK";
		}
	}

	public String getSymbol() {
		return symbol;
	}

	public String getSecType() {
		return secType;
	}

	public double getLast() {
		return last;
	}

	public void setLast(double last) {
		double oldval=this.last;
		this.last = last;
		firePropertyChange("last", oldval, this.last);
	}

	public double getBid() {
		return bid;
	}

	public void setBid(double bid) {
		double oldval=this.bid;
		this.bid = bid;
		firePropertyChange("bid", oldval, this.bid);
	}

	public double getAsk() {
		return ask;
	}

	public void setAsk(double ask) {
		double oldval=this.ask;
		this.ask = ask;
		firePropertyChange("ask", oldval, this.ask);
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		String oldval=this.status;
		this.status = status;
		firePropertyChange("status", oldval, this.status);
	}

	public int getQty() {
		return qty;
	}

	public void setQty(int qty) {
		int oldval=this.qty;
		this.qty = qty;
		firePropertyChange("qty", oldval, this.qty);
	}

	public double getPl() {
		return pl;
	}

	public void setPl(double pl) {
		double oldval=this.pl;
		this.pl = pl;
		setPlS(String.format("%.2f", this.pl));
		firePropertyChange("pl", oldval, this.pl);
	}
	

	public double getAvgOpenPrice() {
		return avgOpenPrice;
	}

	public void setAvgOpenPrice(double avgOpenPrice) {
		double oldval=this.avgOpenPrice;
		this.avgOpenPrice = avgOpenPrice;
		setAvgOpenPriceS(String.format("%.2f", this.avgOpenPrice));
		firePropertyChange("avgOpenPrice", oldval, this.avgOpenPrice);
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		double oldval=this.value;
		this.value = value;
		firePropertyChange("value", oldval, this.value);
	}

	public DateTime getLastPortfolioUpdate() {
		return lastPortfolioUpdate;
	}

	public void setLastPortfolioUpdate(DateTime lastPortfolioUpdate) {
		this.lastPortfolioUpdate = lastPortfolioUpdate;
	}
	
	
	
	public boolean isShortable() {
		return shortable;
	}

	public void setShortable(boolean shortable) {
		boolean oldval=this.shortable;
		this.shortable = shortable;
		firePropertyChange("shortable", oldval, this.shortable);
	}
	

	@Subscribe
	public void onTick(Tick t) {
		if (symbol.equals(t.symbol)) {
			switch(t.type) {
			case TickType.BID:
				setBid(t.price);
				break;
			case TickType.ASK:
				setAsk(t.price);
				break;
			case TickType.LAST:
				setLast(t.price);
				break;
			}
		}
		
	}
	
	@Subscribe
	public void onTickSize(TickSize t) {
		if (symbol.equals(t.symbol)) {
			switch(t.type) {
			case TickType.BID_SIZE:
				setBidSize(t.size);
				break;
			case TickType.ASK_SIZE:
				setAskSize(t.size);
				break;
			}
		}
		
	}
	
	@Subscribe
	public void onGenericTick(GenericTick t) {
		//System.out.println((String.format("position: gen tick type %d of %s value %f (%s)", t.type, t.symbol, t.value, symbol)));
		if (symbol.equals(t.symbol)) {
			switch(t.type) {
			case TickType.SHORTABLE:
				setShortable(t.value>2.5);
				//System.out.println((String.format("position: symbol %s shortable %b", symbol, shortable)));
				break;
			}
		}
	}

	public String getPlS() {
		return plS;
	}

	public void setPlS(String plS) {
		String oldval = this.plS;
		this.plS = plS;
		firePropertyChange("plS", oldval, this.plS);
	}

	public String getAvgOpenPriceS() {
		return avgOpenPriceS;
	}

	public void setAvgOpenPriceS(String avgOpenPriceS) {
		String oldval = this.avgOpenPriceS;
		this.avgOpenPriceS = avgOpenPriceS;
		firePropertyChange("avgOpenPriceS", oldval, this.avgOpenPriceS);
	}

	public int getBidSize() {
		return bidSize;
	}

	public void setBidSize(int bidSize) {
		int oldval = this.bidSize;
		this.bidSize = bidSize;
		firePropertyChange("bidSize", oldval, this.bidSize);
	}

	public int getAskSize() {
		return askSize;
	}

	public void setAskSize(int askSize) {
		int oldval = this.askSize;
		this.askSize = askSize;
		firePropertyChange("askSize", oldval, this.askSize);
	}
	
	
	
	

}
