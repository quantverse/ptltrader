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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ib.client.*;

import java.util.Iterator;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.eventbus.*;
import com.pairtradinglab.ptltrader.events.StrategySyncOutRequest;
import com.pairtradinglab.ptltrader.trading.*;
import com.pairtradinglab.ptltrader.trading.events.PortfolioUpdate;
import com.tictactec.ta.lib.MAType;

import net.jcip.annotations.*;

import java.util.concurrent.CopyOnWriteArrayList;

@ThreadSafe
public class PairStrategy extends AbstractModelObject {
	public static final String STATUS_LONG = "long";
	public static final String STATUS_SHORT = "short";
	public static final String STATUS_ACTIVE = "active";
	public static final String STATUS_INACTIVE = "inactive";
	public static final String STATUS_NONE = "";
	
	public static final String MODEL_RATIO = "Ratio";
	public static final String MODEL_RESIDUAL = "Residual";
	public static final String MODEL_KALMAN_GRID = "Kalman-grid-v2";
    public static final String MODEL_KALMAN_AUTO = "Kalman-auto";
	
	public static final int TRADING_STATUS_INACTIVE = 0;
	public static final int TRADING_STATUS_MAINTAIN = 1;
	public static final int TRADING_STATUS_ACTIVE = 2;
	
	public static final int ALLOW_POSITIONS_BOTH = 0;
	public static final int ALLOW_POSITIONS_LONG = 1;
	public static final int ALLOW_POSITIONS_SHORT = 2;

	public static final int NEUTRALITY_DOLLAR = 0;
	public static final int NEUTRALITY_BETA = 1;

	public static final int TRADE_AS_STK = 0;
	public static final int TRADE_AS_CFD = 1;
	
	// injected dependencies
	private final EventBus bus;
	private final PairTradingCoreFactory coreFactory;
	
	@JsonIgnore
	private final String uid;
	@JsonIgnore
	private final List<Position> positions = new CopyOnWriteArrayList<Position>();
	@JsonIgnore
	private volatile String stock1;
	@JsonIgnore
	private volatile String stock2;
	@JsonIgnore
	private volatile String stockDisp1;
	@JsonIgnore
	private volatile String stockDisp2;
	@JsonIgnore
	private volatile int tradeAs1;
	@JsonIgnore
	private volatile int tradeAs2;
	@JsonIgnore
	private volatile double zscoreBid=0;
	@JsonIgnore
	private volatile String zscoreBidS="";
    @JsonIgnore
    private volatile double zscoreAsk=0;
    @JsonIgnore
    private volatile String zscoreAskS="";
	@JsonIgnore
	private volatile double rsi=0;
	@JsonIgnore
	private volatile String rsiS="";
	@JsonIgnore
	private volatile double pl=0;
	@JsonIgnore
	private volatile String plS="";
	@JsonIgnore
	private volatile DateTime lastOpened;
	@JsonIgnore
	private volatile AbstractPairTradingModelState modelState;
	@JsonIgnore
	private volatile String lastOpenedS="";
	@JsonIgnore
	private volatile int daysRemaining=0;
	@JsonIgnore
	private volatile String status = STATUS_NONE;
	@JsonIgnore
	private volatile CoreStatus coreStatus=CoreStatus.NONE;
	@JsonIgnore
	private volatile double profitPotential=0;
	@JsonIgnore
	private volatile String profitPotentialS="";
	@JsonIgnore
	private volatile double lastOpenEquity = 0;
	
	// model settings
	@JsonIgnore
	private volatile String model=MODEL_RATIO;
	@JsonIgnore
	private volatile double entryThreshold=2;
	@JsonIgnore
	private volatile double exitThreshold=0;
    @JsonIgnore
    private volatile double downtickThreshold=0;
	@JsonIgnore
	private volatile double maxEntryScore=6;
	@JsonIgnore
	private volatile int ratioMaPeriod=15;
	@JsonIgnore
	private volatile MAType ratioMaType=MAType.Sma;
	@JsonIgnore
	private volatile int ratioStdDevPeriod=15;
	@JsonIgnore
	private volatile int residualLinRegPeriod=30;
	@JsonIgnore
	private volatile int entryMode=PairTradingModel.ENTRY_MODE_SIMPLE;
	@JsonIgnore
	private volatile int ratioRsiPeriod=10;
	@JsonIgnore
	private volatile double ratioRsiThreshold=0;
	@JsonIgnore
	private volatile double kalmanAutoVe = 0.001;
	@JsonIgnore
	private volatile double kalmanAutoUsageTarget = 60;
	
	// extra rules
	@JsonProperty("max_days")
	private volatile int maxDays=20;
	@JsonProperty("enable_max_days")
	private volatile boolean maxDaysEnabled=true;
	@JsonProperty("min_pl")
	private volatile double minPlToClose=0;
	@JsonProperty("enable_min_pl")
	private volatile boolean minPlToCloseEnabled=true;
	@JsonProperty("min_price")
	private volatile double minPrice=5;
	@JsonProperty("enable_min_price")
	private volatile boolean minPriceEnabled=true;
	@JsonProperty("min_profit_potential")
	private volatile double minExpectation=30; //in absolute profit
	@JsonProperty("enable_min_profit_potential")
	private volatile boolean minExpectationEnabled=true;

