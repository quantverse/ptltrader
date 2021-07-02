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

import org.picocontainer.Startable;
import java.util.prefs.Preferences;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.StringXorProcessor;
import net.jcip.annotations.*;

@ThreadSafe
public class Settings extends AbstractModelObject implements Startable {
	private static final String SECRET_KEY_ENC_KEY="PENaicUbi9JuwLxqKLcXfeNvOX3IIgnUg";
	
	private final RuntimeParams runtimeParams;
	private final StringXorProcessor xorProcessor;
	
	private volatile String ptlAccessKey = "";
	private volatile String ptlSecretKey = "";
	private volatile boolean savePtlSecretKey = true;
	private volatile boolean enableConfidentialMode = false;
	
	private volatile boolean ptlConnectEnabled = true;
	
	public Settings(RuntimeParams runtimeParams, StringXorProcessor xorProcessor) {
		super();
		this.runtimeParams = runtimeParams;
		this.xorProcessor = xorProcessor;
	}
	
	public String getPtlAccessKey() {
		return ptlAccessKey;
	}
	public void setPtlAccessKey(String ptlAccessKey) {
		String oldval = this.ptlAccessKey;
		this.ptlAccessKey = ptlAccessKey;
		firePropertyChange("ptlAccessKey", oldval, this.ptlAccessKey);
		Preferences prefs = getPreferences();
		prefs.put("ptlAccessKey", ptlAccessKey);
	}
	public String getPtlSecretKey() {
		return ptlSecretKey;
	}
	public void setPtlSecretKey(String ptlSecretKey) {
		String oldval = this.ptlSecretKey;
		this.ptlSecretKey = ptlSecretKey;
		firePropertyChange("ptlSecretKey", oldval, this.ptlSecretKey);
		Preferences prefs = getPreferences();
		if (savePtlSecretKey) {
			storePtlSecretKey();
		}
	}
	public boolean isPtlConnectEnabled() {
		return ptlConnectEnabled;
	}
	public void setPtlConnectEnabled(boolean ptlConnectEnabled) {
		boolean oldval = this.ptlConnectEnabled;
		this.ptlConnectEnabled = ptlConnectEnabled;
		firePropertyChange("ptlConnectEnabled", oldval, this.ptlConnectEnabled);
	}
	
	private Preferences getPreferences() {
		String nodename = this.getClass().getName()+"."+runtimeParams.getProfile();
		nodename = nodename.replace(".", "/");
		//System.out.println("settings loading prefs for "+nodename);
		Preferences prefs = Preferences.userRoot().node(nodename);
		return prefs;
	}
	
	
	public boolean isSavePtlSecretKey() {
		return savePtlSecretKey;
	}
	public void setSavePtlSecretKey(boolean savePtlSecretKey) {
		boolean oldval = this.savePtlSecretKey;
		this.savePtlSecretKey = savePtlSecretKey;
		firePropertyChange("savePtlSecretKey", oldval, this.savePtlSecretKey);
		Preferences prefs = getPreferences();
		prefs.putBoolean("savePtlSecretKey", savePtlSecretKey);
		if (!savePtlSecretKey) prefs.remove("ptlSecretKey");
		else storePtlSecretKey();
	}
	
	private void storePtlSecretKey() {
		Preferences prefs = getPreferences();
		if (ptlSecretKey.isEmpty()) {
			prefs.put("ptlSecretKey", "");
		} else {
			String encKey = xorProcessor.encode(ptlSecretKey, SECRET_KEY_ENC_KEY);
			prefs.put("ptlSecretKey", encKey);
		}
	}
	
	
	@Override
	public void start() {
		// load preferences
		Preferences prefs = getPreferences();
		
		setPtlAccessKey(prefs.get("ptlAccessKey",""));
		String encryptedSecretKey=prefs.get("ptlSecretKey","");
		if (encryptedSecretKey.isEmpty()) {
			setPtlSecretKey("");
		} else {
			String secretKey = xorProcessor.decode(encryptedSecretKey, SECRET_KEY_ENC_KEY);
			//System.out.println("key to load: "+secretKey+" original "+encryptedSecretKey);
			if (secretKey!=null) setPtlSecretKey(secretKey);
		}
				
		setSavePtlSecretKey(prefs.getBoolean("savePtlSecretKey", true));
		setEnableConfidentialMode(prefs.getBoolean("enableConfidentialMode", false));
		
	}
	@Override
	public void stop() {
		// nothing to do
		
	}
	public boolean isEnableConfidentialMode() {
		return enableConfidentialMode;
	}
	public void setEnableConfidentialMode(boolean enableConfidentialMode) {
		boolean oldval = this.enableConfidentialMode;
		this.enableConfidentialMode = enableConfidentialMode;
		firePropertyChange("enableConfidentialMode", oldval, this.enableConfidentialMode);
		Preferences prefs = getPreferences();
		prefs.putBoolean("enableConfidentialMode", enableConfidentialMode);
	}
	
	
	

}
