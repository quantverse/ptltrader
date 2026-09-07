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

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.LoggerFactoryImpl;
import com.pairtradinglab.ptltrader.RuntimeParams;

/**
 * The trader is now the master store for strategy configuration, so every field
 * updateFromJson() reads must survive a serialize/parse round trip. A field that
 * is parsed but not written is lost on the next restart, with no error.
 */
public class SerializationRoundTripTest {

	/** Field names compared as text. */
	private static final String[] TEXT_FIELDS = {
		"uid", "model", "ticker1", "ticker2", "timezone"
	};

	/**
	 * Field names compared numerically. asDouble() coerces booleans to 1.0/0.0,
	 * so this also covers the enable_* flags, which are ints in the document and
	 * booleans on the bean.
	 */
	private static final String[] NUMERIC_FIELDS = {
		"trade_as_1", "trade_as_2",
		"entry_threshold", "exit_threshold", "downtick_threshold", "max_score",
		"ratio_ma_type", "ratio_ma_period", "ratio_stddev_period", "ratio_entry_mode",
		"ratio_rsi_period", "ratio_rsi_threshold", "residual_linreg_period",
		"neutrality", "ka_ve", "ka_usage_target",
		"ticker1margin", "ticker2margin",
		"allow_reversals",
		"enable_max_days", "max_days",
		"enable_min_pl", "min_pl",
		"enable_min_price", "min_price",
		"enable_min_profit_potential", "min_profit_potential",
		"entry_start_hour", "entry_start_minute", "entry_end_hour", "entry_end_minute",
		"exit_start_hour", "exit_start_minute", "exit_end_hour", "exit_end_minute",
		"allow_positions", "status", "slot_occupation"
	};

	private static final String STRATEGY_JSON = "{"
		+ "\"uid\":\"Sssfqqf1f11l2Jk4\",\"model\":\"Kalman-auto\","
		+ "\"ticker1\":\"NYSE:V\",\"ticker2\":\"NYSE:MA\",\"trade_as_1\":0,\"trade_as_2\":1,"
		+ "\"entry_threshold\":2.5,\"exit_threshold\":0.25,\"downtick_threshold\":0.75,\"max_score\":9.0,"
		+ "\"ratio_ma_type\":1,\"ratio_ma_period\":17,\"ratio_stddev_period\":13,\"ratio_entry_mode\":2,"
		+ "\"ratio_rsi_period\":11,\"ratio_rsi_threshold\":25.0,\"residual_linreg_period\":33,"
		+ "\"neutrality\":1,\"ka_ve\":0.002,\"ka_usage_target\":55.0,"
		+ "\"ticker1margin\":40.0,\"ticker2margin\":60.0,"
		+ "\"last_opened_datetime\":null,\"last_opened_equity\":null,\"last_model_state\":null,"
		+ "\"allow_reversals\":1,\"enable_max_days\":1,\"max_days\":22,"
		+ "\"enable_min_pl\":0,\"min_pl\":1.5,\"enable_min_price\":1,\"min_price\":6.0,"
		+ "\"enable_min_profit_potential\":1,\"min_profit_potential\":45.0,"
		+ "\"entry_start_hour\":9,\"entry_start_minute\":45,\"entry_end_hour\":15,\"entry_end_minute\":55,"
		+ "\"exit_start_hour\":10,\"exit_start_minute\":5,\"exit_end_hour\":15,\"exit_end_minute\":57,"
		+ "\"timezone\":\"America/Chicago\",\"allow_positions\":1,\"status\":1,\"slot_occupation\":0.5"
		+ "}";

	private static final String PORTFOLIO_JSON = "{"
		+ "\"uid\":\"Uuzfqqf1f11l2Jk4\",\"name\":\"My Portfolio\",\"account_code\":\"DU123456\","
		+ "\"max_pairs_open\":7,\"master_status\":1,\"pdt_rules\":0,\"account_alloc\":80,"
		+ "\"strategies\":[]}";

	private ObjectMapper mapper;
	private Portfolio portfolio;

	@Before
	public void setUp() {
		mapper = new ObjectMapper();
		LoggerFactory lf = new LoggerFactoryImpl(new RuntimeParams(new String[] { "unittest" }));
		portfolio = new Portfolio(null, null, lf, "Uuzfqqf1f11l2Jk4");
	}

	private PairStrategy buildStrategy(JsonNode n) {
		PairStrategy s = new PairStrategy(n.get("uid").asText(), portfolio,
				n.get("ticker1").asText(), n.get("ticker2").asText(),
				n.get("trade_as_1").asInt(), n.get("trade_as_2").asInt(), null, null);
		s.updateFromJson(n);
		return s;
	}

