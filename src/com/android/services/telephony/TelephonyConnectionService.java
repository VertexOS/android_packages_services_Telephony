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

package com.android.services.telephony;

import android.content.ComponentName;
import android.net.Uri;
import android.telecomm.CallCapabilities;
import android.telecomm.Connection;
import android.telecomm.ConnectionRequest;
import android.telecomm.ConnectionService;
import android.telecomm.PhoneAccountHandle;
import android.telecomm.Response;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.PhoneFactory;
import com.android.internal.telephony.SubscriptionController;

import java.util.Objects;

/**
 * Service for making GSM and CDMA connections.
 */
public class TelephonyConnectionService extends ConnectionService {
    static String SCHEME_TEL = "tel";

    private ComponentName mExpectedComponentName = null;
    private EmergencyCallHelper mEmergencyCallHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        mExpectedComponentName = new ComponentName(this, this.getClass());
    }

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            final ConnectionRequest request) {
        Log.v(this, "onCreateOutgoingConnection, request: " + request);

        Uri handle = request.getHandle();
        if (handle == null) {
            Log.d(this, "onCreateOutgoingConnection, handle is null");
            return Connection.getFailedConnection(DisconnectCause.NO_PHONE_NUMBER_SUPPLIED,
                    "Handle is null");
        }

        if (!SCHEME_TEL.equals(handle.getScheme())) {
            Log.d(this, "onCreateOutgoingConnection, Handle %s is not type tel",
                    handle.getScheme());
            return Connection.getFailedConnection(DisconnectCause.INVALID_NUMBER,
                    "Handle scheme is not type tel");
        }

        final String number = handle.getSchemeSpecificPart();
        if (TextUtils.isEmpty(number)) {
            Log.d(this, "onCreateOutgoingConnection, unable to parse number");
            return Connection.getFailedConnection(DisconnectCause.INVALID_NUMBER,
                    "Unable to parse number");
        }

        boolean isEmergencyNumber = PhoneNumberUtils.isPotentialEmergencyNumber(number);

        // Get the right phone object from the account data passed in.
        final Phone phone = getPhoneForAccount(request.getAccountHandle(), isEmergencyNumber);
        if (phone == null) {
            Log.d(this, "onCreateOutgoingConnection, phone is null");
            return Connection.getFailedConnection(DisconnectCause.OUTGOING_FAILURE,
                    "Phone is null");
        }

        if (!isEmergencyNumber) {
            int state = phone.getServiceState().getState();
            switch (state) {
                case ServiceState.STATE_IN_SERVICE:
                case ServiceState.STATE_EMERGENCY_ONLY:
                    break;
                case ServiceState.STATE_OUT_OF_SERVICE:
                    return Connection.getFailedConnection(DisconnectCause.OUT_OF_SERVICE,
                            "ServiceState.STATE_OUT_OF_SERVICE");
                case ServiceState.STATE_POWER_OFF:
                    return Connection.getFailedConnection(DisconnectCause.POWER_OFF,
                            "ServiceState.STATE_POWER_OFF");
                default:
                    Log.d(this, "onCreateOutgoingConnection, unkown service state: %d", state);
                    return Connection.getFailedConnection(DisconnectCause.OUTGOING_FAILURE,
                            "Unknown service state " + state);
            }
        }

        if (isEmergencyNumber) {
            final Connection emergencyConnection = startOutgoingCall(request, phone, number);

            // Start the emergency call in the initializing state to wait for the radio to spin up.
            emergencyConnection.setInitializing();

            Log.d(this, "onCreateOutgoingConnection, doing startTurnOnRadioSequence for " +
                    "emergency number");
            if (mEmergencyCallHelper == null) {
                mEmergencyCallHelper = new EmergencyCallHelper(this);
            }
            mEmergencyCallHelper.startTurnOnRadioSequence(phone,
                    new EmergencyCallHelper.Callback() {
                        @Override
                        public void onComplete(boolean isRadioReady) {
                            if (isRadioReady) {
                                emergencyConnection.setInitialized();
                            } else {
                                Log.d(this, "onCreateOutgoingConnection, failed to turn on radio");
                                emergencyConnection.setFailed(DisconnectCause.POWER_OFF,
                                        "Failed to turn on radio.");
                            }
                        }
                    });

            return emergencyConnection;
        }

        return startOutgoingCall(request, phone, number);
    }

    @Override
    public void onCreateConferenceConnection(
            String token,
            Connection connection,
            Response<String, Connection> response) {
        Log.v(this, "onCreateConferenceConnection, connection: " + connection);
        if (connection instanceof GsmConnection || connection instanceof ConferenceConnection) {
            if ((connection.getCallCapabilities() & CallCapabilities.MERGE_CALLS) != 0) {
                response.onResult(token,
                        GsmConferenceController.createConferenceConnection(connection));
            }
        }
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.v(this, "onCreateIncomingConnection, request: " + request);

        Phone phone = getPhoneForAccount(request.getAccountHandle(), false);
        if (phone == null) {
            return Connection.getFailedConnection(DisconnectCause.ERROR_UNSPECIFIED, null);
        }

        Call call = phone.getRingingCall();
        if (!call.getState().isRinging()) {
            Log.v(this, "onCreateIncomingConnection, no ringing call");
            return Connection.getFailedConnection(DisconnectCause.INCOMING_MISSED,
                    "Found no ringing call");
        }

        com.android.internal.telephony.Connection originalConnection = call.getEarliestConnection();
        if (isOriginalConnectionKnown(originalConnection)) {
            Log.v(this, "onCreateIncomingConnection, original connection already registered");
            return Connection.getCanceledConnection();
        }

        TelephonyConnection connection = null;
        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            connection = new GsmConnection(originalConnection);
        } else if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            connection = new CdmaConnection(originalConnection);
        }

        if (connection == null) {
            return Connection.getCanceledConnection();
        } else {
            return connection;
        }
    }

    private Connection startOutgoingCall(
            ConnectionRequest request,
            Phone phone,
            String number) {
        Log.v(this, "startOutgoingCall");

        com.android.internal.telephony.Connection originalConnection;
        try {
            originalConnection = phone.dial(number, request.getVideoState());
        } catch (CallStateException e) {
            Log.e(this, e, "startOutgoingCall, phone.dial exception: " + e);
            return Connection.getFailedConnection(DisconnectCause.OUTGOING_FAILURE, e.getMessage());
        }

        if (originalConnection == null) {
            int disconnectCause = DisconnectCause.OUTGOING_FAILURE;

            // On GSM phones, null connection means that we dialed an MMI code
            if (phone.getPhoneType() == PhoneConstants.PHONE_TYPE_GSM) {
                disconnectCause = DisconnectCause.DIALED_MMI;
            }
            Log.d(this, "startOutgoingCall, phone.dial returned null");
            return Connection.getFailedConnection(disconnectCause, "Connection is null");
        }

        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            return new GsmConnection(originalConnection);
        } else if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            return new CdmaConnection(originalConnection);
        } else {
            // TODO(ihab): Tear down 'originalConnection' here, or move recognition of
            // getPhoneType() earlier in this method before we've already asked phone to dial()
            return Connection.getFailedConnection(DisconnectCause.OUTGOING_FAILURE,
                    "Invalid phone type");
        }
    }

    private boolean isOriginalConnectionKnown(
            com.android.internal.telephony.Connection originalConnection) {
        for (Connection connection : getAllConnections()) {
            TelephonyConnection telephonyConnection = (TelephonyConnection) connection;
            if (connection instanceof TelephonyConnection) {
                if (telephonyConnection.getOriginalConnection() == originalConnection) {
                    return true;
                }
            }
        }
        return false;
    }

    private Phone getPhoneForAccount(PhoneAccountHandle accountHandle, boolean isEmergency) {
        if (isEmergency) {
            return PhoneFactory.getDefaultPhone();
        }

        if (Objects.equals(mExpectedComponentName, accountHandle.getComponentName())) {
            if (accountHandle.getId() != null) {
                try {
                    int phoneId = SubscriptionController.getInstance().getPhoneId(
                            Long.parseLong(accountHandle.getId()));
                    return PhoneFactory.getPhone(phoneId);
                } catch (NumberFormatException e) {
                    Log.w(this, "Could not get subId from account: " + accountHandle.getId());
                }
            }
        }
        return null;
    }
}
