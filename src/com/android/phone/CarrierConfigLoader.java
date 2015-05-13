/**
 * Copyright (c) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.android.phone;

import static android.Manifest.permission.READ_PHONE_STATE;
import static com.android.internal.telephony.uicc.IccCardProxy.ACTION_INTERNAL_SIM_STATE_CHANGED;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.ServiceConnection;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncResult;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.service.carrier.CarrierConfigService;
import android.service.carrier.CarrierIdentifier;
import android.service.carrier.ICarrierConfigService;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.internal.telephony.ICarrierConfigLoader;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.TelephonyIntents;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;

/**
 * CarrierConfigLoader binds to privileged carrier apps to fetch carrier config overlays.
 * TODO: implement persist cache
 * TODO: add gid2 to phone
 * TODO: handle package install/uninstall events
 */

public class CarrierConfigLoader extends ICarrierConfigLoader.Stub {
    private static final String TAG = "CarrierConfigLoader";
    // Package name for default carrier config app, bundled with system image.
    private static final String DEFAULT_CARRIER_CONFIG_PACKAGE = "com.android.carrierconfig";

    /** The singleton instance. */
    private static CarrierConfigLoader sInstance;
    // The context for phone app, passed from PhoneGlobals.
    private Context mContext;
    // Carrier configs from default app, indexed by phoneID.
    private Bundle[] mConfigFromDefaultApp;
    // Carrier configs from privileged carrier config app, indexed by phoneID.
    private Bundle[] mConfigFromCarrierApp;
    // Service connection for binding to config app.
    private ConfigServiceConnection[] mServiceConnection;

    // Broadcast receiver for SIM and pkg intents, register intent filter in constructor.
    private final BroadcastReceiver mReceiver = new ConfigLoaderBroadcastReceiver();

    // Message codes; see mHandler below.
    // Request from SubscriptionInfoUpdater when SIM becomes absent or error.
    private static final int EVENT_CLEAR_CONFIG = 0;
    // Request from SubscriptionInfoUpdater to update config.
    private static final int EVENT_UPDATE_CONFIG = 1;
    // Request from carrier app to reload config.
    private static final int EVENT_RELOAD_CONFIG = 2;
    // Has connected to default app.
    private static final int EVENT_CONNECTED_TO_DEFAULT = 3;
    // Has connected to carrier app.
    private static final int EVENT_CONNECTED_TO_CARRIER = 4;
    // Config has been loaded from default app.
    private static final int EVENT_LOADED_FROM_DEFAULT = 5;
    // Config has been loaded from carrier app.
    private static final int EVENT_LOADED_FROM_CARRIER = 6;


