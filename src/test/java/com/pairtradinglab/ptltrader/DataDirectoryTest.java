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
package com.pairtradinglab.ptltrader;

import static org.junit.Assert.*;

import java.io.File;
import org.junit.Test;

public class DataDirectoryTest {

	@Test
	public void testResolveWindowsUsesLocalAppData() {
		File d = DataDirectory.resolve("Windows 10", "C:\\Users\\joe", "C:\\Users\\joe\\AppData\\Local");
		assertEquals(new File("C:\\Users\\joe\\AppData\\Local", "PTLTrader"), d);
	}

	@Test
	public void testResolveWindowsFallsBackWhenLocalAppDataMissing() {
		// LOCALAPPDATA is normally set, but a stripped service environment may not have it.
		File d = DataDirectory.resolve("Windows 10", "C:\\Users\\joe", null);
		assertEquals(new File(new File("C:\\Users\\joe", "AppData"), "Local").getPath(),
				d.getParentFile().getPath());
		assertEquals("PTLTrader", d.getName());
	}

	@Test
	public void testResolveLinux() {
		File d = DataDirectory.resolve("Linux", "/home/joe", null);
		assertEquals(new File("/home/joe/.local/share", "ptltrader"), d);
	}

	@Test
	public void testResolveMac() {
		File d = DataDirectory.resolve("Mac OS X", "/Users/joe", null);
		assertEquals(new File("/Users/joe/Library/Application Support", "PTLTrader"), d);
	}

	@Test
	public void testResolveUnknownOsFallsBackToLinuxLayout() {
		File d = DataDirectory.resolve("SunOS", "/export/home/joe", null);
		assertEquals(new File("/export/home/joe/.local/share", "ptltrader"), d);
	}

	@Test
	public void testDatabaseAndLogFileNamesUseProfile() {
		assertEquals("live.db", DataDirectory.databaseFile("live").getName());
		assertEquals("live.log", DataDirectory.logFile("live").getName());
		assertEquals(DataDirectory.current(), DataDirectory.databaseFile("live").getParentFile());
	}

	@Test
	public void testEnsureExistsCreatesNestedDirectories() throws Exception {
		File base = File.createTempFile("ptltrader-dd", "");
		assertTrue(base.delete());
		File nested = new File(new File(base, "a"), "b");
		DataDirectory.ensureExists(nested);
		assertTrue(nested.isDirectory());
		// Idempotent: a second call on an existing directory must not throw.
		DataDirectory.ensureExists(nested);
		assertTrue(nested.isDirectory());
		nested.delete();
		new File(base, "a").delete();
		base.delete();
	}
}
