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
package com.pairtradinglab.ptltrader.trading.kernelfx;

import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

public class SubModelKalmanGrid extends AbstractSubModel {
    public static final int TRACKER_OLS = 0;
    public static final int TRACKER_SHARPE = 1;

    // for transition covariance Delta
    private double kalmanDeltaStart = -13;
    private double kalmanDeltaEnd = -1;
    private int kalmanDeltaGridSize = 52;

    // for observation covariance Ve
    private double kalmanVeStart = -4;
    private double kalmanVeEnd = -3;
    private int kalmanVeGridSize = 5;

    private boolean allowLong = true;
    private boolean allowShort = true;
    private int maxDays = 20;
    private int trackingCutOffPeriod = 240;
    private boolean trackingUseTradeCount = false;
    private int trackingMode = TRACKER_OLS;
    private int unstablePeriod = 20; // unstable period for kalman filters

    private final ArrayList<SimpleStrategy> strategies = new ArrayList<>();
    private final ArrayList<SubModelKalman> kalmans = new ArrayList<>();
    private final LinkedList<PairPosition> positions = new LinkedList<>();
    private AbstractPerfTracker tracker;
    private boolean[] strategiesInPosition;
    private double[] scores;

    private long timestamp = 0;
    private SimpleMatrix beta = new SimpleMatrix(1, 2);
    private double e, sq; // spread and stddev coming from Kalman calculations
    private boolean isValid = false;
    private int rdycnt = 0;

    public double getAlpha() {
        return beta.get(0, 1);
    }

    @Override
    public double getBeta() {
        return beta.get(0, 0);
    }

    double getE() {
        return e;
    }

    public double getSq() {
        return sq;
    }

    @Override
    public void init() {
        super.init();
        kalmans.clear();
        strategies.clear();
        positions.clear();
        strategiesInPosition = new boolean[0];
        scores = new double[0];
        timestamp = 0;
        isValid = false;
        rdycnt = 0;

        double step = (1.0 + kalmanDeltaEnd - kalmanDeltaStart)/(double) kalmanDeltaGridSize;
        for (double i=kalmanDeltaStart; i<=kalmanDeltaEnd; i+=step) {
            //printf("step %f i %f delta %f\n", step, i, pow(10, i));
            double veStep = (1.0 + kalmanVeEnd - kalmanVeStart)/(double) kalmanVeGridSize;
            for (double ve=kalmanVeStart; ve<=kalmanVeEnd; ve+=veStep) {
                //printf("step %f i %f delta %f vexp %f ve %f\n", step, i, pow(10, i), ve, pow(10, ve));
                SubModelKalman m = new SubModelKalman();
                m.Ve = Math.pow(10, ve);
                m.delta = Math.pow(10, i);
                //printf("ve %.16f delta %.16f\n", m->Ve, m->delta);
                m.transform = transform;
                m.invert = invert;
                m.invTransformA = invTransformA;
                m.invTransformB = invTransformB;
                m.init();
                kalmans.add(m);

                int modelId = kalmans.size()-1;
                SimpleStrategy pts = new SimpleStrategy(modelId);
                pts.entryThreshold = 1; // always 1
                if (allowLong && allowShort) pts.exitThreshold = -1; // always -1 if both directions enabled
                else pts.exitThreshold = 0; // always 0 otherwise!
                pts.timeStop = maxDays;
                pts.allowLong = allowLong;
                pts.allowShort = allowShort;
                pts.invert = invert;
                strategies.add(pts);
            }

        }

        if (trackingMode == TRACKER_OLS) {
            PerfTrackerOls pp = new PerfTrackerOls();
            pp.inhibitThreshold = 0;
            pp.enableKiller = false;
            tracker = pp;
        } else {
            // sharpe
            tracker = new PerfTrackerSharpe();
        }
        tracker.period = trackingCutOffPeriod;
        tracker.useTradeCount = trackingUseTradeCount;
        tracker.setSize(strategies.size());
        tracker.reset();

        strategiesInPosition = new boolean[strategies.size()];
        scores = new double[strategies.size()];

    }

