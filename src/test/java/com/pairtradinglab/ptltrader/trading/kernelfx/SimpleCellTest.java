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
import static org.junit.Assert.assertTrue;

public class SimpleCellTest {
    private SimpleCell t;

    @Before
    public void setUp() throws Exception {
        t = new SimpleCell(8);
    }


    @Test
    public void testInitialState() {
        assertEquals(0, t.size);
        assertEquals(0, t.rawSum(), 0.000000001);
        assertTrue(t.isReady());
        assertEquals(0, t.getSum(), 0.000000001);
    }

    @Test
    public void testBasicFunctionality() {
        assertTrue(t.isReady());
        assertEquals(0, t.last(), 0.001);
        assertEquals(1, t.add(1));
        assertEquals(1, t.last(), 0.001);
        assertEquals(2, t.add(3));
        assertEquals(3, t.add(5));
        assertEquals(4, t.add(7));
        assertEquals(5, t.add(9));
        assertEquals(6, t.add(11));
        assertEquals(7, t.add(13));
        assertTrue(t.isReady());
        assertEquals(8, t.add(15));
        assertTrue(t.isReady());
        assertEquals(64, t.getSum(), 0.001);
        assertEquals(8, t.add(17.2), 0.001);
        assertTrue(t.isReady());
        assertEquals(80.2, t.getSum(), 0.001);

        assertEquals(8, t.add(25));
        assertTrue(t.isReady());

        assertEquals(25, t.last(), 0.001);

        assertEquals(8, t.size);
    }

    @Test
    public void testBasicFunctionalityPeriod9() {
        SimpleCell x = new SimpleCell(9);
        x.reset();
        assertTrue(x.isReady());
        assertEquals(0, x.last(), 0.001);
        assertEquals(1, x.add(1));
        assertEquals(1, x.last(), 0.001);
        assertEquals(2, x.add(3));
        assertEquals(3, x.add(5));
        assertEquals(4, x.add(7));
        assertEquals(5, x.add(9));
        assertEquals(6, x.add(11));
        assertEquals(7, x.add(13));
        assertTrue(x.isReady());
        assertEquals(8, x.add(15));
        assertTrue(x.isReady());
        assertEquals(64, x.getSum(), 0.001);
        assertEquals(9, x.add(17), 0.001);
        assertTrue(x.isReady());
        assertEquals(81, x.getSum(), 0.001);

        assertEquals(10, x.add(19));
        assertTrue(x.isReady());
        assertEquals(99, x.getSum(), 0.001);

    }
}
