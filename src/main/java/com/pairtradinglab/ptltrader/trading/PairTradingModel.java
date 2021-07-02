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
import org.joda.time.*;

import net.jcip.annotations.*;

import com.pairtradinglab.ptltrader.model.PairStrategy;

/**
 * Abstract pair trading model.
 * @author carloss
 *
 * Remark: part of the class is not synchronized (setBidX, setAskX, entry/exit logic), requires single thread access or external sync!
 */
@NotThreadSafe
public abstract class PairTradingModel {
	public static final int ENTRY_MODE_SIMPLE=0;
	public static final int ENTRY_MODE_UPTICK=1;
	public static final int ENTRY_MODE_DOWNTICK=2;
	
	public static final int SIGNAL_NONE=0;
	public static final int SIGNAL_LONG=1;
	public static final int SIGNAL_SHORT=-1;
	
	public static final double MIN_PRICE=0.01;
	
	public static final int ZSCORE_AUTO = 0;
	public static final int ZSCORE_BID = -1;
	public static final int ZSCORE_ASK = 1;
	
	protected DateTimeZone timezone = DateTimeZone.getDefault();
	
	protected boolean pricesInitialized=false;
	
	protected double lastZscoreInvolved;
	
	protected final MarketRates mr1;
	protected final MarketRates mr2;
	protected final Logger logger;
	
	
	// abstract methods to override
	abstract double getZScore(int mode); //must never fail!
	abstract int entryLogic(); // may fail with IllegalStateException
	abstract boolean exitLogic(int currentPosition); // may fail with IllegalStateException
	abstract int getLookbackRequired(); // may fail with IllegalStateException
	abstract String getStatusInfo(); // must never fail
	public abstract void setupFromStrategy(PairStrategy ps);
	public abstract double getProfitPotential(double marginAvailable, double marginCoef1, double marginCoef2); // must never fail
	
	abstract boolean checkReversalCondition(); // true = yes this seems like reversal
	abstract void storeReversalState();
	
	
	
	public PairTradingModel(MarketRates mr1, MarketRates mr2, Logger logger) {
		super();
		this.mr1 = mr1;
		this.mr2 = mr2;
		this.logger = logger;
	}

	/**
	 * Initializes the model by prices (to be called when fresh data is available)
	 * @param prices1 price series of instrument 1
	 * @param prices2 price series of instrument 2
	 * @throws IllegalArgumentException if something is wrong with prices
	 * @throws IllegalStateException if called in wrong sequence
	 */
	public void setPrices(double[] prices1, double[] prices2) { // may fail with IllegalStateException / IllegalArgumentException
		if (prices1.length!=prices2.length) throw new IllegalArgumentException("Price arrays must have same length");
		if (prices1.length==0) throw new IllegalArgumentException("Empty price arrays not accepted");
		pricesInitialized=true;
	}
	
	public void initialize() {
		
	}

    /**
     * Default implementation to allocate margin to stocks
     * @param marginAvailable Margin to allocate
     * @param marginCoef1 Margin coefficient of equity 1
     * @param marginCoef2 Margin coefficient of equity 2
     * @param price1 Equity price 1
     * @param price2 Equity price 2
     * @return Qtys to allocate
     */
	QtyQty calcLegQtys(double marginAvailable, double marginCoef1, double marginCoef2, double price1, double price2) {
        double out1 = marginAvailable / (marginCoef2 * price1 + marginCoef1 * price1);
        double out2 = out1 * price1 / price2;
        return new QtyQty((int) Math.floor(out1), (int) Math.floor(out2));
    }
	
	
	public double getLastZscoreInvolved() {
		return lastZscoreInvolved;
	}
	
	
	public boolean isPricesInitialized() {
		return pricesInitialized;
	}
	public MarketRates getMr1() {
		return mr1;
	}
	
	public MarketRates getMr2() {
		return mr2;
	}
	
	
}
