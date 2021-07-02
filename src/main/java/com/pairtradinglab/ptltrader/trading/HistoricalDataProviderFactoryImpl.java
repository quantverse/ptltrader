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

import java.util.List;

import org.apache.log4j.Logger;

import com.ib.client.Contract;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.Settings;

public class HistoricalDataProviderFactoryImpl implements
		HistoricalDataProviderFactory {
	
	private final List<SimpleWrapper> ibWrapperList;
	private final Settings settings;
	private final Logger l;
	
	

	public HistoricalDataProviderFactoryImpl(List<SimpleWrapper> ibWrapperList,
			Settings settings, Logger l) {
		super();
		this.ibWrapperList = ibWrapperList;
		this.settings = settings;
		this.l = l;
	}



	@Override
	public HistoricalDataProvider createForContract(Contract c) {
		return new HistoricalDataProvider(ibWrapperList, settings, c, l);
	}

}
