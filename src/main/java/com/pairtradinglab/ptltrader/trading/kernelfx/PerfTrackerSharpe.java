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
class PerfTrackerSharpe extends AbstractPerfTracker {
    boolean ignorePositiveDeviation = true;
    final private ArrayList<SharpeCell> cells = new ArrayList<>();

    int getUnstablePeriod() {
        return (period < 20) ? period : 20;
    }

    @Override
    void setSize(int length) {
        //printf("rwma resize %u\n", length);
        super.setSize(length);
        cells.clear();
        for(int i=0; i<length; ++i) {
            SharpeCell sc = new SharpeCell(getUnstablePeriod(), period);
            sc.ignorePositiveDeviation = ignorePositiveDeviation;
            cells.add(i, sc);
        }
    }

    @Override
    void reset() {
        super.reset();
        //printf("rwma reset, cell size %u\n", cells.size());
        for (SharpeCell c : cells) c.reset();
    }

    @Override
    boolean isReady() {
        return super.isReady() && cells.size()>0 && cells.get(0).isReady();
    }

    @Override
    void update() {
        super.update();
        for(int i=0; i<w.length; ++i) {
            double x = buffer[i]-1;
            //printf("update: %u using %f to %f\n", i, buffer[i], x);
            cells.get(i).add(x);
        }
    }

    void fillWeights() {
        for(int i=0; i<w.length; ++i) {
            if (cells.get(i).isReady() && tradeCntCells.get(i).isReady()) {
                //printf("tcnt sum: %f\n", tradeCntCells[i]->getSum());
                double stddev = cells.get(i).getStdDev();
                if (stddev<0.00000001) stddev = 0.00000001;
                if (cells.get(i).getMean()<0 || tradeCntCells.get(i).getSum()==0) {
                    w[i] = 0; // cover case if there is zero stdDev or negative mean or no trades
                } else {
                    if (useTradeCount) {
                        w[i] = Math.log(1 + tradeCntCells.get(i).getSum()) * cells.get(i).getMean() / stddev;
                    } else {
                        w[i] = cells.get(i).getMean() / stddev;
                    }
                }
                //printf("fill: %u to %f (mean=%f stddev=%.12f)\n", i, w[i], cells[i]->getMean(), stddev);
            } else {
                w[i] = 0;
            }
        }
    }
}
