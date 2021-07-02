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
package com.pairtradinglab.ptltrader.ib;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

//ibWrapper.getIbSocket().reqHistoricalData(reqid, contract, DateTime.now().toString(fmt), "1 Y", "1 day", "TRADES", 1, 1);
public class HistoricalDataRequest {
	public final int reqid;
	public final Contract contract;
	public final String endDatetime;
	public final String duration;
	public final String barSize;
	public final String whatToShow;
	public final int useRTH;
	public final int format;
	
	
	public HistoricalDataRequest(int reqid, Contract contract,
			String endDatetime, String duration, String barSize,
			String whatToShow, int useRTH, int format) {
		super();
		this.reqid = reqid;
		this.contract = contract;
		this.endDatetime = endDatetime;
		this.duration = duration;
		this.barSize = barSize;
		this.whatToShow = whatToShow;
		this.useRTH = useRTH;
		this.format = format;
	}


	public HistoricalDataRequest(int reqid, Contract contract,
			String endDatetime, String duration, String barSize) {
		super();
		this.reqid = reqid;
		this.contract = contract;
		this.endDatetime = endDatetime;
		this.duration = duration;
		this.barSize = barSize;
		
		this.whatToShow="TRADES";
		this.useRTH = 1;
		this.format = 1;
	}
	
	public void execute(EClientSocket socket) {
		socket.reqHistoricalData(reqid, contract, endDatetime, duration, barSize, whatToShow, useRTH, format, null);
	}
	
	

}
