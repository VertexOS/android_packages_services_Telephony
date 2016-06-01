/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.phone.settings;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Network;
import android.preference.DialogPreference;
import android.telecom.PhoneAccountHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.android.phone.R;
import com.android.phone.common.mail.MessagingException;
import com.android.phone.vvm.omtp.OmtpConstants;
import com.android.phone.vvm.omtp.OmtpConstants.ChangePinResult;
import com.android.phone.vvm.omtp.imap.ImapHelper;
import com.android.phone.vvm.omtp.sync.VvmNetworkRequestCallback;

/**
 * Dialog to change the voicemail PIN. The TUI PIN is used when accessing traditional voicemail through
 * phone call.
 */
public class VoicemailChangePinDialogPreference extends DialogPreference {

    private static final String TAG = "VmChangePinDialog";

    private EditText mOldPin;
    private EditText mNewPin;
    private PhoneAccountHandle mPhoneAccountHandle;

    private ProgressDialog mProgressDialog;

    public VoicemailChangePinDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public VoicemailChangePinDialogPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected View onCreateDialogView() {
        setDialogLayoutResource(R.layout.voicemail_dialog_change_pin);

        View dialog = super.onCreateDialogView();

        mOldPin = (EditText) dialog.findViewById(R.id.vm_old_pin);
        mNewPin = (EditText) dialog.findViewById(R.id.vm_new_pin);

        return dialog;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            processPinChange();
        }
        super.onDialogClosed(positiveResult);
    }

    public VoicemailChangePinDialogPreference setPhoneAccountHandle(PhoneAccountHandle handle) {
        mPhoneAccountHandle = handle;
        return this;
    }

    private void processPinChange() {
        mProgressDialog = new ProgressDialog(getContext());
        mProgressDialog.setCancelable(false);
        mProgressDialog.setMessage(getContext().getString(R.string.vm_change_pin_progress_message));
        mProgressDialog.show();

        ChangePinNetworkRequestCallback callback = new ChangePinNetworkRequestCallback();
        callback.requestNetwork();
    }

    private void finishPinChange() {
        mProgressDialog.dismiss();
    }

    private void showError(@ChangePinResult int result) {
        if (result != OmtpConstants.CHANGE_PIN_SUCCESS) {
            CharSequence message;
            switch (result) {
                case OmtpConstants.CHANGE_PIN_TOO_SHORT:
                    message = getContext().getString(R.string.vm_change_pin_error_too_short);
                    break;
                case OmtpConstants.CHANGE_PIN_TOO_LONG:
                    message = getContext().getString(R.string.vm_change_pin_error_too_long);
                    break;

                case OmtpConstants.CHANGE_PIN_TOO_WEAK:
                    message = getContext().getString(R.string.vm_change_pin_error_too_weak);
                    break;
                case OmtpConstants.CHANGE_PIN_INVALID_CHARACTER:
                    message = getContext().getString(R.string.vm_change_pin_error_invalid);
                    break;
                case OmtpConstants.CHANGE_PIN_MISMATCH:
                    message = getContext().getString(R.string.vm_change_pin_error_mismatch);
                    break;
                case OmtpConstants.CHANGE_PIN_SYSTEM_ERROR:
                    message = getContext().getString(R.string.vm_change_pin_error_system_error);
                    break;
                default:
                    Log.wtf(TAG, "Unexpected ChangePinResult " + result);
                    return;
            }
            new AlertDialog.Builder(getContext())
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private class ChangePinNetworkRequestCallback extends VvmNetworkRequestCallback {

        public ChangePinNetworkRequestCallback() {
            super(getContext(), mPhoneAccountHandle);
        }

        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            ImapHelper helper = new ImapHelper(getContext(), mPhoneAccountHandle, network);
            try {
                @ChangePinResult int result =
                        helper.changePin(mOldPin.getText().toString(),
                                mNewPin.getText().toString());
                finishPinChange();
                if (result != OmtpConstants.CHANGE_PIN_SUCCESS) {
                    showError(result);
                }
            } catch (MessagingException e) {
                finishPinChange();
                showError(OmtpConstants.CHANGE_PIN_SYSTEM_ERROR);
            }

        }

        @Override
        public void onFailed(String reason) {
            super.onFailed(reason);
            finishPinChange();
            showError(OmtpConstants.CHANGE_PIN_SYSTEM_ERROR);
        }
    }
}