	@JsonProperty("allow_reversals")
	private volatile boolean allowReversals = false;
	
	// time settings
	@JsonProperty("entry_start_hour")
	private volatile int entryStartHour = 10;
	@JsonProperty("entry_start_minute")
	private volatile int entryStartMinute = 0;
	@JsonProperty("entry_end_hour")
	private volatile int entryEndHour = 15;
	@JsonProperty("entry_end_minute")
	private volatile int entryEndMinute = 50;
	@JsonProperty("exit_start_hour")
	private volatile int exitStartHour = 10;
	@JsonProperty("exit_start_minute")
	private volatile int exitStartMinute = 0;
	@JsonProperty("exit_end_hour")
	private volatile int exitEndHour = 15;
	@JsonProperty("exit_end_minute")
	private volatile int exitEndMinute = 58;
	@JsonIgnore
	private volatile String timezoneId = "America/New_York";
	@JsonIgnore
	private volatile DateTimeZone timezone = DateTimeZone.forID("America/New_York");
	
	// margin settings
	@JsonProperty("ticker1margin")
	private double marginPerc1 = 50;
	@JsonProperty("ticker2margin")
	private double marginPerc2 = 50;
	@JsonProperty("slot_occupation")
	private double slotOccupation = 1.0;

	// neutrality
	@JsonIgnore
	private volatile int neutrality = NEUTRALITY_DOLLAR;
	
	// maintenance
	@JsonProperty("allow_positions")
	private volatile int allowPositions = ALLOW_POSITIONS_BOTH;
	@JsonProperty("status")
	private volatile int tradingStatus = TRADING_STATUS_ACTIVE;
	@JsonIgnore
	private volatile boolean closeable=false;
	@JsonIgnore
	private volatile boolean openable=false;
	@JsonIgnore
	private volatile boolean deletable=true;
	@JsonIgnore
	private volatile boolean resumable=false;
	
	@JsonIgnore
	private final Portfolio parent;
	@JsonIgnore
	private volatile PairTradingCore core=null;
	@JsonIgnore
	private volatile boolean initialized=false;
	
	@JsonIgnore
	private volatile boolean dirty = false;
	private volatile boolean syncOutEnabled=true;
	
	@JsonIgnore
	private final List<String> features = new CopyOnWriteArrayList<String>();
	
	public boolean isDirty() {
		return dirty;
	}

	public List<String> getFeatures() {
		return features;
	}

	protected void setDirty(boolean dirty) {
		//System.out.println("strategy "+uid+" dirty status:"+dirty);
		this.dirty = dirty;
	}
	
	public PairStrategy(String uid, Portfolio parent, String stock1, String stock2, int tradeAs1, int tradeAs2, EventBus bus, PairTradingCoreFactory coreFactory) {
		this.uid = uid;
		this.parent = parent;
		this.stock1 = stock1;
		this.stock2 = stock2;
		this.tradeAs1 = tradeAs1;
		this.tradeAs2 = tradeAs2;
		this.bus = bus;
		this.coreFactory = coreFactory;
		positions.add(new Position(stock1, tradeAs1 == TRADE_AS_CFD));
		positions.add(new Position(stock2, tradeAs2 == TRADE_AS_CFD));

		this.stockDisp1 = getDisplaySymbol(stock1, tradeAs1);
        this.stockDisp2 = getDisplaySymbol(stock2, tradeAs2);
	}
	
	
	public String getStock1() {
		return stock1;
	}
	public void setStock1(String stock1) {
		String oldval=this.stock1;
		this.stock1 = stock1;
		firePropertyChange("stock1", oldval, this.stock1);
		setStockDisp1(getDisplaySymbol(stock1, tradeAs1));
	}

	private static String getDisplaySymbol(String stock, int tradeAs) {
		if (tradeAs == TRADE_AS_CFD) {
            return String.format("(CFD) %s", stock);
		} else {
			return stock;
		}
	}


	public String getStock2() {
		return stock2;
	}
	public void setStock2(String stock2) {
		String oldval=this.stock2;
		this.stock2 = stock2;
		firePropertyChange("stock2", oldval, this.stock2);
        setStockDisp2(getDisplaySymbol(stock2, tradeAs2));
	}

	public String getStockDisp1() {
		return stockDisp1;
	}

	public void setStockDisp1(String stockDisp1) {
		String oldval=this.stockDisp1;
		this.stockDisp1 = stockDisp1;
		firePropertyChange("stockDisp1", oldval, this.stockDisp1);
	}

	public String getStockDisp2() {
		return stockDisp2;
	}

	public void setStockDisp2(String stockDisp2) {
		String oldval=this.stockDisp2;
		this.stockDisp2 = stockDisp2;
		firePropertyChange("stockDisp2", oldval, this.stockDisp2);
	}

