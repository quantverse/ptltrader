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

public class SubModelKalmanAuto extends AbstractSubModel {
    // for transition covariance Delta
    double kalmanDeltaStart = -13;
    double kalmanDeltaEnd = -1;
    int kalmanDeltaGridSize = 104;

    // observation covariance Ve
    double kalmanVe = 0.001;

    boolean allowLong = true;
    boolean allowShort = true;
    int maxDays = 20;
    int trackingCutOffPeriod = 240;
    int unstablePeriod = 20; // unstable period for kalman filters
    double usageTarget = 50;
    double trackerShapeSigma = 1.5;

    final private ArrayList<SimpleStrategy> strategies = new ArrayList<>();
    final private ArrayList<SubModelKalman> kalmans = new ArrayList<>();
    final private LinkedList<PairPosition> positions = new LinkedList<>();
    final private UsageTracker tracker = new UsageTracker();
    private boolean[] strategiesInPosition;
    private double[] scores;
    final private ArrayList<Double> modelLogDeltas = new ArrayList<>();

    private long timestamp = 0;
    private final SimpleMatrix beta = new SimpleMatrix(1, 2);
    private double sq; // stddev coming from Kalman calculations
    private boolean isValid = false;
    private int rdycnt = 0;
    private double currentDelta = 0;
    private double currentDeltaTarget = 0;
    private int modelIdUsed = -1;
    private int lockedModelId = -1;

    public double getAlpha() {
        return beta.get(0, 1);
    }

    public double getSq() {
        return sq;
    }

    public double getCurrentDelta() {
        return currentDelta;
    }

    public double getCurrentDeltaTarget() {
        return currentDeltaTarget;
    }

    private void refreshStats() {
        if (lockedModelId>=0) {
            sq = kalmans.get(lockedModelId).getSq();
            beta.set(0, 0, kalmans.get(lockedModelId).getUncompB0());
            beta.set(0, 1, kalmans.get(lockedModelId).getUncompB1());
            isValid = true;
            currentDelta = modelLogDeltas.get(lockedModelId);
            currentDeltaTarget = currentDelta;
            modelIdUsed = lockedModelId;
        } else {
            double wsum = tracker.sum();
            if (wsum>0) {
                double dsum = 0;
                for(int i=0; i<strategies.size(); ++i) {
                    double w = tracker.getWeight(i);
                    //printf("%f ", w);
                    dsum += modelLogDeltas.get(strategies.get(i).modelId) * w;
                }
                double targetDelta = dsum / wsum;
                currentDeltaTarget = targetDelta;
                //printf(": %f\n", targetDelta);
                // now we have to find model with delta as close as possible to targetDelta
                double min = 1000;
                int modid = -1;
                for(int i=0; i<modelLogDeltas.size(); ++i) {
                    double diff = Math.abs(modelLogDeltas.get(i)-targetDelta);
                    if (diff < min) {
                        min = diff;
                        modid = i;
                    }
                }

                if (modid>=0) {
                    //printf("model selected %d log delta %f\n", modid, modelLogDeltas[modid]);
                    sq = kalmans.get(modid).getSq();
                    beta.set(0, 0, kalmans.get(modid).getUncompB0());
                    beta.set(0, 1, kalmans.get(modid).getUncompB1());
                    isValid = true;
                    currentDelta = modelLogDeltas.get(modid);
                    modelIdUsed = modid;
                    return;

                } else {
                   modelIdUsed = -1;
                }

            } else {
                // there is no model to use!
                modelIdUsed = -1;
            }
            sq = 0;
            beta.zero();
            currentDelta = 0;
            currentDeltaTarget = 0;
            isValid = false;
        }
    }

    public int getModelIdUsed() {
        return modelIdUsed;
    }

    public int getLockedModelId() {
        return lockedModelId;
    }

    public void lock(int modelId) {
        lockedModelId = modelId;
        refreshStats();
    }

