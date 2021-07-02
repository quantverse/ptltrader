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
import com.ib.client.*;

public class ExecutionEvent {
	private final int reqId;
	private final Contract contract;
	private final Execution execution;
	
	public ExecutionEvent(int reqId, Contract contract, Execution execution) {
		super();
		this.reqId = reqId;
		this.contract = contract;
		this.execution = execution;
	}

	public int getReqId() {
		return reqId;
	}

	public Contract getContract() {
		return contract;
	}

	public Execution getExecution() {
		return execution;
	}
	
	
	
	

}
