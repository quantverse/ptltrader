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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.trading.ContractExt;

/**
 * Validates a portfolio export file and prepares it for insertion.
 *
 * Import always creates a new portfolio: every uid is regenerated and the account
 * binding is cleared. That makes an import incapable of modifying an existing
 * portfolio, and in particular incapable of disturbing the runtime state of a
 * strategy that currently holds an open position.
 */
public class PortfolioImporter {

	public static class InvalidImportException extends Exception {
		private static final long serialVersionUID = 1L;

		public InvalidImportException(String message) {
			super(message);
		}
	}

	/** Fields Portfolio.updateFromJson() reads with get(), which throws if absent. */
	public static final String[] REQUIRED_PORTFOLIO_FIELDS = {
		"name", "max_pairs_open", "account_code", "account_alloc", "master_status", "pdt_rules"
	};

	/** Fields PairStrategy.updateFromJson() reads with get(), which throws if absent. */
	public static final String[] REQUIRED_STRATEGY_FIELDS = {
		"model", "ticker1", "ticker2", "trade_as_1", "trade_as_2",
		"entry_threshold", "exit_threshold", "downtick_threshold", "max_score",
		"ratio_ma_type", "ratio_ma_period", "ratio_stddev_period", "ratio_entry_mode",
		"residual_linreg_period", "neutrality", "ka_ve", "ka_usage_target",
		"ticker1margin", "ticker2margin",
		"allow_reversals", "enable_max_days", "max_days", "enable_min_pl", "min_pl",
		"enable_min_price", "min_price", "enable_min_profit_potential", "min_profit_potential",
		"entry_start_hour", "entry_start_minute", "entry_end_hour", "entry_end_minute",
		"exit_start_hour", "exit_start_minute", "exit_end_hour", "exit_end_minute",
		"timezone", "allow_positions", "status", "slot_occupation"
	};

	/**
	 * The model names PairTradingCore recognises. An unrecognised one is not fatal at
	 * runtime - core start falls through to PairTradingModelDummy with a log line -
	 * but a pair that silently never trades is worse than a rejected import file, so
	 * it is caught here instead.
	 */
	public static final Set<String> VALID_MODELS = Collections.unmodifiableSet(
		new HashSet<String>(Arrays.asList(
			PairStrategy.MODEL_RATIO, PairStrategy.MODEL_RESIDUAL,
			PairStrategy.MODEL_KALMAN_GRID, PairStrategy.MODEL_KALMAN_AUTO)));

	private PortfolioImporter() {
	}

	public static List<ObjectNode> parse(String json, ObjectMapper mapper) throws InvalidImportException {
		JsonNode root;
		try {
			root = mapper.readTree(json);
		} catch (Exception e) {
			throw new InvalidImportException("The file is not valid JSON: " + e.getMessage());
		}
		if (root == null || root.isNull()) {
			throw new InvalidImportException("The file is empty.");
		}

		List<JsonNode> portfolios = new ArrayList<JsonNode>();
		if (root.isArray()) {
			for (JsonNode n : root) portfolios.add(n);
		} else if (root.isObject()) {
			portfolios.add(root);
		} else {
			throw new InvalidImportException("Expected a portfolio object or an array of them.");
		}
		if (portfolios.isEmpty()) {
			throw new InvalidImportException("The file contains no portfolios.");
		}

		// Validate everything before returning anything: a bad file is rejected whole.
		List<ObjectNode> out = new ArrayList<ObjectNode>();
		for (JsonNode p : portfolios) {
			out.add(prepare(p));
		}
		return out;
	}

	private static ObjectNode prepare(JsonNode source) throws InvalidImportException {
		if (!source.isObject()) {
			throw new InvalidImportException("Expected a portfolio object.");
		}
		ObjectNode p = source.deepCopy();
		String pname = p.path("name").asText("(unnamed)");

		for (String f : REQUIRED_PORTFOLIO_FIELDS) {
			if (!p.has(f)) {
				throw new InvalidImportException(String.format(
						"Portfolio \"%s\" is missing the required field \"%s\".", pname, f));
			}
		}

		JsonNode strategies = p.get("strategies");
		if (strategies == null || !strategies.isArray()) {
			throw new InvalidImportException(String.format(
					"Portfolio \"%s\" has no strategies array.", pname));
		}

		p.put("uid", UUID.randomUUID().toString());
		// An imported account binding is meaningless on this machine, and binding
		// to a live account must be a deliberate act by the user.
		p.put("account_code", "");
		p.remove("features");

		for (JsonNode node : strategies) {
			if (!node.isObject()) {
				throw new InvalidImportException("Expected a strategy object.");
			}
			ObjectNode s = (ObjectNode) node;
			String label = s.path("ticker1").asText("?") + "/" + s.path("ticker2").asText("?");

			for (String f : REQUIRED_STRATEGY_FIELDS) {
				if (!s.has(f)) {
					throw new InvalidImportException(String.format(
							"Strategy %s is missing the required field \"%s\".", label, f));
				}
			}
			String model = s.get("model").asText();
			if (!VALID_MODELS.contains(model)) {
				throw new InvalidImportException(String.format(
						"Strategy %s has an unrecognized model \"%s\".", label, model));
			}
			validateSymbol(s.get("ticker1").asText());
			validateSymbol(s.get("ticker2").asText());

			s.put("uid", UUID.randomUUID().toString());
			s.remove("features");
			// Runtime state describes a position in an account this import is not
			// bound to, so it must not travel with the configuration.
			for (String f : PortfolioDocuments.STATE_FIELDS) {
				s.remove(f);
			}
		}
		return p;
	}

	private static void validateSymbol(String symbol) throws InvalidImportException {
		try {
			ContractExt.createFromGoogleSymbol(symbol, false);
		} catch (RuntimeException e) {
			throw new InvalidImportException(String.format(
					"Symbol \"%s\" is not a supported EXCHANGE:TICKER symbol.", symbol));
		}
	}
}
