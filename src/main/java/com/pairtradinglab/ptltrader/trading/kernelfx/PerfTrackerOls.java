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
class PerfTrackerOls extends AbstractPerfTracker {
    double inhibitThreshold = 2; // in standard deviations (strategy is inactive if below)
    boolean enableKiller = false;
    boolean ignorePositiveDeviation = true;

    final private ArrayList<OlsCell> cells = new ArrayList<>();
    private boolean[] aliveMask;

    int getUnstablePeriod() {
        return (period < 20) ? period : 20;
    }


    @Override
    void setSize(int length) {
        //printf("rwma resize %u\n", length);
        super.setSize(length);
        aliveMask = new boolean[length];
        cells.clear();
        for(int i=0; i<length; ++i) {
            aliveMask[i] = true;
            OlsCell c = new OlsCell(getUnstablePeriod(), period);
            c.ignorePositiveDeviation = ignorePositiveDeviation;
            cells.add(i, c);
        }
    }

    @Override
    void reset() {
        super.reset();
        for(int i=0; i<cells.size(); ++i) {
            aliveMask[i] = true;
            cells.get(i).reset();
        }
    }

    @Override
    boolean isReady() {
        return super.isReady() && cells.size()>0 && cells.get(0).isReady();
    }

    @Override
    void update() {
        super.update();
        for(int i=0; i<w.length; ++i) {
            double x = cells.get(i).last() + Math.log(buffer[i]);
            //printf("update: %u using %f to %f (last %f)\n", i, buffer[i], x, cells[i]->getLast());
            cells.get(i).add(x);
        }
    }

    void fillWeights() {
        for(int i=0; i<w.length; ++i) {
            if (!aliveMask[i]) {
                //printf("%u strategy is dead\n", i);
                w[i] = 0; // this strategy is dead
                continue;
            }
            if (cells.get(i).isReady()) {
                double stddev = cells.get(i).getStdDev();
                if (cells.get(i).getA()<0.00000001 || tradeCntCells.get(i).getSum()==0) {
                    w[i] = 0; // cover case if slope is too low or even negative (avoid)
                } else {
                    //printf("experiment %u: last %f prediction %f pred-2sd %f\n", i, cells[i]->getLast(), cells[i]->predictNext(), cells[i]->predictNext()-2*stddev);
                    if (inhibitThreshold>0 && cells.get(i).last() < cells.get(i).predictNext()-inhibitThreshold*stddev) {
                        //printf("inhibited %u\n", i);
                        w[i] = 0; // inhibited
                        if (enableKiller) {
                            //printf("killed %u\n", i);
                            aliveMask[i]=false;
                        }
                    } else {
                        if (useTradeCount) {
                            w[i] = Math.log(1 + tradeCntCells.get(i).getSum()) * cells.get(i).getA() / (1 + stddev);
                        } else {
                            w[i] = cells.get(i).getA() / (1 + stddev);
                        }
                    }
                }
            } else {
                w[i] = 0;
            }
            //printf("fill: %u to %f (beta=%f stddev=%f trades=%f)\n", i, w[i], cells[i]->getA(), cells[i]->getStdDev(), tradeCntCells[i]->getSum());
        }
    }
}