	@Test
	public void testStrategyConfigSurvivesRoundTrip() throws Exception {
		JsonNode original = mapper.readTree(STRATEGY_JSON);
		JsonNode out = mapper.valueToTree(buildStrategy(original));

		for (String f : TEXT_FIELDS) {
			assertTrue("serialized strategy is missing field: " + f, out.has(f));
			assertEquals("field " + f, original.get(f).asText(), out.get(f).asText());
		}
		for (String f : NUMERIC_FIELDS) {
			assertTrue("serialized strategy is missing field: " + f, out.has(f));
			assertEquals("field " + f, original.get(f).asDouble(), out.get(f).asDouble(), 1e-9);
		}
	}

	@Test
	public void testStrategyReparsesToAnIdenticalBean() throws Exception {
		JsonNode original = mapper.readTree(STRATEGY_JSON);
		PairStrategy first = buildStrategy(original);

		// Serialize, re-parse, and confirm the observable configuration matches.
		ObjectNode serialized = mapper.valueToTree(first);
		serialized.putNull("last_opened_datetime");
		serialized.putNull("last_opened_equity");
		serialized.putNull("last_model_state");
		PairStrategy second = buildStrategy(serialized);

		assertEquals(first.getModel(), second.getModel());
		assertEquals(first.getEntryThreshold(), second.getEntryThreshold(), 1e-9);
		assertEquals(first.getExitThreshold(), second.getExitThreshold(), 1e-9);
		assertEquals(first.getRatioMaType(), second.getRatioMaType());
		assertEquals(first.getRatioMaPeriod(), second.getRatioMaPeriod());
		assertEquals(first.getNeutrality(), second.getNeutrality());
		assertEquals(first.getKalmanAutoVe(), second.getKalmanAutoVe(), 1e-9);
		assertEquals(first.getTimezoneId(), second.getTimezoneId());
		assertEquals(first.getSlotOccupation(), second.getSlotOccupation(), 1e-9);
		assertEquals(first.getTradingStatus(), second.getTradingStatus());
		assertEquals(first.isAllowReversals(), second.isAllowReversals());
		assertEquals(first.getStock1(), second.getStock1());
		assertEquals(first.getTradeAs2(), second.getTradeAs2());
	}

	@Test
	public void testRatioMaTypeIsSerializedAsOrdinalNotName() throws Exception {
		JsonNode out = mapper.valueToTree(buildStrategy(mapper.readTree(STRATEGY_JSON)));
		assertTrue("ratio_ma_type must serialize as a numeric ordinal, because "
				+ "updateFromJson reads it with MAType.values()[node.asInt()]",
				out.get("ratio_ma_type").isNumber());
		assertEquals(1, out.get("ratio_ma_type").asInt());
	}

	@Test
	public void testRuntimeStateIsNotPartOfTheDocument() throws Exception {
		JsonNode out = mapper.valueToTree(buildStrategy(mapper.readTree(STRATEGY_JSON)));
		assertFalse("runtime state belongs to strategy_state, not the document",
				out.has("last_opened_datetime"));
		assertFalse(out.has("last_opened_equity"));
		assertFalse(out.has("last_model_state"));
	}

	@Test
	public void testDerivedFieldsAreNotSerialized() throws Exception {
		JsonNode out = mapper.valueToTree(buildStrategy(mapper.readTree(STRATEGY_JSON)));
		for (String f : new String[] { "positions", "core", "parent", "portfolio", "dirty",
				"initialized", "coreStatus", "daysRemaining", "profitPotential",
				"zscoreBid", "zscoreAsk", "closeable", "openable", "deletable", "resumable",
				"features", "syncOutEnabled", "modelState", "lastOpened" }) {
			assertFalse("derived/runtime field must not be serialized: " + f, out.has(f));
		}
	}

	@Test
	public void testPortfolioConfigSurvivesRoundTrip() throws Exception {
		JsonNode original = mapper.readTree(PORTFOLIO_JSON);
		portfolio.updateFromJson(original);
		JsonNode out = mapper.valueToTree(portfolio);

		assertEquals("Uuzfqqf1f11l2Jk4", out.get("uid").asText());
		assertEquals("My Portfolio", out.get("name").asText());
		assertEquals("DU123456", out.get("account_code").asText());
		assertEquals(7, out.get("max_pairs_open").asInt());
		assertEquals(1, out.get("master_status").asInt());
		assertEquals(0, out.get("pdt_rules").asInt());
		assertEquals(80, out.get("account_alloc").asInt());
		assertFalse("strategies are added by PortfolioDocuments, not by bean serialization",
				out.has("strategies"));
		assertFalse(out.has("features"));
		assertFalse(out.has("pairStrategies"));
	}
}
