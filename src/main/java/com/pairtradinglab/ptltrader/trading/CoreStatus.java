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

public enum CoreStatus {
	NONE(""),
	PENDING("pending"),
	INACTIVE("fully inactive"),
	MAINTAIN_ONLY("no new positions allowed"),
	NOT_READY("wait for portfolio"),
	BLOCKED("wait for manual intervention"),
	NOT_CONNECTED("not connected"),
	TRANSIENT("transient"),
	ONE_LEG("one leg opened only"),
	SOCKET_DISCOVERY("wait for socket"),
	ENTRY_HOURS("entry: wait for trading hours"),
	ENTRY_HIST_DATA("entry: wait for hist data"),
	ENTRY_MIN_PRICE("entry: wait for min price"),
	ENTRY_PROFIT_POTENTIAL("entry: wait for min profit"),
	ENTRY_SIGNAL("entry: wait for signal"),
	ENTRY_SHORTABILITY("entry: wait for shortability"),
	ENTRY_MANAGER("entry: wait for slot"),
	ENTRY_REVERSAL_NOT_ALLOWED("entry: reversal not allowed"),
	ENTRY_PDT("entry: PDT protection"),
	EXIT_PDT("exit: PDT protection"),
	EXIT_HOURS("exit: wait for trading hours"),
	EXIT_HIST_DATA("exit: wait for hist_data"),
	EXIT_SIGNAL("exit: wait for signal"),
	EXIT_WAIT_PL_RULE("exit: wait for min P/L rule"),
	COOLDOWN("entry: cooldown"),
	UNSUPPORTED_MODEL("model not supported"),
	UNSUPPORTED_FEATURES("unsupported features"),
	MARKET_STATUS_NOT_OK("suspicious market data"),
	EXCHANGE_DEAD("no activity at the exchange");
	
	private final String status;
	
	private CoreStatus(String status) {
		this.status = status;
	}


	@Override
	public String toString() {
		return status;
	}
	
	

}
