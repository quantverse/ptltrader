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
public class SimpleStrategy {
    static final int SIGNONE = 0;
    static final int SIGLONG = 1;
    static final int SIGSHORT = 2;

    final int modelId; // model ID

    int invert = 0;
    boolean allowShort = true;
    boolean allowLong = true;
    int timeStop = 20; // in days
    double entryThreshold = 2;
    double exitThreshold = 0;

    public SimpleStrategy(int modelId) {
        this.modelId = modelId;
    }

    int entryLogic(double score) {
        if (score >= entryThreshold && allowShort) {
            return SIGSHORT;
        } else if (score <= -entryThreshold && allowLong) {
            return SIGLONG;
        } else return SIGNONE;
    }


    PairPosition getNewPosition(int sig, int poolId, int stratId, double p1, double p2, long timestamp, double fee) {
        int dir;
        int pec;
        double targetScore;

        if (sig == SIGLONG) {
            dir = PairPosition.DIR_LONG;
            pec = PairPosition.PEC_GE;
            targetScore = -exitThreshold;
        } else if (sig == SIGSHORT) {
            dir = PairPosition.DIR_SHORT;
            pec = PairPosition.PEC_LE;
            targetScore = exitThreshold;
        } else return null;
        long expires = timestamp + timeStop*86400;

        return new PairPosition(poolId, stratId, modelId, dir, invert, expires, targetScore, pec, fee, p1, p2);
    }
}
