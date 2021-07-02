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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PerfTrackerOlsTest {
    private PerfTrackerOls t;

    @Before
    public void setUp() throws Exception {
        t = new PerfTrackerOls();
        t.period = 4;
        t.inhibitThreshold = 0;
        t.setSize(3);
        t.reset();
    }


    @Test
    public void testInitialState() {
        t.prepare();
        t.update();
        t.fillWeights();
        assertEquals(0, t.sum(), 0.001);
    }

    @Test
    public void test1() {
        t.prepare();
        t.set(1, 0.5, 1);
        t.set(0, 1.2, 1);
        t.set(2, 1.1, 1);
        t.update();
        t.fillWeights();
        assertFalse(t.isReady());
        assertEquals(0, t.sum(), 0.001);
        t.prepare();
        t.set(1, 0.5, 1);
        t.set(2, 0.95, 1);
        t.update();
        t.fillWeights();
        assertFalse(t.isReady());
        assertEquals(0, t.sum(), 0.001);
        t.prepare();
        t.set(0, 1.3, 1);
        t.update();
        t.fillWeights();
        assertFalse(t.isReady());
        assertEquals(0, t.sum(), 0.001);
        t.prepare();
        t.set(2, 1.07, 1);
        t.update();
        t.fillWeights();
        assertTrue(t.isReady());
        assertEquals(0.11736784998476141, t.sum(), 0.0000000001);
    }
}