    public void unlock() {
        lockedModelId = -1;
        refreshStats();
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
        lockedModelId = -1;
        modelIdUsed = -1;

        double step = (1.0 + kalmanDeltaEnd - kalmanDeltaStart)/(double) kalmanDeltaGridSize;
        for (double i=kalmanDeltaStart; i<=kalmanDeltaEnd; i+=step) {
            //printf("step %f i %f delta %f\n", step, i, pow(10, i));
            SubModelKalman m = new SubModelKalman();
            m.Ve = kalmanVe;
            m.delta = Math.pow(10, i);
            //printf("ve %.16f delta %.16f\n", m->Ve, m->delta);
            m.transform = transform;
            m.invert = invert;
            m.invTransformA = invTransformA;
            m.invTransformB = invTransformB;
            m.init();
            kalmans.add(m);
            modelLogDeltas.add(i);

            int modelId = kalmans.size()-1;

            SimpleStrategy pts = new SimpleStrategy(modelId);
            pts.entryThreshold = 1; // always 1
            pts.exitThreshold = 0; // always 0
            pts.timeStop = maxDays;
            pts.allowLong = allowLong;
            pts.allowShort = allowShort;
            pts.invert = invert;
            strategies.add(pts);
        }

        tracker.period = trackingCutOffPeriod;
        tracker.target = usageTarget;
        tracker.shapeSigma = trackerShapeSigma;
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

    @Override
    public double getBeta() {
        return beta.get(0, 0);
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
            scores[i] = kalmans.get(i).evaluate(p1, p2);
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
            //System.out.printf("pos %d sid %d\n", pos, sid);
            if (!pstate.active) {
                // no longer active: delete this position and mark the strategy inactive!
                //printf("closing position: strat %u reason %u\n", p->stratId, pres);
                strategiesInPosition[sid] = false;
                it.remove();
                //System.out.printf("pos %d REMOVED sid %d score %f reason %d\n", pos, sid, scores[p.modelId], pstate.exitReason);
            }
            tracker.markUsage(sid, true); // mark strategy usage
        }

        // update perf tracker, wait for rdy
        tracker.update();
        boolean trackerReady = tracker.isReady();

        // now execute all strategies and create new positions (but only for strategies marked inactive)
        for (int i=0; i<strategies.size(); i++) {
            if (strategiesInPosition[i]) continue; // skip active strategies
            //auto& s = poolStrategies[poolId][i];
            SimpleStrategy s = strategies.get(i);
            int sig = s.entryLogic(scores[s.modelId]);
            if (sig != SimpleStrategy.SIGNONE) {
                // model and strategy signals to open a position
                //printf("opening position: strat %u sig %u\n", i, sig);
                PairPosition position = s.getNewPosition(sig, 0, i, p1, p2, timestamp, 0); // we need to use original prices here; no fee!
                positions.add(position);
                //printf("new position opened sid %u model %u dir1 %u dir2 %u: p1 %f p2 %f\n", pos->stratId, pos->modelId, pos->getDir1(), pos->getDir2(), p1, p2);
                strategiesInPosition[i] = true; // marked active!
                //System.out.printf("pos %d ADDED sid %d for score %f\n", pos, i, scores[s.modelId]);

            }
        }

        if (!trackerReady) {
            pos++;
            timestamp += 86400;
            return false; // tracker not ready
        }

        // merge beta, alpha and sq based on perf tracker weights
        tracker.fillWeights();

        refreshStats();
        pos++;
        timestamp += 86400;
        // all done
        return true;


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

    public double getKalmanVe() {
        return kalmanVe;
    }

    public void setKalmanVe(double kalmanVe) {
        this.kalmanVe = kalmanVe;
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

    public int getUnstablePeriod() {
        return unstablePeriod;
    }

    public void setUnstablePeriod(int unstablePeriod) {
        this.unstablePeriod = unstablePeriod;
    }

    public double getUsageTarget() {
        return usageTarget;
    }

    public void setUsageTarget(double usageTarget) {
        this.usageTarget = usageTarget;
    }

    public double getTrackerShapeSigma() {
        return trackerShapeSigma;
    }

    public void setTrackerShapeSigma(double trackerShapeSigma) {
        this.trackerShapeSigma = trackerShapeSigma;
    }
}
