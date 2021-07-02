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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.pairtradinglab.ptltrader.CustomDatetimeUtcSerializer;

public class ManualInterventionRequested {
	@JsonSerialize(using = CustomDatetimeUtcSerializer.class)
	public final DateTime datetime;
	public final String strategyUid;
	public final String accountCode;
	public final String reason;
	
	public ManualInterventionRequested(DateTime datetime, String strategyUid, String accountCode, String reason) {
		super();
		this.datetime = datetime;
		this.strategyUid = strategyUid;
		this.accountCode = accountCode;
		this.reason = reason;
	}
	
	

}
