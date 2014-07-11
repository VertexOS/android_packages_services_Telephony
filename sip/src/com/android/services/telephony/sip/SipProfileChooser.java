/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony.sip;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.sip.SipProfile;
import android.net.sip.SipManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.List;

/** Find a SIP profile to use for the an outgoing call. */
final class SipProfileChooser {
    private static final String PREFIX = "[SipProfileChooser] ";
    private static final boolean VERBOSE = true; /* STOP SHIP if true */

    interface Callback {
        // Call will be completed by the given SIP profile.
        void onSipChosen(SipProfile profile);
        // Call will be tried by another connection service (GSM, CDMA, etc...).
        void onSipNotChosen();
        // Call will be aborted.
        void onCancelCall();
    }

    private final Context mContext;
    private final Callback mCallback;
    private final SipProfileDb mSipProfileDb;
    private List<SipProfile> mProfileList;
    private SipProfile mPrimaryProfile;
    private final Handler mHandler = new Handler();

    SipProfileChooser(Context context, Callback callback) {
        mContext = context;
        mCallback = callback;
        mSipProfileDb = new SipProfileDb(mContext);
    }

    void start(Uri handle, Bundle extras) {
        if (VERBOSE) log("start, handle: " + handle);

        String scheme = handle.getScheme();
        if (!SipUtil.SCHEME_SIP.equals(scheme) && !SipUtil.SCHEME_TEL.equals(scheme)) {
            if (VERBOSE) log("start, unknown scheme");
            mCallback.onSipNotChosen();
            return;
        }

        String phoneNumber = handle.getSchemeSpecificPart();
        // Consider "tel:foo@exmaple.com" a SIP number.
        boolean isSipNumber = SipUtil.SCHEME_SIP.equals(scheme) ||
                PhoneNumberUtils.isUriNumber(phoneNumber);
        if (!SipUtil.isVoipSupported(mContext)) {
            if (isSipNumber) {
                if (VERBOSE) log("start, VOIP not supported, dropping call");
                SipProfileChooserDialogs.showNoVoip(mContext, new ResultReceiver(mHandler) {
                        @Override
                        protected void onReceiveResult(int choice, Bundle resultData) {
                            mCallback.onCancelCall();
                        }
                });
            } else {
                if (VERBOSE) log("start, VOIP not supported");
                mCallback.onSipNotChosen();
            }
            return;
        }

        // Don't use SIP for numbers modified by a gateway.
        if (extras != null && extras.getString(SipUtil.GATEWAY_PROVIDER_PACKAGE) != null) {
            if (VERBOSE) log("start, not using SIP for numbers modified by gateway");
            mCallback.onSipNotChosen();
            return;
        }

        if (!isNetworkConnected()) {
            if (isSipNumber) {
                if (VERBOSE) log("start, network not connected, dropping call");
                SipProfileChooserDialogs.showNoInternetError(mContext,
                        new ResultReceiver(mHandler) {
                            @Override
                            protected void onReceiveResult(int choice, Bundle resultData) {
                                mCallback.onCancelCall();
                            }
                });
            } else {
                if (VERBOSE) log("start, network not connected");
                mCallback.onSipNotChosen();
            }
            return;
        }

        // Only ask user to pick SIP or Cell if they're actually connected to a cell network.
        SipSharedPreferences sipPreferences = new SipSharedPreferences(mContext);
        String callOption = sipPreferences.getSipCallOption();
        if (callOption.equals(Settings.System.SIP_ASK_ME_EACH_TIME) && !isSipNumber &&
                isInCellNetwork()) {
            if (VERBOSE) log("start, call option set to ask, asking");
            ResultReceiver receiver = new ResultReceiver(mHandler) {
                @Override
                protected void onReceiveResult(int choice, Bundle resultData) {
                    if (choice == DialogInterface.BUTTON_NEGATIVE) {
                        mCallback.onCancelCall();
                    } else if (SipProfileChooserDialogs.isSelectedPhoneTypeSip(mContext,
                            choice)) {
                        buildProfileList();
                    } else {
                        mCallback.onSipNotChosen();
                    }
                }
            };
            SipProfileChooserDialogs.showSelectPhoneType(mContext, receiver);
            return;
        }

        if (callOption.equals(Settings.System.SIP_ADDRESS_ONLY) && !isSipNumber) {
            if (VERBOSE) log("start, call option set to SIP only, not a sip number");
            mCallback.onSipNotChosen();
            return;
        }

        if ((mSipProfileDb.getProfilesCount() == 0) && !isSipNumber) {
            if (VERBOSE) log("start, no SIP accounts and not sip number");
            mCallback.onSipNotChosen();
            return;
        }

        if (VERBOSE) log("start, building profile list");
        buildProfileList();
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) {
                return ni.getType() == ConnectivityManager.TYPE_WIFI ||
                        !SipManager.isSipWifiOnly(mContext);
            }
        }
        return false;
    }

    private boolean isInCellNetwork() {
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            // We'd also like to check the radio's power state but there's no public API to do this.
            int phoneType = telephonyManager.getPhoneType();
            return phoneType != TelephonyManager.PHONE_TYPE_NONE &&
                    phoneType != TelephonyManager.PHONE_TYPE_SIP;
        }
        return false;
    }

    private void buildProfileList() {
        if (VERBOSE) log("buildProfileList");
        final SipSharedPreferences preferences = new SipSharedPreferences(mContext);
        new Thread(new Runnable() {
            @Override
            public void run() {
                mProfileList = mSipProfileDb.retrieveSipProfileList();
                if (mProfileList != null) {
                    String primarySipUri = preferences.getPrimaryAccount();
                    for (SipProfile profile : mProfileList) {
                        if (profile.getUriString().equals(primarySipUri)) {
                            mPrimaryProfile = profile;
                            break;
                        }
                    }
                }
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onBuildProfileListDone();
                    }
                });
            }
        }).start();
    }

    /**
      * At this point we definitely want to make a SIP call, we just need to figure out which
      * profile to use.
      */
    private void onBuildProfileListDone() {
        if (VERBOSE) log("onBuildProfileListDone");
        if (mProfileList == null || mProfileList.size() == 0) {
            if (VERBOSE) log("onBuildProfileListDone, no profiles, showing settings dialog");
            ResultReceiver receiver = new ResultReceiver(mHandler) {
                @Override
                protected void onReceiveResult(int choice, Bundle resultData) {
                    if (choice == DialogInterface.BUTTON_POSITIVE) {
                        openSipSettings();
                    }
                    mCallback.onCancelCall();
                }
            };
            SipProfileChooserDialogs.showStartSipSettings(mContext, receiver);
        } else if (mPrimaryProfile == null) {
            if (VERBOSE) log("onBuildProfileListDone, no primary profile, showing select dialog");
            ResultReceiver receiver = new ResultReceiver(mHandler) {
                @Override
                protected void onReceiveResult(int choice, Bundle resultData) {
                    if (choice >= 0 && choice < mProfileList.size()) {
                        SipProfile profile = mProfileList.get(choice);
                        if (SipProfileChooserDialogs.shouldMakeSelectedProflePrimary(mContext,
                                resultData)) {
                            SipSharedPreferences pref = new SipSharedPreferences(mContext);
                            pref.setPrimaryAccount(profile.getUriString());
                        }
                        mCallback.onSipChosen(profile);
                    } else {
                        mCallback.onCancelCall();
                    }
                }
            };
            SipProfileChooserDialogs.showSelectProfile(mContext, mProfileList, receiver);
        } else {
            mCallback.onSipChosen(mPrimaryProfile);
        }
    }

    private void openSipSettings() {
        Intent newIntent = new Intent(mContext, SipSettings.class);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(newIntent);
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
