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

import org.joda.time.DateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.ib.client.Contract;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.SupportedFeatures;
import com.pairtradinglab.ptltrader.events.BeaconFlash;
import com.pairtradinglab.ptltrader.events.LogEvent;
import com.pairtradinglab.ptltrader.events.PortfolioSyncOutRequest;
import com.pairtradinglab.ptltrader.trading.events.EquityChange;

import org.apache.log4j.*;

import net.jcip.annotations.*;

import java.util.concurrent.CopyOnWriteArrayList;

@ThreadSafe
public class Portfolio extends AbstractModelObject {
	public static final int MASTER_STATUS_INACTIVE=0;
	public static final int MASTER_STATUS_MAINTAIN=1;
	public static final int MASTER_STATUS_ACTIVE=2;
	
	public static final int PDT_DISABLE=0;
	public static final int PDT_ENABLE_25K=1;
	
	// dependencies to be injected
	private final EventBus bus;
	private final PairStrategyFactory strategyFactory;
	private final LoggerFactory loggerFactory;
	
	
	@JsonIgnore
	private final String uid;
	@JsonIgnore
	private final List<PairStrategy> pairStrategies = new CopyOnWriteArrayList<PairStrategy>();
	private volatile String name="";
	
	@JsonIgnore
	private volatile int pairCount=0;
	@JsonProperty("account_code")
	private volatile String accountCode="";
	@JsonIgnore
	private volatile DateTime lastUpdate=new DateTime();
	@JsonProperty("max_pairs_open")
	private volatile int maxPairs=10;
	@JsonProperty("master_status")
	private volatile int masterStatus = MASTER_STATUS_ACTIVE;
	@JsonProperty("pdt_rules")
	private volatile int pdtEnable = PDT_ENABLE_25K;
	@JsonProperty("account_alloc")
	private volatile int accountAlloc = 100;
	@JsonIgnore
	private volatile boolean bindEnabled = true;
	@JsonIgnore
	private volatile boolean unbindEnabled = false;
	@JsonIgnore
	private volatile boolean initialized = false;
	
	@JsonIgnore
	private volatile double totalPl=0;
	@JsonIgnore
	private volatile String totalPlS="";
	
	@JsonIgnore
	private volatile boolean dirty = false;
	private volatile boolean syncOutEnabled=true;
	
	
	@JsonIgnore
	private volatile int slotUsage=0; // in percents!
	
	@JsonIgnore
	private final Logger logger;
	
	@JsonIgnore
	private final List<String> features = new CopyOnWriteArrayList<String>();
	
	
	public boolean isDirty() {
		return dirty;
	}
	
	public List<String> getFeatures() {
		return features;
	}

	protected void setDirty(boolean dirty) {
		//System.out.println("portfolio "+uid+" dirty status:"+dirty);
		this.dirty = dirty;
	}

	// AccountManager stuff
	@JsonIgnore
	private double equity = 0;
	@GuardedBy("positionLockUids")
	private HashSet<String> positionLockUids = new HashSet<String>();
	

	public Portfolio(EventBus bus, PairStrategyFactory strategyFactory, LoggerFactory loggerFactory, String uid) {
		super();
		this.bus = bus;
		this.strategyFactory = strategyFactory;
		this.loggerFactory = loggerFactory;
		this.uid = uid;
		this.logger = loggerFactory.createLogger("pfolio_"+uid);
	}

	public int getPairCount() {
		return pairCount;
	}

	public void setPairCount(int pairCount) {
		int oldVal = this.pairCount;
		this.pairCount = pairCount;
		firePropertyChange("pairCount", oldVal, this.pairCount);
		setDirty(true);
	}

	

	public String getName() {
		return name;
	}

	public void setName(String name) {
		String oldval=this.name;
		this.name = name;
		firePropertyChange("name", oldval, this.name);
		setDirty(true);
	}

	
	public synchronized void addPairStrategy(PairStrategy pairStrategy) {
		pairStrategies.add(pairStrategy);
		setPairCount(pairStrategies.size());
		firePropertyChange("pairStrategies", null, pairStrategies);
		
	}

	public synchronized void removePairStrategy(PairStrategy pairStrategy) {
		pairStrategies.remove(pairStrategy);
		setPairCount(pairStrategies.size());
		firePropertyChange("pairStrategies", null, pairStrategies);
		
	}

	public String getAccountCode() {
		return accountCode;
	}

	public synchronized void setAccountCode(String accountCode) {
		String oldval = this.accountCode;
		
		if (!oldval.isEmpty() && !accountCode.isEmpty()) {
			throw new IllegalArgumentException("Not allowed to modify account code!");
		}
		
		this.accountCode = accountCode;
		firePropertyChange("accountCode", null, accountCode);
		setBindEnabled(this.accountCode.isEmpty());
		setUnbindEnabled(!this.accountCode.isEmpty());
		
		if ((oldval.isEmpty() && !accountCode.isEmpty()) || (!oldval.isEmpty() && accountCode.isEmpty())) {
			setEquity(0);
		}
		
		
	}

