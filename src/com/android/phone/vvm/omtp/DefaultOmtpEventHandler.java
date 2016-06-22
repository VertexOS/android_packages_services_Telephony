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
 * limitations under the License
 */

package com.android.phone.vvm.omtp;

import android.content.Context;
import android.provider.VoicemailContract;
import android.provider.VoicemailContract.Status;

import com.android.phone.VoicemailStatus;
import com.android.phone.vvm.omtp.OmtpEvents.Type;

public class DefaultOmtpEventHandler {

    private static final String TAG = "DefErrorCodeHandler";

    public static void handleEvent(Context context, int subId, OmtpEvents event) {
        switch (event.getType()) {
            case Type.CONFIGURATION:
                handleConfigurationEvent(context, subId, event);
                break;
            case Type.DATA_CHANNEL:
                handleDataChannelEvent(context, subId, event);
                break;
            case Type.NOTIFICATION_CHANNEL:
                handleNotificationChannelEvent(context, subId, event);
                break;
            case Type.OTHER:
                handleOtherEvent(context, subId, event);
                break;
            default:
                VvmLog.wtf(TAG, "invalid event type " + event.getType() + " for " + event);
        }
    }

    private static void handleConfigurationEvent(Context context, int subId,
            OmtpEvents event) {
        switch (event) {
            case CONFIG_REQUEST_STATUS_SUCCESS:
                VoicemailStatus.edit(context, subId)
                        .setConfigurationState(VoicemailContract.Status.CONFIGURATION_STATE_OK)
                        .setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_OK)
                        .apply();
                break;
            default:
                VvmLog.wtf(TAG, "invalid configuration event " + event);
        }
    }

    private static void handleDataChannelEvent(Context context, int subId,
            OmtpEvents event) {
        switch (event) {
            case DATA_IMAP_OPERATION_COMPLETED:
                VoicemailStatus.edit(context, subId)
                        .setDataChannelState(Status.DATA_CHANNEL_STATE_OK)
                        .apply();
                break;

            case DATA_NO_CONNECTION:
                VoicemailStatus.edit(context, subId)
                        .setDataChannelState(Status.DATA_CHANNEL_STATE_NO_CONNECTION)
                        .apply();
                break;

            case DATA_NO_CONNECTION_CELLULAR_REQUIRED:
                VoicemailStatus.edit(context, subId)
                        .setDataChannelState(
                                Status.DATA_CHANNEL_STATE_NO_CONNECTION_CELLULAR_REQUIRED)
                        .apply();
                break;
            case DATA_INVALID_PORT:
                VoicemailStatus.edit(context, subId)
                        .setDataChannelState(
                                VoicemailContract.Status.DATA_CHANNEL_STATE_BAD_CONFIGURATION)
                        .apply();
                break;
            case DATA_CANNOT_RESOLVE_HOST_ON_NETWORK:
                VoicemailStatus.edit(context, subId)
                        .setDataChannelState(
                                VoicemailContract.Status.DATA_CHANNEL_STATE_SERVER_CONNECTION_ERROR)
                        .apply();
                break;
            case DATA_SSL_INVALID_HOST_NAME:
            case DATA_CANNOT_ESTABLISH_SSL_SESSION:
            case DATA_IOE_ON_OPEN:
                VoicemailStatus.edit(context, subId)
                        .setDataChannelState(
                                VoicemailContract.Status.DATA_CHANNEL_STATE_COMMUNICATION_ERROR)
                        .apply();
                break;
            case DATA_BAD_IMAP_CREDENTIAL:
                VoicemailStatus.edit(context, subId)
                        .setDataChannelState(
                                VoicemailContract.Status.DATA_CHANNEL_STATE_BAD_CONFIGURATION)
                        .apply();
                break;

            case DATA_REJECTED_SERVER_RESPONSE:
            case DATA_INVALID_INITIAL_SERVER_RESPONSE:
            case DATA_SSL_EXCEPTION:
            case DATA_ALL_SOCKET_CONNECTION_FAILED:
                VoicemailStatus.edit(context, subId)
                        .setDataChannelState(
                                VoicemailContract.Status.DATA_CHANNEL_STATE_SERVER_ERROR)
                        .apply();
                break;

            default:
                VvmLog.wtf(TAG, "invalid data channel event " + event);
        }
    }

    private static void handleNotificationChannelEvent(Context context, int subId,
            OmtpEvents event) {
        switch (event) {
            case NOTIFICATION_IN_SERVICE:
                VoicemailStatus.edit(context, subId)
                        .setNotificationChannelState(Status.NOTIFICATION_CHANNEL_STATE_OK)
                        .apply();
                break;
            case NOTIFICATION_SERVICE_LOST:
                VoicemailStatus.edit(context, subId)
                        .setNotificationChannelState(
                                Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION)
                        .apply();
                break;
            default:
                VvmLog.wtf(TAG, "invalid notification channel event " + event);
        }
    }

    private static void handleOtherEvent(Context context, int subId, OmtpEvents event) {
        switch (event) {
            case OTHER_SOURCE_REMOVED:
                VoicemailStatus.edit(context, subId)
                        .setConfigurationState(Status.CONFIGURATION_STATE_NOT_CONFIGURED)
                        .setNotificationChannelState(
                                Status.NOTIFICATION_CHANNEL_STATE_NO_CONNECTION)
                        .setDataChannelState(Status.DATA_CHANNEL_STATE_NO_CONNECTION)
                        .apply();
                break;
            default:
                VvmLog.wtf(TAG, "invalid other event " + event);
        }
    }
}
