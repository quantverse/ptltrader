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

import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import com.google.common.eventbus.EventBus;
import com.pairtradinglab.ptltrader.ActiveCores;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.PairStrategy;

public class PairTradingCoreFactoryImpl implements PairTradingCoreFactory {
	// dependencies to be injected
	private final Map<String, SimpleWrapper> ibWrapperMap;
	private final LoggerFactory loggerFactory;
	private final PairDataProviderFactory pairDataProviderFactory;
	private final MarketDataProvider marketDataProvider;
	private final EventBus bus;
	private final Set<String> connectedAccounts;
	private final ActiveCores activeCores;
	private final ActivityDetector activityDetector;
	

	public PairTradingCoreFactoryImpl(Map<String, SimpleWrapper> ibWrapperMap,
			LoggerFactory loggerFactory,
			PairDataProviderFactory pairDataProviderFactory,
			MarketDataProvider marketDataProvider, EventBus bus, Set<String> connectedAccounts, ActiveCores activeCores, ActivityDetector activityDetector) {
		super();
		this.ibWrapperMap = ibWrapperMap;
		this.loggerFactory = loggerFactory;
		this.pairDataProviderFactory = pairDataProviderFactory;
		this.marketDataProvider = marketDataProvider;
		this.bus = bus;
		this.connectedAccounts = connectedAccounts;
		this.activeCores = activeCores;
		this.activityDetector = activityDetector;
	}



	@Override
	public PairTradingCore createForStrategy(PairStrategy ps) {
		// setup pair trading model for the strategy
		PairTradingModel ptmodel;
		
		ContractExt c1 = ContractExt.createFromGoogleSymbol(ps.getStock1(), false); // CFD logic not needed here
		ContractExt c2 = ContractExt.createFromGoogleSymbol(ps.getStock2(), false); // CFD logic not needed here
		
		Logger l = loggerFactory.createLogger(c1.m_symbol.replace(" ", ".")+"_"+c2.m_symbol.replace(" ", "."));
		if (PairStrategy.MODEL_RESIDUAL.equals(ps.getModel())) {
			ptmodel = new PairTradingModelResidual(new MarketRates(), new MarketRates(), l);
		} else if (PairStrategy.MODEL_RATIO.equals(ps.getModel())) {
			ptmodel = new PairTradingModelRatio(new MarketRates(), new MarketRates(), l);
		} else if (PairStrategy.MODEL_KALMAN_GRID.equals(ps.getModel())) {
			ptmodel = new PairTradingModelKalmanGrid(new MarketRates(), new MarketRates(), l);
		} else if (PairStrategy.MODEL_KALMAN_AUTO.equals(ps.getModel())) {
			ptmodel = new PairTradingModelKalmanAuto(new MarketRates(), new MarketRates(), l);
		} else {
			// unknown/unsupported model
			ptmodel = new PairTradingModelDummy(new MarketRates(), new MarketRates(), l);
		}
		ptmodel.initialize();
		ptmodel.setupFromStrategy(ps);
		return createForStrategy(ps, ptmodel);
	}



	@Override
	public PairTradingCore createForStrategy(PairStrategy ps,
			PairTradingModel ptmodel) {
		// find appropriate IB WRAPPER to use
		return new PairTradingCore(ptmodel, ps, ibWrapperMap, loggerFactory, marketDataProvider, bus, pairDataProviderFactory, connectedAccounts, activeCores, activityDetector);
	}

}
