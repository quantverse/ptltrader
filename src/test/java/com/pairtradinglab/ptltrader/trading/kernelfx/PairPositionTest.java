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

public class PairPositionTest {
    private PairPosition tlong;
    private PairPosition tshort;

    @Before
    public void setUp() throws Exception {
        tlong = new PairPosition(0, 111, 222, PairPosition.DIR_LONG, PairPosition.INV_NONE, 864000, 0.5, PairPosition.PEC_GE, 0.001, 24, 16);
        tshort = new PairPosition(0, 111, 222, PairPosition.DIR_SHORT, PairPosition.INV_NONE, 864000, -0.5, PairPosition.PEC_LE, 0.001, 24, 16);
    }

    @Test
    public void testLongPos() {
        PairPositionState res;
        assertEquals(PairPosition.DIR_LONG, tlong.getDir1());
        assertEquals(PairPosition.DIR_SHORT, tlong.getDir2());
        // should not close the position
        res = tlong.update(24.5, 16.5, 0, 864000);
        assertNotNull(res);
        assertTrue(res.active);
        assertEquals(0.99379166666666663, res.ret, 0.0000000001);
        assertEquals(PairPositionState.PREA_NONE, res.exitReason);
        res = tlong.update(24.5, 16.5, 0.4999, 864000);
        assertNotNull(res);
        assertEquals(1, res.ret, 0.001);
        assertTrue(res.active);
        // should close the position because it has expired
        res = tlong.update(24.5, 16.5, 0, 950400);
        assertNotNull(res);
        assertFalse(res.active);
        assertEquals(0.99899375288247871, res.ret, 0.0000000001);
        assertEquals(PairPositionState.PREA_TIMEOUT, res.exitReason);
        // inactive, dead
        res = tlong.update(24.5, 16.5, 0, 864000);
        assertNotNull(res);
        //printf("ret = %f\n", ret);
        assertEquals(1, res.ret, 0.001);
        assertFalse(res.active);
        assertEquals(PairPositionState.PREA_NONE, res.exitReason);
    }

    @Test
    public void TestLongPosInv1() {
        PairPosition tp = new PairPosition(0, 111, 222, PairPosition.DIR_LONG, PairPosition.INV_FIRST, 864000, 0.5, PairPosition.PEC_GE, 0, 24, 16);

        assertEquals(PairPosition.DIR_SHORT, tp.getDir1());
        assertEquals(PairPosition.DIR_SHORT, tp.getDir2());
    }

    @Test
    public void TestLongPosInv2() {
        PairPosition tp = new PairPosition(0, 111, 222, PairPosition.DIR_LONG, PairPosition.INV_SECOND, 864000, 0.5, PairPosition.PEC_GE, 0, 24, 16);

        assertEquals(PairPosition.DIR_LONG, tp.getDir1());
        assertEquals(PairPosition.DIR_LONG, tp.getDir2());
    }

    @Test
    public void TestLongPos2() {
        PairPositionState res;
        // should close position because of the score
        res = tlong.update(24.5, 16.5, 0.5001, 864000);
        assertNotNull(res);
        assertFalse(res.active);
        assertEquals(0.99279166666666663, res.ret, 0.0000000001);
        assertEquals(PairPositionState.PREA_CLOSED, res.exitReason);
    }

    @Test
    public void TestSpecial() {
        PairPositionState res;
        PairPosition pp = new PairPosition(0, 111, 222, PairPosition.DIR_LONG, PairPosition.INV_NONE, 8640000, 2, PairPosition.PEC_GE, 0.001, 10, 20);
        res = pp.update(10.2, 20.1, 0.5001, 864000);
        assertNotNull(res);
        assertTrue(res.active);
        assertEquals(1.0065, res.ret, 0.0001);
        assertEquals(PairPositionState.PREA_NONE, res.exitReason);
        res = pp.update(10.3, 19.7, 0.5001, 864000);
        assertNotNull(res);
        assertTrue(res.active);
        assertEquals(1.0149031296572282, res.ret, 0.0000000001);
        assertEquals(PairPositionState.PREA_NONE, res.exitReason);
        res = pp.update(10.2, 19.2, 0.5001, 864000);
        assertNotNull(res);
        assertTrue(res.active);
        assertEquals(1.0073421439060206, res.ret, 0.0000000001);
        assertEquals(PairPositionState.PREA_NONE, res.exitReason);
        res = pp.update(10.2, 19.8, 0.5001, 864000);
        assertNotNull(res);
        assertTrue(res.active);
        assertEquals(0.98542274052478118, res.ret, 0.0000000001);
        assertEquals(PairPositionState.PREA_NONE, res.exitReason);
    }

    @Test
    public void TestShortPos() {
        PairPositionState res;
        assertEquals(PairPosition.DIR_SHORT, tshort.getDir1());
        assertEquals(PairPosition.DIR_LONG, tshort.getDir2());
        // should not close the position
        res = tshort.update(24.5, 16.5, 0, 864000);
        assertNotNull(res);
        assertTrue(res.active);
        assertEquals(1.0042083333333334, res.ret, 0.0000000001);
        res = tshort.update(24.5, 16.5, -0.4999, 864000);
        assertNotNull(res);
        assertTrue(res.active);
        assertEquals(1, res.ret, 0.001);
        assertEquals(PairPositionState.PREA_NONE, res.exitReason);
        // should close the position because it has expired
        res = tshort.update(23.5, 15.5, 0, 950400);
        assertNotNull(res);
        assertFalse(res.active);
        assertEquals(0.9886311771295796, res.ret, 0.00000000001);
        assertEquals(PairPositionState.PREA_TIMEOUT, res.exitReason);
        // inactive, dead
        res = tshort.update(24.5, 16.5, 0, 864000);
        assertNotNull(res);
        //printf("ret = %f\n", ret);
        assertEquals(1, res.ret, 0.001);
        assertFalse(res.active);
        assertEquals(PairPositionState.PREA_NONE, res.exitReason);
    }

    @Test
    public void TestShortPosInv1() {
        PairPosition tp = new PairPosition(0, 111, 222, PairPosition.DIR_SHORT, PairPosition.INV_FIRST, 864000, 0.5, PairPosition.PEC_GE, 0, 24, 16);

        assertEquals(PairPosition.DIR_LONG, tp.getDir1());
        assertEquals(PairPosition.DIR_LONG, tp.getDir2());
    }
    @Test
    public void TestShortPosInv2() {
        PairPosition tp = new PairPosition(0, 111, 222, PairPosition.DIR_SHORT, PairPosition.INV_SECOND, 864000, 0.5, PairPosition.PEC_GE, 0, 24, 16);

        assertEquals(PairPosition.DIR_SHORT, tp.getDir1());
        assertEquals(PairPosition.DIR_SHORT, tp.getDir2());

    }
    @Test
    public void TestShortPos2() {
        PairPositionState res;
        // should close position because of the score
        res = tshort.update(23.5, 15.5, -0.5001, 864000);
        assertNotNull(res);
        assertFalse(res.active);
        assertEquals(0.99279166666666663, res.ret, 0.0000000001);
        assertEquals(PairPositionState.PREA_CLOSED, res.exitReason);
    }

}
