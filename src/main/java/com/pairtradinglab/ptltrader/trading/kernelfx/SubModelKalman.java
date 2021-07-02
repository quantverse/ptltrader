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
import org.ejml.simple.SimpleMatrix;

@NotThreadSafe
class SubModelKalman extends AbstractSubModel {

    double delta = 0.0001; // transition covariance
    double Ve = 0.001; // observation covariance
    private SimpleMatrix Vw = SimpleMatrix.identity(2); // matrix 2x2
    private SimpleMatrix beta = new SimpleMatrix(1, 2); // vactor / matrix 1x2
    private SimpleMatrix P = new SimpleMatrix(2, 2);
    private SimpleMatrix R = null;
    private double e, sq; // spread and stddev coming from Kalman calculations
    private double uncompB0;
    private double uncompB1;

    double getE() {
        return e;
    }

    double getSq() {
        return sq;
    }

    SimpleMatrix getBetaVector() {
        return beta;
    }

    public boolean update(double p1, double p2) {
        TransformedPrices t = applyTransformation(p1, p2);
        SimpleMatrix X = new SimpleMatrix(1, 2, true, t.p1, 1.0);
        // x = np.asarray([data[context.ewa].price, 1.0]).reshape((1, 2))

        // update Kalman filter with latest price
        // R = matrix 2x2
        if (R!=null) {
            R = P.plus(Vw);
        } else {
            R = new SimpleMatrix(2, 2);
        }

        double yhat = X.dot(beta);

        // beta before compensation
        uncompB0 = beta.get(0, 0);
        uncompB1 = beta.get(0, 1);

        double q = X.mult(R).dot(X.transpose()) + Ve;
        sq = Math.sqrt(q);
        e = yhat - t.p2;

        SimpleMatrix K = R.mult(X.transpose()).divide(q);

        // ORIG: context.beta = context.beta + K.flatten() * -e
        beta = beta.plus(new SimpleMatrix(1, 2, true, K.get(0), K.get(1)).scale(-e));
        // ontext.P = context.R - K * x.dot(context.R)
        P = R.minus(K.mult(X.mult(R)));
        pos++;
        return true;
    }

    public boolean isReady() {
        return pos>0;
    }

    public double evaluate(double p1, double p2) {
        if (R==null) return 0;
        TransformedPrices t = applyTransformation(p1, p2);
        double yhat = t.p1*uncompB0 + uncompB1;

        double spread = yhat - t.p2;
        return spread/sq;
    }

    @Override
    public void init() {
        super.init();

        e = 0;
        sq = 0;
        uncompB0 = 0;
        uncompB1 = 0;

        P.zero();
        beta.zero();
        Vw = SimpleMatrix.identity(2).scale(delta / (1 - delta));
        R = null;
    }

    public int getLookback() {
        return 1;
    }


    short getPeriod() {
        return 0;
    }

    public double getBeta() {
        return beta.get(0, 1);
    }

    public double getUncompB0() {
        return uncompB0;
    }

    public double getUncompB1() {
        return uncompB1;
    }

    public double getDelta() {
        return delta;
    }

    public void setDelta(double delta) {
        this.delta = delta;
    }

    public double getVe() {
        return Ve;
    }

    public void setVe(double ve) {
        Ve = ve;
    }
}
