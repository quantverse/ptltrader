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


public class SimpleStrategyTest {
    private SimpleStrategy t;

    @Before
    public void setUp() throws Exception {
        t = new SimpleStrategy(11);
    }

    @Test
    public void testDefaultEntryLogic() {
        int sig = t.entryLogic(0);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(1.99);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(2.01);
        assertEquals(SimpleStrategy.SIGSHORT, sig);
        sig = t.entryLogic(-1.99);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(-2.01);
        assertEquals(SimpleStrategy.SIGLONG, sig);
    }

    @Test
    public void testLongOnlyEntryLogic() {
        t.allowShort = false;
        int sig = t.entryLogic(0);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(1.99);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(2.01);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(-1.99);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(-2.01);
        assertEquals(SimpleStrategy.SIGLONG, sig);
    }

    @Test
    public void testShortOnlyEntryLogic() {
        t.allowLong = false;
        int sig = t.entryLogic(0);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(1.99);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(2.01);
        assertEquals(SimpleStrategy.SIGSHORT, sig);
        sig = t.entryLogic(-1.99);
        assertEquals(SimpleStrategy.SIGNONE, sig);
        sig = t.entryLogic(-2.01);
        assertEquals(SimpleStrategy.SIGNONE, sig);
    }

    @Test
    public void testCreateLongPosition() {
        t.exitThreshold = -0.5;

        PairPosition q = t.getNewPosition(SimpleStrategy.SIGLONG, 0, 999, 100, 50, 86400, 0);
        assertNotNull(q);
        assertEquals(PairPosition.DIR_LONG, q.direction);
        assertEquals(PairPosition.DIR_LONG, q.getDir1());
        assertEquals(PairPosition.DIR_SHORT, q.getDir2());
        assertEquals(86400 + 20 * 86400, q.expires);
        assertEquals(11, q.modelId);
        assertEquals(999, q.stratId);
        assertEquals(PairPosition.PEC_GE, q.condition);
        assertEquals(100, q.openP1, 0.001);
        assertEquals(50, q.openP2, 0.001);
        assertEquals(0.5, q.targetScore, 0.001);
    }

    @Test
    public void testCreateShortPosition() {
        t.exitThreshold = -0.5;

        PairPosition q = t.getNewPosition(SimpleStrategy.SIGSHORT, 0, 999, 100, 50, 86400, 0);
        assertNotNull(q);
        assertEquals(PairPosition.DIR_SHORT, q.direction);
        assertEquals(PairPosition.DIR_SHORT, q.getDir1());
        assertEquals(PairPosition.DIR_LONG, q.getDir2());
        assertEquals(86400 + 20 * 86400, q.expires);
        assertEquals(11, q.modelId);
        assertEquals(999, q.stratId);
        assertEquals(PairPosition.PEC_LE, q.condition);
        assertEquals(100, q.openP1, 0.001);
        assertEquals(50, q.openP2, 0.001);
        assertEquals(-0.5, q.targetScore, 0.001);
    }
}
