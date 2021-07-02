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

/**
 * Created by carloss on 12/5/16.
 */
public class MemoryCellTest {
    private MemoryCell t;

    @Before
    public void setUp() throws Exception {
        t = new MemoryCell((short) 3);
        t.reset();

    }


    @Test
    public void testInitialState() {
        assertEquals(0, t.size);
        assertEquals(0, t.rawSum(), 0.000000001);
    }

    @Test
    public void testMem() {
        assertEquals(1, t.add(1));
        assertEquals(2, t.add(1));
        assertEquals(3, t.add(1));
        assertEquals(4, t.add(1));
        assertEquals(5, t.add(1));
        assertEquals(6, t.add(1));
        assertEquals(7, t.add(1));
        assertEquals(8, t.add(1));
        assertEquals(8, t.add(-2));
        assertEquals(-2, t.last(), 0.0001);

        assertEquals(8, t.size);
        assertEquals(5, t.rawSum(), 0.0000001);
    }
}
