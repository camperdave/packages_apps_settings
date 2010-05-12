/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.TelephonyProperties;
import com.android.settings.bluetooth.BluetoothEnabler;
import com.android.settings.wifi.WifiEnabler;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;

public class WirelessSettings extends PreferenceActivity {

    private static final String KEY_TOGGLE_AIRPLANE = "toggle_airplane";
    private static final String KEY_TOGGLE_BLUETOOTH = "toggle_bluetooth";
    private static final String KEY_TOGGLE_WIFI = "toggle_wifi";
    private static final String KEY_WIFI_SETTINGS = "wifi_settings";
    private static final String KEY_BT_SETTINGS = "bt_settings";
    private static final String KEY_VPN_SETTINGS = "vpn_settings";
    private static final String KEY_TOGGLE_TETHERING = "toggle_tethering";
    private static final String KEY_PROXY_SETTING = "proxy_setting";
    private static final String KEY_RADIO_INFO_TYPE = "radio_info_type";
    private static final String KEY_RADIO_INFO_STATUS = "radio_info_status";
    public static final String EXIT_ECM_RESULT = "exit_ecm_result";
    public static final int REQUEST_CODE_EXIT_ECM = 1;

    private WifiEnabler mWifiEnabler;
    private AirplaneModeEnabler mAirplaneModeEnabler;
    private BluetoothEnabler mBtEnabler;
    private CheckBoxPreference mAirplaneModePreference;
    private TetheringEnabler mTetheringEnabler;

    private Preference mRadioInformationType;
    private Preference mRadioInformationStatus;

    /**
     * Invoked on each preference click in this hierarchy, overrides
     * PreferenceActivity's implementation.  Used to make sure we track the
     * preference click events.
     */
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if ( (preference == mAirplaneModePreference) &&
                (Boolean.parseBoolean(
                    SystemProperties.get(TelephonyProperties.PROPERTY_INECM_MODE))) ) {
            // In ECM mode launch ECM app dialog
            startActivityForResult(
                new Intent(TelephonyIntents.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null),
                REQUEST_CODE_EXIT_ECM);

            return true;
        }
	else if ( (preference == mRadioInformationType) || (preference == mRadioInformationStatus) )
	{
		updateRadioInfo();
		return true;
	}
        else {
            // Let the intents be launched by the Preference manager
            return false;
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.wireless_settings);

        initToggles();
        mAirplaneModePreference = (CheckBoxPreference) findPreference(KEY_TOGGLE_AIRPLANE);
	updateRadioInfo();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        mWifiEnabler.resume();
        mBtEnabler.resume();
        mAirplaneModeEnabler.resume();
        mTetheringEnabler.resume();

	updateRadioInfo();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        mWifiEnabler.pause();
        mAirplaneModeEnabler.pause();
        mBtEnabler.pause();
        mTetheringEnabler.pause();
    }
    
    private void initToggles() {
        
        Preference airplanePreference = findPreference(KEY_TOGGLE_AIRPLANE);
        Preference wifiPreference = findPreference(KEY_TOGGLE_WIFI);
        Preference btPreference = findPreference(KEY_TOGGLE_BLUETOOTH);
        Preference wifiSettings = findPreference(KEY_WIFI_SETTINGS);
        Preference vpnSettings = findPreference(KEY_VPN_SETTINGS);
        Preference proxySetting = findPreference(KEY_PROXY_SETTING);
	mRadioInformationType = findPreference(KEY_RADIO_INFO_TYPE);
	mRadioInformationStatus = findPreference(KEY_RADIO_INFO_STATUS);
        
        IBinder b = ServiceManager.getService(BluetoothAdapter.BLUETOOTH_SERVICE);
        if (b == null) {
            // Disable BT Settings if BT service is not available.
            Preference btSettings = findPreference(KEY_BT_SETTINGS);
            btSettings.setEnabled(false);
        }

        mWifiEnabler = new WifiEnabler(
                this, (WifiManager) getSystemService(WIFI_SERVICE),
                (CheckBoxPreference) wifiPreference);
        mAirplaneModeEnabler = new AirplaneModeEnabler(
                this, (CheckBoxPreference) airplanePreference);
        mBtEnabler = new BluetoothEnabler(this, (CheckBoxPreference) btPreference);

        // manually set up dependencies for Wifi if its radio is not toggleable in airplane mode
        String toggleableRadios = Settings.System.getString(getContentResolver(),
                Settings.System.AIRPLANE_MODE_TOGGLEABLE_RADIOS);
        if (toggleableRadios == null || !toggleableRadios.contains(Settings.System.RADIO_WIFI)) {
            wifiPreference.setDependency(airplanePreference.getKey());
            wifiSettings.setDependency(airplanePreference.getKey());
            vpnSettings.setDependency(airplanePreference.getKey());
            proxySetting.setDependency(airplanePreference.getKey());
        }
        
        mTetheringEnabler = new TetheringEnabler(
        		this, (CheckBoxPreference) findPreference(KEY_TOGGLE_TETHERING));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
        case REQUEST_CODE_EXIT_ECM:
            Boolean isChoiceYes =
                data.getBooleanExtra(EXIT_ECM_RESULT, false);
            // Set Airplane mode based on the return value and checkbox state
            mAirplaneModeEnabler.setAirplaneModeInECM(isChoiceYes,
                    mAirplaneModePreference.isChecked());
            break;

        default:
            break;
        }
    }

    private void updateRadioInfo()
    {
	String riType = "";
	String riStatus = "";

	ConnectivityManager cm = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);

	NetworkInfo ni = cm.getActiveNetworkInfo();

	if(ni == null)
	{
		// If there's no radio active (ie: airplane mode)
		riType = "No radio active.";
		riStatus = "";
	}
	else
	{
		riType = ni.getTypeName();
		riType += " - " + ni.getSubtypeName();

		if(ni.isConnected())
		{
			riStatus = "Connected!";
		}
		else if(ni.isAvailable())
		{
			riStatus = "Available!";
		}
		else
		{
			riStatus = "Down...";
		}

		if(ni.isRoaming())
		{
			riStatus += " (roaming)";
		}	
	}


	try 
        {
            findPreference("radio_info_type").setSummary(riType);
        } 
        catch (RuntimeException e) 
        {
            findPreference("radio_info_type").setSummary(getResources().getString(R.string.radio_info_type_default));
        }

	try 
        {
            findPreference("radio_info_status").setSummary(riStatus);
        } 
        catch (RuntimeException e) 
        {
            findPreference("radio_info_status").setSummary(getResources().getString(R.string.radio_info_status_default));
        }


    }

}
