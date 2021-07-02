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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SubModelKalmanGridTest {
    private SubModelKalmanGrid t;

    @Before
    public void setUp() throws Exception {
        t = new SubModelKalmanGrid();
        t.setTrackingUseTradeCount(true);
        t.setKalmanVeStart(-3);
        t.setKalmanVeEnd(-3);
        t.setKalmanVeGridSize(1);
        t.setUnstablePeriod(1);
        t.init();
    }

    @Test
    public void testNotReadyState() {
        assertFalse(t.isReady());
        assertEquals(0, t.getPos());
        assertEquals(21, t.getLookback());
    }

    @Test
    public void testReadyState1() {
        int i;
        double p1=0, p2=0;
        for (i=0; i<30; i++) {
            p1 = 10+0.2*i+0.2*Math.sin(2*i);
            p2 = 5+0.1*i+0.1*Math.cos(2.1*i);
            //printf("%f %f\n", p1, p2);
            t.update(p1, p2);
        }
        assertTrue(t.isReady());
        assertEquals(i, t.getPos());
        assertEquals(0.49703111981803128, t.getBeta(), 0.00000000000000001);
        assertEquals(0.051558254748522228, t.getAlpha(), 0.0000000000000001);

        double e = t.getE();
        assertEquals(0.13867397276492621, e, 0.00000000000000001);
        double sq = t.getSq();
        assertEquals(0.11333661837539351, sq, 0.00000000000000001);

        assertEquals(e/sq, t.evaluate(p1, p2), 0.00000000000000001);
        assertEquals(e/sq, t.evaluate(p1, p2), 0.00000000000000001); // test again (should be the same to make sure it does not update)
    }
}
