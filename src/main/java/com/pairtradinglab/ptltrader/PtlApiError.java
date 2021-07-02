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
package com.pairtradinglab.ptltrader;

public enum PtlApiError {
	SSL_NAME("SSL Certificate Security Error. Please contact helpdesk."),
	ACCESS_DENIED("Access denied. Sorry, your credentials are invalid."),
	BIND_DENIED("Bind operation rejected. There is already another portfolio bound to this account or you have exceeded the maximum amount of accounts you can use."),
	INVALID_RESPONSE("Invalid server response. Please try again later or contact helpdesk."),
	UNSUPPORTED_VERSION("This version of PTL Trader is not supported anymore. Please download and install fresh version of PTL Trader software."),
	UNKNOWN("There was an error contacting Pair Trading Lab server. Please try again later.");
	
	private final String status;
	
	private PtlApiError(String status) {
		this.status = status;
	}


	@Override
	public String toString() {
		return status;
	}

}