	public List<PairStrategy> getPairStrategies() {
		return pairStrategies;
	}

	public DateTime getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(DateTime lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
	
	public boolean checkFeatures() {
		for(String f : features) {
			if (!SupportedFeatures.portfolioFeatures.contains(f)) {
				return false;
			}
		}
		return true;
	}
	
	
	public synchronized void initialize() {
		// initialize all pair strategies
		// we need to check features
		boolean featuresOk = true;
		for(String f : features) {
			if (!SupportedFeatures.portfolioFeatures.contains(f)) {
				featuresOk = false;
				bus.post(new LogEvent(String.format("portfolio feature %s not supported", f)));
			}
		}
		if (!featuresOk) {
			bus.post(new LogEvent(String.format("portfolio \"%s\": aborting initialization", name)));
			return;
		}
		if (!initialized) {
			bus.register(this);
		}
		for (PairStrategy p: pairStrategies) {
			p.initialize();
		}
		initialized=true;
	}
	
	public void bind(String accountCode) {
		if (accountCode.isEmpty()) throw new IllegalArgumentException("Cannot bind to empty account code");
		setEquity(0);
		setAccountCode(accountCode);
		for (PairStrategy p: pairStrategies) {
			p.bind();
		}
	}
	
	public void unbind() {
		setAccountCode("");
		setEquity(0);
		for (PairStrategy p: pairStrategies) {
			p.unbind();
		}
	}
	
	
	public int getMaxPairs() {
		return maxPairs;
	}

	public void setMaxPairs(int maxPairs) {
		int oldval = this.maxPairs;
		this.maxPairs = maxPairs;
		firePropertyChange("maxPairs", oldval, this.maxPairs);
		setDirty(true);
	}

	public int getMasterStatus() {
		return masterStatus;
	}

	public void setMasterStatus(int masterStatus) {
		int oldval = this.masterStatus;
		this.masterStatus = masterStatus;
		firePropertyChange("masterStatus", oldval, this.masterStatus);
		setDirty(true);
	}

	public int getPdtEnable() {
		return pdtEnable;
	}

	public void setPdtEnable(int pdtEnable) {
		int oldval = this.pdtEnable;
		this.pdtEnable = pdtEnable;
		firePropertyChange("pdtEnable", oldval, this.pdtEnable);
		setDirty(true);
	}

	public int getAccountAlloc() {
		return accountAlloc;
	}

	public void setAccountAlloc(int accountAlloc) {
		int oldval = this.accountAlloc;
		this.accountAlloc = accountAlloc;
		firePropertyChange("accountAlloc", oldval, this.accountAlloc);
		setDirty(true);
	}

	public boolean isBindEnabled() {
		return bindEnabled;
	}

	public void setBindEnabled(boolean bindEnabled) {
		boolean oldval = this.bindEnabled;
		this.bindEnabled = bindEnabled;
		firePropertyChange("bindEnabled", oldval, this.bindEnabled);
	}

	public boolean isUnbindEnabled() {
		return unbindEnabled;
	}

	public void setUnbindEnabled(boolean unbindEnabled) {
		boolean oldval = this.unbindEnabled;
		this.unbindEnabled = unbindEnabled;
		firePropertyChange("unbindEnabled", oldval, this.unbindEnabled);
	}

	public String getUid() {
		return uid;
	}
	
	// AccountManager methods
	protected boolean acquirePositionLock(String uid, double targetOccupation) {
		logger.debug(String.format("acquirePositionLock: uid %s targetOccupation %f equity %f maxSlots %d", uid, targetOccupation, equity, maxPairs));
		if (targetOccupation<0.01) {
			logger.debug("targetOccupation<0.01: aborted");
			return false; // occupation lower than 1% not supported
		}
		if (equity<1) {
			logger.debug("equity<1: aborted");
			return false; // equity not yet initialized
		}
		synchronized(positionLockUids) {
			double occupation = 0;
			boolean uidFound=false;
			for (PairStrategy ps: pairStrategies) {
				//logger.debug(String.format("checking strategy %s (%s / %s), total determined occupation %f, strat occupation %f", ps.getUid(), ps.getStock1(), ps.getStock2(), occupation, ps.getSlotOccupation()));
				if (positionLockUids.contains(ps.getUid()) || ps.checkPositionFailFast()) {
					occupation+=ps.getSlotOccupation();
				}
				if (!uidFound && uid.equals(ps.getUid())) uidFound=true;
			}
			if (!uidFound) return false; // unknown strategy uid
			logger.debug(String.format("final detected occupation %f, target occupation %f", occupation, targetOccupation));
			if ((occupation+targetOccupation)>(double) maxPairs) return false; //acquire failure
			positionLockUids.add(uid);
			return true; // success
		}
	}
	
	protected void releasePositionLock(String uid) {
		synchronized(positionLockUids) {
			positionLockUids.remove(uid);
		}
	}
	
	protected void resetLocks() {
		synchronized(positionLockUids) {
			positionLockUids.clear();
		}
	}
	
	
	public double allocateMargin(double occupation) { // per pair
		double allocLimit = (double) accountAlloc/100;
		return (occupation*equity*allocLimit)/(double) maxPairs;
	}

	
	public double getEquity() {
		return equity;
	}

	public void setEquity(double equity) {
		this.equity = equity;
	}
	
	public int countPositions() {
		int cnt = 0;
		for (PairStrategy ps: pairStrategies) {
			if (ps.checkPosition()) cnt++;
		}
		return cnt;
	}
	
	
	
	@Subscribe
	public void onEquityChange(EquityChange ech) {
		//System.out.println("portf onEquityChange "+ech.accountCode);
		if (accountCode.equals(ech.accountCode)) setEquity(ech.equity);
	}
	
	
	private PairStrategy findStrategyByUid(String uid) {
		for (PairStrategy s: pairStrategies) {
			if (uid.equals(s.getUid())) return s; 
		}
		return null;
	}
	
	
	public void updateFromJson(JsonNode r) {
		syncOutEnabled=false;
		setName(r.get("name").asText());
		if (accountCode.isEmpty()) {
			// not bound yet
			setMaxPairs(r.get("max_pairs_open").asInt());
			if (r.get("account_code").isNull()) {
				setAccountCode("");
			} else setAccountCode(r.get("account_code").asText());
			setAccountAlloc(r.get("account_alloc").asInt());
		}
		setMasterStatus(r.get("master_status").asInt());
		setPdtEnable(r.get("pdt_rules").asInt());
		Iterator<JsonNode> ite = r.path("features").elements();
		features.clear();
		while (ite.hasNext()) {
			String fea = ite.next().asText();
			features.add(fea);
		}
		
		// iterate through pair strategies
		Iterator<JsonNode> strategyit = r.get("strategies").elements();
		HashSet<String> strategyUids = new HashSet<String>(); 
		while(strategyit.hasNext()) {
			JsonNode rs = strategyit.next();
			PairStrategy s = findStrategyByUid(rs.get("uid").asText());
			if (s==null) {
				// add new strategy
				PairStrategy news = strategyFactory.createForPortfolio(rs, this);
				addPairStrategy(news);
			} else {
				s.updateFromJson(rs);
			}
			strategyUids.add(rs.get("uid").asText());
			
		}
		
		// if the portfolio is not bound to any account, delete all strategies not found in the json set
		if (accountCode.isEmpty()) {
			List<PairStrategy> lcopy = new ArrayList<PairStrategy>(pairStrategies);
			for (PairStrategy s:  lcopy) {
				if (!strategyUids.contains(s.getUid())) {
					s.prepareToDelete();
					removePairStrategy(s); 
				}
			}
		}
		
		setDirty(false);
		syncOutEnabled=true;
	}

	public double getTotalPl() {
		return totalPl;
	}
	

	public String getTotalPlS() {
		return totalPlS;
	}

	public void setTotalPlS(String totalPlS) {
		String oldval = this.totalPlS;
		this.totalPlS = totalPlS;
		firePropertyChange("totalPlS", oldval, this.totalPlS);
	}
	
	public void refreshTotalPl() {
		double pl=0;
		for (PairStrategy p: pairStrategies) {
			pl+=p.getPl();
		}
		setTotalPlS(String.format("%.2f", pl));
	}
	
	@Subscribe
	public void onBeaconFlash(BeaconFlash e) {
		synchronized(this) {
			if (syncOutEnabled && dirty) {
				//System.out.println("I AM DIRTY");
				setDirty(false);
				bus.post(new PortfolioSyncOutRequest(this));
				
			}
		}
		for (PairStrategy ps: pairStrategies) {
			ps.onBeaconFlash();
		}
	}

	public int getSlotUsage() {
		return slotUsage;
	}

	public void setSlotUsage(int slotUsage) {
		int oldval = this.slotUsage;
		this.slotUsage = slotUsage;
		firePropertyChange("slotUsage", oldval, this.slotUsage);
	}
	
	
	public void calculateSlotUsage() {
		double occupation = 0;
		
		for (PairStrategy ps: pairStrategies) {
			if (ps.checkPosition()) occupation+=ps.getSlotOccupation();
		}
		
		occupation = Math.round((100.0*occupation)/(double) maxPairs);
		setSlotUsage((int) occupation);
		
	}
	
	
	public void swUpdatePortfolio(Contract contract, int position,
			double marketPrice, double marketValue, double averageCost,
			double unrealizedPNL, double realizedPNL, String accountName) {
		
			// traverse pair trade strategies
			for (PairStrategy ps: pairStrategies) {
				
				ps.swUpdatePortfolio(contract, position, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL, accountName);
			}
			
	}
	
	public void swAccountDownloadEnd(String accountName) {
		// traverse pair trade strategies
		for (PairStrategy ps: pairStrategies) {
			ps.swAccountDownloadEnd(accountName);
			
		}
		// mark portfolio update flag
		setLastUpdate(DateTime.now());

	}
	
	public void stopStrategyCores() {
		for (PairStrategy ps: pairStrategies) {
			ps.stopCoreIfRunning();
		}
	}
	
}
