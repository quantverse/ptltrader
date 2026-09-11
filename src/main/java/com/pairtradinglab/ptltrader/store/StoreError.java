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
package com.pairtradinglab.ptltrader.store;

/**
 * Local persistence failures, replacing the former PtlApiError.
 */
public enum StoreError {
	BIND_DENIED("There is another portfolio already bound to this account."),
	IO_FAILURE("Unable to read or write the local database."),
	INVALID_IMPORT("The selected file is not a valid portfolio export."),
	INVALID_STORED_PORTFOLIO("A stored portfolio could not be loaded and was skipped. Check the log for details.");

	private final String message;

	private StoreError(String message) {
		this.message = message;
	}

	@Override
	public String toString() {
		return message;
	}
}
