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

public class UsageTrackerTest {
    private UsageTracker t;

    @Before
    public void setUp() throws Exception {
        t = new UsageTracker();
        t.period = 4;
        t.setSize(3);
        t.reset();
    }


    @Test
    public void testInitialState() {
        t.prepare();
        t.update();
        t.fillWeights();
        assertEquals(1.4402676716757795e-60, t.sum(), 1e-70);
    }

    @Test
    public void test1() {
        t.prepare();
        t.markUsage(1, true);
        t.markUsage(0, false);
        t.markUsage(2, true);
        t.update(); // #1=100 #0=0 #2=100
        t.fillWeights();
        assertFalse(t.isReady());
        assertEquals(1.4402676716757795e-60, t.sum(), 1e-70);

        t.prepare();
        t.markUsage(1, true);

        t.update(); // #1=100 #0=0 #2=50
        t.fillWeights();
        assertTrue(t.isReady());
        assertEquals(1, t.sum(), 0.001);

        t.prepare();
        t.markUsage(0, true);
        t.update(); // #1=66 #0=33 #2=33
        t.fillWeights();
        assertTrue(t.isReady());
        //System.out.println(t.sum());
        assertEquals(5.9572512450362858e-07, t.sum(), 1e-14);

        t.prepare();
        t.markUsage(0, true);
        t.update(); // #1=50 #0=50 #2=25
        t.fillWeights();
        assertTrue(t.isReady());
        //System.out.println(t.sum());
        assertEquals(2, t.sum(), 0.0000000001);


        t.prepare();
        t.markUsage(0, true);
        t.update(); // #1=75 #0=25 #2=0
        t.fillWeights();
        assertTrue(t.isReady());
        //System.out.println(t.sum());
        assertEquals(1.6647939353962333e-15, t.sum(), 1e-25);
    }
}
