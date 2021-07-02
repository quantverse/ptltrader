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

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.common.base.Joiner;
import com.google.common.primitives.Doubles;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.tictactec.ta.lib.*;

import net.jcip.annotations.*;

@NotThreadSafe
public class PairTradingModelRatio extends PairTradingModel {
	
	public PairTradingModelRatio(MarketRates mr1, MarketRates mr2, Logger logger) {
		super(mr1, mr2, logger);
		
	}

	private static final int unstablePeriod=34;
	private static final int unstablePeriodRsi=24;
	
	// settings
	private double maxEntryScore=4;
	private double entryThreshold=2;
	private double exitThreshold=0;
	private double downtickThreshold=0;
	private int maPeriod=15;
	private MAType maType=MAType.Ema;
	private int stdDevPeriod=15;
	private int entryMode=ENTRY_MODE_SIMPLE;
	private int rsiPeriod=10;
	private double rsiThreshold=0;
	
	private double stddev;
	private double curma;
	private double lastma;
	private double laststddev;
	
	private double[] ratios;
	
	private final Core talib = new Core();
	
	private double lastExitZscore = 0;
	private double lastExitRsi = 0;
	

	@Override
	public double getZScore(int mode) {
		if (!pricesInitialized) throw new IllegalArgumentException("Prices not initialized");
		
		MultiRatio mr = new MultiRatio(mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());
		
		double minratio=mr.getMinRatio();
		double maxratio=mr.getMaxRatio();
		
		if (mode== ZSCORE_BID) {
			return (minratio-curma)/stddev;
		} else if (mode== ZSCORE_ASK) {
			return (maxratio-curma)/stddev;
		} else {
			// auto
			double zscoremin=(minratio-curma)/stddev;
			double zscoremax=(maxratio-curma)/stddev;
			
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
		
		MultiRatio mr = new MultiRatio(mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());
		
		double minratio=mr.getMinRatio();
		double maxratio=mr.getMaxRatio();
		
		double zscoremin=(minratio-curma)/stddev;
		double zscoremax=(maxratio-curma)/stddev;
		double lastZscore=(ratios[ratios.length-1]-lastma)/laststddev;
		
		//System.out.println(String.format("minr %f maxr %f zscmin %f zscmax %f lastscore %f curma %f", minratio, maxratio, zscoremin, zscoremax, lastZscore, curma));
		int out = SIGNAL_NONE;
		
		// check long signal
		switch(entryMode) {
		case ENTRY_MODE_SIMPLE:
			if (zscoremax<=-entryThreshold && zscoremax>=-maxEntryScore) {
				lastZscoreInvolved=zscoremax;
				out = SIGNAL_LONG;
			}
			break;
		case ENTRY_MODE_UPTICK:
			if (zscoremax<=-entryThreshold && lastZscore>-entryThreshold && zscoremax>=-maxEntryScore) {
				lastZscoreInvolved=zscoremax;
				out = SIGNAL_LONG;
			}
			break;	
		case ENTRY_MODE_DOWNTICK:
			if (zscoremax>-entryThreshold && lastZscore<=-entryThreshold && zscoremax<-downtickThreshold) {
				lastZscoreInvolved=zscoremax;
				out = SIGNAL_LONG;
			}
			break;	
		}
		
		
		// check short signal
		if (out == SIGNAL_NONE) {
			switch(entryMode) {
			case ENTRY_MODE_SIMPLE:
				if (zscoremin>=entryThreshold && zscoremin<=maxEntryScore) {
					lastZscoreInvolved=zscoremin;
					out = SIGNAL_SHORT;
				}
				break;
			case ENTRY_MODE_UPTICK:
				if (zscoremin>=entryThreshold && lastZscore<entryThreshold && zscoremin<=maxEntryScore) {
					lastZscoreInvolved=zscoremin;
					out = SIGNAL_SHORT;
				}
				break;	
			case ENTRY_MODE_DOWNTICK:
				if (zscoremin<entryThreshold && lastZscore>=entryThreshold && zscoremin>downtickThreshold) {
					lastZscoreInvolved=zscoremin;
					out = SIGNAL_SHORT;
				}
				break;	
			}
		}
		
		if (rsiThreshold>0.001 && out != SIGNAL_NONE) {
			// additional RSI filtering
			double rsi1 = calcRsi(minratio);
			double rsi2 = calcRsi(maxratio);
			
			//System.out.println(String.format("sig %d minr %f maxr %f rsi1 %f rsi2 %f thres %f", out, minratio, maxratio, rsi1, rsi2, rsiThreshold));
			if (out == SIGNAL_LONG) {
				// RSI must be below long threshold, otherwise we cancel the signal
				double th = 50 - rsiThreshold;
				if (rsi1>th || rsi2>th) out = SIGNAL_NONE;
			} else if (out == SIGNAL_SHORT) {
				// RSI must be above short threshold, otherwise we cancel the signal
				double th = 50 + rsiThreshold;
				if (rsi1<th || rsi2<th) out = SIGNAL_NONE;
				
			}
		}
		
		return out;
	}
	
	
	
	@Override
	public void initialize() {
		super.initialize();
		if (talib.SetUnstablePeriod(FuncUnstId.Ema, unstablePeriod) != RetCode.Success) throw new RuntimeException("Ta-lib unstable period initialization failed");
		if (talib.SetUnstablePeriod(FuncUnstId.Kama, unstablePeriod) != RetCode.Success) throw new RuntimeException("Ta-lib unstable period initialization failed");
		if (talib.SetUnstablePeriod(FuncUnstId.Mama, unstablePeriod) != RetCode.Success) throw new RuntimeException("Ta-lib unstable period initialization failed");
		if (talib.SetUnstablePeriod(FuncUnstId.Rsi, unstablePeriodRsi) != RetCode.Success) throw new RuntimeException("Ta-lib unstable period initialization failed");
		if (talib.SetUnstablePeriod(FuncUnstId.T3, unstablePeriod) != RetCode.Success) throw new RuntimeException("Ta-lib unstable period initialization failed");
		
	}

	private double calcRsi(double trailingRatio) {
		int rsiLookback = talib.rsiLookback(rsiPeriod);
		double buffer[] = new double[rsiLookback+1];
		int k=0;
		for (int i = ratios.length-rsiLookback; i<ratios.length; i++) {
			buffer[k++] = ratios[i];
		}
		buffer[k] = trailingRatio;
		double rsi[] = new double[1];
		MInteger begidx = new MInteger();
		MInteger nbelem = new MInteger();
		
		RetCode res = talib.rsi(buffer.length-1, buffer.length-1, buffer, rsiPeriod, begidx, nbelem, rsi);
		if (res!=RetCode.Success) throw new RuntimeException("TA-Lib call failed for stdDev");
		
		return rsi[0];
		
	}
	
	public double getRsi() {
		if (!pricesInitialized) throw new IllegalArgumentException("Prices not initialized");
		
		MultiRatio mr = new MultiRatio(mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());
		
		double minRsi = calcRsi(mr.getMinRatio());
		double maxRsi = calcRsi(mr.getMaxRatio());
		
		if (minRsi>50 && maxRsi>50) {
			return minRsi;
			
		} else if (minRsi<50 && maxRsi<50) {
			return maxRsi;
			
		} else {
			return (minRsi+maxRsi)/2;
		}
		
	}

	@Override
	public void setPrices(double[] prices1, double[] prices2) {
		super.setPrices(prices1, prices2);
		pricesInitialized=false;
		// calculate ratio series
		ratios=new double[prices1.length];
		for(int i=0;i<prices1.length;i++) {
			if (prices2[i]!=0) ratios[i]=prices1[i]/prices2[i];
			else ratios[i]=0;
		}
		//System.out.println("ratios:");
		//for(int i=0;i<ratios.length;i++) System.out.print(ratios[i]+" "); System.out.println("");
		
		// calculate moving average
		
		int totalLookback = getLookbackRequired();
		if (prices1.length<totalLookback) throw new IllegalArgumentException(String.format("Prices array must have at least %d items", totalLookback));
		
		double maout[] = new double[2];
		//System.out.println(String.format("maout len=%d lookback1=%d lookback2=%d totallookback=%d ratiolen=%d", maout.length, lookback1, lookback2, totalLookback, ratios.length));
		
		MInteger begidx = new MInteger();
		MInteger nbelem = new MInteger();
		logger.debug(String.format("calculating moving average: period %d type %d input len %d", maPeriod, maType.ordinal(), ratios.length));
		int ls1 = (ratios.length>49) ? 49 : ratios.length;
		List<Double> l1 = new ArrayList<Double>(Doubles.asList(ratios)).subList(ratios.length-ls1, ratios.length);
		logger.debug(String.format("last %d ratios: %s", ls1, Joiner.on(" ").join(l1)));
				
		RetCode res = talib.movingAverage(ratios.length-2, ratios.length-1, ratios, maPeriod, maType, begidx, nbelem, maout);
		if (res!=RetCode.Success) throw new RuntimeException("TA-Lib call failed for movingAverage");
		//logger.debug(String.format("ma-0: %f, ma-1: %f, ma-begidx: %d, ma-nbelem: %d", maout[1], maout[0], begidx.value, nbelem.value));
				
		// calculate standard deviation
		double stddevout[] = new double[2];
		res = talib.stdDev(ratios.length-2, ratios.length-1, ratios, stdDevPeriod, 1, begidx, nbelem, stddevout);
		if (res!=RetCode.Success) throw new RuntimeException("TA-Lib call failed for stdDev");
		
		stddev=stddevout[1];
		laststddev=stddevout[0];
		curma=maout[1];
		lastma=maout[0];
		//System.out.println(String.format("stddev: %f curma: %f", stddev, curma));
		pricesInitialized=true;
		
	}

	@Override
	public int getLookbackRequired() {
		int lookback = talib.movingAverageLookback(maPeriod, maType)+1;
		//System.out.println(String.format("ma lookback: %d", lookback));
		int tmp = talib.stdDevLookback(stdDevPeriod, 1)+1;
		if (tmp>lookback) lookback=tmp;
		tmp = talib.rsiLookback(rsiPeriod);
		//System.out.println(String.format("rsi lookback: %d", tmp));
		if (tmp>lookback) lookback=tmp;
		return lookback;
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

	public int getMaPeriod() {
		return maPeriod;
	}

	public void setMaPeriod(int maPeriod) {
		this.maPeriod = maPeriod;
	}

	public MAType getMaType() {
		return maType;
	}

	public void setMaType(MAType maType) {
		this.maType = maType;
	}

	public int getStdDevPeriod() {
		return stdDevPeriod;
	}

	public void setStdDevPeriod(int stdDevPeriod) {
		this.stdDevPeriod = stdDevPeriod;
	}

	public int getEntryMode() {
		return entryMode;
	}

	public void setEntryMode(int entryMode) {
		this.entryMode = entryMode;
	}
	
	public int getRsiPeriod() {
		return rsiPeriod;
	}

	public void setRsiPeriod(int rsiPeriod) {
		this.rsiPeriod = rsiPeriod;
	}

	public double getRsiThreshold() {
		return rsiThreshold;
	}

	public void setRsiThreshold(double rsiThreshold) {
		this.rsiThreshold = rsiThreshold;
	}

	@Override
	public boolean exitLogic(int currentPosition) {
		if (!pricesInitialized) throw new IllegalArgumentException("Prices not initialized");
		MultiRatio mr = new MultiRatio(mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());
		
		double minratio=mr.getMinRatio();
		double maxratio=mr.getMaxRatio();
		
		double zscoremin=(minratio-curma)/stddev;
		double zscoremax=(maxratio-curma)/stddev;
		
		//System.out.println(String.format("minr %f maxr %f zscmin %f zscmax %f curma %f", minratio, maxratio, zscoremin, zscoremax, curma));
		
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
	public String getStatusInfo() {
		if (!pricesInitialized) return "";
		
		// calculate ratio average using the same period as we have set
		double sum = 0;
		double avg = 0;
		if (ratios.length>=maPeriod) {
			for(int i=0; i<maPeriod; i++) {
				sum += ratios[ratios.length-maPeriod+i]; 
			}
			avg = sum / (double) maPeriod;
		}
		return String.format("LastRatio=%.4f CurMA=%.4f StdDev=%.4f Avg=%.4f", (ratios.length>0)?ratios[ratios.length-1]:0, curma, stddev, avg);
	}

	@Override
	public void setupFromStrategy(PairStrategy ps) {
		if (pricesInitialized) throw new IllegalArgumentException("Prices are already initialized");
		maPeriod = ps.getRatioMaPeriod();
		maType = ps.getRatioMaType();
		stdDevPeriod = ps.getRatioStdDevPeriod();
		entryThreshold = ps.getEntryThreshold();
		exitThreshold = ps.getExitThreshold();
		maxEntryScore = ps.getMaxEntryScore();
		entryMode = ps.getEntryMode();
		timezone = ps.getTimezone();
		rsiPeriod = ps.getRatioRsiPeriod();
		rsiThreshold = ps.getRatioRsiThreshold();
		downtickThreshold = ps.getDowntickThreshold();
		
	}
	
	@Override
	public double getProfitPotential(double marginAvailable, double marginCoef1, double marginCoef2) {
		if (!pricesInitialized) return 0;
		if (mr1.getMarketStatus()!=MarketRates.MARKET_STATUS_OK) return 0;
		double zscoreAsk = getZScore(ZSCORE_ASK);
		double zscoreBid = getZScore(ZSCORE_BID);
		MultiRatio mr = new MultiRatio(mr1.getBid(), mr1.getAsk(), mr2.getBid(), mr2.getAsk());

		if (zscoreAsk<-exitThreshold) {
			// this is long position
			double spread = mr.getMinRatio() - curma; // we use BID here
			double targetSpread = -exitThreshold*stddev;
			double delta=targetSpread - spread;

			double target1 = delta*mr2.getAsk() + mr1.getBid();
			double target2 = 1/(delta/mr1.getAsk() + 1/mr2.getBid());

			//System.out.println(target1+" "+target2);

			double medtarget1 = (target1 + mr1.getBid())/2;
			double medtarget2 = (target2 + mr2.getAsk())/2;
			QtyQty qty = calcLegQtys(marginAvailable, marginCoef1, marginCoef2, mr1.getAsk(), mr2.getBid());
			return (medtarget1-mr1.getAsk())*(double) qty.qty1+(mr2.getBid()-medtarget2)*(double) qty.qty2;

		} else if (zscoreBid>exitThreshold) {
			// this is short position
			double spread = mr.getMaxRatio() - curma; // we use ASK here
			double targetSpread = exitThreshold*stddev;
			double delta=targetSpread - spread;

			double target1 = delta*mr2.getBid() + mr1.getAsk();
			double target2 = 1/(delta/mr1.getBid() + 1/mr2.getAsk());

			//System.out.println(target1+" "+target2);

			double medtarget1 = (target1 + mr1.getAsk())/2;
			double medtarget2 = (target2 + mr2.getBid())/2;
			QtyQty qty = calcLegQtys(marginAvailable, marginCoef1, marginCoef2, mr1.getBid(), mr2.getAsk());
			return (mr1.getBid()-medtarget1)*(double) qty.qty1+(medtarget2-mr2.getAsk())*(double) qty.qty2;

		} else return 0; // nothing to do

	}


	@Override
	boolean checkReversalCondition() {
		double zscore = getZScore(ZSCORE_AUTO);
		if (rsiThreshold>0.001) {
			double rsi = getRsi()-50;
			return Math.abs(lastExitZscore)>=entryThreshold && Math.abs(zscore)>=entryThreshold && zscore*lastExitZscore>=0 && Math.abs(lastExitRsi)>=rsiThreshold && Math.abs(rsi)>=rsiThreshold && rsi*lastExitRsi>=0;
		} else {
			return Math.abs(lastExitZscore)>=entryThreshold && Math.abs(zscore)>=entryThreshold && zscore*lastExitZscore>=0;
		}
		
	}


	@Override
	void storeReversalState() {
		lastExitZscore = getZScore(ZSCORE_AUTO);
		if (rsiThreshold>0.001) lastExitRsi = getRsi()-50;
	}
	
	

}
