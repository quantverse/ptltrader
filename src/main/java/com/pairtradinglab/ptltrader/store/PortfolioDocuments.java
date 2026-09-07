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

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;

/**
 * Converts between the observable model beans and the stored JSON document.
 *
 * The document has the same shape the PTL REST API used to return, which is what
 * Portfolio.updateFromJson() and PairStrategy.updateFromJson() parse. Runtime state
 * is deliberately absent from the stored document and is spliced back in on load
 * from the strategy_state table.
 */
public class PortfolioDocuments {

	/** Runtime fields that live in strategy_state, never in the document. */
	public static final String[] STATE_FIELDS = {
		"last_opened_datetime", "last_opened_equity", "last_model_state"
	};

	private PortfolioDocuments() {
	}

	/**
	 * Serializes a portfolio and its strategies into a configuration document.
	 * The result contains no runtime state.
	 */
	public static ObjectNode build(Portfolio p, ObjectMapper mapper) {
		ObjectNode doc = mapper.valueToTree(p);
		ArrayNode strategies = doc.putArray("strategies");
		for (PairStrategy s : p.getPairStrategies()) {
			strategies.add(mapper.<ObjectNode>valueToTree(s));
		}
		return doc;
	}

	/**
	 * Returns a copy of the document with runtime state applied to each strategy.
	 *
	 * Every strategy node gets all three state fields, explicitly null where no
	 * state row exists: PairStrategy.updateFromJson() reads them with n.get(...),
	 * which returns null for an absent field and would throw.
	 */
	public static ObjectNode splice(JsonNode document, Map<String, StrategyState> states, ObjectMapper mapper) {
		ObjectNode copy = document.deepCopy();
		JsonNode strategies = copy.get("strategies");
		if (strategies == null || !strategies.isArray()) return copy;

		for (JsonNode node : strategies) {
			ObjectNode s = (ObjectNode) node;
			StrategyState st = states.get(s.path("uid").asText());
			if (st == null) {
				s.putNull("last_opened_datetime");
				s.putNull("last_opened_equity");
				s.putNull("last_model_state");
				continue;
			}
			if (st.lastOpenedDatetime == null) s.putNull("last_opened_datetime");
			else s.put("last_opened_datetime", st.lastOpenedDatetime);

			if (st.lastOpenedEquity == null) s.putNull("last_opened_equity");
			else s.put("last_opened_equity", st.lastOpenedEquity.doubleValue());

			if (st.lastModelState == null) {
				s.putNull("last_model_state");
			} else {
				try {
					// Stored as JSON text; updateFromJson expects a nested object.
					s.set("last_model_state", mapper.readTree(st.lastModelState));
				} catch (IOException e) {
					// A corrupt state blob must not prevent the portfolio loading.
					// The strategy resumes as if it had no stored state.
					s.putNull("last_model_state");
				}
			}
		}
		return copy;
	}
}
