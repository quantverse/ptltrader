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

import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.trading.kernelfx.SubModelKalmanAuto;
import net.jcip.annotations.NotThreadSafe;
import org.apache.log4j.Logger;

@NotThreadSafe
public class PairTradingModelKalmanAuto extends PairTradingModel implements LockableStateModel {
    private static final int UNSTABLE_PERIOD = 120;
    // settings
    private int neutrality = PairStrategy.NEUTRALITY_DOLLAR;

    // state
    private PairTradingModelKalmanAutoState state;
    private TripleBandLogic tbl;
    SubModelKalmanAuto grid;
    double lastScore;
    private double lastExitScore;

    PairTradingModelKalmanAuto(MarketRates mr1, MarketRates mr2, Logger logger) {
        super(mr1, mr2, logger);
    }

    @Override
    double getZScore(int mode) { // should never fail
        if (grid == null || !pricesInitialized ||!grid.getIsValid() || grid.getSq()<0.00000001) return 0;
        if (mr1.getMarketStatus()!=MarketRates.MARKET_STATUS_OK) return 0;

        if (mode== ZSCORE_BID) {
            return grid.evaluate(mr1.getBid(), mr2.getAsk());
        } else if (mode== ZSCORE_ASK) {
            return grid.evaluate(mr1.getAsk(), mr2.getBid());
        } else {
            // auto
            double zscoremin = grid.evaluate(mr1.getBid(), mr2.getAsk());
            double zscoremax = grid.evaluate(mr1.getAsk(), mr2.getBid());

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
    int entryLogic() {
        if (grid == null) throw new IllegalStateException("Model not set up");
        if (!pricesInitialized) throw new IllegalStateException("Prices not initialized");
        if (!grid.getIsValid() || grid.getSq()<0.00000001 || tbl == null) return SIGNAL_NONE;
        EntrySignal sig = tbl.entryLogic(grid.evaluate(mr1.getAsk(), mr2.getBid()), grid.evaluate(mr1.getBid(), mr2.getAsk()), lastScore, lastScore);
        if (sig.signal != PairTradingModel.SIGNAL_NONE) lastZscoreInvolved = sig.zscore;
        return sig.signal;
    }

    @Override
    boolean exitLogic(int currentPosition) {
        if (grid == null) throw new IllegalStateException("Model not set up");
        if (!pricesInitialized) throw new IllegalStateException("Prices not initialized");
        if (!grid.getIsValid() || grid.getSq()<0.00000001 || tbl == null) return false;
        ExitSignal sig = tbl.exitLogic(currentPosition, grid.evaluate(mr1.getAsk(), mr2.getBid()), grid.evaluate(mr1.getBid(), mr2.getAsk()));
        if (sig.signal) lastZscoreInvolved = sig.zscore;
        return sig.signal;
    }

    @Override
    int getLookbackRequired() {
        if (grid == null) throw new IllegalStateException("Model not set up");
        return grid.getLookback() + UNSTABLE_PERIOD - 3;
    }

    @Override
    String getStatusInfo() { // should never fail
        if (!pricesInitialized || grid==null || !grid.getIsValid()) return "";
        return String.format("Beta=%.4f Intercept=%.4f StdDev=%.4f LogDelta=%.4f LastZScore=%.4f", grid.getBeta(), grid.getAlpha(), grid.getSq(), grid.getCurrentDelta(), lastScore);
    }

    @Override
    public void setupFromStrategy(PairStrategy ps) {
        tbl = new TripleBandLogic(1, 0, 0, ps.getMaxEntryScore(), ps.getEntryMode());
        setNeutrality(ps.getNeutrality());
        grid = new SubModelKalmanAuto();
        grid.setKalmanVe(ps.getKalmanAutoVe());
        grid.setUsageTarget(ps.getKalmanAutoUsageTarget());
        grid.setMaxDays(ps.isMaxDaysEnabled() ? ps.getMaxDays() : 0);
        grid.setAllowLong(ps.getAllowPositions() == PairStrategy.ALLOW_POSITIONS_BOTH || ps.getAllowPositions() == PairStrategy.ALLOW_POSITIONS_LONG);
        grid.setAllowShort(ps.getAllowPositions() == PairStrategy.ALLOW_POSITIONS_BOTH || ps.getAllowPositions() == PairStrategy.ALLOW_POSITIONS_SHORT);
        grid.init();
    }

    @Override
    public double getProfitPotential(double marginAvailable, double marginCoef1, double marginCoef2) {
        // should never fail
        if (!pricesInitialized || grid==null || !grid.getIsValid() || tbl == null) return 0;
        if (mr1.getMarketStatus()!=MarketRates.MARKET_STATUS_OK) return 0;
        if (grid.getSq()<0.00000001 || grid.getBeta()<0.05) return 0;
        double zscoreAsk = getZScore(ZSCORE_ASK);
        double zscoreBid = getZScore(ZSCORE_BID);

        if (zscoreAsk<-tbl.outThreshold) {
            // this is long position
            double spread = zscoreBid * grid.getSq();
            double targetSpread = -tbl.outThreshold*grid.getSq();
            double delta=targetSpread - spread;

            double target2 = delta + mr2.getBid();
            double medtarget2 = 0.5*(target2 + mr2.getBid());
            double spread1 = medtarget2-(grid.getBeta()*mr1.getAsk()+grid.getAlpha());
            double delta1 = targetSpread - spread1;

            // compute the medtarget1 using medtarget2
            double medtarget1 = (medtarget2-grid.getAlpha()-delta1)/grid.getBeta();
            //System.out.printf("spread %f targetSpred %f delta1 %f target2 %f medtarget1 %f spread1 %f delta2 %f medtarget2 %f\n", spread, targetSpread, delta, target2, medtarget1, spread1, delta1, medtarget2);
            QtyQty qty = calcLegQtys(marginAvailable, marginCoef1, marginCoef2, mr1.getAsk(), mr2.getBid());
            return (medtarget1-mr1.getAsk())*(double) qty.qty1+(mr2.getBid()-medtarget2)*(double) qty.qty2;


        } else if (zscoreBid>tbl.outThreshold) {
            // this is short position
            double spread = zscoreAsk * grid.getSq();
            double targetSpread = tbl.outThreshold*grid.getSq();
            double delta=targetSpread - spread;

            double target2 = delta + mr2.getAsk();
            double medtarget2 = 0.5*(target2 + mr2.getAsk());
            double spread1 = medtarget2-(grid.getBeta()*mr1.getBid()+grid.getAlpha());
            double delta1 = targetSpread - spread1;

            // compute the medtarget1 using medtarget2
            double medtarget1 = (medtarget2-grid.getAlpha()-delta1)/grid.getBeta();
            //System.out.printf("spread %f targetSpred %f delta1 %f target2 %f medtarget1 %f spread1 %f delta2 %f medtarget2 %f\n", spread, targetSpread, delta, target2, medtarget1, spread1, delta1, medtarget2);
            QtyQty qty = calcLegQtys(marginAvailable, marginCoef1, marginCoef2, mr1.getBid(), mr2.getAsk());
            return (mr1.getBid()-medtarget1)*(double) qty.qty1+(medtarget2-mr2.getAsk())*(double) qty.qty2;

        } else return 0; // nothing to do
    }

    @Override
    boolean checkReversalCondition() {
        return tbl!=null && tbl.checkReversal(getZScore(ZSCORE_AUTO), lastExitScore);
    }

    @Override
    void storeReversalState() {
        lastExitScore = getZScore(ZSCORE_AUTO);
    }

    @Override
    public void setPrices(double[] prices1, double[] prices2) {
        if (grid == null) throw new IllegalStateException("Model not set up");
        super.setPrices(prices1, prices2);
        pricesInitialized=false;
        lastScore = 0;
        grid.init();
        int lb = getLookbackRequired();
        if (prices1.length<lb) throw new IllegalArgumentException(String.format("Prices array must have at least %d items", lb));
        for(int i=0;i<prices1.length;i++) {
            if (i==prices1.length-1) lastScore = grid.evaluate(prices1[i], prices2[i]); // for last price
            //System.out.printf("score %f b0 %f b1 %f\n", lastScore, grid.getBeta(), grid.getAlpha());
            grid.update(prices1[i], prices2[i]);
        }
        // we need to re-lock the sub model if we have state!
        if (state != null) {
            logger.debug(String.format("re-locking model to %d", state.subModelId));
            grid.lock(state.subModelId);
        }
        pricesInitialized=true;
    }

    public int getNeutrality() {
        return neutrality;
    }

    public void setNeutrality(int neutrality) {
        this.neutrality = neutrality;
    }

    @Override
    QtyQty calcLegQtys(double marginAvailable, double marginCoef1, double marginCoef2, double price1, double price2) {
        if (neutrality == PairStrategy.NEUTRALITY_BETA && grid!=null && pricesInitialized && grid.getIsValid()) {
            double b = grid.getBeta();
            //sanitize beta for now
            if (b < 0.05) b = 0.05;
            else if (b > 10) b = 10;
            double o2 = marginAvailable / (marginCoef1 * price1 * b + marginCoef2 * price2);

            return new QtyQty((int) Math.floor(o2 * b), (int) Math.floor(o2));

        } else return super.calcLegQtys(marginAvailable, marginCoef1, marginCoef2, price1, price2);
    }

    @Override
    public AbstractPairTradingModelState getCurrentState() {
        if (grid==null || !grid.getIsValid()) return null;
        int submodid = grid.getModelIdUsed();
        if (submodid>=0) return new PairTradingModelKalmanAutoState(submodid);
        else return null;
    }

    @Override
    public void lockState(AbstractPairTradingModelState state) {
        if (!(state instanceof PairTradingModelKalmanAutoState)) throw new IllegalArgumentException("Incompatible state argument");
        grid.lock(((PairTradingModelKalmanAutoState) state).subModelId);
        this.state = (PairTradingModelKalmanAutoState) state;
        logger.debug(String.format("locked kalman-auto to sub-model ID %d", this.state.subModelId));
    }

    @Override
    public void unlockState() {
        grid.unlock();
        state = null;
        logger.debug("unlocked kalman-auto sub-model");
    }
}
