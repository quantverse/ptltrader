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
abstract class AbstractSubModel {

    abstract public boolean isReady();
    abstract public boolean update(double p1, double p2);
    abstract public int getLookback();
    abstract public double evaluate(double p1, double p2);
    abstract short getPeriod(); // for models with period


    int getPos() {
        return pos;
    }


    int invert = 0; // 0 = invert none, 1=invert first, 2=invert second
    int transform = 0; // 0 = transform none, 1=transform first, 2=transform second, used together with invert
    double invTransformA = 1000; // price transformation: P2 = B + A/P1
    double invTransformB = 0.1;


    protected int pos = 0;

    private double invertPrice(double p) {
        return invTransformB + invTransformA / p;
    }

    TransformedPrices applyTransformation(double p1, double p2) {
        switch(transform) {
            case 1:
                return new TransformedPrices(invertPrice(p1), p2);
            case 2:
                return new TransformedPrices(p1, invertPrice(p2));
            default:
                return new TransformedPrices(p1, p2);
        }
    }

    public void init() {
        pos = 0;
    }

    public boolean getIsValid() {
        return true; // always; override this in other models
    }

    public double getBeta() {
        return 0; // always; override this in other models
    }

    public int getInvert() {
        return invert;
    }

    public void setInvert(int invert) {
        this.invert = invert;
    }

    public int getTransform() {
        return transform;
    }

    public void setTransform(int transform) {
        this.transform = transform;
    }

    public double getInvTransformA() {
        return invTransformA;
    }

    public void setInvTransformA(double invTransformA) {
        this.invTransformA = invTransformA;
    }

    public double getInvTransformB() {
        return invTransformB;
    }

    public void setInvTransformB(double invTransformB) {
        this.invTransformB = invTransformB;
    }
}
