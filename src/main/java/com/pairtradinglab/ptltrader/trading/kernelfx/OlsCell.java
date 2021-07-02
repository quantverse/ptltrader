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
class OlsCell extends MemoryCell {

    final int unstablePeriod;
	final int cutOffPeriod;
    boolean ignorePositiveDeviation = true;

    private double a = 0;
    private double b = 0;

    private double B[] = new double[2];
    private double A[][] = new double[2][2];
    private double CB[] = new double[2];
    private double CA[][] = new double[2][2];

    private double stdDev = 0;

    public OlsCell(int unstablePeriod, int cutOffPeriod) {
        super((short) Math.ceil(Math.log(cutOffPeriod)/Math.log(2)));
        this.unstablePeriod = unstablePeriod;
        this.cutOffPeriod = cutOffPeriod;
    }

    private double f(int j, double p) {
        if (j==0) {
            return 1;
        } else {
            return p;
        }
    }

    private double calcSpread(double price1, double price2) {
        return price1-(a*price2+b); // actual-predicted
    }

    double getA() {
        return a;
    }

    double getB() {
        return b;
    }

    double predictNext() {
        if (isReady()) {
            return a*(double) pos + b;
        } else return 0;
    }

    double getStdDev() {
        return stdDev;
    }

    boolean isReady() {
        return size >= unstablePeriod;
    }


    @Override
    void reset() {
        super.reset();
        a = 0;
        b = 0;
        stdDev = 0;

        // int A matrix
        for(int j=0;j<2;j++) {
            for(int k=0;k<2;k++) {
                A[j][k]=0;
            }
        }
        // init B matrix
        B[0]=0;
        B[1]=0;
    }

    @Override
    protected void valAdded(double v) {
        int effperiod = size+1;
        if (effperiod>cutOffPeriod) effperiod = cutOffPeriod;
        //printf("size %u unstable %u cutoff %u effective %u pos %u val %f\n", size, unstablePeriod, cutOffPeriod, effperiod, pos, v);

        // add to A matrix
        for(int j=0;j<2;j++) {
            for(int k=0;k<2;k++) {
                A[j][k]+=f(j, (double) pos)*f(k, (double) pos);
            }
        }
        // add to B matrix
        for(int j=0;j<2;j++) {
            B[j]+=f(j, (double) pos)*v;
        }

        //printf("size %u period %u pos %u\n", size, period, pos);

        if (effperiod<unstablePeriod) {
            //printf("pre: pos=%d A[0][0]=%f A[0][1]=%f A[1][0]=%f A[1][1]=%f B[0]=%f B[1]=%f v=%f\n", pos, A[0][0], A[0][1], A[1][0], A[1][1], B[0], B[1], v);

            return;
        }

        if (size>=cutOffPeriod) {
            //printf("substracting oldest value %d %.20f!\n", pos-period, x[(pos-period) & imask]);

            // now we have to substract the oldest value from all matrices
            for(int j=0;j<2;j++) {
                for(int k=0;k<2;k++) {
                    double w=f(j, (double) (pos-cutOffPeriod))*f(k, (double) (pos-cutOffPeriod));
                    A[j][k]-=w;
                }
            }
            for(int j=0;j<2;j++) {
                B[j]-=f(j, (double) (pos-cutOffPeriod))*x[(pos-cutOffPeriod) & imask];
            }
        }
        //printf("pos=%d A[0][0]=%f A[0][1]=%f A[1][0]=%f A[1][1]=%f B[0]=%f B[1]=%f v=%f\n", pos, A[0][0], A[0][1], A[1][0], A[1][1], B[0], B[1], v);

        // now we have to clone the matrix and solve it in order to get the regression
        CA[0][0]=A[0][0];
        CA[0][1]=A[0][1];
        CA[1][0]=A[1][0];
        CA[1][1]=A[1][1];

        CB[0] = B[0];
        CB[1] = B[1];

        // solve matrix
        // inverse matrix
        for (int k=0; k<1; k++)
            for (int i=k+1; i<=1; i++)
            {
                CB[i] -= CA[i][k] / CA[k][k] * CB[k];
                for (int j=k+1; j<=1; j++)
                    CA[i][j] -= CA[i][k] / CA[k][k] * CA[k][j];
            }

        // ---- Compute coefficients using inverse of c ----
        double X[] = new double[2];

        X[1] = CB[1] / CA[1][1];
        for (int i=0; i>=0; i--)
        {
            double s = 0;
            for (int k=i+1; k<=1; k++)
                s += CA[i][k] * X[k];
            X[i] = (CB[i] - s) / CA[i][i];
        }

        a=X[1];
        b=X[0];

        // now we have to calculate the stddev of the spread series - we can calculate it using the whole ring buffer
        double sqsum=0;
        for (int i=pos-effperiod+1; i<pos; i++) {
            //printf("stddev loop: i=%d masked=%d\n", i, i & imask);
            double mean=a*(double) i+b;
            if (x[i & imask]<mean || !ignorePositiveDeviation) sqsum+=(x[i & imask]-mean)*(x[i & imask]-mean);
            //printf("%.16f ", x[i & imask]);
            //printf("\nmean: %f sqsum %f\n", mean, sqsum);
        }
        // we need to add one more element (v!)
        double mean=a*(double) pos+b;
        if (v<mean || !ignorePositiveDeviation) sqsum+=(v-mean)*(v-mean);
        //printf("mean: %f sqsum %f\n", mean, sqsum);
        stdDev=Math.sqrt(sqsum/(double) effperiod);

        //printf("\npos=%d a=%f b=%f stddev=%f\n", pos, a, b, stdDev);

    }
}