    public int getLookback() {
        return unstablePeriod + tracker.getUnstablePeriod();
    }

    short getPeriod() {
        return 0;
    }

    @Override
    public boolean getIsValid() {
        return isValid;
    }

    public boolean update(double p1, double p2) {
        // we need to update all models until they are all ready
        boolean allrdy = true;
        for(SubModelKalman m : kalmans) {
            boolean rdy = m.update(p1, p2);
            allrdy = allrdy && rdy;
        }
        if (!allrdy) {
            // not all models are ready
            pos++;
            timestamp += 86400;
            return false;
        }

        //printf("all rdy, %u models, pos %u rdycnt %u timestamp %u\n", kalmans.size(), pos, rdycnt, timestamp);
        // all models are now ready; now we need to wait unstablePeriod samples to start
        if (++rdycnt < unstablePeriod) {
            pos++;
            timestamp += 86400;
            return false;
        }

        //printf("unstable period passed %u rdycnt %u\n", pos, rdycnt);

        // all models are ready and unstable period is passed!
        // calculate all scores
        for (int i=0; i<kalmans.size(); i++) {
            scores[i] = kalmans.get(i).evaluate(p1, p2); // use original prices here, models apply the transformation themselves
        }

        // start process strategies
        tracker.prepare();

        // here we go
        // let's iterate all positions and mark strategies which terminated position in profit/loss
        ListIterator<PairPosition> it = positions.listIterator();
        while(it.hasNext()) {
            PairPosition p = it.next();
            PairPositionState pstate =  p.update(p1, p2, scores[p.modelId], timestamp); // we need to use original prices here
            int sid = p.stratId;
            int closed = 0;

            //printf("rewarding/penalizing return %f of strat %u\n", ret, sid);
            if (!pstate.active) {
                // no longer active: delete this position and mark the strategy inactive!
                //printf("closing position: strat %u reason %u\n", p->stratId, pres);
                strategiesInPosition[sid] = false;
                closed = (pstate.exitReason == PairPositionState.PREA_CLOSED) ? 1:0;
                it.remove();
            }

            // set the return
            tracker.set(sid, pstate.ret, closed);
        }
        // update perf tracker, wait for rdy
        tracker.update();
        boolean trackerReady = tracker.isReady();

        // now execute all strategies and create new positions (but only for strategies marked inactive)
        for (int i=0; i<strategies.size(); ++i) {
            if (strategiesInPosition[i]) continue; // skip active strategies
            //auto& s = poolStrategies[poolId][i];
            SimpleStrategy s = strategies.get(i);
            int sig = s.entryLogic(scores[s.modelId]);
            if (sig != SimpleStrategy.SIGNONE) {
                // model and strategy signals to open a position
                //printf("opening position: strat %u sig %u\n", i, sig);
                PairPosition pos = s.getNewPosition(sig, 0, i, p1, p2, timestamp, 0); // we need to use original prices here; no fee!
                positions.add(pos);
                //printf("new position opened sid %u model %u dir1 %u dir2 %u: p1 %f p2 %f\n", pos->stratId, pos->modelId, pos->getDir1(), pos->getDir2(), p1, p2);
                strategiesInPosition[i] = true; // marked active!

            }
        }

        if (!trackerReady) {
            pos++;
            timestamp += 86400;
            return false; // tracker not ready
        }

        // merge beta, alpha and sq based on perf tracker weights
        tracker.fillWeights();

        double wsum = tracker.sum();
        if (wsum>0) {
            // we have something
            double tsq=0, b0=0, b1=0;
            for(int i=0; i<strategies.size(); ++i) {
                double w = tracker.getWeight(i);
                //printf("strat %u weight %f\n", i, w);
                tsq += kalmans.get(strategies.get(i).modelId).getSq() * w;
                b0 += kalmans.get(strategies.get(i).modelId).getUncompB0() * w;
                b1 += kalmans.get(strategies.get(i).modelId).getUncompB1() * w;
            }

            sq = tsq / wsum;
            beta.set(0, 0, b0 / wsum);
            beta.set(0, 1, b1 / wsum);

            TransformedPrices tp = applyTransformation(p1, p2);

            double yhat = tp.p1*beta.get(0, 0) + beta.get(0, 1);
            e = yhat - tp.p2;
            isValid = true;
        } else {
            // there is nothing
            sq = 0;
            beta.set(0, 0, 0);
            beta.set(0, 1, 0);
            e = 0;
            isValid = false;
        }

        pos++;
        timestamp += 86400;
        // all done
        return true;
    }

