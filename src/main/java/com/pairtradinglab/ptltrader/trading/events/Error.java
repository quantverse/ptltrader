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

public class Error {
	public static final int ERRC_NO_SECURITY_DEFINITION = 200;
	public static final int ERRC_ORDER_HELD = 404; // for short orders
	public static final int ERRC_ORDER_REJECTED = 201; // insufficient funds
	public static final int ERRC_RECONNECT_DATA_LOST = 1101;
	
	public final String ibWrapperUid;
	public final int id;
	public final int code;
	public final String message;
	
	public Error(String ibWrapperUid, int id, int code, String message) {
		this.ibWrapperUid = ibWrapperUid;
		this.id = id;
		this.code = code;
		this.message = message;
	}
	
	
}