	public double getZscoreBid() {
		return zscoreBid;
	}
	public void setZscoreBid(double zscoreBid) {
		double oldval=this.zscoreBid;
		this.zscoreBid = zscoreBid;
		setZscoreBidS(String.format("%.3f", this.zscoreBid));
		firePropertyChange("zscoreBid", oldval, this.zscoreBid);
	}

    public double getZscoreAsk() {
        return zscoreAsk;
    }
    public void setZscoreAsk(double zscoreAsk) {
        double oldval=this.zscoreAsk;
        this.zscoreAsk = zscoreAsk;
        setZscoreAskS(String.format("%.3f", this.zscoreAsk));
        firePropertyChange("zscoreAsk", oldval, this.zscoreAsk);
    }
	
	
	public double getRsi() {
		return rsi;
	}

	public void setRsi(double rsi) {
		double oldval = this.rsi;
		this.rsi = rsi;
		setRsiS(String.format("%.3f", this.rsi));
		firePropertyChange("rsi", oldval, this.rsi);
	}
	

	public String getRsiS() {
		return rsiS;
	}

	public void setRsiS(String rsiS) {
		String oldval = this.rsiS;
		this.rsiS = rsiS;
		firePropertyChange("rsiS", oldval, this.rsiS);
	}

	public double getPl() {
		return pl;
	}
	public void setPl(double pl) {
		double oldval=this.pl;
		this.pl = pl;
		setPlS(String.format("%.2f", this.pl));
		firePropertyChange("pl", oldval, this.pl);
		if (Math.abs(pl-oldval)>=0.01) parent.refreshTotalPl();
	}
	
	
	
	
	public DateTime getLastOpened() {
		return lastOpened;
	}
	public void setLastOpened(DateTime lastOpened) {
		DateTime oldval=this.lastOpened;
		this.lastOpened = lastOpened;
		firePropertyChange("lastOpened", oldval, this.lastOpened);
		if (this.lastOpened==null) {
			setLastOpenedS("");
		} else {
			DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm:ss a");
			setLastOpenedS(lastOpened.toString(fmt));
		}
		updateDaysRemaining();
	}

	public AbstractPairTradingModelState getModelState() {
		return modelState;
	}

	public void setModelState(AbstractPairTradingModelState modelState) {
		this.modelState = modelState;
	}

	public int getDaysRemaining() {
		return daysRemaining;
	}
	public void setDaysRemaining(int daysRemaining) {
		int oldval=this.daysRemaining;
		this.daysRemaining = daysRemaining;
		firePropertyChange("daysRemaining", oldval, this.daysRemaining);
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		String oldval=this.status;
		this.status = status;
		firePropertyChange("status", oldval, this.status);
		if (coreStatus==CoreStatus.PENDING || coreStatus==CoreStatus.NOT_READY || coreStatus==CoreStatus.NOT_CONNECTED || coreStatus==CoreStatus.TRANSIENT) {
			setCloseable(false);
			setOpenable(false);
		}
		//setCloseable((STATUS_LONG.equals(this.status) || STATUS_SHORT.equals(this.status)) && (coreStatus==CoreStatus.EXIT_SIGNAL || coreStatus==CoreStatus.EXIT_WAIT_PL_RULE || coreStatus==CoreStatus.EXIT_PDT));
		//setOpenable((STATUS_ACTIVE.equals(this.status) || STATUS_INACTIVE.equals(this.status)) && (coreStatus==CoreStatus.ENTRY_PROFIT_POTENTIAL || coreStatus==CoreStatus.ENTRY_MIN_PRICE || coreStatus==CoreStatus.ENTRY_PDT || coreStatus==CoreStatus.ENTRY_SIGNAL || coreStatus==CoreStatus.ENTRY_SHORTABILITY));
		setDeletable((STATUS_ACTIVE.equals(this.status) || STATUS_INACTIVE.equals(this.status) || STATUS_NONE.equals(this.status)) && coreStatus!=CoreStatus.PENDING && coreStatus!=CoreStatus.NOT_READY);
		updateDaysRemaining();
		if (!status.equals(oldval)) parent.calculateSlotUsage();
		
	}

	public List<Position> getPositions() {
		return positions;
	}
	
	public void addPosition(Position position) {
		positions.add(position);
		firePropertyChange("positions", null, positions);
		
	}

	public void removePosition(Position position) {
		positions.remove(position);
		firePropertyChange("positions", null, positions);
		
	}
	
	public synchronized void initialize() {
		// register all position objects to event bus
		if (initialized) return;
		for (Position p: (List<Position>) positions) {
			bus.register(p);
		}
		
		bind();
		initialized=true;
	}
	
	protected void bind() {
		// should start now
		if (!parent.getAccountCode().isEmpty()) {
			if (core==null) core = coreFactory.createForStrategy(this);
			core.start();
		}
	}
	
	public void prepareToDelete() {
		if (core!=null) core.stop();
		for (Position p: (List<Position>) positions) {
			bus.unregister(p);
		}
		
	}
	
	protected void unbind() {
		setLastOpened(null);
		setLastOpenEquity(0);
		core.stop();
		core=null; // delete object
	
	}

