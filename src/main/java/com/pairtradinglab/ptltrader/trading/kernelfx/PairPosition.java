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
class PairPosition {
    static final int DIR_LONG = 0;
    static final int DIR_SHORT = 1;
    static final int INV_NONE = 0;
    static final int INV_FIRST = 1;
    static final int INV_SECOND = 2;
    static final int PEC_GE = 0;
    static final int PEC_LE = 1;

    
    final int poolId; // pool ID
	final int stratId; // strategy ID
	final int modelId; // model ID
	final int direction;
	final int inversion;
	final long expires; // timestamp of expiration
	final double targetScore;
	final int condition;
	final double openP1;
	final double openP2;
	final double fee; // for both legs in total!

    // internal state
    private double lastEquity = 1;
    private double qty1 = 0;
    private double qty2 = 0;
    private boolean active = true;
    private int dir1; // direction of first leg (long/short)
    private int dir2; // direction of second leg (long/short)

    PairPosition(int poolId, int stratId, int modelId, int direction, int inversion, long expires, double targetScore, int condition, double fee, double openP1, double openP2) {
        this.poolId = poolId;
        this.stratId = stratId;
        this.modelId = modelId;
        this.direction = direction;
        this.inversion = inversion;
        this.expires = expires;
        this.targetScore = targetScore;
        this.condition = condition;
        this.openP1 = openP1;
        this.openP2 = openP2;
        this.fee = fee;

        qty1 = 0.5 / openP1;
        qty2 = 0.5 / openP2;

        if (direction == DIR_LONG) {
            // long position
            switch(inversion) {
                case INV_FIRST:
                    dir1 = DIR_SHORT; // inverted
                    dir2 = DIR_SHORT;
                    break;
                case INV_SECOND:
                    dir1 = DIR_LONG;
                    dir2 = DIR_LONG; // inverted
                    break;
                case INV_NONE:
                    dir1 = DIR_LONG;
                    dir2 = DIR_SHORT;
                    break;
            }
        } else {
            // short position
            switch(inversion) {
                case INV_FIRST:
                    dir1 = DIR_LONG; // inverted
                    dir2 = DIR_LONG;
                    break;
                case INV_SECOND:
                    dir1 = DIR_SHORT;
                    dir2 = DIR_SHORT; // inverted
                    break;
                case INV_NONE:
                    dir1 = DIR_SHORT;
                    dir2 = DIR_LONG;
                    break;
            }
        }
    }

    int getDir1() {
        return dir1;
    }

    int getDir2() {
        return dir2;
    }

    PairPositionState update(double p1, double p2, double score, long timestamp) {
        int exitReason = PairPositionState.PREA_NONE;
        if (!active) {
            // this position is no longer active
            return new PairPositionState(false, exitReason, 1);
        }
        // check NAV
        double nav = 0;
        if (dir1 == DIR_LONG) {
            // first leg is long
            nav += qty1*(p1-openP1);
        } else {
            // first leg is short
            nav += qty1*(openP1-p1);
        }
        if (dir2 == DIR_LONG) {
            // second leg is long
            nav += qty2*(p2-openP2);
        } else {
            // second leg is short
            nav += qty2*(openP2-p2);
        }

        // check exit signal
        if (timestamp>expires || (condition == PEC_LE && score <= targetScore) || (condition == PEC_GE && score >= targetScore)) {
            active = false;
            if (timestamp>expires) exitReason = PairPositionState.PREA_TIMEOUT;
            else exitReason = PairPositionState.PREA_CLOSED;

            nav -= fee;
            //printf("close fee charged\n");
        }

        double ret = (1 + nav - fee) / lastEquity;
        //printf("nav %f lastE %f ret %f\n", nav, lastEquity, ret);
        lastEquity = 1 + nav - fee;
        //printf("new lastE %f\n", lastEquity);

        return new PairPositionState(active, exitReason, ret);
    }

    /**
     * This variant of update method is to process timeout logic only (no score)
     */
    PairPositionState update(double p1, double p2, long timestamp) {
        int exitReason = PairPositionState.PREA_NONE;
        if (!active) {
            // this position is no longer active
            return new PairPositionState(false, exitReason, 1);
        }
        // check NAV
        double nav = 0;
        if (dir1 == DIR_LONG) {
            // first leg is long
            nav += qty1*(p1-openP1);
        } else {
            // first leg is short
            nav += qty1*(openP1-p1);
        }
        if (dir2 == DIR_LONG) {
            // second leg is long
            nav += qty2*(p2-openP2);
        } else {
            // second leg is short
            nav += qty2*(openP2-p2);
        }

        // check exit signal
        if (timestamp>expires) {
            active = false;
            exitReason = PairPositionState.PREA_TIMEOUT;
            nav -= fee;
        }

        double ret = (1 + nav - fee) / lastEquity;
        //printf("nav %f lastE %f ret %f\n", nav, lastEquity, ret);
        lastEquity = 1 + nav - fee;
        //printf("new lastE %f\n", lastEquity);

        return new PairPositionState(active, exitReason, ret);
    }

}
