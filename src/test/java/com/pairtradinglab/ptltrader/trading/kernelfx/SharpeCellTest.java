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

public class SharpeCellTest {

    private SharpeCell t;

    @Before
    public void setUp() throws Exception {
        t = new SharpeCell(4, 8);
        t.reset();
    }


    @Test
    public void testInitialState() {
        assertEquals(0, t.size);
        assertEquals(0, t.rawSum(), 0.000000001);
        assertFalse(t.isReady());
    }

    @Test
    public void testBasicFunctionality() {
        assertFalse(t.isReady());
        assertEquals(0, t.last(), 0.001);
        assertEquals(1, t.add(1));
        assertEquals(1, t.last(), 0.001);
        assertEquals(2, t.add(3));
        assertEquals(3, t.add(5));
        assertFalse(t.isReady());
        assertEquals(4, t.add(7));
        assertTrue(t.isReady());
        assertEquals(5, t.add(9));
        assertEquals(6, t.add(11));
        assertEquals(7, t.add(13));
        assertTrue(t.isReady());
        assertEquals(8, t.add(15));
        assertTrue(t.isReady());
        assertEquals(8, t.getMean(), 0.001);
        //EXPECT_DOUBLE_EQ(1, t.getB());
        assertEquals(3.2403703492039302, t.getStdDev(), 0.00000001);
        //assertEquals(17, t.predictNext());
        assertEquals(8, t.add(17.2), 0.001);
        assertTrue(t.isReady());
        assertEquals(10.025, t.getMean(), 0.001);
        //EXPECT_DOUBLE_EQ(0.95000000000000107, t.getB());
        assertEquals(3.255812110672236, t.getStdDev(), 0.00000001);

        assertEquals(8, t.add(25));
        assertTrue(t.isReady());
        assertEquals(3.7283122857400239, t.getStdDev(), 0.00000001);

        assertEquals(25, t.last(), 0.001);

        assertEquals(8, t.size);
    }

    @Test
    public void testBasicFunctionalityPeriod9() {
        SharpeCell x = new SharpeCell(4, 9);
        x.reset();
        assertFalse(x.isReady());
        assertEquals(0, x.last(), 0.001);
        assertEquals(1, x.add(1));
        assertEquals(1, x.last(), 0.001);
        assertEquals(2, x.add(3));
        assertEquals(3, x.add(5));
        //assertEquals(0, x.predictNext());
        assertFalse(x.isReady());
        assertEquals(4, x.add(7));
        assertTrue(x.isReady());
        assertEquals(5, x.add(9));
        assertEquals(6, x.add(11));
        assertEquals(7, x.add(13));
        assertTrue(x.isReady());
        assertEquals(8, x.add(15));
        assertTrue(x.isReady());
        assertEquals(9, x.add(17));
        assertTrue(x.isReady());
        assertEquals(9, x.getMean(), 0.001);
        //EXPECT_DOUBLE_EQ(1, x.getB());
        assertEquals(3.6514837167011076, x.getStdDev(), 0.00000001);
        //assertEquals(19, x.predictNext());
        assertEquals(10, x.add(19));
        assertTrue(x.isReady());
        assertEquals(11, x.getMean(), 0.001);
        //EXPECT_DOUBLE_EQ(1, x.getB());
        assertEquals(3.6514837167011076, x.getStdDev(), 0.00000001);
        //assertEquals(21, x.predictNext());
    }


}