	public CoreStatus getCoreStatus() {
		return coreStatus;
	}

	public void setCoreStatus(CoreStatus coreStatus) {
		CoreStatus oldval=this.coreStatus;
		this.coreStatus = coreStatus;
		firePropertyChange("coreStatus", oldval, this.coreStatus);
		if (coreStatus==CoreStatus.PENDING || coreStatus==CoreStatus.NOT_READY || coreStatus==CoreStatus.NOT_CONNECTED || coreStatus==CoreStatus.TRANSIENT) {
			setCloseable(false);
			setOpenable(false);
		}
		//setCloseable((STATUS_LONG.equals(status) || STATUS_SHORT.equals(status)) && (this.coreStatus==CoreStatus.EXIT_SIGNAL || this.coreStatus==CoreStatus.EXIT_WAIT_PL_RULE || this.coreStatus==CoreStatus.EXIT_PDT));
		//setOpenable((STATUS_ACTIVE.equals(this.status) || STATUS_INACTIVE.equals(this.status)) && (coreStatus==CoreStatus.ENTRY_PROFIT_POTENTIAL || coreStatus==CoreStatus.ENTRY_MIN_PRICE || coreStatus==CoreStatus.ENTRY_PDT || coreStatus==CoreStatus.ENTRY_SIGNAL || coreStatus==CoreStatus.ENTRY_SHORTABILITY));
		setDeletable((STATUS_ACTIVE.equals(this.status) || STATUS_INACTIVE.equals(this.status) || STATUS_NONE.equals(this.status)) && coreStatus!=CoreStatus.PENDING && coreStatus!=CoreStatus.NOT_READY);
		setResumable(this.coreStatus==CoreStatus.BLOCKED);
	}

	public String getZscoreBidS() {
		return zscoreBidS;
	}

	public void setZscoreBidS(String zscoreBidS) {
		String oldval = this.zscoreBidS;
		this.zscoreBidS = zscoreBidS;
		firePropertyChange("zscoreBidS", oldval, this.zscoreBidS);
	}

    public String getZscoreAskS() {
        return zscoreAskS;
    }

    public void setZscoreAskS(String zscoreAskS) {
        String oldval = this.zscoreAskS;
        this.zscoreAskS = zscoreAskS;
        firePropertyChange("zscoreAskS", oldval, this.zscoreAskS);
    }

	public String getPlS() {
		return plS;
	}

