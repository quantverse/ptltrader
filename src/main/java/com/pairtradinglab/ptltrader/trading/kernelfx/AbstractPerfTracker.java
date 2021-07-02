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
abstract class AbstractPerfTracker {

    int period = 120;
    boolean useTradeCount = true;
    double[] w;
    double[] buffer;
    private int[] tradeCntBuffer;
    final ArrayList<SimpleCell> tradeCntCells = new ArrayList<>();

    abstract void fillWeights();
    abstract int getUnstablePeriod();

    double getWeight(int ix) {
        return w[ix];
    }

    void setSize(int length) {
        w = new double[length];
        buffer = new double[length];
        tradeCntBuffer = new int[length];

        tradeCntCells.clear();
        for(int i=0; i<length; ++i) {
            tradeCntCells.add(i, new SimpleCell(period));
        }
    }

    void reset() {
        for (int i=0; i<w.length; ++i) {
            w[i] = 0;
            tradeCntCells.get(i).reset();
        }
        prepare();
    }

    double sum() {
        double s = 0;
        for(double a : w) s += a;
        return s;
    }

    boolean isReady() {
        return tradeCntCells.size()>0 && tradeCntCells.get(0).isReady(); // we can use any cell here as long we have any (they are of same capacity)
    }

    void prepare() {
        for (int i=0; i<buffer.length; ++i) {
            buffer[i] = 1;
            tradeCntBuffer[i] = 0;
        }
    }

    void update() {
        for(int i=0; i<w.length; ++i) {
            tradeCntCells.get(i).add(tradeCntBuffer[i]);
        }
    }

    void set(int ix, double dailyReturn, int closedTradeCnt) {
        buffer[ix] = dailyReturn;
        tradeCntBuffer[ix] = closedTradeCnt;
        //printf("set: ctc %u buf %u\n", closedTradeCnt, tradeCntBuffer[ix]);
    }

}
