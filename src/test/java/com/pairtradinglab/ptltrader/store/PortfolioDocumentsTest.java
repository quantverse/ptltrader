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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.eventbus.EventBus;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.LoggerFactoryImpl;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.PairStrategyFactoryImpl;
import com.pairtradinglab.ptltrader.model.Portfolio;
import com.pairtradinglab.ptltrader.trading.PairTradingCoreFactory;

public class PortfolioDocumentsTest {

	private static final String STRATEGY_JSON = "{"
		+ "\"uid\":\"S1\",\"model\":\"Ratio\",\"ticker1\":\"NYSE:V\",\"ticker2\":\"NYSE:MA\","
		+ "\"trade_as_1\":0,\"trade_as_2\":0,"
		+ "\"entry_threshold\":2.0,\"exit_threshold\":0.0,\"downtick_threshold\":0.0,\"max_score\":10.0,"
		+ "\"ratio_ma_type\":1,\"ratio_ma_period\":15,\"ratio_stddev_period\":15,\"ratio_entry_mode\":0,"
		+ "\"ratio_rsi_period\":10,\"ratio_rsi_threshold\":0.0,\"residual_linreg_period\":30,"
		+ "\"neutrality\":0,\"ka_ve\":0.001,\"ka_usage_target\":60.0,"
		+ "\"ticker1margin\":50.0,\"ticker2margin\":50.0,"
		+ "\"last_opened_datetime\":null,\"last_opened_equity\":null,\"last_model_state\":null,"
		+ "\"allow_reversals\":1,\"enable_max_days\":1,\"max_days\":20,"
		+ "\"enable_min_pl\":0,\"min_pl\":0.0,\"enable_min_price\":1,\"min_price\":5.0,"
		+ "\"enable_min_profit_potential\":1,\"min_profit_potential\":40.0,"
		+ "\"entry_start_hour\":9,\"entry_start_minute\":30,\"entry_end_hour\":15,\"entry_end_minute\":55,"
		+ "\"exit_start_hour\":9,\"exit_start_minute\":30,\"exit_end_hour\":15,\"exit_end_minute\":55,"
		+ "\"timezone\":\"America/New_York\",\"allow_positions\":0,\"status\":2,\"slot_occupation\":1.0"
		+ "}";

	private static final String PORTFOLIO_JSON = "{"
		+ "\"uid\":\"P1\",\"name\":\"My Portfolio\",\"account_code\":\"\","
		+ "\"max_pairs_open\":10,\"master_status\":2,\"pdt_rules\":1,\"account_alloc\":100,"
		+ "\"strategies\":[]}";

	private ObjectMapper mapper;
	private Portfolio portfolio;
	private EventBus eventBus;
	private PairTradingCoreFactory coreFactory;

	@Before
	public void setUp() throws Exception {
		mapper = new ObjectMapper();
		eventBus = mock(EventBus.class);
		coreFactory = mock(PairTradingCoreFactory.class);
		LoggerFactory lf = new LoggerFactoryImpl(new RuntimeParams(new String[] { "unittest" }));
		portfolio = new Portfolio(null, null, lf, "P1");
		portfolio.updateFromJson(mapper.readTree(PORTFOLIO_JSON));

		JsonNode sn = mapper.readTree(STRATEGY_JSON);
		PairStrategy s = new PairStrategy("S1", portfolio, "NYSE:V", "NYSE:MA", 0, 0, null, null);
		s.updateFromJson(sn);
		portfolio.addPairStrategy(s);
	}

