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

import java.io.File;
import java.io.IOException;

/**
 * Resolves the per-user directory where this application keeps its database and log.
 *
 * Historically the log was written to "$HOME/Application Data/PTLTrader" on every
 * platform, which is a Windows-only convention and wrong on Linux and macOS. The
 * database and the log now live together in the correct per-platform location.
 */
public class DataDirectory {

	private DataDirectory() {
	}

	/**
	 * Pure resolution, parameterised for testing.
	 *
	 * @param osName        value of the "os.name" system property
	 * @param userHome      value of the "user.home" system property
	 * @param localAppData  value of the LOCALAPPDATA environment variable, may be null
	 */
	public static File resolve(String osName, String userHome, String localAppData) {
		String os = osName == null ? "" : osName.toLowerCase();
		if (os.contains("windows")) {
			File base = (localAppData == null || localAppData.isEmpty())
					? new File(new File(userHome, "AppData"), "Local")
					: new File(localAppData);
			return new File(base, "PTLTrader");
		}
		if (os.contains("mac")) {
			return new File(new File(new File(userHome, "Library"), "Application Support"), "PTLTrader");
		}
		// Linux and anything else: the XDG default layout.
		return new File(new File(new File(userHome, ".local"), "share"), "ptltrader");
	}

	public static File current() {
		return resolve(System.getProperty("os.name"), System.getProperty("user.home"), System.getenv("LOCALAPPDATA"));
	}

	public static File databaseFile(String profile) {
		return new File(current(), profile + ".db");
	}

	public static File logFile(String profile) {
		return new File(current(), profile + ".log");
	}

	/**
	 * Creates the directory and any missing parents. Does nothing if it already exists.
	 */
	public static void ensureExists(File dir) throws IOException {
		if (dir.isDirectory()) return;
		if (!dir.mkdirs() && !dir.isDirectory()) {
			throw new IOException("unable to create data directory: " + dir.getAbsolutePath());
		}
	}
}
