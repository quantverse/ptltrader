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

import net.jcip.annotations.*;

@NotThreadSafe
public class PairTradingModelResidual extends PairTradingModel {
	
	public PairTradingModelResidual(MarketRates mr1, MarketRates mr2, Logger logger) {
		super(mr1, mr2, logger);
		
	}


	// settings
	private double maxEntryScore=6;
	private double entryThreshold=1.5;
	private double exitThreshold=0;
	private double downtickThreshold = 0;
	private int linRegPeriod=30;
	private int entryMode=ENTRY_MODE_SIMPLE;
	
	// internal state
	private OlsResult olsr;
	private OlsResult olsr1;
	private double lastExitZscore = 0;
	
	@Override
	public double getZScore(int mode) {
		if (!pricesInitialized) throw new IllegalArgumentException("Prices not initialized");
		if (olsr.stdDev<0.00000001 || olsr1.stdDev<0.00000001) return 0;
		
		MultiSpread ms = new MultiSpread(olsr.A, olsr.B, mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());
		if (mode== ZSCORE_BID) {
			return ms.getMinSpread()/olsr.stdDev;
		} else if (mode== ZSCORE_ASK) {
			return ms.getMaxSpread()/olsr.stdDev;
		} else {
			// auto
			double zscoremin=ms.getMinSpread()/olsr.stdDev;
			double zscoremax=ms.getMaxSpread()/olsr.stdDev;
			
			if (zscoremin>0 && zscoremax>0) {
				return zscoremin;
			} else if (zscoremin<0 && zscoremax<0) {
				return zscoremax;
			} else {
				// return mean
				return (zscoremin+zscoremax)/2;
			}
		}
		
	}

	@Override
	public int entryLogic() {
		if (!pricesInitialized) throw new IllegalArgumentException("Prices not initialized");
		if (olsr.stdDev<0.00000001 || olsr1.stdDev<0.00000001) return SIGNAL_NONE;
		
		MultiSpread ms = new MultiSpread(olsr.A, olsr.B, mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());
		
		double zscoremin=ms.getMinSpread()/olsr.stdDev;
		double zscoremax=ms.getMaxSpread()/olsr.stdDev;
		
		double lastZscore = olsr1.lastSpread / olsr1.stdDev;
		
		// check long signal
		switch(entryMode) {
			case ENTRY_MODE_SIMPLE:
				if (zscoremax<=-entryThreshold && zscoremax>=-maxEntryScore) {
					lastZscoreInvolved=zscoremax;
					return SIGNAL_LONG;
				}
				break;
			case ENTRY_MODE_UPTICK:
				if (zscoremax<=-entryThreshold && lastZscore>-entryThreshold && zscoremax>=-maxEntryScore) {
					lastZscoreInvolved=zscoremax;
					return SIGNAL_LONG;
				}
				break;	
			case ENTRY_MODE_DOWNTICK:
				if (zscoremax>-entryThreshold && lastZscore<=-entryThreshold && zscoremax<-downtickThreshold) {
					lastZscoreInvolved=zscoremax;
					return SIGNAL_LONG;
				}
				break;	
		}
		
		
		// check short signal
		switch(entryMode) {
			case ENTRY_MODE_SIMPLE:
				if (zscoremin>=entryThreshold && zscoremin<=maxEntryScore) {
					lastZscoreInvolved=zscoremin;
					return SIGNAL_SHORT;
				}
				break;
			case ENTRY_MODE_UPTICK:
				if (zscoremin>=entryThreshold && lastZscore<entryThreshold && zscoremin<=maxEntryScore) {
					lastZscoreInvolved=zscoremin;
					return SIGNAL_SHORT;
				}
				break;	
			case ENTRY_MODE_DOWNTICK:
				if (zscoremin<entryThreshold && lastZscore>=entryThreshold && zscoremin>downtickThreshold) {
					lastZscoreInvolved=zscoremin;
					return SIGNAL_SHORT;
				}
				break;	
		}
		
		return SIGNAL_NONE;
	}

	@Override
	public boolean exitLogic(int currentPosition) {
		if (!pricesInitialized) throw new IllegalArgumentException("Prices not initialized");
		if (olsr.stdDev<0.00000001) return false;
		
		MultiSpread ms = new MultiSpread(olsr.A, olsr.B, mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());
		
		double zscoremin=ms.getMinSpread()/olsr.stdDev;
		double zscoremax=ms.getMaxSpread()/olsr.stdDev;
		
		//System.out.println(String.format("zscmin %f zscmax %f", zscoremin, zscoremax));
		
		if (currentPosition==SIGNAL_LONG) {
			if (zscoremin>=-exitThreshold) {
				lastZscoreInvolved=zscoremin;
				return true;
			}
		} else if (currentPosition==SIGNAL_SHORT) {
			if (zscoremax<=exitThreshold) {
				lastZscoreInvolved=zscoremax;
				return true;
			}
		}
			
		
		return false;
	}

	@Override
	public int getLookbackRequired() {
		return linRegPeriod+1;
	}