	@Test
	public void testBuildProducesADocumentWithNestedStrategies() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);

		assertEquals("P1", doc.get("uid").asText());
		assertEquals("My Portfolio", doc.get("name").asText());
		assertEquals(10, doc.get("max_pairs_open").asInt());
		assertTrue(doc.get("strategies").isArray());
		assertEquals(1, doc.get("strategies").size());
		assertEquals("S1", doc.get("strategies").get(0).get("uid").asText());
		assertEquals("NYSE:V", doc.get("strategies").get(0).get("ticker1").asText());
	}

	@Test
	public void testBuildOmitsRuntimeState() throws Exception {
		JsonNode s = PortfolioDocuments.build(portfolio, mapper).get("strategies").get(0);
		for (String f : PortfolioDocuments.STATE_FIELDS) {
			assertFalse("build() must not emit runtime state: " + f, s.has(f));
		}
	}

	@Test
	public void testSpliceNullFillsWhenNoStateRowExists() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		ObjectNode spliced = PortfolioDocuments.splice(doc, new HashMap<String, StrategyState>(), mapper);

		JsonNode s = spliced.get("strategies").get(0);
		for (String f : PortfolioDocuments.STATE_FIELDS) {
			assertTrue("updateFromJson calls n.get(\"" + f + "\") and would NPE if absent", s.has(f));
			assertTrue("absent state must splice as null, not be omitted", s.get(f).isNull());
		}
	}

	@Test
	public void testSpliceInjectsState() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		Map<String, StrategyState> states = new HashMap<String, StrategyState>();
		states.put("S1", new StrategyState("S1", "2026-03-01 14:30:00", Double.valueOf(12345.75),
				"{\"@class\":\".PairTradingModelKalmanAutoState\",\"ve\":0.001}"));

		JsonNode s = PortfolioDocuments.splice(doc, states, mapper).get("strategies").get(0);
		assertEquals("2026-03-01 14:30:00", s.get("last_opened_datetime").asText());
		assertEquals(12345.75, s.get("last_opened_equity").asDouble(), 1e-9);
		assertTrue("model state must splice as an object, not a JSON string",
				s.get("last_model_state").isObject());
		assertEquals(".PairTradingModelKalmanAutoState",
				s.get("last_model_state").get("@class").asText());
	}

	@Test
	public void testSpliceDoesNotMutateTheInputDocument() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		Map<String, StrategyState> states = new HashMap<String, StrategyState>();
		states.put("S1", new StrategyState("S1", "2026-03-01 14:30:00", Double.valueOf(1.0), null));

		PortfolioDocuments.splice(doc, states, mapper);
		assertFalse("splice must return a copy",
				doc.get("strategies").get(0).has("last_opened_datetime"));
	}

	@Test
	public void testCorruptModelStateSplicesAsNullRatherThanFailing() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		Map<String, StrategyState> states = new HashMap<String, StrategyState>();
		states.put("S1", new StrategyState("S1", null, null, "{ this is not json"));

		List<String> corrupt = new ArrayList<String>();
		JsonNode s = PortfolioDocuments.splice(doc, states, mapper, corrupt)
				.get("strategies").get(0);
		assertTrue("a corrupt blob must not prevent the portfolio loading",
				s.get("last_model_state").isNull());
		// ...but discarding it must not be silent: losing a model state is the one
		// persistence failure with a monetary cost.
		assertEquals("the affected strategy must be reported", Arrays.asList("S1"), corrupt);
	}

	@Test
	public void testSpliceReportsNothingWhenEveryBlobParses() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		Map<String, StrategyState> states = new HashMap<String, StrategyState>();
		states.put("S1", new StrategyState("S1", null, null,
				"{\"@class\":\".PairTradingModelKalmanAutoState\",\"subModelId\":3}"));

		List<String> corrupt = new ArrayList<String>();
		PortfolioDocuments.splice(doc, states, mapper, corrupt);
		assertTrue("a healthy blob must not be reported as corrupt", corrupt.isEmpty());
	}

	@Test
	public void testSplicedDocumentIsParseableByUpdateFromJson() throws Exception {
		// The whole point: a stored document plus its state must feed updateFromJson.
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		ObjectNode spliced = PortfolioDocuments.splice(doc, new HashMap<String, StrategyState>(), mapper);

		LoggerFactory lf = new LoggerFactoryImpl(new RuntimeParams(new String[] { "unittest" }));
		PairStrategyFactoryImpl factory = new PairStrategyFactoryImpl(eventBus, coreFactory);
		Portfolio reloaded = new Portfolio(null, factory, lf, "P1");
		reloaded.updateFromJson(spliced);
		assertEquals("My Portfolio", reloaded.getName());
		assertEquals(10, reloaded.getMaxPairs());
	}
}
