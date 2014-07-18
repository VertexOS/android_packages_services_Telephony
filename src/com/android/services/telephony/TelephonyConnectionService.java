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
import android.content.Context;
import android.net.Uri;
import android.os.Debug;
import android.telecomm.PhoneAccountMetadata;
import android.telecomm.TelecommManager;
import android.telephony.DisconnectCause;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.telecomm.CallCapabilities;
import android.telecomm.Connection;
import android.telecomm.ConnectionRequest;
import android.telecomm.ConnectionService;
import android.telecomm.PhoneAccount;
import android.telecomm.Response;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

/**
 * Service for making GSM and CDMA connections.
 */
public class TelephonyConnectionService extends ConnectionService {
    private static String SCHEME_TEL = "tel";

    private EmergencyCallHelper mEmergencyCallHelper;

    @Override
    protected void onCreateOutgoingConnection(
            final ConnectionRequest request,
            final CreateConnectionResponse<Connection> response) {
        Log.v(this, "onCreateOutgoingConnection, request: " + request);

        Uri handle = request.getHandle();
        if (handle == null) {
            Log.d(this, "onCreateOutgoingConnection, handle is null");
            response.onFailure(request, DisconnectCause.NO_PHONE_NUMBER_SUPPLIED, "Handle is null");
            return;
        }

        if (!SCHEME_TEL.equals(handle.getScheme())) {
            Log.d(this, "onCreateOutgoingConnection, Handle %s is not type tel",
                    handle.getScheme());
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED,
                    "Handle scheme is not type tel");
            return;
        }

        final String number = handle.getSchemeSpecificPart();
        if (TextUtils.isEmpty(number)) {
            Log.d(this, "onCreateOutgoingConnection, unable to parse number");
            response.onFailure(request, DisconnectCause.INVALID_NUMBER, "Unable to parse number");
            return;
        }

        final Phone phone = PhoneFactory.getDefaultPhone();
        if (phone == null) {
            Log.d(this, "onCreateOutgoingConnection, phone is null");
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED, "Phone is null");
            return;
        }

        boolean isEmergencyNumber = PhoneNumberUtils.isPotentialEmergencyNumber(number);
        if (!isEmergencyNumber) {
            int state = phone.getServiceState().getState();
            switch (state) {
                case ServiceState.STATE_IN_SERVICE:
                    break;
                case ServiceState.STATE_OUT_OF_SERVICE:
                    response.onFailure(request, DisconnectCause.OUT_OF_SERVICE,
                            "ServiceState.STATE_OUT_OF_SERVICE");
                    return;
                case ServiceState.STATE_EMERGENCY_ONLY:
                    response.onFailure(request, DisconnectCause.EMERGENCY_ONLY,
                            "ServiceState.STATE_EMERGENCY_ONLY");
                    return;
                case ServiceState.STATE_POWER_OFF:
                    response.onFailure(request, DisconnectCause.POWER_OFF,
                            "ServiceState.STATE_POWER_OFF");
                    return;
                default:
                    Log.d(this, "onCreateOutgoingConnection, unkown service state: %d", state);
                    response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED,
                            "Unkown service state " + state);
                    return;
            }
        }

        if (isEmergencyNumber) {
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
                                startOutgoingCall(request, response, phone, number);
                            } else {
                                Log.d(this, "onCreateOutgoingConnection, failed to turn on radio");
                                response.onFailure(request, DisconnectCause.POWER_OFF,
                                        "Failed to turn on radio.");
                            }
                        }
            });
            return;
        }

        startOutgoingCall(request, response, phone, number);
    }

    @Override
    protected void onCreateConferenceConnection(
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
    protected void onCreateIncomingConnection(
            ConnectionRequest request,
            CreateConnectionResponse<Connection> response) {
        Log.v(this, "onCreateIncomingConnection, request: " + request);

        Phone phone = PhoneFactory.getDefaultPhone();
        Call call = phone.getRingingCall();
        if (!call.getState().isRinging()) {
            Log.v(this, "onCreateIncomingConnection, no ringing call");
            response.onFailure(request, DisconnectCause.INCOMING_MISSED, "Found no ringing call");
            return;
        }

        com.android.internal.telephony.Connection originalConnection = call.getEarliestConnection();
        if (isOriginalConnectionKnown(originalConnection)) {
            Log.v(this, "onCreateIncomingConnection, original connection already registered");
            response.onCancel(request);
            return;
        }

        Uri handle = getHandleFromAddress(originalConnection.getAddress());
        ConnectionRequest telephonyRequest = new ConnectionRequest(
                request.getAccount(),
                request.getCallId(),
                handle,
                originalConnection.getNumberPresentation(),
                request.getExtras(),
                request.getVideoState());

        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            response.onSuccess(telephonyRequest, new GsmConnection(originalConnection));
        } else if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            response.onSuccess(telephonyRequest, new CdmaConnection(originalConnection));
        } else {
            response.onCancel(request);
        }
    }

    @Override
    protected void onConnectionAdded(Connection connection) {
        Log.v(this, "onConnectionAdded, connection: " + connection);
        if (connection instanceof TelephonyConnection) {
            ((TelephonyConnection) connection).onAddedToCallService(this);
        }
    }

    @Override
    protected void onConnectionRemoved(Connection connection) {
        Log.v(this, "onConnectionRemoved, connection: " + connection);
        if (connection instanceof TelephonyConnection) {
            ((TelephonyConnection) connection).onRemovedFromCallService();
        }
    }

    private void startOutgoingCall(
            ConnectionRequest request,
            CreateConnectionResponse<Connection> response,
            Phone phone,
            String number) {
        Log.v(this, "startOutgoingCall");

        com.android.internal.telephony.Connection originalConnection;
        try {
            originalConnection = phone.dial(number, request.getVideoState());
        } catch (CallStateException e) {
            Log.e(this, e, "startOutgoingCall, phone.dial exception: " + e);
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED, e.getMessage());
            return;
        }

        if (originalConnection == null) {
            Log.d(this, "startOutgoingCall, phone.dial returned null");
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED, "Connection is null");
            return;
        }

        ConnectionRequest telephonyRequest = new ConnectionRequest(
                request.getAccount(),
                request.getCallId(),
                request.getHandle(),
                request.getHandlePresentation(),
                request.getExtras(),
                request.getVideoState());

        if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            response.onSuccess(telephonyRequest, new GsmConnection(originalConnection));
        } else if (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA) {
            response.onSuccess(telephonyRequest, new CdmaConnection(originalConnection));
        } else {
            // TODO(ihab): Tear down 'originalConnection' here, or move recognition of
            // getPhoneType() earlier in this method before we've already asked phone to dial()
            response.onFailure(request, DisconnectCause.ERROR_UNSPECIFIED, "Invalid phone type");
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

    static Uri getHandleFromAddress(String address) {
        // Address can be null for blocked calls.
        if (address == null) {
            address = "";
        }
        return Uri.fromParts(SCHEME_TEL, address, null);
    }
}
