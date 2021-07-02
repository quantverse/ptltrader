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

import net.jcip.annotations.NotThreadSafe;

import java.util.ArrayList;

@NotThreadSafe
class UsageTracker {
    int period = 120;
    double target = 50;
    double shapeSigma = 3;
    private double[] w;
    private int[] buffer;
    private final ArrayList<SimpleCell> cells = new ArrayList<>();


    private double shape(double x) {
        return Math.exp(-(x-target)*(x-target)/(2*shapeSigma*shapeSigma));
    }

    double getWeight(int ix) {
        return w[ix];
    }

    void setSize(int length) {
        w = new double[length];
        buffer = new int[length];
        cells.clear();
        for (int i=0; i<length; ++i) {
            SimpleCell c = new SimpleCell(period);
            cells.add(i, c);
        }

    }

    void reset() {
        for (int i=0; i<w.length; ++i) {
            w[i] = 0;
            cells.get(i).reset();
        }
        prepare();
    }

    double sum() {
        double s = 0;
        for(double a : w) s += a;
        return s;
    }

    boolean isReady() {
        return cells.size()>0 && cells.get(0).isReady() && cells.get(0).getEffectiveSize()>=2;
    }

    void prepare() {
        for(int i=0; i<buffer.length; ++i) buffer[i]=0;
    }

    void update() {
        for(int i=0; i<w.length; ++i) {
            cells.get(i).add(buffer[i]);
        }
    }

    void markUsage(int ix, boolean used) {
        buffer[ix] = used ? 1:0;
    }

    int getUnstablePeriod() {
        return (period < 2) ? period : 2;
    }

    void fillWeights() {
        for(int i=0; i<w.length; ++i) {
            if (cells.get(i).isReady() && cells.get(i).getEffectiveSize()>0) {
                double usage = 100 * cells.get(i).getSum() / cells.get(i).getEffectiveSize();
                w[i] = shape(usage);
                //printf("fill: %u to %f (sum=%f size=%u usage=%f)\n", i, w[i], cells[i]->getSum(), cells[i]->getEffectiveSize(), usage);
            } else {
                w[i] = 0;
            }

        }
    }
}
