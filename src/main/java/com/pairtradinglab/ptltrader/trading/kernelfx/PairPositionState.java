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

import net.jcip.annotations.Immutable;

@Immutable
class PairPositionState {
    static final int PREA_NONE = 0;
    static final int PREA_CLOSED = 1;
    static final int PREA_TIMEOUT = 2;

    final boolean active;
    final int exitReason;
    final double ret;

    public PairPositionState(boolean active, int exitReason, double ret) {
        this.active = active;
        this.exitReason = exitReason;
        this.ret = ret;
    }
}