    // Handler to process various events.
    // For each phoneId, state transition should be: default app bind->connected->loaded,
    // carrier app (if exists) bind-> connected->loaded. At any time, at most one connection is active.
    // If events are not in this order, previous connection will be unbind, so only latest event takes effect. 
    // We broadcast config change when:
    // 1. loaded from carrier app
    // 2. loaded from default app if no carrier app
    // 3. config cleared, possibly due to sim removed
    // 4. bind or IPC error
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int phoneId = msg.arg1;
            log("mHandler: " + msg.what + " phoneId: " + phoneId);
            CarrierIdentifier carrierId;
            ConfigServiceConnection conn;
            Bundle config;
            switch (msg.what) {
                case EVENT_CLEAR_CONFIG:
                    mConfigFromDefaultApp[phoneId] = null;
                    mConfigFromCarrierApp[phoneId] = null;
                    mServiceConnection[phoneId] = null;
                    broadcastConfigChangedIntent(phoneId);
                    break;
                case EVENT_UPDATE_CONFIG:
                    // Use persist cache to avoid loading from app.
                    // Fall through to next event if cache not hit.
                case EVENT_RELOAD_CONFIG:
                    if (!bindToConfigPackage(DEFAULT_CARRIER_CONFIG_PACKAGE,
                            phoneId, EVENT_CONNECTED_TO_DEFAULT)) {
                        //Send bcast if bind fails
                        broadcastConfigChangedIntent(phoneId);
                    }
                    break;

                case EVENT_CONNECTED_TO_DEFAULT:
                    carrierId = getCarrierIdForPhoneId(phoneId);
                    conn = (ConfigServiceConnection) msg.obj;
                    // If new service connection has been created, unbind.
                    if (mServiceConnection[phoneId] != conn || conn.service == null) {
                        mContext.unbindService(conn);
                        break;
                    }
                    try {
                        ICarrierConfigService configService = ICarrierConfigService.Stub.asInterface(conn.service);
                        config = configService.getCarrierConfig(carrierId);
                        mConfigFromDefaultApp[phoneId] = config;
                        mHandler.sendMessage(mHandler.obtainMessage(EVENT_LOADED_FROM_DEFAULT, phoneId));
                    } catch (RemoteException ex) {
                        Log.e(TAG, "Failed to get carrier config: " + ex.toString());
                    } finally {
                        mContext.unbindService(mServiceConnection[phoneId]);
                    }
                    break;

                case EVENT_LOADED_FROM_DEFAULT:
                    if (mServiceConnection[phoneId] == null) {
                        break;
                    }
                    List<String> carrierPackageNames = TelephonyManager.from(mContext)
                            .getCarrierPackageNamesForIntentAndPhone(
                                    new Intent(CarrierConfigService.SERVICE_INTERFACE), phoneId);
                    log("Found carrier config app: " + carrierPackageNames);
                    if (carrierPackageNames != null && carrierPackageNames.size() > 0) {
                        if (!bindToConfigPackage(carrierPackageNames.get(0),
                                phoneId, EVENT_CONNECTED_TO_CARRIER)) {
                            broadcastConfigChangedIntent(phoneId);
                        }
                    } else {
                        broadcastConfigChangedIntent(phoneId);
                    }
                    break;

                case EVENT_CONNECTED_TO_CARRIER:
                    carrierId = getCarrierIdForPhoneId(phoneId);
                    conn = (ConfigServiceConnection) msg.obj;
                    // If new service connection has been created, unbind.
                    if (mServiceConnection[phoneId] != conn ||
                        conn.service == null) {
                        mContext.unbindService(conn);
                        break;
                    }
                    try {
                        ICarrierConfigService configService = ICarrierConfigService.Stub.asInterface(conn.service);
                        config = configService.getCarrierConfig(carrierId);
                        mConfigFromCarrierApp[phoneId] = config;
                        mHandler.sendMessage(mHandler.obtainMessage(EVENT_LOADED_FROM_CARRIER, phoneId));
                    } catch (RemoteException ex) {
                        Log.e(TAG, "Failed to get carrier config: " + ex.toString());
                    } finally {
                        mContext.unbindService(mServiceConnection[phoneId]);
                    }
                    break;

                case EVENT_LOADED_FROM_CARRIER:
                    if (mServiceConnection[phoneId] == null) {
                        break;
                    }
                    broadcastConfigChangedIntent(phoneId);
                    break;
            }
        }
    };

    /**
     * Constructs a CarrierConfigLoader, registers it as a service, and registers a broadcast
     * receiver for relevant events.
     */
    private CarrierConfigLoader(Context context) {
        mContext = context;

        // Register for package updates.
        IntentFilter triggers = new IntentFilter();
        triggers.addAction(Intent.ACTION_PACKAGE_ADDED);
        triggers.addAction(Intent.ACTION_PACKAGE_CHANGED);
        triggers.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mContext.registerReceiver(mReceiver, triggers);

        int numPhones = TelephonyManager.from(context).getPhoneCount();
        mConfigFromDefaultApp = new Bundle[numPhones];
        mConfigFromCarrierApp = new Bundle[numPhones];
        mServiceConnection = new ConfigServiceConnection[numPhones];
        // Make this service available through ServiceManager.
        ServiceManager.addService(Context.CARRIER_CONFIG_SERVICE, this);
        log("CarrierConfigLoader has started");
    }

    /**
     * Initialize the singleton CarrierConfigLoader instance.
     *
     * This is only done once, at startup, from {@link com.android.phone.PhoneApp#onCreate}.
     */
    /* package */
    static CarrierConfigLoader init(Context context) {
        synchronized (CarrierConfigLoader.class) {
            if (sInstance == null) {
                sInstance = new CarrierConfigLoader(context);
            } else {
                Log.wtf(TAG, "init() called multiple times!  sInstance = " + sInstance);
            }
            return sInstance;
        }
    }

    private void broadcastConfigChangedIntent(int phoneId) {
        Intent intent = new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        SubscriptionManager.putPhoneIdAndSubIdExtra(intent, phoneId);
        ActivityManagerNative.broadcastStickyIntent(intent, READ_PHONE_STATE,
              UserHandle.USER_ALL);
    }

    /** Binds to the default or carrier config app. */
    private boolean bindToConfigPackage(String pkgName, int phoneId, int eventId) {
        log("Binding to " + pkgName + " for phone " + phoneId);
        Intent carrierConfigService = new Intent(CarrierConfigService.SERVICE_INTERFACE);
        carrierConfigService.setPackage(pkgName);
        mServiceConnection[phoneId] = new ConfigServiceConnection(phoneId, eventId);
        try {
            return mContext.bindService(carrierConfigService, mServiceConnection[phoneId],
                    Context.BIND_AUTO_CREATE);
        } catch (SecurityException ex) {
            return false;
        }
    }

    private CarrierIdentifier getCarrierIdForPhoneId(int phoneId) {
        String mcc = "";
        String mnc = "";
        String imsi = "";
        String gid1 = "";
        String gid2 = "";
        String spn = TelephonyManager.from(mContext).getSimOperatorNameForPhone(phoneId);
        String simOperator = TelephonyManager.from(mContext).getSimOperatorNumericForPhone(phoneId);
        // A valid simOperator should be 5 or 6 digits, depending on the length of the MNC.
        if (simOperator != null && simOperator.length() >= 3) {
            mcc = simOperator.substring(0, 3);
            mnc = simOperator.substring(3);
        }
        Phone phone = PhoneFactory.getPhone(phoneId);
        if (phone != null) {
            imsi = phone.getSubscriberId();
            gid1 = phone.getGroupIdLevel1();
            // add gid2 after phone supports it.
        }

        return new CarrierIdentifier(mcc, mnc, spn, imsi, gid1, gid2);
    }

    @Override
    public Bundle getConfigForSubId(int subId) {
        int phoneId = SubscriptionManager.getPhoneId(subId);
        Bundle retConfig = CarrierConfigManager.getDefaultConfig();
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            Bundle config = mConfigFromDefaultApp[phoneId];
            if (config != null) retConfig.putAll(config);
            config = mConfigFromCarrierApp[phoneId];
            if (config != null) retConfig.putAll(config);
        }
        return retConfig;
    }

    @Override
    public void reloadCarrierConfigForSubId(int subId) {
        int phoneId = SubscriptionManager.getPhoneId(subId);
        if (SubscriptionManager.isValidPhoneId(phoneId)) {
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_RELOAD_CONFIG, phoneId));
        } else {
            log("Ignore invalid phoneId: " + phoneId + " for subId: " + subId);
        }
    }

    @Override
    public void updateConfigForPhoneId(int phoneId, String simState) {
        log("update config for phoneId: " + phoneId + " simState: " + simState);
        if (!SubscriptionManager.isValidPhoneId(phoneId)) {
            return;
        }
        // requires Java 7 for switch on string.
        switch (simState) {
            case IccCardConstants.INTENT_VALUE_ICC_ABSENT:
            case IccCardConstants.INTENT_VALUE_ICC_CARD_IO_ERROR:
            case IccCardConstants.INTENT_VALUE_ICC_UNKNOWN:
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_CLEAR_CONFIG, phoneId));
                break;
            case IccCardConstants.INTENT_VALUE_ICC_LOADED:
            case IccCardConstants.INTENT_VALUE_ICC_LOCKED:
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_UPDATE_CONFIG, phoneId));
                break;
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump carrierconfig from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        pw.println("CarrierConfigLoader: " + this);
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            pw.println("  Phone Id=" + i);
            pw.println("  mConfigFromDefaultApp=" + mConfigFromDefaultApp[i]);
            pw.println("  mConfigFromCarrierApp=" + mConfigFromCarrierApp[i]);
        }
    }

    private class ConfigServiceConnection implements ServiceConnection {
        int phoneId;
        int eventId;
        IBinder service;

        public ConfigServiceConnection(int phoneId, int eventId) {
            this.phoneId = phoneId;
            this.eventId = eventId;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            log("Connected to config app: " + name.flattenToString());
            this.service = service;
            mHandler.sendMessage(mHandler.obtainMessage(eventId, phoneId, -1, this));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            this.service = null;
        }
    }

    private class ConfigLoaderBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            log("Receive action: " + action);
        }
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}

