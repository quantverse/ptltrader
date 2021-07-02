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

@NotThreadSafe
class SimpleCell extends MemoryCell {
    final int period;
    private double sum = 0d;

    public SimpleCell(int period) {
        super((short) Math.ceil(Math.log(period)/Math.log(2)));
        this.period = period;
    }

    @Override
    void reset() {
        super.reset();
        sum = 0;
    }

    @Override
    protected void valAdded(double v) {
        // add to rawSum
        sum += v;
        //printf("size %u period %u pos %u\n", size, period, pos);

        if (size+1<period) {
            //printf("pre: pos=%d rawSum=%f v=%f\n", pos, rawSum, v);
            return;
        }

        if (size>=period) {
            //printf("substracting oldest value %d %f!\n", pos-period, x[(pos-period) & imask]);
            sum -= x[(pos-period) & imask];
        }
        //printf("rawSum %f\n", rawSum);
    }

    double getSum() {
        return sum;
    }

    boolean isReady() {
        return true;
    }

    int getEffectiveSize() {
        return size>period ? period:size;
    }
}
