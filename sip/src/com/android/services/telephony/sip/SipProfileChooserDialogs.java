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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.List;

public final class SipProfileChooserDialogs extends Activity
        implements DialogInterface.OnClickListener,
        DialogInterface.OnCancelListener, CompoundButton.OnCheckedChangeListener {
    private static final String PREFIX = "[SipProfileChooserDialogs] ";
    private static final boolean VERBOSE = true; /* STOP SHIP if true */

    private static final String EXTRA_RESULT_RECEIVER = "result_receiver";
    private static final String EXTRA_DIALOG_ID = "dialog_id";
    private static final String EXTRA_PROFILE_NAMES = "profile_names";
    private static final String EXTRA_MAKE_PRIMARY = "make_primary";

    private static final int DIALOG_SELECT_PHONE_TYPE = 0;
    private static final int DIALOG_SELECT_PROFILE = 1;
    private static final int DIALOG_START_SIP_SETTINGS = 2;
    private static final int DIALOG_NO_INTERNET_ERROR = 3;
    private static final int DIALOG_NO_VOIP = 4;

    private TextView mUnsetPriamryHint;
    private boolean mMakePrimary;

    static void showSelectPhoneType(Context context, ResultReceiver resultReceiver) {
        show(context, DIALOG_SELECT_PHONE_TYPE, null, resultReceiver);
    }

    static void showSelectProfile(Context context, List<SipProfile> profiles,
            ResultReceiver resultReceiver) {
        show(context, DIALOG_SELECT_PROFILE, profiles, resultReceiver);
    }

    static void showStartSipSettings(Context context, ResultReceiver resultReceiver) {
        show(context, DIALOG_START_SIP_SETTINGS, null, resultReceiver);
    }

    static void showNoInternetError(Context context, ResultReceiver resultReceiver) {
        show(context, DIALOG_NO_INTERNET_ERROR, null, resultReceiver);
    }

    static void showNoVoip(Context context, ResultReceiver resultReceiver) {
        show(context, DIALOG_NO_VOIP, null, resultReceiver);
    }

    static boolean isSelectedPhoneTypeSip(Context context, int choice) {
        String[] phoneTypes = context.getResources().getStringArray(R.array.phone_type_values);
        if (choice >= 0 && choice < phoneTypes.length) {
            return phoneTypes[choice].equals(context.getString(R.string.internet_phone));
        }
        return false;
    }

    static boolean shouldMakeSelectedProflePrimary(Context context, Bundle extras) {
        return extras.getBoolean(EXTRA_MAKE_PRIMARY);
    }

    static private void show(final Context context, final int dialogId,
            final List<SipProfile> profiles,
            final ResultReceiver resultReceiver) {
        if (VERBOSE) log("show, starting delayed show, dialogId: " + dialogId);

        // Wait for 1 second before showing the dialog. The sometimes prevents the InCallUI from
        // popping up on top of the dialog. See http://b/16184268
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (VERBOSE) log("show, starting activity");
                Intent intent = new Intent(context, SipProfileChooserDialogs.class)
                        .putExtra(EXTRA_RESULT_RECEIVER, resultReceiver)
                        .putExtra(EXTRA_DIALOG_ID, dialogId)
                        .putExtra(EXTRA_PROFILE_NAMES, getProfileNames(profiles))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            }
        }, 1000);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int dialogId = getIntent().getIntExtra(EXTRA_DIALOG_ID, 0);
        if (VERBOSE) log("onCreate, dialogId: " + dialogId);

        // Allow this activity to be visible in front of the keyguard. (This is only necessary for
        // obscure scenarios like the user initiating a call and then immediately pressing the Power
        // button.)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        showDialog(dialogId);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        Dialog dialog;
        switch(id) {
            case DIALOG_SELECT_PHONE_TYPE:
                dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.pick_outgoing_call_phone_type)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setSingleChoiceItems(R.array.phone_type_values, -1, this)
                        .setNegativeButton(android.R.string.cancel, this)
                        .setOnCancelListener(this)
                        .create();
                break;
            case DIALOG_SELECT_PROFILE:
                String[] profileNames = getIntent().getStringArrayExtra(EXTRA_PROFILE_NAMES);
                dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.pick_outgoing_sip_phone)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setSingleChoiceItems(profileNames, -1, this)
                        .setNegativeButton(android.R.string.cancel, this)
                        .setOnCancelListener(this)
                        .create();
                addMakeDefaultCheckBox(dialog);
                break;
            case DIALOG_START_SIP_SETTINGS:
                dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.no_sip_account_found_title)
                        .setMessage(R.string.no_sip_account_found)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setPositiveButton(R.string.sip_menu_add, this)
                        .setNegativeButton(android.R.string.cancel, this)
                        .setOnCancelListener(this)
                        .create();
                break;
            case DIALOG_NO_INTERNET_ERROR:
                boolean wifiOnly = SipManager.isSipWifiOnly(this);
                dialog = new AlertDialog.Builder(this)
                        .setTitle(wifiOnly ? R.string.no_wifi_available_title
                                           : R.string.no_internet_available_title)
                        .setMessage(wifiOnly ? R.string.no_wifi_available
                                             : R.string.no_internet_available)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setPositiveButton(android.R.string.ok, this)
                        .setOnCancelListener(this)
                        .create();
                break;
            case DIALOG_NO_VOIP:
                dialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.no_voip)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setPositiveButton(android.R.string.ok, this)
                        .setOnCancelListener(this)
                        .create();
                break;
            default:
                dialog = null;
        }
        if (dialog != null) {
            //mDialogs[id] = dialog;
        }
        return dialog;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (VERBOSE) log("onPause");
    }

    @Override
    public void finish() {
        if (VERBOSE) log("finish");
        super.finish();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (VERBOSE) log("onCheckedChanged, isChecked: " + isChecked);
        mMakePrimary = isChecked;
        if (isChecked) {
            mUnsetPriamryHint.setVisibility(View.VISIBLE);
        } else {
            mUnsetPriamryHint.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int id) {
        if (VERBOSE) log("onClick, id: " + id);
        onChoiceMade(id);
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (VERBOSE) log("onCancel");
        onChoiceMade(DialogInterface.BUTTON_NEGATIVE);
    }

    private void onChoiceMade(int choice) {
        ResultReceiver resultReceiver = getIntent().getParcelableExtra(EXTRA_RESULT_RECEIVER);
        if (resultReceiver != null) {
            Bundle extras = new Bundle();
            extras.putBoolean(EXTRA_MAKE_PRIMARY, mMakePrimary);
            resultReceiver.send(choice, extras);
        }
        finish();
    }

    private void addMakeDefaultCheckBox(Dialog dialog) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(com.android.internal.R.layout.always_use_checkbox, null);
        CheckBox makePrimaryCheckBox =
                (CheckBox) view.findViewById(com.android.internal.R.id.alwaysUse);
        makePrimaryCheckBox.setText(R.string.remember_my_choice);
        makePrimaryCheckBox.setOnCheckedChangeListener(this);
        mUnsetPriamryHint = (TextView)view.findViewById(com.android.internal.R.id.clearDefaultHint);
        mUnsetPriamryHint.setText(R.string.reset_my_choice_hint);
        mUnsetPriamryHint.setVisibility(View.GONE);
        ((AlertDialog)dialog).setView(view);
    }

    static private String[] getProfileNames(List<SipProfile> profiles) {
        if (profiles == null) {
            return null;
        }

        String[] entries = new String[profiles.size()];
        int i = 0;
        for (SipProfile p : profiles) {
            entries[i++] = p.getProfileName();
        }
        return entries;
    }

    private static void log(String msg) {
        Log.d(SipUtil.LOG_TAG, PREFIX + msg);
    }
}
