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
package com.pairtradinglab.ptltrader.events;

public class IbConnectionFailed {
	public static final int REASON_UNKNOWN = 0;
	public static final int REASON_API_VERSION = 1;
	
	public final String uid;
	public final int reason;
	public final int reasonData1;
	public final int reasonData2;
	
	public IbConnectionFailed(String uid, int reason, int reasonData1,
			int reasonData2) {
		super();
		this.uid = uid;
		this.reason = reason;
		this.reasonData1 = reasonData1;
		this.reasonData2 = reasonData2;
	}
	
	

}
