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
import java.util.Date;
import net.jcip.annotations.*;

@ThreadSafe
public class Account extends AbstractModelObject {
	private final String code;
	private volatile String currency="";
	private volatile double equityWithLoanValue=0;
	private volatile String equityWithLoanValueS="";
	private volatile double buyingPower=0;
	private volatile String buyingPowerS="";
	private volatile double unrealizedPl=0;
	private volatile String unrealizedPlS="";
	private volatile double totalCash=0;
	private volatile String totalCashS="";
	private volatile double initialMargin=0;
	private volatile String initialMarginS="";
	private volatile double maintenanceMargin=0;
	private volatile String maintenanceMarginS="";
	private volatile double availableFunds=0;
	private volatile String availableFundsS="";
	
	private volatile Date lastUpdated;
	
	public Account(String code) {
		this.code=code;
	}
	
	public String getCode() {
		return code;
	}
	
	public String getCurrency() {
		return currency;
	}
	
	public void setCurrency(String currency) {
		String oldval=this.currency;
		this.currency = currency;
		firePropertyChange("currency", oldval, this.currency);
	}
	
	public double getEquityWithLoanValue() {
		return equityWithLoanValue;
	}
	
	public void setEquityWithLoanValue(double equityWithLoanValue) {
		double oldval=this.equityWithLoanValue;
		this.equityWithLoanValue = equityWithLoanValue;
		firePropertyChange("equityWithLoanValue", oldval, this.equityWithLoanValue);
		setEquityWithLoanValueS(String.format("%.2f", equityWithLoanValue));
	}
	
	public Date getLastUpdated() {
		return lastUpdated;
	}
	public void setLastUpdated(Date lastUpdated) {
		Date oldval=this.lastUpdated;
		this.lastUpdated = lastUpdated;
		firePropertyChange("lastUpdated", oldval, this.lastUpdated);
		
	}

	public String getEquityWithLoanValueS() {
		return equityWithLoanValueS;
	}

	public void setEquityWithLoanValueS(String equityWithLoanValueS) {
		String oldval = this.equityWithLoanValueS;
		this.equityWithLoanValueS = equityWithLoanValueS;
		firePropertyChange("equityWithLoanValueS", oldval, this.equityWithLoanValueS);
	}

	public double getBuyingPower() {
		return buyingPower;
	}

	public void setBuyingPower(double buyingPower) {
		this.buyingPower = buyingPower;
		setBuyingPowerS(String.format("%.2f", buyingPower));
		
	}

	public String getBuyingPowerS() {
		return buyingPowerS;
	}

	public void setBuyingPowerS(String buyingPowerS) {
		String oldval = this.buyingPowerS;
		this.buyingPowerS = buyingPowerS;
		firePropertyChange("buyingPowerS", oldval, this.buyingPowerS);
	}

	public double getUnrealizedPl() {
		return unrealizedPl;
	}

	public void setUnrealizedPl(double unrealizedPl) {
		this.unrealizedPl = unrealizedPl;
		setUnrealizedPlS(String.format("%.2f", unrealizedPl));
	}

	public String getUnrealizedPlS() {
		return unrealizedPlS;
	}

	public void setUnrealizedPlS(String unrealizedPlS) {
		String oldval = this.unrealizedPlS;
		this.unrealizedPlS = unrealizedPlS;
		firePropertyChange("unrealizedPlS", oldval, this.unrealizedPlS);
	}

	public double getTotalCash() {
		return totalCash;
	}

	public void setTotalCash(double totalCash) {
		this.totalCash = totalCash;
		setTotalCashS(String.format("%.2f", totalCash));
	}

	public String getTotalCashS() {
		return totalCashS;
	}

	public void setTotalCashS(String totalCashS) {
		String oldval = this.totalCashS;
		this.totalCashS = totalCashS;
		firePropertyChange("totalCashS", oldval, this.totalCashS);
	}

	public double getInitialMargin() {
		return initialMargin;
	}

	public void setInitialMargin(double initialMargin) {
		this.initialMargin = initialMargin;
		setInitialMarginS(String.format("%.2f", initialMargin));
	}

	public String getInitialMarginS() {
		return initialMarginS;
	}

	public void setInitialMarginS(String initialMarginS) {
		String oldval = this.initialMarginS;
		this.initialMarginS = initialMarginS;
		firePropertyChange("initialMarginS", oldval, this.initialMarginS);
	}

	public double getMaintenanceMargin() {
		return maintenanceMargin;
	}

	public void setMaintenanceMargin(double maintenanceMargin) {
		this.maintenanceMargin = maintenanceMargin;
		setMaintenanceMarginS(String.format("%.2f", maintenanceMargin));
	}

	public String getMaintenanceMarginS() {
		return maintenanceMarginS;
	}

	public void setMaintenanceMarginS(String maintenanceMarginS) {
		String oldval = this.maintenanceMarginS;
		this.maintenanceMarginS = maintenanceMarginS;
		firePropertyChange("maintenanceMarginS", oldval, this.maintenanceMarginS);
	}

	public double getAvailableFunds() {
		return availableFunds;
	}

	public void setAvailableFunds(double availableFunds) {
		this.availableFunds = availableFunds;
		setAvailableFundsS(String.format("%.2f", availableFunds));
	}

	public String getAvailableFundsS() {
		return availableFundsS;
	}

	public void setAvailableFundsS(String availableFundsS) {
		String oldval = this.availableFundsS;
		this.availableFundsS = availableFundsS;
		firePropertyChange("availableFundsS", oldval, this.availableFundsS);
	}

	
	
	
	
}
