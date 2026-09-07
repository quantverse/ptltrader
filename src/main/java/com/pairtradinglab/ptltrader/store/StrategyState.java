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
package com.pairtradinglab.ptltrader.store;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairtradinglab.ptltrader.model.PairStrategy;

/**
 * One strategy's runtime state: what it needs in order to resume an open position
 * after a restart. Stored in its own table because it is written on a different
 * cadence from configuration, and losing it is the one persistence failure that
 * has a monetary cost.
 */
public class StrategyState {
	/**
	 * The format PairStrategy.updateFromJson() parses, in UTC. Must match exactly.
	 */
	public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

	public final String strategyUid;
	public final String lastOpenedDatetime;
	public final Double lastOpenedEquity;
	public final String lastModelState;

	public StrategyState(String strategyUid, String lastOpenedDatetime,
			Double lastOpenedEquity, String lastModelState) {
		super();
		this.strategyUid = strategyUid;
		this.lastOpenedDatetime = lastOpenedDatetime;
		this.lastOpenedEquity = lastOpenedEquity;
		this.lastModelState = lastModelState;
	}

	public static StrategyState fromStrategy(PairStrategy s, ObjectMapper mapper) {
		String dt = null;
		if (s.getLastOpened() != null) {
			DateTimeFormatter fmt = DateTimeFormat.forPattern(DATETIME_FORMAT);
			dt = s.getLastOpened().withZone(DateTimeZone.UTC).toString(fmt);
		}
		String state = null;
		if (s.getModelState() != null) {
			try {
				state = mapper.writeValueAsString(s.getModelState());
			} catch (JsonProcessingException e) {
				throw new IllegalStateException(
						"unable to serialize model state for strategy " + s.getUid(), e);
			}
		}
		return new StrategyState(s.getUid(), dt, Double.valueOf(s.getLastOpenEquity()), state);
	}
}
