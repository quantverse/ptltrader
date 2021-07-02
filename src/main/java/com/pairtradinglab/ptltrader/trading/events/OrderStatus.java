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

public class OrderStatus {
	public int id;
	public String status;
	public int filled;
	public int remaining;
	public double avgPrice;
	public double lastPrice;
	public String whyHeld;
	
	public OrderStatus(int id, String status, int filled, int remaining,
			double avgPrice, double lastPrice, String whyHeld) {
		
		this.id = id;
		this.status = status;
		this.filled = filled;
		this.remaining = remaining;
		this.avgPrice = avgPrice;
		this.lastPrice = lastPrice;
		this.whyHeld = whyHeld;
	}
	
	
}
