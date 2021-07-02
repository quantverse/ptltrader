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
import com.pairtradinglab.ptltrader.trading.PairTradingCoreFactory;

public class PairStrategyFactoryImpl implements PairStrategyFactory {
	private final EventBus bus;
	private final PairTradingCoreFactory coreFactory;
	
	

	public PairStrategyFactoryImpl(EventBus bus,
			PairTradingCoreFactory coreFactory) {
		super();
		this.bus = bus;
		this.coreFactory = coreFactory;
	}



	@Override
	public PairStrategy createForPortfolio(JsonNode n, Portfolio p) {
		PairStrategy s = createForPortfolio(n.get("uid").asText(), p, n.get("ticker1").asText(), n.get("ticker2").asText(), n.get("trade_as_1").asInt(), n.get("trade_as_2").asInt());
		s.updateFromJson(n);
		return s;
	}



	@Override
	public PairStrategy createForPortfolio(String uid, Portfolio p,
			String stock1, String stock2, int tradeAs1, int tradeAs2) {
		return new PairStrategy(uid, p, stock1, stock2, tradeAs1, tradeAs2, bus, coreFactory);
	}



	@Override
	public PairStrategy createForPortfolio(Portfolio p, String stock1, String stock2, int tradeAs1, int tradeAs2) {
		return new PairStrategy(UUID.randomUUID().toString(), p, stock1, stock2, tradeAs1, tradeAs2, bus, coreFactory);
	}

	

}
