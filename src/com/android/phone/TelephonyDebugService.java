/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.phone;

import com.android.internal.telephony.DebugService;
import com.android.internal.telephony.ITelephonyDebug;
import com.android.internal.telephony.TelephonyEventLog;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import static com.android.internal.telephony.RILConstants.*;

/**
 * A debug service for telephony.
 */
public class TelephonyDebugService extends Service {
    private static String TAG = "TelephonyDebugService";
    private static final boolean DBG = true;
    private static final boolean VDBG = true;
    private DebugService mDebugService = new DebugService();

    public static final String JSON_KEY_TAG = "tag";
    public static final String JSON_KEY_REG_STATE = "reg-state";
    public static final String JSON_KEY_DATA_REG_STATE = "data-reg-state";
    public static final String JSON_KEY_ROAMING_TYPE = "roaming-type";
    public static final String JSON_KEY_DATA_ROAMING_TYPE = "data-roaming-type";
    public static final String JSON_KEY_OPERATOR_ALPHA_LONG = "operator-alpha-long";
    public static final String JSON_KEY_OPERATOR_ALPHA_SHORT = "operator-alpha-short";
    public static final String JSON_KEY_OPERATOR_NUMERIC = "operator-numeric";
    public static final String JSON_KEY_DATA_OPERATOR_ALPHA_LONG = "data-operator-alpha-long";
    public static final String JSON_KEY_DATA_OPERATOR_ALPHA_SHORT = "data-operator-alpha-short";
    public static final String JSON_KEY_DATA_OPERATOR_NUMERIC = "data-operator-numeric";
    public static final String JSON_KEY_RAT = "rat";
    public static final String JSON_KEY_DATA_RAT = "data-rat";
    public static final String JSON_KEY_STATE = "state";
    public static final String JSON_KEY_REASON_INFO = "reason_info";
    public static final String JSON_KEY_REASON_INFO_CODE = "code";
    public static final String JSON_KEY_REASON_INFO_EXTRA_CODE = "extra_code";
    public static final String JSON_KEY_REASON_INFO_EXTRA_MESSAGE = "extra_message";
    public static final String JSON_KEY_VOLTE = "VoLTE";
    public static final String JSON_KEY_VILTE = "ViLTE";
    public static final String JSON_KEY_VOWIFI = "VoWiFi";
    public static final String JSON_KEY_VIWIFI = "ViWiFi";
    public static final String JSON_KEY_UTLTE = "UTLTE";
    public static final String JSON_KEY_UTWIFI = "UTWiFi";
    public static final String JSON_KEY_DATA_CALLS = "data-calls";
    public static final String JSON_KEY_STATUS = "status";
    public static final String JSON_KEY_CID = "cid";
    public static final String JSON_KEY_ACTIVE = "active";
    public static final String JSON_KEY_TYPE = "type";
    public static final String JSON_KEY_IFNAME = "ifname";
    public static final String JSON_KEY_SERIAL = "serial";
    public static final String JSON_KEY_PROFILE = "profile";
    public static final String JSON_KEY_APN = "apn";
    public static final String JSON_KEY_PROTOCOL = "protocol";
    public static final String JSON_KEY_REASON = "reason";
    public static final String JSON_KEY_CLIR_MODE = "clirMode";
    public static final String JSON_KEY_EVT = "evt";
    public static final String JSON_KEY_GSM_INDEX = "gsmIndex";
    public static final String JSON_KEY_RETRY = "retry";
    public static final String JSON_KEY_SMS_MESSAGE_REF = "messageRef";
    public static final String JSON_KEY_SMS_ERROR_CODE = "errorCode";
    public static final String JSON_KEY_RIL_ERROR = "error";
    public static final String JSON_KEY_CALL_ID = "call-id";
    public static final String JSON_KEY_SRC_TECH = "src-tech";
    public static final String JSON_KEY_TARGET_TECH = "target-tech";

