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
import static org.junit.Assert.*;

import org.ejml.simple.SimpleMatrix;
import org.junit.Before;
import org.junit.Test;


public class SubModelKalmanTest {
    private SubModelKalman t;

    @Before
    public void setUp() throws Exception {
        t = new SubModelKalman();
        t.init();

    }


    @Test
    public void testNotReadyState() {
        assertFalse(t.isReady());
        assertEquals(0, t.getPos());
        assertEquals(1, t.getLookback());
    }

    @Test
    public void testReadyState1() {
        int i;
        double p1=0, p2=0;
        for (i=0; i<20; i++) {
            p1 = 10+0.2*i+0.2*Math.sin(2*i);
            p2 = 5+0.1*i+0.1*Math.cos(2.1*i);
            //System.out.printf("%d %f %f\n", i, p1, p2);
            t.update(p1, p2);
        }
        assertTrue(t.isReady());
        assertEquals(i, t.getPos());
        SimpleMatrix b = t.getBetaVector();
        assertEquals(0.490806423362324, b.get(0, 0), 0.00000001);
        assertEquals(0.051850568750981339, b.get(0, 1), 0.00000001);

        double e = t.getE();
        assertEquals(0.27710924803989823, e, 0.00000001);
        double sq = t.getSq();
        assertEquals(0.14603949931646956, sq, 0.00000001);

        assertEquals(e/sq, t.evaluate(p1, p2), 0.00000001);
        assertEquals(e/sq, t.evaluate(p1, p2), 0.00000001); // test again (should be the same to make sure it does not update)
    }

    @Test
    public void testReadyState1Trans1() {
        t.transform = 1;
        int i;
        double p1=0, p2=0;
        for (i=0; i<20; i++) {
            p1 = 10+0.2*i+0.2*Math.sin(2*i);
            p2 = 5+0.1*i+0.1*Math.cos(2.1*i);
            //System.out.printf("%d %f %f\n", i, p1, p2);
            t.update(p1, p2);
        }
        assertTrue(t.isReady());
        assertEquals(i, t.getPos());
        SimpleMatrix b = t.getBetaVector();
        assertEquals(0.094660770322177498, b.get(0, 0), 0.00000001);
        assertEquals(0.0011495341693955664, b.get(0, 1), 0.00000001);

        double e = t.getE();
        assertEquals(-0.16939220772354258, e, 0.00000001);
        double sq = t.getSq();
        assertEquals(0.72398175751774163, sq, 0.00000001);

        assertEquals(e/sq, t.evaluate(p1, p2), 0.00000001);
        assertEquals(e/sq, t.evaluate(p1, p2), 0.00000001); // test again (should be the same to make sure it does not update)
    }
}
