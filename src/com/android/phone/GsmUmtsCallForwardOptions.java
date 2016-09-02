package com.android.phone;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.telephony.ServiceState;
import android.telephony.CarrierConfigManager;
import android.util.Log;
import android.view.MenuItem;

import java.util.ArrayList;
import android.telephony.SubscriptionManager;

public class GsmUmtsCallForwardOptions extends TimeConsumingPreferenceActivity
        implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private static final String LOG_TAG = "GsmUmtsCallForwardOptions";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static final String NUM_PROJECTION[] = {
        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
    };

    private static final String BUTTON_CFU_KEY   = "button_cfu_key";
    private static final String BUTTON_CFB_KEY   = "button_cfb_key";
    private static final String BUTTON_CFNRY_KEY = "button_cfnry_key";
    private static final String BUTTON_CFNRC_KEY = "button_cfnrc_key";

    private static final String KEY_TOGGLE = "toggle";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NUMBER = "number";

    private CallForwardEditPreference mButtonCFU;
    private CallForwardEditPreference mButtonCFB;
    private CallForwardEditPreference mButtonCFNRy;
    private CallForwardEditPreference mButtonCFNRc;

    private final ArrayList<CallForwardEditPreference> mPreferences =
            new ArrayList<CallForwardEditPreference> ();
    private int mInitIndex= 0;

    private boolean mFirstResume;
    private Bundle mIcicle;
    private Phone mPhone;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;
    private int mServiceClass;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mPhone = mSubscriptionInfoHelper.getPhone();
        final SubscriptionManager subscriptionManager = SubscriptionManager.from(this);
        // check the active data sub.
        int sub = mPhone.getSubId();
        int defaultDataSub = subscriptionManager.getDefaultDataSubscriptionId();
        CarrierConfigManager configManager = (CarrierConfigManager)mPhone.
                getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle pb = configManager.getConfigForSubId(mPhone.getSubId());
        boolean checkData = pb.getBoolean("check_mobile_data_for_cf");
        if (mPhone != null && mPhone.isUtEnabled() && checkData) {
            int activeNetworkType = getActiveNetworkType();
            boolean isDataRoaming = mPhone.getServiceState().getDataRoaming();
            boolean isDataRoamingEnabled = mPhone.getDataRoamingEnabled();
            boolean promptForDataRoaming = isDataRoaming && !isDataRoamingEnabled;
            Log.d(LOG_TAG, "activeNetworkType = " + getActiveNetworkType() + ", sub = " + sub +
                    ", defaultDataSub = " + defaultDataSub + ", isDataRoaming = " +
                    isDataRoaming + ", isDataRoamingEnabled= " + isDataRoamingEnabled);
            if ((activeNetworkType != ConnectivityManager.TYPE_MOBILE
                    || sub != defaultDataSub)
                    && !(activeNetworkType == ConnectivityManager.TYPE_NONE
                    && promptForDataRoaming)) {
                   if (DBG) Log.d(LOG_TAG, "please open mobile network for UT settings!");
                   String title = (String)this.getResources().getText(R.string.no_mobile_data);
                   String message = (String)this.getResources()
                           .getText(R.string.cf_setting_mobile_data_alert);
                   showAlertDialog(title, message);
                   return;
            }
            if (promptForDataRoaming) {
                   if (DBG) Log.d(LOG_TAG, "please open data roaming for UT settings!");
                   String title = (String)this.getResources()
                           .getText(R.string.no_mobile_data_roaming);
                   String message = (String)this.getResources()
                           .getText(R.string.cf_setting_mobile_data_roaming_alert);
                   showAlertDialog(title, message);
                   return;
            }
        }

        addPreferencesFromResource(R.xml.callforward_options);

        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.call_forwarding_settings_with_label);

        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonCFU = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFU_KEY);
        mButtonCFB = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFB_KEY);
        mButtonCFNRy = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNRY_KEY);
        mButtonCFNRc = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNRC_KEY);

        mButtonCFU.setParentActivity(this, mButtonCFU.reason);
        mButtonCFB.setParentActivity(this, mButtonCFB.reason);
        mButtonCFNRy.setParentActivity(this, mButtonCFNRy.reason);
        mButtonCFNRc.setParentActivity(this, mButtonCFNRc.reason);

        mPreferences.add(mButtonCFU);
        mPreferences.add(mButtonCFB);
        mPreferences.add(mButtonCFNRy);
        mPreferences.add(mButtonCFNRc);

        // we wait to do the initialization until onResume so that the
        // TimeConsumingPreferenceActivity dialog can display as it
        // relies on onResume / onPause to maintain its foreground state.

        /*Retrieve Call Forward ServiceClass*/
        Intent intent = getIntent();
        if (DBG) Log.d(LOG_TAG, "Intent is"+intent);
        int serviceClass;
        mServiceClass = intent.getIntExtra(PhoneUtils.SERVICE_CLASS,
                CommandsInterface.SERVICE_CLASS_VOICE);
        if (DBG) Log.d(LOG_TAG, "serviceClass: " +mServiceClass);

        mFirstResume = true;
        mIcicle = icicle;

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int id) {
        if (id == DialogInterface.BUTTON_POSITIVE) {
            Intent newIntent = new Intent("android.settings.SETTINGS");
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(newIntent);
        }
        finish();
        return;
    }

    private int getActiveNetworkType() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if ((ni == null) || !ni.isConnected()){
                return ConnectivityManager.TYPE_NONE;
            }
            return ni.getType();
        }
        return ConnectivityManager.TYPE_NONE;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mFirstResume) {
            if (mIcicle == null) {
                if (DBG) Log.d(LOG_TAG, "start to init ");
                mPreferences.get(mInitIndex).init(this, false, mPhone, mServiceClass);
            } else {
                mInitIndex = mPreferences.size();

                for (CallForwardEditPreference pref : mPreferences) {
                    Bundle bundle = mIcicle.getParcelable(pref.getKey());
                    pref.setToggled(bundle.getBoolean(KEY_TOGGLE));
                    CallForwardInfo cf = new CallForwardInfo();
                    cf.number = bundle.getString(KEY_NUMBER);
                    cf.status = bundle.getInt(KEY_STATUS);
                    pref.handleCallForwardResult(cf);
                    pref.init(this, true, mPhone, mServiceClass);
                }
            }
            mFirstResume = false;
            mIcicle = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        for (CallForwardEditPreference pref : mPreferences) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(KEY_TOGGLE, pref.isToggled());
            if (pref.callForwardInfo != null) {
                bundle.putString(KEY_NUMBER, pref.callForwardInfo.number);
                bundle.putInt(KEY_STATUS, pref.callForwardInfo.status);
            }
            outState.putParcelable(pref.getKey(), bundle);
        }
    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (mInitIndex < mPreferences.size()-1 && !isFinishing()) {
            mInitIndex++;
            mPreferences.get(mInitIndex).init(this, false, mPhone, mServiceClass);
        }

        super.onFinished(preference, reading);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DBG) Log.d(LOG_TAG, "onActivityResult: done");
        if (resultCode != RESULT_OK) {
            if (DBG) Log.d(LOG_TAG, "onActivityResult: contact picker result not OK.");
            return;
        }
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(data.getData(),
                NUM_PROJECTION, null, null, null);
            if ((cursor == null) || (!cursor.moveToFirst())) {
                if (DBG) Log.d(LOG_TAG, "onActivityResult: bad contact data, no results found.");
                return;
            }

            switch (requestCode) {
                case CommandsInterface.CF_REASON_UNCONDITIONAL:
                    mButtonCFU.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_BUSY:
                    mButtonCFB.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_NO_REPLY:
                    mButtonCFNRy.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_NOT_REACHABLE:
                    mButtonCFNRc.onPickActivityResult(cursor.getString(0));
                    break;
                default:
                    // TODO: may need exception here.
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            CallFeaturesSetting.goUpToTopLevelSetting(this, mSubscriptionInfoHelper);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAlertDialog(String title, String message) {
        Dialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setOnCancelListener(this)
                .create();
        dialog.show();
    }
}
