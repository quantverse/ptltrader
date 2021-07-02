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
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import net.jcip.annotations.*;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Collections;

@ThreadSafe
public class AccountList extends AbstractModelObject {
	private final List<Account>accounts = new CopyOnWriteArrayList<Account>();
	private final Map<String, Integer> accountsMap = Collections.synchronizedMap(new LinkedHashMap<String, Integer>());
	
	private volatile boolean loaded=false;
	private volatile int activeIndex=-1;
	
	public List<Account> getAccounts() {
		return accounts;
	}

	public void addAccount(Account account) {
		accounts.add(account);
		accountsMap.put(account.getCode(), accounts.size()-1);
		firePropertyChange("accounts", null, accounts);
		
	}

	public void removeAccount(Account account) {
		accountsMap.remove(account.getCode());
		accounts.remove(account);
		firePropertyChange("accounts", null, accounts);
		
	}

	public boolean isLoaded() {
		return loaded;
	}

	public void setLoaded(boolean loaded) {
		this.loaded = loaded;
	}

	public Account getAccountByCode(String code) {
		Integer index=accountsMap.get(code);
		if (index==null) return null;
		else return (Account) accounts.get(index);
		
	}

	public int getActiveIndex() {
		return activeIndex;
	}

	public void setActiveIndex(int activeIndex) {
		int oldvalue=this.activeIndex;
		this.activeIndex = activeIndex;
		firePropertyChange("activeIndex", oldvalue, this.activeIndex);
	}	

}
