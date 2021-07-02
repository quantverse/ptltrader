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
package com.pairtradinglab.ptltrader.trading;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import net.jcip.annotations.NotThreadSafe;

@NotThreadSafe
public class MarketRates {
	private volatile double bid = 0;
	private volatile double ask = 0;
	private volatile double last = 0;

	private DateTime lastBidChange = null;
	private DateTime lastAskChange = null;

	public static final int MARKET_STATUS_OK = 0;
	public static final int MARKET_STATUS_INVALID_PRICES = 1;
	public static final int MARKET_STATUS_PRICES_TOO_OLD = 3;

	public static final double MIN_PRICE=0.01;

	protected DateTimeZone timezone = DateTimeZone.getDefault();

	public double getBid() {
		return bid;
	}
	public void setBid(double bid) {
		this.bid = bid;
		lastBidChange = DateTime.now();
	}
	public double getAsk() {
		return ask;
	}
	public void setAsk(double ask) {
		this.ask = ask;
		lastAskChange = DateTime.now();
	}
	public double getLast() {
		return last;
	}
	public void setLast(double last) {
		this.last = last;
	}

	public int getMarketStatus() {
		if (bid<MIN_PRICE || ask<MIN_PRICE) return MARKET_STATUS_INVALID_PRICES;

		DateTime dt = DateTime.now();
		if (lastBidChange==null || lastBidChange.withZone(timezone).getDayOfMonth()!=dt.withZone(timezone).getDayOfMonth()) return MARKET_STATUS_PRICES_TOO_OLD;
		if (lastAskChange==null || lastAskChange.withZone(timezone).getDayOfMonth()!=dt.withZone(timezone).getDayOfMonth()) return MARKET_STATUS_PRICES_TOO_OLD;

		return MARKET_STATUS_OK;

	}


}
