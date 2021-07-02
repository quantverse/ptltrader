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

import org.apache.log4j.Logger;

import com.pairtradinglab.ptltrader.model.PairStrategy;

/**
 * This is just a dummy model class with no logic implemented.
 * It is used when strategy uses model which is not supported in PTL Trader yet.
 * @author carloss
 *
 */
public class PairTradingModelDummy extends PairTradingModel {

	public PairTradingModelDummy(MarketRates mr1, MarketRates mr2, Logger logger) {
		super(mr1, mr2, logger);
		
	}

	@Override
	double getZScore(int mode) {
		return 0;
	}

	@Override
	int entryLogic() {
		return 0;
	}

	@Override
	boolean exitLogic(int currentPosition) {
		return false;
	}

	@Override
	int getLookbackRequired() {
		return 0;
	}

	@Override
	String getStatusInfo() {
		return "";
	}

	@Override
	public void setupFromStrategy(PairStrategy ps) {

	}

	@Override
	public double getProfitPotential(double marginAvailable, double marginCoef1, double marginCoef2) {
		return 0;
	}

	@Override
	boolean checkReversalCondition() {
		return false;
	}


	@Override
	void storeReversalState() {
		
	}

}
