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
class MemoryCell {
    final int capacity;
    final int imask;
    protected int pos;
    protected int size;
    protected final double[] x;

    public MemoryCell(short logCap) {
        capacity = 1 << logCap;
        imask = capacity  - 1;
        x = new double[capacity];
        pos = 0;
        size = 0;
    }

    protected void valAdded(double v) {
        // nothing here
    }

    int add(double v) {
        valAdded(v);
        // now insert our value at the end
        x[pos & imask] = v;
        if (++pos<0) pos = 0; // we need to do this because it is signed integer
        if (++size > capacity) size = capacity;
        //for(int i=0; i<capacity; i++) {
        //	printf("%f ", x[i]);
        //}
        //printf("size %d pos %d mpos %d mask %d cap %d thr %d x %f\n", size, pos, pos & imask, imask, capacity, sthreshold, x[(pos-1) & imask]);
        return size;
    }

    void reset() {
        size = 0;
        pos = 0;
        for(int i=0; i<x.length; ++i) x[i] = 0d;
    }

    double rawSum() {
        double s = 0;
        for(double a : x) s += a;
        return s;
    }

    double last() {
        if (size>0) {
            return x[(pos-1) & imask];

        } else return 0;
    }
}