	@Override
	public String getStatusInfo() {
		if (!pricesInitialized) return "";
		
		double lastZScore;
		if (olsr1.stdDev<0.00000001) lastZScore = 0;
		else lastZScore = olsr1.lastSpread / olsr1.stdDev;
		
		return String.format("Beta=%.4f Alpha=%.4f StdDev=%.4f LastZScore=%.4f", olsr.A, olsr.B, olsr.stdDev, lastZScore);
	}

	@Override
	public void setupFromStrategy(PairStrategy ps) {
		if (pricesInitialized) throw new IllegalArgumentException("Prices are already initialized");
		entryThreshold = ps.getEntryThreshold();
		exitThreshold = ps.getExitThreshold();
		maxEntryScore = ps.getMaxEntryScore();
		linRegPeriod = ps.getResidualLinRegPeriod();
		timezone = ps.getTimezone();
		entryMode = ps.getEntryMode();
		downtickThreshold = ps.getDowntickThreshold();

	}

	public double getMaxEntryScore() {
		return maxEntryScore;
	}

	public void setMaxEntryScore(double maxEntryScore) {
		this.maxEntryScore = maxEntryScore;
	}

	public double getEntryThreshold() {
		return entryThreshold;
	}

	public void setEntryThreshold(double entryThreshold) {
		this.entryThreshold = entryThreshold;
	}

	public double getExitThreshold() {
		return exitThreshold;
	}

	public void setExitThreshold(double exitThreshold) {
		this.exitThreshold = exitThreshold;
	}

	public int getLinRegPeriod() {
		return linRegPeriod;
	}

	public void setLinRegPeriod(int linRegPeriod) {
		this.linRegPeriod = linRegPeriod;
	}
	
	public int getEntryMode() {
		return entryMode;
	}

	public void setEntryMode(int entryMode) {
		this.entryMode = entryMode;
	}
	
	
	@Override
	public void setPrices(double[] prices1, double[] prices2) {
		super.setPrices(prices1, prices2);
		pricesInitialized=false;
		int lb = getLookbackRequired();
		if (prices1.length<lb) throw new IllegalArgumentException(String.format("Prices array must have at least %d items", lb));
		OlsCalculator olsc = new OlsCalculator();
		olsr = olsc.calculate(linRegPeriod, 0, prices1, prices2);
		olsr1 = olsc.calculate(linRegPeriod, 1, prices1, prices2);
		
		//System.out.println(String.format("A: %f B: %f stddev: %f", A, B, stdDev));
		pricesInitialized=true;
		
	}
	
	
	@Override
	public double getProfitPotential(double marginAvailable, double marginCoef1, double marginCoef2) {
		if (!pricesInitialized) return 0;
		if (mr1.getMarketStatus()!=MarketRates.MARKET_STATUS_OK) return 0;
        if (olsr.stdDev<0.00000001 || olsr.A<0.1) return 0;
		double zscoreAsk = getZScore(ZSCORE_ASK);
		double zscoreBid = getZScore(ZSCORE_BID);
		MultiSpread ms = new MultiSpread(olsr.A, olsr.B, mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());

		if (zscoreAsk<-exitThreshold) {
			// this is long position
			double spread = ms.getMinSpread();
			double targetSpread = -exitThreshold*olsr.stdDev;
			double delta=targetSpread - spread;

			double target1 = delta + mr1.getBid();
			double medtarget1 = 0.5*(target1 + mr1.getBid());
			double spread2 = medtarget1-(olsr.A*mr2.getAsk()+olsr.B);
			double delta2 = targetSpread - spread2;

			// compute the medtarget2 using medtarget1
			double medtarget2 = (medtarget1-olsr.B-delta2)/olsr.A;
			QtyQty qty = calcLegQtys(marginAvailable, marginCoef1, marginCoef2, mr1.getAsk(), mr2.getBid());
			return (medtarget1-mr1.getAsk())*(double) qty.qty1+(mr2.getBid()-medtarget2)*(double) qty.qty2;


		} else if (zscoreBid>exitThreshold) {
			// this is short position
			double spread = ms.getMaxSpread();
			double targetSpread = exitThreshold*olsr.stdDev;
			double delta=targetSpread - spread;

			double target1 = delta + mr1.getAsk();
			double medtarget1 = 0.5*(target1 + mr1.getAsk());
			double spread2 = medtarget1-(olsr.A*mr2.getBid()+olsr.B);
			double delta2 = targetSpread - spread2;

			// compute the medtarget2 using medtarget1
			double medtarget2 = (medtarget1-olsr.B-delta2)/olsr.A;
			QtyQty qty = calcLegQtys(marginAvailable, marginCoef1, marginCoef2, mr1.getBid(), mr2.getAsk());
			return (mr1.getBid()-medtarget1)*(double) qty.qty1+(medtarget2-mr2.getAsk())*(double) qty.qty2;

		} else return 0; // nothing to do
	}
	
	@Override
	boolean checkReversalCondition() {
		double zscore = getZScore(ZSCORE_AUTO);
		return Math.abs(lastExitZscore)>=entryThreshold && Math.abs(zscore)>=entryThreshold && zscore*lastExitZscore>=0;
	}


	@Override
	void storeReversalState() {
		lastExitZscore = getZScore(ZSCORE_AUTO);
	}
	
	

}
