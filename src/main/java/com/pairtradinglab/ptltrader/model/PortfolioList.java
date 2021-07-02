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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import com.fasterxml.jackson.databind.*;

import com.ib.client.Contract;
import net.jcip.annotations.*;
import java.util.concurrent.CopyOnWriteArrayList;

@ThreadSafe
public class PortfolioList extends AbstractModelObject {
	// dependencies to be injected
	private final PortfolioFactory portfolioFactory;
	private final List<Portfolio> portfolios = new CopyOnWriteArrayList<Portfolio>();
	
	public PortfolioList(PortfolioFactory portfolioFactory) {
		super();
		this.portfolioFactory = portfolioFactory;
	}

	public List<Portfolio> getPortfolios() {
		return portfolios;
	}
	
	public void addPortfolio(Portfolio portfolio) {
		portfolios.add(portfolio);
		firePropertyChange("portfolios", null, portfolios);
		
	}

	public void removePortfolio(Portfolio portfolio) {
		portfolios.remove(portfolio);
		firePropertyChange("portfolios", null, portfolios);
		
	}
	
	public void updateFromJson(JsonNode root) {
		Iterator<JsonNode> portfolioit = root.elements();
		HashSet<String> folioUids = new HashSet<String>();
		while ( portfolioit.hasNext() ){
	    	JsonNode r = portfolioit.next();
	    	String uid = r.get("uid").asText();
	    	Portfolio p = findPortfolioByUid(uid);
	    	if (p==null) {
	    		// create new portfolio
	    		Portfolio newp = portfolioFactory.create(r);
	    		addPortfolio(newp);
	    	} else {
	    		p.updateFromJson(r);
	    	}
	    	folioUids.add(r.get("uid").asText());
	      
	    }
		
		// delete all portfolios which are not bound to account and are not found
		List<Portfolio> plcopy = new ArrayList<Portfolio>(portfolios);
		for (Portfolio p: (List<Portfolio>) plcopy) {
			if (!folioUids.contains(p.getUid()) && p.getAccountCode().isEmpty()) {
				removePortfolio(p);
			}
		}
		
	}
	
	private Portfolio findPortfolioByUid(String uid) {
		for (Portfolio p: (List<Portfolio>) portfolios) {
			if (uid.equals(p.getUid())) return p; 
		}
		return null;
	}
	
	public void initialize() {
		// initialize all pair strategies
		for (Portfolio p: (List<Portfolio>) portfolios) {
			p.initialize();
		}
	}
	
	public void swUpdatePortfolio(Contract contract, int position,
			double marketPrice, double marketValue, double averageCost,
			double unrealizedPNL, double realizedPNL, String accountName) {
		
		// now traverse all positions in all relevant portfolios and update them
		for (Portfolio po : (List<Portfolio>) portfolios) {
			if (!po.getAccountCode().isEmpty() && accountName.equals(po.getAccountCode())) {
				po.swUpdatePortfolio(contract, position, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL, accountName);
				
				
			}
			
		}

	}
	
	public void swAccountDownloadEnd(String accountName) {
		
		// now traverse all positions in all relevant portfolios and deactivate positions which were not updated in this round
		for(Portfolio po : (List<Portfolio>) portfolios) {
			if (!po.getAccountCode().isEmpty() && accountName.equals(po.getAccountCode())) {
				po.swAccountDownloadEnd(accountName);
			}
			
		}

	}
	
	public void stopAllCores() {
		for(Portfolio po : portfolios) {
			po.stopStrategyCores();
		}
	}

	
}
