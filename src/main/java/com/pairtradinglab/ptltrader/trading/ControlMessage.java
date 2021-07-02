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

import net.jcip.annotations.*;

import org.joda.time.DateTime;

@Immutable
public class ControlMessage {
	public static final int TYPE_START = 1;
	public static final int TYPE_STOP = 2;
	public static final int TYPE_TICK = 3;
	public static final int TYPE_GENERIC_TICK = 4;
	public static final int TYPE_BEACON = 5;
	public static final int TYPE_CLOSE_POSITION_REQUEST = 6;
	public static final int TYPE_OPEN_POSITION_REQUEST = 7;
	public static final int TYPE_ERROR = 8;
	public static final int TYPE_ORDER_STATUS = 9;
	public static final int TYPE_EXECUTION = 10;
	public static final int TYPE_COMMISSION = 11;
	public static final int TYPE_PORTFOLIO_UPDATE = 12;
	public static final int TYPE_PAIR_DATA_READY = 13;
	public static final int TYPE_PAIR_DATA_FAILURE = 14;
	public static final int TYPE_ACCOUNT_CONNECTED = 15;
	public static final int TYPE_RESUME_REQUEST = 16;
	
	public final int type;
	public final Object data;
	public final DateTime timestamp;
	
	public ControlMessage(int type, Object data) {
		super();
		this.type = type;
		this.data = data;
		this.timestamp = DateTime.now();
	}
	
}