	public void setPlS(String plS) {
		String oldval=this.plS;
		this.plS = plS;
		firePropertyChange("plS", oldval, this.plS);
	}

	
	
	
	// model settings getters/setters
	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		String oldval=this.model;
		this.model = model;
		firePropertyChange("model", oldval, this.model);
	}

	public double getEntryThreshold() {
		return entryThreshold;
	}

	public void setEntryThreshold(double entryThreshold) {
		double oldval = this.entryThreshold;
		this.entryThreshold = entryThreshold;
		firePropertyChange("entryThreshold", oldval, this.entryThreshold);
	}

	public double getExitThreshold() {
		return exitThreshold;
	}

	public void setExitThreshold(double exitThreshold) {
		double oldval = this.exitThreshold;
		this.exitThreshold = exitThreshold;
		firePropertyChange("exitThreshold", oldval, this.exitThreshold);
	}

    public double getDowntickThreshold() {
        return downtickThreshold;
    }

    public void setDowntickThreshold(double downtickThreshold) {
        double oldval = this.downtickThreshold;
        this.downtickThreshold = downtickThreshold;
        firePropertyChange("downtickThreshold", oldval, this.downtickThreshold);
    }

    public double getMaxEntryScore() {
		return maxEntryScore;
	}

	public void setMaxEntryScore(double maxEntryScore) {
		double oldval = this.maxEntryScore;
		this.maxEntryScore = maxEntryScore;
		firePropertyChange("maxEntryScore", oldval, this.maxEntryScore);
	}

	public int getRatioMaPeriod() {
		return ratioMaPeriod;
	}

	public void setRatioMaPeriod(int ratioMaPeriod) {
		int oldval = this.ratioMaPeriod;
		this.ratioMaPeriod = ratioMaPeriod;
		firePropertyChange("ratioMaPeriod", oldval, this.ratioMaPeriod);
	}

	public MAType getRatioMaType() {
		return ratioMaType;
	}

	public void setRatioMaType(MAType ratioMaType) {
		//MAType oldval = this.ratioMaType;
		this.ratioMaType = ratioMaType;
		//firePropertyChange("ratioMaType", oldval, this.ratioMaType);
	}

	public int getRatioStdDevPeriod() {
		return ratioStdDevPeriod;
	}

	public void setRatioStdDevPeriod(int ratioStdDevPeriod) {
		int oldval = this.ratioStdDevPeriod;
		this.ratioStdDevPeriod = ratioStdDevPeriod;
		firePropertyChange("ratioStdDevPeriod", oldval, this.ratioStdDevPeriod);
	}

	public int getResidualLinRegPeriod() {
		return residualLinRegPeriod;
	}

	public void setResidualLinRegPeriod(int residualLinRegPeriod) {
		int oldval = this.residualLinRegPeriod;
		this.residualLinRegPeriod = residualLinRegPeriod;
		firePropertyChange("residualLinRegPeriod", oldval, this.residualLinRegPeriod);
	}

	public double getKalmanAutoVe() {
		return kalmanAutoVe;
	}

	public void setKalmanAutoVe(double kalmanAutoVe) {
		double oldval = this.kalmanAutoVe;
		this.kalmanAutoVe = kalmanAutoVe;
		firePropertyChange("kalmanAutoVe", oldval, this.kalmanAutoVe);
	}

	public double getKalmanAutoUsageTarget() {
		return kalmanAutoUsageTarget;
	}

	public void setKalmanAutoUsageTarget(double kalmanAutoUsageTarget) {
		double oldval = this.kalmanAutoUsageTarget;
		this.kalmanAutoUsageTarget = kalmanAutoUsageTarget;
		firePropertyChange("kalmanAutoUsageTarget", oldval, this.kalmanAutoUsageTarget);
	}

	public int getEntryMode() {
		return entryMode;
	}

	public void setEntryMode(int entryMode) {
		int oldval = this.entryMode;
		this.entryMode = entryMode;
		firePropertyChange("entryMode", oldval, this.entryMode);
	}

	public int getMaxDays() {
		return maxDays;
	}

	public void setMaxDays(int maxDays) {
		int oldval = this.maxDays;
		this.maxDays = maxDays;
		firePropertyChange("maxDays", oldval, this.maxDays);
		updateDaysRemaining();
		setDirty(true);
	}

	public boolean isMaxDaysEnabled() {
		return maxDaysEnabled;
	}

	public void setMaxDaysEnabled(boolean maxDaysEnabled) {
		boolean oldval = this.maxDaysEnabled;
		this.maxDaysEnabled = maxDaysEnabled;
		firePropertyChange("maxDaysEnabled", oldval, this.maxDaysEnabled);
		updateDaysRemaining();
		setDirty(true);
	}

	public double getMinPlToClose() {
		return minPlToClose;
	}

	public void setMinPlToClose(double minPlToClose) {
		double oldval = this.minPlToClose;
		this.minPlToClose = minPlToClose;
		firePropertyChange("minPlToClose", oldval, this.minPlToClose);
		setDirty(true);
	}

	public boolean isMinPlToCloseEnabled() {
		return minPlToCloseEnabled;
	}

	public void setMinPlToCloseEnabled(boolean minPlToCloseEnabled) {
		boolean oldval = this.minPlToCloseEnabled;
		this.minPlToCloseEnabled = minPlToCloseEnabled;
		firePropertyChange("minPlToCloseEnabled", oldval, this.minPlToCloseEnabled);
		setDirty(true);
	}

	public double getMinPrice() {
		return minPrice;
	}

	public void setMinPrice(double minPrice) {
		double oldval = this.minPrice;
		this.minPrice = minPrice;
		firePropertyChange("minPrice", oldval, this.minPrice);
		setDirty(true);
	}

	public boolean isMinPriceEnabled() {
		return minPriceEnabled;
	}

	public void setMinPriceEnabled(boolean minPriceEnabled) {
		boolean oldval = this.minPriceEnabled;
		this.minPriceEnabled = minPriceEnabled;
		firePropertyChange("minPriceEnabled", oldval, this.minPriceEnabled);
		setDirty(true);
	}

	public double getMinExpectation() {
		return minExpectation;
	}

	public void setMinExpectation(double minExpectation) {
		double oldval = this.minExpectation;
		this.minExpectation = minExpectation;
		firePropertyChange("minExpectation", oldval, this.minExpectation);
		setDirty(true);
	}

	public boolean isMinExpectationEnabled() {
		return minExpectationEnabled;
	}

	public void setMinExpectationEnabled(boolean minExpectationEnabled) {
		boolean oldval = this.minExpectationEnabled;
		this.minExpectationEnabled = minExpectationEnabled;
		firePropertyChange("minExpectationEnabled", oldval, this.minExpectationEnabled);
		setDirty(true);
	}

	public int getEntryStartHour() {
		return entryStartHour;
	}

	public void setEntryStartHour(int entryStartHour) {
		int oldval = this.entryStartHour;
		this.entryStartHour = entryStartHour;
		firePropertyChange("entryStartHour", oldval, this.entryStartHour);
		setDirty(true);
	}

	public int getEntryStartMinute() {
		return entryStartMinute;
	}

	public void setEntryStartMinute(int entryStartMinute) {
		int oldval = this.entryStartMinute;
		this.entryStartMinute = entryStartMinute;
		firePropertyChange("entryStartMinute", oldval, this.entryStartMinute);
		setDirty(true);
	}

	public int getEntryEndHour() {
		return entryEndHour;
	}

	public void setEntryEndHour(int entryEndHour) {
		int oldval = this.entryEndHour;
		this.entryEndHour = entryEndHour;
		firePropertyChange("entryEndHour", oldval, this.entryEndHour);
		setDirty(true);
	}

	public int getEntryEndMinute() {
		return entryEndMinute;
	}

	public void setEntryEndMinute(int entryEndMinute) {
		int oldval = this.entryEndMinute;
		this.entryEndMinute = entryEndMinute;
		firePropertyChange("entryEndMinute", oldval, this.entryEndMinute);
		setDirty(true);
	}

	public int getExitStartHour() {
		return exitStartHour;
	}

	public void setExitStartHour(int exitStartHour) {
		int oldval = this.exitStartHour;
		this.exitStartHour = exitStartHour;
		firePropertyChange("exitStartHour", oldval, this.exitStartHour);
		setDirty(true);
	}

	public int getExitStartMinute() {
		return exitStartMinute;
	}

	public void setExitStartMinute(int exitStartMinute) {
		int oldval = this.exitStartMinute;
		this.exitStartMinute = exitStartMinute;
		firePropertyChange("exitStartMinute", oldval, this.exitStartMinute);
		setDirty(true);
	}

	public int getExitEndHour() {
		return exitEndHour;
	}

	public void setExitEndHour(int exitEndHour) {
		int oldval = this.exitEndHour;
		this.exitEndHour = exitEndHour;
		firePropertyChange("exitEndHour", oldval, this.exitEndHour);
		setDirty(true);
	}

	public int getExitEndMinute() {
		return exitEndMinute;
	}

	public void setExitEndMinute(int exitEndMinute) {
		int oldval = this.exitEndMinute;
		this.exitEndMinute = exitEndMinute;
		firePropertyChange("exitEndMinute", oldval, this.exitEndMinute);
		setDirty(true);
	}

	public String getTimezoneId() {
		return timezoneId;
	}

	public void setTimezoneId(String timezoneId) {
		String oldval = this.timezoneId;
		this.timezone = DateTimeZone.forID(timezoneId);
		this.timezoneId = timezoneId;
		firePropertyChange("timezoneId", oldval, this.timezoneId);
		
	}

	public double getMarginPerc1() {
		return marginPerc1;
	}

	public void setMarginPerc1(double marginPerc1) {
		double oldval = this.marginPerc1;
		this.marginPerc1 = marginPerc1;
		firePropertyChange("marginPerc1", oldval, this.marginPerc1);
		setDirty(true);
	}

	public double getMarginPerc2() {
		return marginPerc2;
	}

	public void setMarginPerc2(double marginPerc2) {
		double oldval = this.marginPerc2;
		this.marginPerc2 = marginPerc2;
		firePropertyChange("marginPerc2", oldval, this.marginPerc2);
		setDirty(true);
	}

	public int getAllowPositions() {
		return allowPositions;
	}

	public void setAllowPositions(int allowPositions) {
		int oldval = this.allowPositions;
		this.allowPositions = allowPositions;
		firePropertyChange("allowPositions", oldval, this.allowPositions);
		setDirty(true);
	}

	public int getTradingStatus() {
		return tradingStatus;
	}

	public void setTradingStatus(int tradingStatus) {
		int oldval = this.tradingStatus;
		this.tradingStatus = tradingStatus;
		firePropertyChange("tradingStatus", oldval, this.tradingStatus);
		setDirty(true);
	}

	public DateTimeZone getTimezone() {
		return timezone;
	}

	public boolean isCloseable() {
		return closeable;
	}

	public void setCloseable(boolean closeable) {
		boolean oldval = this.closeable;
		this.closeable = closeable;
		firePropertyChange("closeable", oldval, this.closeable);
	}
	
	
	public boolean isOpenable() {
		return openable;
	}

	public void setOpenable(boolean openable) {
		boolean oldval = this.openable;
		this.openable = openable;
		firePropertyChange("openable", oldval, this.openable);
	}
	
	
	
	public int getRatioRsiPeriod() {
		return ratioRsiPeriod;
	}

	public void setRatioRsiPeriod(int ratioRsiPeriod) {
		int oldval = this.ratioRsiPeriod;
		this.ratioRsiPeriod = ratioRsiPeriod;
		firePropertyChange("ratioRsiPeriod", oldval, this.ratioRsiPeriod);
	}

	public double getRatioRsiThreshold() {
		return ratioRsiThreshold;
	}

	public void setRatioRsiThreshold(double ratioRsiThreshold) {
		double oldval = this.ratioRsiThreshold;
		this.ratioRsiThreshold = ratioRsiThreshold;
		firePropertyChange("ratioRsiThreshold", oldval, this.ratioRsiThreshold);
	}

	public int getNeutrality() {
		return neutrality;
	}

	public void setNeutrality(int neutrality) {
		int oldval = this.neutrality;
		this.neutrality = neutrality;
		firePropertyChange("neutrality", oldval, this.neutrality);
	}

	@JsonIgnore
	public int getPortfolioMasterStatus() {
		return parent.getMasterStatus();
	}

	public String getUid() {
		return uid;
	}
	
	@JsonIgnore
	public int getPortfolioPdtEnable() {
		return parent.getPdtEnable();
	}

	public boolean isDeletable() {
		return deletable;
	}

	public void setDeletable(boolean deletable) {
		boolean oldval = this.deletable;
		this.deletable = deletable;
		firePropertyChange("deletable", oldval, this.deletable);
	}
	
	@JsonIgnore
	public Portfolio getPortfolio() {
		return parent;
	}

	public String getLastOpenedS() {
		return lastOpenedS;
	}

	public void setLastOpenedS(String lastOpenedS) {
		String oldval = this.lastOpenedS;
		this.lastOpenedS = lastOpenedS;
		firePropertyChange("lastOpenedS", oldval, this.lastOpenedS);
	}
	
	
	public boolean checkPosition() {
		if (core==null) return false;
		return core.hasActiveOrPendingPosition();
	}
	
	public boolean checkPositionFailFast() {
		if (core==null) throw new IllegalStateException("Missing core object in active strategy");
		return core.hasActiveOrPendingPosition();
	}
	
	public void releasePositionLock() {
		parent.releasePositionLock(uid);
	}
	
	public boolean acquirePositionLock() {
		return parent.acquirePositionLock(uid, slotOccupation);
	}

	public double getProfitPotential() {
		return profitPotential;
	}

	public void setProfitPotential(double profitPotential) {
		double oldval = this.profitPotential;
		this.profitPotential = profitPotential;
		firePropertyChange("profitPotential", oldval, this.profitPotential);
		setProfitPotentialS(String.format("%.2f", profitPotential));
	}

	public String getProfitPotentialS() {
		return profitPotentialS;
	}

	public void setProfitPotentialS(String profitPotentialS) {
		String oldval = this.profitPotentialS;
		this.profitPotentialS = profitPotentialS;
		firePropertyChange("profitPotentialS", oldval, this.profitPotentialS);
	}
	
	
	
	public double getLastOpenEquity() {
		return lastOpenEquity;
	}

	public void setLastOpenEquity(double lastOpenEquity) {
		double oldval = this.lastOpenEquity;
		this.lastOpenEquity = lastOpenEquity;
		firePropertyChange("lastOpenEquity", oldval, this.lastOpenEquity);
	}
	
	

	public double getSlotOccupation() {
		return slotOccupation;
	}


	public void setSlotOccupation(double slotOccupation) {
		double oldval = this.slotOccupation;
		this.slotOccupation = slotOccupation;
		firePropertyChange("slotOccupation", oldval, this.slotOccupation);
		setDirty(true);
	}

	public boolean isAllowReversals() {
		return allowReversals;
	}

	public void setAllowReversals(boolean allowReversals) {
		boolean oldval = this.allowReversals;
		this.allowReversals = allowReversals;
        firePropertyChange("allowReversals", oldval, this.allowReversals);
        setDirty(true);
	}

	public int getTradeAs1() {
		return tradeAs1;
	}

	public void setTradeAs1(int tradeAs1) {
		this.tradeAs1 = tradeAs1;
	}

	public int getTradeAs2() {
		return tradeAs2;
	}

	public void setTradeAs2(int tradeAs2) {
		this.tradeAs2 = tradeAs2;
	}

	public void updateFromJson(JsonNode n) {
		syncOutEnabled=false;
		if (core==null) {
			// these are only available if strategy is not active yet
			 setModel(n.get("model").asText());
			 setEntryThreshold(n.get("entry_threshold").asDouble());
			 setExitThreshold(n.get("exit_threshold").asDouble());
             setDowntickThreshold(n.get("downtick_threshold").asDouble());
			 setMaxEntryScore(n.get("max_score").asDouble());
			 setRatioMaType(MAType.values()[n.get("ratio_ma_type").asInt()]);
			 setRatioMaPeriod(n.get("ratio_ma_period").asInt());
			 setRatioStdDevPeriod(n.get("ratio_stddev_period").asInt());
			 setEntryMode(n.get("ratio_entry_mode").asInt());
			 setResidualLinRegPeriod(n.get("residual_linreg_period").asInt());
			 setRatioRsiPeriod(n.path("ratio_rsi_period").asInt(10));
			 setRatioRsiThreshold(n.path("ratio_rsi_threshold").asDouble(0));
			 
			 setMarginPerc1(n.get("ticker1margin").asDouble());
			 setMarginPerc2(n.get("ticker2margin").asDouble());

			// kalman stuff here
            setKalmanAutoUsageTarget(n.get("ka_usage_target").asDouble());
            setKalmanAutoVe(n.get("ka_ve").asDouble());
            setNeutrality(n.get("neutrality").asInt());

			 if (n.get("last_opened_datetime").isNull()) {
				 setLastOpened(null);
			 } else {
				 DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
				 DateTime dt = fmt.withZoneUTC().parseDateTime(n.get("last_opened_datetime").asText());
				 setLastOpened(dt.withZone(DateTimeZone.getDefault()));
			 }
			 if (n.get("last_opened_equity").isNull()) setLastOpenEquity(0);
			 else setLastOpenEquity(n.get("last_opened_equity").asDouble());

			if (n.get("last_model_state").isNull()) {
				setModelState(null);
			} else {
				ObjectMapper mapper = new ObjectMapper();
				try {
					setModelState(mapper.treeToValue(n.get("last_model_state"), AbstractPairTradingModelState.class));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
					setModelState(null); // fuck!
				}
			}
		}

		setAllowReversals(n.get("allow_reversals").asInt()==1);
		setMaxDaysEnabled(n.get("enable_max_days").asInt()==1);
		setMaxDays(n.get("max_days").asInt());
		setMinPlToCloseEnabled(n.get("enable_min_pl").asInt()==1);
		setMinPlToClose(n.get("min_pl").asDouble());
		setMinPriceEnabled(n.get("enable_min_price").asInt()==1);
		setMinPrice(n.get("min_price").asDouble());
		setMinExpectationEnabled(n.get("enable_min_profit_potential").asInt()==1);
		setMinExpectation(n.get("min_profit_potential").asDouble());
		
		setEntryStartHour(n.get("entry_start_hour").asInt());
		setEntryStartMinute(n.get("entry_start_minute").asInt());
		setEntryEndHour(n.get("entry_end_hour").asInt());
		setEntryEndMinute(n.get("entry_end_minute").asInt());
		setExitStartHour(n.get("exit_start_hour").asInt());
		setExitStartMinute(n.get("exit_start_minute").asInt());
		setExitEndHour(n.get("exit_end_hour").asInt());
		setExitEndMinute(n.get("exit_end_minute").asInt());
		 
		setTimezoneId(n.get("timezone").asText());
		setAllowPositions(n.get("allow_positions").asInt());
		setTradingStatus(n.get("status").asInt());
		setSlotOccupation(n.get("slot_occupation").asDouble());
		setDirty(false);
		
		Iterator<JsonNode> ite = n.path("features").elements();
		features.clear();
		while (ite.hasNext()) {
			String fea = ite.next().asText();
			features.add(fea);
		}
		
		syncOutEnabled=true;
		 		
	}
	
	
	public void updateDaysRemaining() {
		if (maxDaysEnabled && lastOpened!=null && (STATUS_LONG.equals(status) || STATUS_SHORT.equals(status))) {
			Duration d=new Duration(lastOpened, DateTime.now());
			setDaysRemaining(maxDays-(int) d.getStandardDays());
		} else setDaysRemaining(0);
	}
	
	// this is not subscribed, it is called from portfolio!!!
	protected void onBeaconFlash() {
		if (syncOutEnabled && dirty) {
			//System.out.println("I AM DIRTY STRATEGY"); //@DEBUG
			setDirty(false);
			bus.post(new StrategySyncOutRequest(this));
			
		}
		
	}

	public boolean isResumable() {
		return resumable;
	}

	public void setResumable(boolean resumable) {
		boolean oldval = this.resumable;
		this.resumable = resumable;
		firePropertyChange("resumable", oldval, this.resumable);
	}
	
	public void swUpdatePortfolio(Contract contract, int position,
			double marketPrice, double marketValue, double averageCost,
			double unrealizedPNL, double realizedPNL, String accountName) {
			
			for (Position p: (List<Position>) positions) {
				ContractExt ce = ContractExt.createFromGoogleSymbol(p.getSymbol(), p.isCfd);
				//logger.info(String.format("test %s %s %s", ce.m_currency, ce.m_secType, ce.m_symbol));
				if (ce.equalsInSymbolTypeCurrency(contract)) {
					// update position!
					p.setQty(position);
					if (position>0) {
						p.setStatus(Position.STATUS_LONG);
					} else if (position<0) {
						p.setStatus(Position.STATUS_SHORT);
					} else {
						p.setStatus(Position.STATUS_NONE);
					}
					p.setValue(marketValue);
					if (position==0) p.setPl(0);
					else p.setPl(unrealizedPNL);
					p.setAvgOpenPrice(averageCost);
					p.setLastPortfolioUpdate(DateTime.now());
				}
			}
			
	}
	
	
	public void swAccountDownloadEnd(String accountName) {
					
		// traverse positions
		for (Position p: (List<Position>) positions) {
			if (p.getLastPortfolioUpdate()==null || parent.getLastUpdate()==null || p.getLastPortfolioUpdate().isBefore(parent.getLastUpdate())) {
				// position not included in the last update
				if (!Position.STATUS_NONE.equals(p.getStatus())) {
					p.setPl(0);
					p.setQty(0);
					p.setValue(0);
					p.setStatus(Position.STATUS_NONE);
				}
				
				PortfolioUpdate ev = new PortfolioUpdate(accountName, ContractExt.createFromGoogleSymbol(p.getSymbol(), p.isCfd));
				bus.post(ev);
			}
		}
	
	}
	
	public void stopCoreIfRunning() {
		if (core!=null) core.stop();
		
	}
	
	// just for unit tests
	public void injectCore(PairTradingCore c) {
		this.core = c;
	}
	

}
