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
package com.pairtradinglab.ptltrader.model;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.eventbus.EventBus;
import com.pairtradinglab.ptltrader.PtlApiClient;
import com.pairtradinglab.ptltrader.LoggerFactory;

public class PortfolioFactoryImpl implements PortfolioFactory {
	// dependencies to be injected
	private final EventBus bus;
	private final PairStrategyFactory strategyFactory;
	private final LoggerFactory loggerFactory;
	

	public PortfolioFactoryImpl(EventBus bus,
			PairStrategyFactory strategyFactory, LoggerFactory loggerFactory) {
		super();
		this.bus = bus;
		this.strategyFactory = strategyFactory;
		this.loggerFactory = loggerFactory;
		
	}

	@Override
	public Portfolio create(String uid) {
		return new Portfolio(bus, strategyFactory, loggerFactory, uid);
	}

	@Override
	public Portfolio create() {
		return create(UUID.randomUUID().toString());
	}

	@Override
	public Portfolio create(JsonNode r) {
		Portfolio newp = create(r.get("uid").asText()); 
		newp.updateFromJson(r);
		return newp;
	}

}