    /** Constructor */
    public TelephonyDebugService() {
        if (DBG) Log.d(TAG, "TelephonyDebugService()");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) Log.d(TAG, "onBind()");
        return mBinder;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        boolean dumpEvents = false;
        if (args != null) {
            for (String arg : args) {
                if ("--events".equals(arg)) {
                    dumpEvents = true;
                } else if ("--reset-events".equals(arg)) {
                    synchronized (mEvents) {
                        mEvents.clear();
                    }
                    pw.println("TelephonyDebugService reset.");
                    return;
                } else if ("-h".equals(arg)) {
                    dumpHelp(pw);
                    return;
                } else {
                    pw.println("Unknown option: " + arg);
                    dumpHelp(pw);
                    return;
                }
            }
        }

        if (dumpEvents) {
            synchronized (mEvents) {
                pw.println("{\"version\": \"1.0\"," +
                        "\"events\": [");
                for (Event e : mEvents) {
                    pw.println(e.toJson());
                }
                pw.println("]}");
            }
        } else {
            mDebugService.dump(fd, pw, args);
        }
    }

    private static void dumpHelp(PrintWriter pw) {
        pw.println("TelephonyDebugService dump options:");
        pw.println("  [--events] [--reset-events] [-h]");
        pw.println("  --events: dump events in JSON format.");
        pw.println("  --reset-events: reset the stats, clearing all current data.");
        pw.println("  -h: print this help text.");
    }

    class Event {

        public static final String JSON_TAG_SETTINGS = "SETTINGS";
        public static final String JSON_TAG_SERVICE_STATE = "SERVICE_STATE";
        public static final String JSON_TAG_IMS_CONNECTION_STATE = "IMS_CONNECTION_STATE";
        public static final String JSON_TAG_IMS_CAPABILITIES = "IMS_CAPABILITIES";
        public static final String JSON_TAG_DATA_CALL_LIST = "DATA_CALL_LIST";
        public static final String JSON_TAG_RIL_REQUEST_SETUP_DATA_CALL
                = "RIL_REQUEST_SETUP_DATA_CALL";
        public static final String JSON_TAG_RIL_REQUEST_DEACTIVATE_DATA_CALL
                = "RIL_REQUEST_DEACTIVATE_DATA_CALL";
        public static final String JSON_TAG_RIL_REQUEST_DIAL = "RIL_REQUEST_DIAL";
        public static final String JSON_TAG_RIL_REQUEST_HANGUP = "RIL_REQUEST_HANGUP";
        public static final String JSON_TAG_RIL_REQUEST_ANSWER = "RIL_REQUEST_ANSWER";
        public static final String JSON_TAG_RIL_REQUEST_SEND_SMS = "RIL_REQUEST_SEND_SMS";
        public static final String JSON_TAG_RIL_RESPONSE_SETUP_DATA_CALL
                = "RIL_RESPONSE_SETUP_DATA_CALL";
        public static final String JSON_TAG_RIL_UNSOL_CALL_RING = "RIL_UNSOL_CALL_RING";
        public static final String JSON_TAG_RIL_UNSOL_SRVCC_STATE_NOTIFY
                = "RIL_UNSOL_SRVCC_STATE_NOTIFY";
        public static final String JSON_TAG_RIL_UNSOL_RESPONSE_NEW_SMS
                = "RIL_UNSOL_RESPONSE_NEW_SMS";
        public static final String JSON_TAG_RIL_UNSOL_RESPONSE_CDMA_NEW_SMS
                = "RIL_UNSOL_RESPONSE_CDMA_NEW_SMS";
        public static final String JSON_TAG_IMS_CALL = "IMS_CALL";
        public static final String JSON_TAG_IMS_CALL_HANDOVER = "IMS_CALL_HANDOVER";
        public static final String JSON_TAG_IMS_CALL_STATE = "IMS_CALL_STATE";
        public static final String JSON_TAG_PHONE_STATE = "PHONE_STATE";
        public static final String JSON_TAG_SMS = "SMS";

        public long timestamp;
        public int phoneId;
        public int tag;
        public int param1;
        public int param2;
        public Bundle data;

        public Event(long timestamp, int phoneId, int tag, int param1, int param2, Bundle data) {
            this.timestamp = timestamp;
            this.phoneId = phoneId;
            this.tag = tag;
            this.param1 = param1;
            this.param2 = param2;
            this.data = data;
        }

        public String imsCallEventToString(int evt) {
            switch (evt) {
                case TelephonyEventLog.TAG_IMS_CALL_START: return "START";
                case TelephonyEventLog.TAG_IMS_CALL_START_CONFERENCE: return "START_CONFERENCE";
                case TelephonyEventLog.TAG_IMS_CALL_RECEIVE: return "RECEIVE";
                case TelephonyEventLog.TAG_IMS_CALL_ACCEPT: return "ACCEPT";
                case TelephonyEventLog.TAG_IMS_CALL_REJECT: return "REJECT";
                case TelephonyEventLog.TAG_IMS_CALL_TERMINATE: return "TERMINATE";
                case TelephonyEventLog.TAG_IMS_CALL_HOLD: return "HOLD";
                case TelephonyEventLog.TAG_IMS_CALL_RESUME: return "RESUME";
                case TelephonyEventLog.TAG_IMS_CALL_MERGE: return "MERGE";
                case TelephonyEventLog.TAG_IMS_CALL_UPDATE: return "UPDATE";
                case TelephonyEventLog.TAG_IMS_CALL_PROGRESSING: return "PROGRESSING";
                case TelephonyEventLog.TAG_IMS_CALL_STARTED: return "STARTED";
                case TelephonyEventLog.TAG_IMS_CALL_START_FAILED: return "START_FAILED";
                case TelephonyEventLog.TAG_IMS_CALL_TERMINATED: return "TERMINATED";
                case TelephonyEventLog.TAG_IMS_CALL_HELD: return "HELD";
                case TelephonyEventLog.TAG_IMS_CALL_HOLD_FAILED: return "HOLD_FAILED";
                case TelephonyEventLog.TAG_IMS_CALL_HOLD_RECEIVED: return "HOLD_RECEIVED";
                case TelephonyEventLog.TAG_IMS_CALL_RESUMED: return "RESUMED";
                case TelephonyEventLog.TAG_IMS_CALL_RESUME_FAILED: return "RESUME_FAILED";
                case TelephonyEventLog.TAG_IMS_CALL_RESUME_RECEIVED: return "RESUME_RECEIVED";
                case TelephonyEventLog.TAG_IMS_CALL_UPDATED: return "UPDATED";
                case TelephonyEventLog.TAG_IMS_CALL_UPDATE_FAILED: return "UPDATE_FAILED";
                case TelephonyEventLog.TAG_IMS_CALL_MERGED: return "MERGED";
                case TelephonyEventLog.TAG_IMS_CALL_MERGE_FAILED: return "MERGE_FAILED";
                case TelephonyEventLog.TAG_IMS_CALL_HANDOVER: return "HANDOVER";
                case TelephonyEventLog.TAG_IMS_CALL_HANDOVER_FAILED: return "HANDOVER_FAILED";
                case TelephonyEventLog.TAG_IMS_CALL_TTY_MODE_RECEIVED: return "TTY_MODE_RECEIVED";
                case TelephonyEventLog.TAG_IMS_CONFERENCE_PARTICIPANTS_STATE_CHANGED:
                    return "CONFERENCE_PARTICIPANTS_STATE_CHANGED";
                case TelephonyEventLog.TAG_IMS_MULTIPARTY_STATE_CHANGED:
                    return "MULTIPARTY_STATE_CHANGED";
                case TelephonyEventLog.TAG_IMS_CALL_STATE: return "STATE";
            }
            return "UNKNOWN("+evt+")";
        }

        public String rilResponseToString(int evt) {
            switch (evt) {
                case RIL_REQUEST_DEACTIVATE_DATA_CALL: return "RIL_RESPONSE_DEACTIVATE_DATA_CALL";
                case RIL_REQUEST_HANGUP: return "RIL_RESPONSE_HANGUP";
                case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND: return "RIL_RESPONSE_HANGUP_WAITING_OR_BACKGROUND";
                case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND: return "RIL_RESPONSE_HANGUP_FOREGROUND_RESUME_BACKGROUND";
                case RIL_REQUEST_DIAL: return "RIL_RESPONSE_DIAL";
                case RIL_REQUEST_ANSWER: return "RIL_RESPONSE_ANSWER";
                case RIL_REQUEST_SEND_SMS: return "RIL_RESPONSE_SEND_SMS";
                case RIL_REQUEST_SEND_SMS_EXPECT_MORE: return "RIL_RESPONSE_SEND_SMS_EXPECT_MORE";
                case RIL_REQUEST_CDMA_SEND_SMS: return "RIL_RESPONSE_CDMA_SEND_SMS";
                case RIL_REQUEST_IMS_SEND_SMS: return "RIL_RESPONSE_IMS_SEND_SMS";
            }
            return "UNKNOWN("+evt+")";
        }

        public String toString() {
            return String.format("%d,%d,%d,%d,%d,%s",
                    timestamp, phoneId, tag, param1, param2, data);
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            Formatter formatter = new Formatter(sb, Locale.US);
            formatter.format("{\"ts\":%d, \"phone\":%d", timestamp, phoneId);
            switch (tag) {
                case TelephonyEventLog.TAG_SETTINGS:
                    formatter.format(", \"%s\":\"%s\"", JSON_KEY_TAG, JSON_TAG_SETTINGS);
                    break;

                case TelephonyEventLog.TAG_SERVICE_STATE:
                    serviceStateToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_IMS_CONNECTION_STATE:
                    imsConnectionStateToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_IMS_CAPABILITIES:
                    imsCapabilitiesToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_DATA_CALL_LIST:
                    dataCallListToJson(sb, formatter);
                    break;

                case TelephonyEventLog.TAG_RIL_REQUEST:
                    rilRequestToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_RIL_RESPONSE:
                    rilResponseToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_RIL_UNSOL_RESPONSE:
                    unsolRilResponseToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_IMS_CALL_START:
                case TelephonyEventLog.TAG_IMS_CALL_START_CONFERENCE:
                case TelephonyEventLog.TAG_IMS_CALL_RECEIVE:
                case TelephonyEventLog.TAG_IMS_CALL_ACCEPT:
                case TelephonyEventLog.TAG_IMS_CALL_REJECT:
                case TelephonyEventLog.TAG_IMS_CALL_TERMINATE:
                case TelephonyEventLog.TAG_IMS_CALL_HOLD:
                case TelephonyEventLog.TAG_IMS_CALL_RESUME:
                case TelephonyEventLog.TAG_IMS_CALL_PROGRESSING:
                case TelephonyEventLog.TAG_IMS_CALL_STARTED:
                case TelephonyEventLog.TAG_IMS_CALL_START_FAILED:
                case TelephonyEventLog.TAG_IMS_CALL_TERMINATED:
                case TelephonyEventLog.TAG_IMS_CALL_HELD:
                case TelephonyEventLog.TAG_IMS_CALL_HOLD_RECEIVED:
                case TelephonyEventLog.TAG_IMS_CALL_HOLD_FAILED:
                case TelephonyEventLog.TAG_IMS_CALL_RESUMED:
                case TelephonyEventLog.TAG_IMS_CALL_RESUME_RECEIVED:
                case TelephonyEventLog.TAG_IMS_CALL_RESUME_FAILED:
                    imsCallEventToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_IMS_CALL_HANDOVER:
                case TelephonyEventLog.TAG_IMS_CALL_HANDOVER_FAILED:
                    imsHandoverToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_IMS_CALL_STATE:
                    imsCallStateToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_PHONE_STATE:
                    phoneStateToJson(formatter);
                    break;

                case TelephonyEventLog.TAG_SMS:
                    formatter.format(", \"%s\":\"%s\"", JSON_KEY_TAG, JSON_TAG_SMS);
                    break;

                default:
                    formatter.format(", \"%s\":\"UNKNOWN(%d)\"", JSON_KEY_TAG, tag);
                    break;
            }
            sb.append("},");
            return sb.toString();
        }

        private void serviceStateToJson(Formatter formatter) {
            formatter.format(", \"%s\":\"%s\""
                            + ",\"%s\":%d,\"%s\":%d,\"%s\":%d,\"%s\":%d"
                            + ",\"%s\":\"%s\",\"%s\":\"%s\",\"%s\":\"%s\""
                            + ",\"%s\":\"%s\",\"%s\":\"%s\",\"%s\":\"%s\""
                            + ",\"%s\":%d,\"%s\":%d",
                    JSON_KEY_TAG, JSON_TAG_SERVICE_STATE,
                    JSON_KEY_REG_STATE, data.getInt("voiceRegState"),
                    JSON_KEY_DATA_REG_STATE, data.getInt("dataRegState"),
                    JSON_KEY_ROAMING_TYPE, data.getInt("voiceRoamingType"),
                    JSON_KEY_DATA_ROAMING_TYPE, data.getInt("dataRoamingType"),
                    JSON_KEY_OPERATOR_ALPHA_LONG, data.getString("operator-alpha-long"),
                    JSON_KEY_OPERATOR_ALPHA_SHORT, data.getString("operator-alpha-short"),
                    JSON_KEY_OPERATOR_NUMERIC, data.getString("operator-numeric"),
                    JSON_KEY_DATA_OPERATOR_ALPHA_LONG, data.getString("data-operator-alpha-long"),
                    JSON_KEY_DATA_OPERATOR_ALPHA_SHORT, data.getString("data-operator-alpha-short"),
                    JSON_KEY_DATA_OPERATOR_NUMERIC, data.getString("data-operator-numeric"),
                    JSON_KEY_RAT, data.getInt("radioTechnology"),
                    JSON_KEY_DATA_RAT, data.getInt("dataRadioTechnology"));
        }

        private void imsConnectionStateToJson(Formatter formatter) {
            if (data == null) {
                formatter.format(", \"%s\":\"%s\", \"%s\":%d",
                        JSON_KEY_TAG, JSON_TAG_IMS_CONNECTION_STATE, JSON_KEY_STATE, param1);
            } else {
                formatter.format(", \"%s\":\"%s\""
                                + ", \"%s\":%d"
                                + ", \"%s\":{\"%s\":%d,\"%s\":%d,\"%s\":%s}",
                        JSON_KEY_TAG, JSON_TAG_IMS_CONNECTION_STATE,
                        JSON_KEY_STATE, param1,
                        JSON_KEY_REASON_INFO,
                        JSON_KEY_REASON_INFO_CODE, data.getInt(
                                TelephonyEventLog.DATA_KEY_REASONINFO_CODE),
                        JSON_KEY_REASON_INFO_EXTRA_CODE, data.getInt(
                                TelephonyEventLog.DATA_KEY_REASONINFO_EXTRA_CODE),
                        JSON_KEY_REASON_INFO_EXTRA_MESSAGE, data.getString(
                                TelephonyEventLog.DATA_KEY_REASONINFO_EXTRA_MESSAGE));
            }
        }

        private void imsCapabilitiesToJson(Formatter formatter) {
            formatter.format(", \"%s\":\"%s\""
                            + ",\"%s\":%b,\"%s\":%b,\"%s\":%b"
                            + ",\"%s\":%b,\"%s\":%b,\"%s\":%b",
                    JSON_KEY_TAG, JSON_TAG_IMS_CAPABILITIES,
                    JSON_KEY_VOLTE, data.getBoolean(TelephonyEventLog.DATA_KEY_VOLTE),
                    JSON_KEY_VILTE, data.getBoolean(TelephonyEventLog.DATA_KEY_VILTE),
                    JSON_KEY_VOWIFI, data.getBoolean(TelephonyEventLog.DATA_KEY_VOWIFI),
                    JSON_KEY_VIWIFI, data.getBoolean(TelephonyEventLog.DATA_KEY_VIWIFI),
                    JSON_KEY_UTLTE, data.getBoolean(TelephonyEventLog.DATA_KEY_UTLTE),
                    JSON_KEY_UTWIFI, data.getBoolean(TelephonyEventLog.DATA_KEY_UTWIFI));
        }

        private void dataCallListToJson(StringBuilder sb, Formatter formatter) {
            formatter.format(", \"%s\":\"%s\",\"%s\":[",
                    JSON_KEY_TAG, JSON_TAG_DATA_CALL_LIST, JSON_KEY_DATA_CALLS);
            int[] statuses = data.getIntArray(TelephonyEventLog.DATA_KEY_DATA_CALL_STATUSES);
            int[] cids = data.getIntArray(TelephonyEventLog.DATA_KEY_DATA_CALL_CIDS);
            int[] actives = data.getIntArray(TelephonyEventLog.DATA_KEY_DATA_CALL_ACTIVES);
            String[] types = data.getStringArray(TelephonyEventLog.DATA_KEY_DATA_CALL_TYPES);
            String[] ifnames = data.getStringArray(TelephonyEventLog.DATA_KEY_DATA_CALL_IFNAMES);
            for (int i = 0; i < cids.length; i++) {
                formatter.format("{\"%s\":%d,\"%s\":%d,\"%s\":%d"
                                + ",\"%s\":\"%s\",\"%s\":\"%s\"},",
                        JSON_KEY_STATUS, statuses[i], JSON_KEY_CID, cids[i],
                        JSON_KEY_ACTIVE, actives[i],
                        JSON_KEY_TYPE, types[i], JSON_KEY_IFNAME, ifnames[i]);
            }
            sb.append("]");
        }

        private void rilRequestToJson(Formatter formatter) {
            switch (param1) {
                case RIL_REQUEST_SETUP_DATA_CALL:
                    formatter.format(", \"%s\":\"%s\""
                                    + ",\"%s\":%d,\"%s\":\"%s\",\"%s\":\"%s\""
                                    + ",\"%s\":\"%s\",\"%s\":\"%s\"",
                            JSON_KEY_TAG, JSON_TAG_RIL_REQUEST_SETUP_DATA_CALL,
                            JSON_KEY_SERIAL, param2,
                            JSON_KEY_RAT, data.getString(
                                    TelephonyEventLog.DATA_KEY_RAT),
                            JSON_KEY_PROFILE, data.getString(
                                    TelephonyEventLog.DATA_KEY_DATA_PROFILE),
                            JSON_KEY_APN, data.getString(
                                    TelephonyEventLog.DATA_KEY_APN),
                            JSON_KEY_PROTOCOL, data.getString(
                                    TelephonyEventLog.DATA_KEY_PROTOCOL));
                    break;
                case RIL_REQUEST_DEACTIVATE_DATA_CALL:
                    formatter.format(", \"%s\":\"%s\""
                                    + ",\"%s\":%d,\"%s\":%d,\"%s\":%d",
                            JSON_KEY_TAG, JSON_TAG_RIL_REQUEST_DEACTIVATE_DATA_CALL,
                            JSON_KEY_SERIAL, param2,
                            JSON_KEY_CID, data.getInt(
                                    TelephonyEventLog.DATA_KEY_DATA_CALL_CID),
                            JSON_KEY_REASON, data.getInt(
                                    TelephonyEventLog.DATA_KEY_DATA_DEACTIVATE_REASON));
                    break;
                case RIL_REQUEST_DIAL:
                    formatter.format(", \"%s\":\"%s\""
                                    + ",\"%s\":%d,\"%s\":%d",
                            JSON_KEY_TAG, JSON_TAG_RIL_REQUEST_DIAL,
                            JSON_KEY_SERIAL, param2,
                            JSON_KEY_CLIR_MODE, data.getInt(
                                    TelephonyEventLog.DATA_KEY_CLIR_MODE));
                    break;
                case RIL_REQUEST_HANGUP:
                case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND:
                case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND:
                    formatter.format(", \"%s\":\"%s\""
                                    + ",\"%s\":%d, \"%s\":%d"
                                    + ",\"%s\":\"%d\"",
                            JSON_KEY_TAG, JSON_TAG_RIL_REQUEST_HANGUP,
                            JSON_KEY_SERIAL, param2, JSON_KEY_EVT, param1,
                            JSON_KEY_GSM_INDEX, data.getInt(
                                    TelephonyEventLog.DATA_KEY_RIL_HANGUP_GSM_INDEX));
                    break;
                case RIL_REQUEST_ANSWER:
                    formatter.format(", \"%s\":\"%s\",\"%s\":%d",
                            JSON_KEY_TAG, JSON_TAG_RIL_REQUEST_ANSWER, JSON_KEY_SERIAL, param2);
                    break;

                case RIL_REQUEST_SEND_SMS:
                case RIL_REQUEST_SEND_SMS_EXPECT_MORE:
                case RIL_REQUEST_CDMA_SEND_SMS:
                case RIL_REQUEST_IMS_SEND_SMS:
                    formatter.format(", \"%s\":\"%s\",\"%s\":%d",
                            JSON_KEY_TAG, JSON_TAG_RIL_REQUEST_SEND_SMS, JSON_KEY_SERIAL, param2);
                    break;
            }
        }

        private void rilResponseToJson(Formatter formatter) {
            switch (param1) {
                case RIL_REQUEST_SETUP_DATA_CALL:
                    formatter.format(", \"%s\":\"%s\""
                                    + ",\"%s\":%d,\"%s\":%d,\"%s\":%d"
                                    + ",\"%s\":%d,\"%s\":%d"
                                    + ",\"%s\":\"%s\",\"%s\":\"%s\"",
                            JSON_KEY_TAG, JSON_TAG_RIL_RESPONSE_SETUP_DATA_CALL,
                            JSON_KEY_SERIAL, param2,
                            JSON_KEY_STATUS, data.getInt(
                                    TelephonyEventLog.DATA_KEY_DATA_CALL_STATUS),
                            JSON_KEY_RETRY, data.getInt(
                                    TelephonyEventLog.DATA_KEY_DATA_CALL_RETRY),
                            JSON_KEY_CID, data.getInt(
                                    TelephonyEventLog.DATA_KEY_DATA_CALL_CID),
                            JSON_KEY_ACTIVE, data.getInt(
                                    TelephonyEventLog.DATA_KEY_DATA_CALL_ACTIVE),
                            JSON_KEY_TYPE, data.getString(
                                    TelephonyEventLog.DATA_KEY_DATA_CALL_TYPE),
                            JSON_KEY_IFNAME, data.getString(
                                    TelephonyEventLog.DATA_KEY_DATA_CALL_IFNAME));
                    break;

                case RIL_REQUEST_DEACTIVATE_DATA_CALL:
                case RIL_REQUEST_HANGUP:
                case RIL_REQUEST_HANGUP_WAITING_OR_BACKGROUND:
                case RIL_REQUEST_HANGUP_FOREGROUND_RESUME_BACKGROUND:
                case RIL_REQUEST_DIAL:
                case RIL_REQUEST_ANSWER:
                    formatter.format(", \"%s\":\"%s\",\"%s\":%d",
                            JSON_KEY_TAG, rilResponseToString(param1), JSON_KEY_SERIAL, param2);
                    break;

                case RIL_REQUEST_SEND_SMS:
                case RIL_REQUEST_SEND_SMS_EXPECT_MORE:
                case RIL_REQUEST_CDMA_SEND_SMS:
                case RIL_REQUEST_IMS_SEND_SMS:
                    formatter.format(", \"%s\":\"%s\",\"%s\":%d"
                                    + ",\"%s\":%d,\"%s\":%d",
                            JSON_KEY_TAG, rilResponseToString(param1), JSON_KEY_SERIAL, param2,
                            JSON_KEY_SMS_MESSAGE_REF, data.getInt(
                                    TelephonyEventLog.DATA_KEY_SMS_MESSAGE_REF),
                            JSON_KEY_SMS_ERROR_CODE, data.getInt(
                                    TelephonyEventLog.DATA_KEY_SMS_ERROR_CODE));
                    break;
            }
            formatter.format(", \"%s\":%d",
                    JSON_KEY_RIL_ERROR, data.getInt(TelephonyEventLog.DATA_KEY_RIL_ERROR));
        }

        private void unsolRilResponseToJson(Formatter formatter) {
            switch (param1) {
                case RIL_UNSOL_CALL_RING:
                    formatter.format(", \"%s\":\"%s\"", JSON_KEY_TAG, JSON_TAG_RIL_UNSOL_CALL_RING);
                    break;
                case RIL_UNSOL_SRVCC_STATE_NOTIFY:
                    formatter.format(", \"%s\":\"%s\",\"%s\":%d",
                            JSON_KEY_TAG, JSON_TAG_RIL_UNSOL_SRVCC_STATE_NOTIFY,
                            JSON_KEY_STATE, param2);
                    break;
                case RIL_UNSOL_RESPONSE_NEW_SMS:
                    formatter.format(", \"%s\":\"%s\"",
                            JSON_KEY_TAG, JSON_TAG_RIL_UNSOL_RESPONSE_NEW_SMS);
                    break;
                case RIL_UNSOL_RESPONSE_CDMA_NEW_SMS:
                    formatter.format(", \"%s\":\"%s\"",
                            JSON_KEY_TAG, JSON_TAG_RIL_UNSOL_RESPONSE_CDMA_NEW_SMS);
                    break;
            }
        }

        private void imsCallEventToJson(Formatter formatter) {
            formatter.format(", \"%s\":\"%s\", \"%s\":\"%s\",\"%s\":%d",
                    JSON_KEY_TAG, JSON_TAG_IMS_CALL, JSON_KEY_EVT, imsCallEventToString(tag),
                    JSON_KEY_CALL_ID, param1);
        }

        private void imsHandoverToJson(Formatter formatter) {
            formatter.format(", \"%s\":\"%s\", \"%s\":\"%s\",\"%s\":%d"
                            + ",\"%s\":%d,\"%s\":%d"
                            + ",\"%s\":%d,\"%s\":%d,\"%s\":\"%s\"",
                    JSON_KEY_TAG, JSON_TAG_IMS_CALL_HANDOVER,
                    JSON_KEY_EVT, imsCallEventToString(tag), JSON_KEY_CALL_ID, param1,
                    JSON_KEY_SRC_TECH, data.getInt(TelephonyEventLog.DATA_KEY_SRC_TECH),
                    JSON_KEY_TARGET_TECH, data.getInt(TelephonyEventLog.DATA_KEY_TARGET_TECH),
                    JSON_KEY_REASON_INFO_CODE, data.getInt(
                            TelephonyEventLog.DATA_KEY_REASONINFO_CODE),
                    JSON_KEY_REASON_INFO_EXTRA_CODE, data.getInt(
                            TelephonyEventLog.DATA_KEY_REASONINFO_EXTRA_CODE),
                    JSON_KEY_REASON_INFO_EXTRA_MESSAGE, data.getString(
                            TelephonyEventLog.DATA_KEY_REASONINFO_EXTRA_MESSAGE));
        }

        private void imsCallStateToJson(Formatter formatter) {
            formatter.format(", \"%s\":\"%s\", \"%s\":%d, \"%s\":%d",
                    JSON_KEY_TAG, JSON_TAG_IMS_CALL_STATE, JSON_KEY_CALL_ID, param1,
                    JSON_KEY_STATE, param2);
        }

        private void phoneStateToJson(Formatter formatter) {
            formatter.format(", \"%s\":\"%s\", \"%s\":%d",
                    JSON_KEY_TAG, JSON_TAG_PHONE_STATE, JSON_KEY_STATE, param1);
        }
    }
    private final List<Event> mEvents = new ArrayList<Event>();

    /**
     * Implementation of the ITelephonyDebug interface.
     */
    private final ITelephonyDebug.Stub mBinder = new ITelephonyDebug.Stub() {
        public void writeEvent(long timestamp, int phoneId, int tag,
                int param1, int param2, Bundle data) {
            if (VDBG) {
                Log.v(TAG, String.format("writeEvent(%d, %d, %d, %d, %d)",
                        timestamp, phoneId, tag, param1, param2));
            }
            synchronized (mEvents) {
                mEvents.add(new Event(timestamp, phoneId, tag, param1, param2, data));
            }
        }
    };
}

