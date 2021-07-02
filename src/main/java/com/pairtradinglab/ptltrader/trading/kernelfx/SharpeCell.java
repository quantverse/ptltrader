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
class SharpeCell extends MemoryCell {
    final int unstablePeriod;
	final int cutOffPeriod;
    boolean ignorePositiveDeviation = true;
    private double sum = 0;
    private double mean = 0, stdDev = 0;

    public SharpeCell(int unstablePeriod, int cutOffPeriod) {
        super((short) Math.ceil(Math.log(cutOffPeriod)/Math.log(2)));
        this.unstablePeriod = unstablePeriod;
        this.cutOffPeriod = cutOffPeriod;
    }

    double getStdDev() {
        return stdDev;
    }

    double getMean() {
        return mean;
    }

    boolean isReady() {
        return size >= unstablePeriod;
    }

    @Override
    void reset() {
        super.reset();
        sum = 0;
        mean = 0;
        stdDev = 0;
    }

    @Override
    protected void valAdded(double v) {
        // add to sum
        sum += v;
        int effperiod = size+1;
        if (effperiod>cutOffPeriod) effperiod = cutOffPeriod;
        //printf("size %u unstable %u cutoff %u effective %u pos %u val %f\n", size, unstablePeriod, cutOffPeriod, effperiod, pos, v);

        if (effperiod<unstablePeriod) {
            //printf("pre: pos=%d sum=%f v=%f\n", pos, sum, v);

            return;
        }

        if (size>=cutOffPeriod) {
            //printf("substracting oldest value %d %f!\n", pos-period, x[(pos-period) & imask]);
            sum -= x[(pos-cutOffPeriod) & imask];
        }

        mean = sum / (double) effperiod;
        //printf("pos=%d sum=%f mean=%f v=%f\n", pos, sum, mean, v);

        // now we have to calculate the stddev
        double sqsum=0;
        for (int i=pos-effperiod+1; i<pos; i++) {
            //printf("stddev loop: i=%d masked=%d\n", i, i & imask);
            if (x[i & imask]<mean || !ignorePositiveDeviation) sqsum+=(x[i & imask]-mean)*(x[i & imask]-mean);
            //printf("pos: %u v: %f mean: %f sqsum %f\n", pos, x[i & imask], mean, sqsum);
        }
        // we need to add one more element (v!)
        if (v<mean || !ignorePositiveDeviation) sqsum+=(v-mean)*(v-mean);
        //printf("pos: %u v: %f mean: %f sqsum %f\n", pos, v, mean, sqsum);
        stdDev=Math.sqrt(sqsum/(double) effperiod);

        //printf("debug: pos=%d a=%f b=%f stddev=%f\n", pos, a, b, stdDev);

    }

}
