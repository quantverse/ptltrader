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

import com.google.common.eventbus.EventBus;
import com.ib.client.Contract;

public class PairDataProviderFactoryImpl implements PairDataProviderFactory {
	private final EventBus bus;
	private final HistoricalDataProviderFactory hdpFactory;
	
	

	public PairDataProviderFactoryImpl(EventBus bus,
			HistoricalDataProviderFactory hdpFactory) {
		super();
		this.bus = bus;
		this.hdpFactory = hdpFactory;
	}



	@Override
	public PairDataProvider createForContracts(Contract c1, Contract c2) {
		return new PairDataProvider(bus, hdpFactory, c1, c2);
	}

}
