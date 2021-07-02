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
package com.pairtradinglab.ptltrader.model;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.LoggerFactoryImpl;

public class PortfolioTest {
	private Portfolio pf;
	private RuntimeParams runtimeMock;

	@Before
	public void setUp() throws Exception {
		runtimeMock = mock(RuntimeParams.class);
		LoggerFactory lf = new LoggerFactoryImpl(runtimeMock);
		pf = new Portfolio(null, null, lf, "ABCD");
		
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testAcquirePositionLock() {
		pf.setMaxPairs(3);
		PairStrategy s1 = mock(PairStrategy.class);
		when(s1.getUid()).thenReturn("S1");
		when(s1.getSlotOccupation()).thenReturn(1.0);
		when(s1.checkPositionFailFast()).thenReturn(false);
		pf.addPairStrategy(s1);
		
		PairStrategy s2 = mock(PairStrategy.class);
		when(s2.getUid()).thenReturn("S2");
		when(s2.checkPositionFailFast()).thenReturn(false);
		when(s2.getSlotOccupation()).thenReturn(1.0);
		pf.addPairStrategy(s2);
		
		PairStrategy s3 = mock(PairStrategy.class);
		when(s3.getUid()).thenReturn("S3");
		when(s3.checkPositionFailFast()).thenReturn(false);
		when(s3.getSlotOccupation()).thenReturn(1.0);
		pf.addPairStrategy(s3);
		
		PairStrategy s4 = mock(PairStrategy.class);
		when(s4.getUid()).thenReturn("S4");
		when(s4.checkPositionFailFast()).thenReturn(true);
		when(s4.getSlotOccupation()).thenReturn(1.0);
		pf.addPairStrategy(s4);
		
		assertFalse(pf.acquirePositionLock("A", 1));
		pf.setEquity(10000);
		assertFalse(pf.acquirePositionLock("A", 1));
		pf.setEquity(0);
		assertFalse(pf.acquirePositionLock("S1", 1));
		pf.setEquity(10000);
		assertTrue(pf.acquirePositionLock("S1", 1));
		assertFalse(pf.acquirePositionLock("S2", 1.1));
		assertTrue(pf.acquirePositionLock("S2", 1));
		assertFalse(pf.acquirePositionLock("S3", 1));
		pf.releasePositionLock("S1");
		assertTrue(pf.acquirePositionLock("S3", 1));
		assertFalse(pf.acquirePositionLock("S3", 1));
		
	}

	@Test
	public void testAllocateMargin() {
		pf.setEquity(10000);
		pf.setMaxPairs(10);
		pf.setAccountAlloc(80);
		
		assertEquals(800, pf.allocateMargin(1), 0.1);
		assertEquals(1600, pf.allocateMargin(2), 0.1);
		assertEquals(400, pf.allocateMargin(0.5), 0.1);
	}

}