    public boolean isReady() {
        return (rdycnt >= unstablePeriod) && tracker.isReady();
    }

    public double evaluate(double p1, double p2) {
        if (!isValid) return 0;
        TransformedPrices tp = applyTransformation(p1, p2);
        double yhat = tp.p1*beta.get(0, 0) + beta.get(0, 1);

        double spread = yhat - tp.p2;
        //printf("evaluate: p1 %f x1 %f p2 %f x2 %f yhat %f e %f spread %f sq %f ret %f\n", p1, x1, p2, x2, yhat, e, spread, sq, spread/sq);
        return spread/sq;
    }

    double getSpread(double p1, double p2) {
        if (!isValid) return 0;
        TransformedPrices tp = applyTransformation(p1, p2);
        double yhat = tp.p1*beta.get(0, 0) + beta.get(0, 1);

        return yhat - tp.p2;
    }

    public double getKalmanDeltaStart() {
        return kalmanDeltaStart;
    }

    public void setKalmanDeltaStart(double kalmanDeltaStart) {
        this.kalmanDeltaStart = kalmanDeltaStart;
    }

    public double getKalmanDeltaEnd() {
        return kalmanDeltaEnd;
    }

    public void setKalmanDeltaEnd(double kalmanDeltaEnd) {
        this.kalmanDeltaEnd = kalmanDeltaEnd;
    }

    public int getKalmanDeltaGridSize() {
        return kalmanDeltaGridSize;
    }

    public void setKalmanDeltaGridSize(int kalmanDeltaGridSize) {
        this.kalmanDeltaGridSize = kalmanDeltaGridSize;
    }

    public double getKalmanVeStart() {
        return kalmanVeStart;
    }

    public void setKalmanVeStart(double kalmanVeStart) {
        this.kalmanVeStart = kalmanVeStart;
    }

    public double getKalmanVeEnd() {
        return kalmanVeEnd;
    }

    public void setKalmanVeEnd(double kalmanVeEnd) {
        this.kalmanVeEnd = kalmanVeEnd;
    }

    public int getKalmanVeGridSize() {
        return kalmanVeGridSize;
    }

    public void setKalmanVeGridSize(int kalmanVeGridSize) {
        this.kalmanVeGridSize = kalmanVeGridSize;
    }

    public boolean isAllowLong() {
        return allowLong;
    }

    public void setAllowLong(boolean allowLong) {
        this.allowLong = allowLong;
    }

    public boolean isAllowShort() {
        return allowShort;
    }

    public void setAllowShort(boolean allowShort) {
        this.allowShort = allowShort;
    }

    public int getMaxDays() {
        return maxDays;
    }

    public void setMaxDays(int maxDays) {
        this.maxDays = maxDays;
    }

    public int getTrackingCutOffPeriod() {
        return trackingCutOffPeriod;
    }

    public void setTrackingCutOffPeriod(int trackingCutOffPeriod) {
        this.trackingCutOffPeriod = trackingCutOffPeriod;
    }

    public boolean isTrackingUseTradeCount() {
        return trackingUseTradeCount;
    }

    public void setTrackingUseTradeCount(boolean trackingUseTradeCount) {
        this.trackingUseTradeCount = trackingUseTradeCount;
    }

    public int getTrackingMode() {
        return trackingMode;
    }

    public void setTrackingMode(int trackingMode) {
        this.trackingMode = trackingMode;
    }

    public int getUnstablePeriod() {
        return unstablePeriod;
    }

    public void setUnstablePeriod(int unstablePeriod) {
        this.unstablePeriod = unstablePeriod;
    }
}
