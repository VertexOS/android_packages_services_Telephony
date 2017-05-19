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

import android.content.Context;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.widget.Toast;
import android.telecom.CallAudioState;
import android.telecom.ConferenceParticipant;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;

import com.android.ims.ImsCall;
import com.android.ims.ImsCallProfile;
import com.android.internal.telephony.Call;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Connection.Capability;
import com.android.internal.telephony.Connection.PostDialListener;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.gsm.SuppServiceNotification;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.imsphone.ImsPhone;
import com.android.internal.telephony.imsphone.ImsPhoneCallTracker;
import com.android.internal.telephony.SubscriptionController;
import com.android.phone.ImsUtil;
import com.android.phone.PhoneGlobals;
import com.android.phone.PhoneUtils;
import com.android.phone.R;

import java.lang.Override;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codeaurora.ims.QtiCallConstants;

/**
 * Base class for CDMA and GSM connections.
 */
abstract class TelephonyConnection extends Connection
        implements TelephonyConnectionService.ConnectionRemovedListener {
    private static final int MSG_PRECISE_CALL_STATE_CHANGED = 1;
    private static final int MSG_RINGBACK_TONE = 2;
    private static final int MSG_HANDOVER_STATE_CHANGED = 3;
    private static final int MSG_DISCONNECT = 4;
    private static final int MSG_MULTIPARTY_STATE_CHANGED = 5;
    private static final int MSG_CONFERENCE_MERGE_FAILED = 6;
    private static final int MSG_SUPP_SERVICE_NOTIFY = 7;

    /**
     * Mappings from {@link com.android.internal.telephony.Connection} extras keys to their
     * equivalents defined in {@link android.telecom.Connection}.
     */
    private static final Map<String, String> sExtrasMap = createExtrasMap();

    private static final int MSG_SET_VIDEO_STATE = 8;
    private static final int MSG_SET_VIDEO_PROVIDER = 9;
    private static final int MSG_SET_AUDIO_QUALITY = 10;
    private static final int MSG_SET_CONFERENCE_PARTICIPANTS = 11;
    private static final int MSG_CONNECTION_EXTRAS_CHANGED = 12;
    private static final int MSG_SET_ORIGNAL_CONNECTION_CAPABILITIES = 13;
    private static final int MSG_ON_HOLD_TONE = 14;
    private static final int MSG_CDMA_VOICE_PRIVACY_ON = 15;
    private static final int MSG_CDMA_VOICE_PRIVACY_OFF = 16;
    private static final int MSG_CONNECTION_REMOVED = 17;

    private boolean[] mIsPermDiscCauseReceived = new
            boolean[TelephonyManager.getDefault().getPhoneCount()];

    private static final Object mLock = new Object();
    boolean mHangupByUser = false;

    private SuppServiceNotification mSsNotification = null;
    private String[] mSubName = {"SIM1", "SIM2", "SIM3"};
    private String mDisplayName;
    private boolean mIsEmergencyNumber = false;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PRECISE_CALL_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_PRECISE_CALL_STATE_CHANGED");
                    updateState();
                    break;
                case MSG_HANDOVER_STATE_CHANGED:
                    Log.v(TelephonyConnection.this, "MSG_HANDOVER_STATE_CHANGED");
                    AsyncResult ar = (AsyncResult) msg.obj;
                    com.android.internal.telephony.Connection connection =
                         (com.android.internal.telephony.Connection) ar.result;
                    if (mOriginalConnection != null) {
                        if (connection != null &&
                            ((!TextUtils.isEmpty(mOriginalConnection.getAddress()) &&
                            !TextUtils.isEmpty(connection.getAddress()) &&
                            mOriginalConnection.getAddress().contains(connection.getAddress())) ||
                            connection.getState() == mOriginalConnection.getStateBeforeHandover())) {
                            Log.d(TelephonyConnection.this,
                                    "SettingOriginalConnection " + mOriginalConnection.toString()
                                            + " with " + connection.toString());

                            boolean isShowToast = (getPhone() != null) ?
                                    getPhone().getContext().getResources()
                                    .getBoolean(R.bool.config_show_srvcc_toast)
                                    : false;

                            if (isShowToast && !shouldTreatAsEmergencyCall()) {
                                int srvccMessageRes = VideoProfile.isVideo(
                                        mOriginalConnection.getVideoState()) ?
                                        R.string.srvcc_video_message : R.string.srvcc_message;
                                Toast.makeText(getPhone().getContext(),
                                        srvccMessageRes, Toast.LENGTH_LONG).show();
                            }
                            setOriginalConnection(connection);
                            mWasImsConnection = false;
                        }
                    } else {
                        Log.w(TelephonyConnection.this,
                                "MSG_HANDOVER_STATE_CHANGED: mOriginalConnection==null - invalid state (not cleaned up)");
                    }
                    break;
                case MSG_RINGBACK_TONE:
                    Log.v(TelephonyConnection.this, "MSG_RINGBACK_TONE");
                    // TODO: This code assumes that there is only one connection in the foreground
                    // call, in other words, it punts on network-mediated conference calling.
                    if (getOriginalConnection() != getForegroundConnection()) {
                        Log.v(TelephonyConnection.this, "handleMessage, original connection is " +
                                "not foreground connection, skipping");
                        return;
                    }
                    setRingbackRequested((Boolean) ((AsyncResult) msg.obj).result);
                    break;
                case MSG_DISCONNECT:
                    updateState();
                    break;
                case MSG_MULTIPARTY_STATE_CHANGED:
                    boolean isMultiParty = (Boolean) msg.obj;
                    Log.i(this, "Update multiparty state to %s", isMultiParty ? "Y" : "N");
                    mIsMultiParty = isMultiParty;
                    if (isMultiParty) {
                        notifyConferenceStarted();
                    }
                    break;
                case MSG_CONFERENCE_MERGE_FAILED:
                    notifyConferenceMergeFailed();
                    break;

                case MSG_SET_VIDEO_STATE:
                    int videoState = (int) msg.obj;
                    setVideoState(videoState);

                    // A change to the video state of the call can influence whether or not it
                    // can be part of a conference, whether another call can be added, and
                    // whether the call should have the HD audio property set.
                    refreshConferenceSupported();
                    refreshDisableAddCall();
                    updateConnectionProperties();
                    break;

                case MSG_SET_VIDEO_PROVIDER:
                    VideoProvider videoProvider = (VideoProvider) msg.obj;
                    setVideoProvider(videoProvider);
                    break;

                case MSG_SET_AUDIO_QUALITY:
                    int audioQuality = (int) msg.obj;
                    setAudioQuality(audioQuality);
                    break;

                case MSG_SET_CONFERENCE_PARTICIPANTS:
                    List<ConferenceParticipant> participants = (List<ConferenceParticipant>) msg.obj;
                    updateConferenceParticipants(participants);
                    break;

                case MSG_CONNECTION_EXTRAS_CHANGED:
                    final Bundle extras = (Bundle) msg.obj;
                    updateExtras(extras);
                    break;

                case MSG_SET_ORIGNAL_CONNECTION_CAPABILITIES:
                    setOriginalConnectionCapabilities(msg.arg1);
                    break;

                case MSG_ON_HOLD_TONE:
                    AsyncResult asyncResult = (AsyncResult) msg.obj;
                    Pair<com.android.internal.telephony.Connection, Boolean> heldInfo =
                            (Pair<com.android.internal.telephony.Connection, Boolean>)
                                    asyncResult.result;

                    // Determines if the hold tone is starting or stopping.
                    boolean playTone = ((Boolean) (heldInfo.second)).booleanValue();

                    // Determine which connection the hold tone is stopping or starting for
                    com.android.internal.telephony.Connection heldConnection = heldInfo.first;

                    // Only start or stop the hold tone if this is the connection which is starting
                    // or stopping the hold tone.
                    if (heldConnection == mOriginalConnection) {
                        // If starting the hold tone, send a connection event to Telecom which will
                        // cause it to play the on hold tone.
                        if (playTone) {
                            sendConnectionEvent(EVENT_ON_HOLD_TONE_START, null);
                        } else {
                            sendConnectionEvent(EVENT_ON_HOLD_TONE_END, null);
                        }
                   }
                   break;

                case MSG_SUPP_SERVICE_NOTIFY:
                    if (getPhone() == null) {
                        break;
                    }
                    int phoneId = getPhone().getPhoneId();
                    Log.v(TelephonyConnection.this, "MSG_SUPP_SERVICE_NOTIFY on phoneId : "
                            +phoneId);
                    if (msg.obj != null && ((AsyncResult) msg.obj).result != null) {
                        //Handle call forward history notification only in dialing, alerting states
                        if (mOriginalConnection != null && ((SuppServiceNotification)((AsyncResult)
                                msg.obj).result).history != null && !(mConnectionState ==
                                Call.State.DIALING || mConnectionState == Call.State.ALERTING)) {
                           return;
                        }
                        mSsNotification =
                                (SuppServiceNotification)((AsyncResult) msg.obj).result;
                        final String notificationText =
                                getSuppSvcNotificationText(mSsNotification, phoneId);
                        if (TelephonyManager.getDefault().getPhoneCount() > 1) {
                            SubscriptionInfo sub =
                                SubscriptionManager.from(getPhone().getContext())
                                .getActiveSubscriptionInfoForSimSlotIndex(phoneId);
                            String displayName =  (sub != null) ?
                                sub.getDisplayName().toString() : mSubName[phoneId];
                            mDisplayName = displayName + ":" + notificationText;
                        } else {
                            mDisplayName = notificationText;
                        }
                        Toast.makeText(getPhone().getContext(),
                                mDisplayName, Toast.LENGTH_LONG).show();
                        if (mOriginalConnection != null && mSsNotification.history != null) {

                            Bundle lastForwardedNumber = new Bundle();
                            Log.v(TelephonyConnection.this,
                                    "Updating call history info in extras.");
                            lastForwardedNumber.putStringArrayList(
                                Connection.EXTRA_LAST_FORWARDED_NUMBER,
                                new ArrayList(Arrays.asList(mSsNotification.history)));
                            putExtras(lastForwardedNumber);
                        }
                    } else {
                        Log.v(this,
                                "MSG_SUPP_SERVICE_NOTIFY event processing failed");
                    }
                    break;

                case MSG_CDMA_VOICE_PRIVACY_ON:
                    Log.d(this, "MSG_CDMA_VOICE_PRIVACY_ON received");
                    setCdmaVoicePrivacy(true);
                    break;
                case MSG_CDMA_VOICE_PRIVACY_OFF:
                    Log.d(this, "MSG_CDMA_VOICE_PRIVACY_OFF received");
                    setCdmaVoicePrivacy(false);
                    break;
                case MSG_CONNECTION_REMOVED:
                    Log.d(this, "MSG_CONNECTION_REMOVED");
                    // Some connection has disconnected. Re fresh disable add call property.
                    refreshDisableAddCall();
                    break;
            }
        }
    };

    private String getSuppSvcNotificationText(SuppServiceNotification suppSvcNotification,
            int phoneId) {
        final int SUPP_SERV_NOTIFICATION_TYPE_MO = 0;
        final int SUPP_SERV_NOTIFICATION_TYPE_MT = 1;
        String callForwardTxt = "";
        if (suppSvcNotification != null) {
            switch (suppSvcNotification.notificationType) {
                // The Notification is for MO call
                case SUPP_SERV_NOTIFICATION_TYPE_MO:
                    callForwardTxt = getMoSsNotificationText(suppSvcNotification.code, phoneId);
                    break;

                // The Notification is for MT call
                case SUPP_SERV_NOTIFICATION_TYPE_MT:
                    callForwardTxt = getMtSsNotificationText(suppSvcNotification.code, phoneId);
                    break;

                default:
                    Log.v(this, "Received invalid Notification Type :"
                            + suppSvcNotification.notificationType);
                    break;
            }
        }
        return callForwardTxt;
    }

    private String getMtSsNotificationText(int code, int phoneId) {
        String callForwardTxt = "";
        if (getPhone() == null) {
            return callForwardTxt;
        }

        Context context = getPhone().getContext();
        switch (code) {
            case SuppServiceNotification.MT_CODE_FORWARDED_CALL:
                //This message is displayed on C when the incoming
                //call is forwarded from B
                callForwardTxt = context.getString(R.string.card_title_forwarded_MTcall);
                break;

            case SuppServiceNotification.MT_CODE_CUG_CALL:
                //This message is displayed on B, when A makes call to B, both A & B
                //belong to a CUG group
                callForwardTxt = context.getString(R.string.card_title_cugcall);
                break;

            case SuppServiceNotification.MT_CODE_CALL_ON_HOLD:
                //This message is displayed on B,when A makes call to B & puts it on
                // hold
                callForwardTxt = context.getString(R.string.card_title_callonhold);
                break;

            case SuppServiceNotification.MT_CODE_CALL_RETRIEVED:
                //This message is displayed on B,when A makes call to B, puts it on
                //hold & retrives it back.
                callForwardTxt = context.getString(R.string.card_title_callretrieved);
                break;

            case SuppServiceNotification.MT_CODE_MULTI_PARTY_CALL:
                //This message is displayed on B when the the call is changed as
                //multiparty
                callForwardTxt = context.getString(R.string.card_title_multipartycall);
                break;

            case SuppServiceNotification.MT_CODE_ON_HOLD_CALL_RELEASED:
                //This message is displayed on B, when A makes call to B, puts it on
                //hold & then releases it.
                callForwardTxt = context.getString(R.string.card_title_callonhold_released);
                break;

            case SuppServiceNotification.MT_CODE_FORWARD_CHECK_RECEIVED:
                //This message is displayed on C when the incoming call is forwarded
                //from B
                callForwardTxt = context.getString(R.string.card_title_forwardcheckreceived);
                break;

            case SuppServiceNotification.MT_CODE_CALL_CONNECTING_ECT:
                //This message is displayed on B,when Call is connecting through
                //Explicit Cold Transfer
                callForwardTxt = context.getString(R.string.card_title_callconnectingect);
                break;

            case SuppServiceNotification.MT_CODE_CALL_CONNECTED_ECT:
                //This message is displayed on B,when Call is connected through
                //Explicit Cold Transfer
                callForwardTxt = context.getString(R.string.card_title_callconnectedect);
                break;

            case SuppServiceNotification.MT_CODE_DEFLECTED_CALL:
                //This message is displayed on B when the incoming call is deflected
                //call
                callForwardTxt = context.getString(R.string.card_title_deflectedcall);
                break;

            case SuppServiceNotification.MT_CODE_ADDITIONAL_CALL_FORWARDED:
                // This message is displayed on B when it is busy and the incoming call
                // gets forwarded to C
                callForwardTxt = context.getString(R.string.card_title_MTcall_forwarding);
                break;

            default :
               Log.v(this,"Received unsupported MT SS Notification :" + code
                      +" "+phoneId);
                break;
        }
        return callForwardTxt;
    }

    private String getMoSsNotificationText(int code, int phoneId) {
        String callForwardTxt = "";

        if (getPhone() == null) {
            return callForwardTxt;
        }

        Context context = getPhone().getContext();
        switch (code) {
            case SuppServiceNotification.MO_CODE_UNCONDITIONAL_CF_ACTIVE:
                // This message is displayed when an outgoing call is made
                // and unconditional forwarding is enabled.
                callForwardTxt = context.getString(R.string.card_title_unconditionalCF);
            break;

            case SuppServiceNotification.MO_CODE_SOME_CF_ACTIVE:
                // This message is displayed when an outgoing call is made
                // and conditional forwarding is enabled.
                callForwardTxt = context.getString(R.string.card_title_conditionalCF);
                break;

            case SuppServiceNotification.MO_CODE_CALL_FORWARDED:
                //This message is displayed on A when the outgoing call
                //actually gets forwarded to C
                callForwardTxt = context.getString(R.string.card_title_MOcall_forwarding);
                break;

            case SuppServiceNotification.MO_CODE_CALL_IS_WAITING:
                //This message is displayed on A when the B is busy on another call
                //and Call waiting is enabled on B
                callForwardTxt = context.getString(R.string.card_title_calliswaiting);
                break;

            case SuppServiceNotification.MO_CODE_CUG_CALL:
                //This message is displayed on A, when A makes call to B, both A & B
                //belong to a CUG group
                callForwardTxt = context.getString(R.string.card_title_cugcall);
                break;

            case SuppServiceNotification.MO_CODE_OUTGOING_CALLS_BARRED:
                //This message is displayed on A when outging is barred on A
                callForwardTxt = context.getString(R.string.card_title_outgoing_barred);
                break;

            case SuppServiceNotification.MO_CODE_INCOMING_CALLS_BARRED:
                //This message is displayed on A, when A is calling B
                //& incoming is barred on B
                callForwardTxt = context.getString(R.string.card_title_incoming_barred);
                break;

            case SuppServiceNotification.MO_CODE_CLIR_SUPPRESSION_REJECTED:
                //This message is displayed on A, when CLIR suppression is rejected
                callForwardTxt = context.getString(R.string.card_title_clir_suppression_rejected);
                break;

            case SuppServiceNotification.MO_CODE_CALL_DEFLECTED:
                //This message is displayed on A, when the outgoing call
                //gets deflected to C from B
                callForwardTxt = context.getString(R.string.card_title_call_deflected);
                break;

            default:
                Log.v(this,"Received unsupported MO SS Notification :" + code
                        +" "+phoneId);
                break;
        }
        return callForwardTxt;
    }

    /**
     * @return {@code true} if carrier video conferencing is supported, {@code false} otherwise.
     */
    public boolean isCarrierVideoConferencingSupported() {
        return mIsCarrierVideoConferencingSupported;
    }

    /**
     * A listener/callback mechanism that is specific communication from TelephonyConnections
     * to TelephonyConnectionService (for now). It is more specific that Connection.Listener
     * because it is only exposed in Telephony.
     */
    public abstract static class TelephonyConnectionListener {
        public void onOriginalConnectionConfigured(TelephonyConnection c) {}
        public void onEmergencyRedial(TelephonyConnection c, PhoneAccountHandle pHandle,
                int phoneId) {}
        public void onOriginalConnectionRetry(TelephonyConnection c) {}
    }

    private final PostDialListener mPostDialListener = new PostDialListener() {
        @Override
        public void onPostDialWait() {
            Log.v(TelephonyConnection.this, "onPostDialWait");
            if (mOriginalConnection != null) {
                setPostDialWait(mOriginalConnection.getRemainingPostDialString());
            }
        }

        @Override
        public void onPostDialChar(char c) {
            Log.v(TelephonyConnection.this, "onPostDialChar: %s", c);
            if (mOriginalConnection != null) {
                setNextPostDialChar(c);
            }
        }
    };

    /**
     * Listener for listening to events in the {@link com.android.internal.telephony.Connection}.
     */
    private final com.android.internal.telephony.Connection.Listener mOriginalConnectionListener =
            new com.android.internal.telephony.Connection.ListenerBase() {
        @Override
        public void onVideoStateChanged(int videoState) {
            mHandler.obtainMessage(MSG_SET_VIDEO_STATE, videoState).sendToTarget();
        }

        /*
         * The {@link com.android.internal.telephony.Connection} has reported a change in
         * connection capability.
         * @param capabilities bit mask containing voice or video or both capabilities.
         */
        @Override
        public void onConnectionCapabilitiesChanged(int capabilities) {
            mHandler.obtainMessage(MSG_SET_ORIGNAL_CONNECTION_CAPABILITIES,
                    capabilities, 0).sendToTarget();
        }

        /**
         * The {@link com.android.internal.telephony.Connection} has reported a change in the
         * video call provider.
         *
         * @param videoProvider The video call provider.
         */
        @Override
        public void onVideoProviderChanged(VideoProvider videoProvider) {
            mHandler.obtainMessage(MSG_SET_VIDEO_PROVIDER, videoProvider).sendToTarget();
        }

        /**
         * Used by {@link com.android.internal.telephony.Connection} to report a change in whether
         * the call is being made over a wifi network.
         *
         * @param isWifi True if call is made over wifi.
         */
        @Override
        public void onWifiChanged(boolean isWifi) {
            setWifi(isWifi);
        }

        /**
         * Used by the {@link com.android.internal.telephony.Connection} to report a change in the
         * audio quality for the current call.
         *
         * @param audioQuality The audio quality.
         */
        @Override
        public void onAudioQualityChanged(int audioQuality) {
            mHandler.obtainMessage(MSG_SET_AUDIO_QUALITY, audioQuality).sendToTarget();
        }
        /**
         * Handles a change in the state of conference participant(s), as reported by the
         * {@link com.android.internal.telephony.Connection}.
         *
         * @param participants The participant(s) which changed.
         */
        @Override
        public void onConferenceParticipantsChanged(List<ConferenceParticipant> participants) {
            mHandler.obtainMessage(MSG_SET_CONFERENCE_PARTICIPANTS, participants).sendToTarget();
        }

        /*
         * Handles a change to the multiparty state for this connection.
         *
         * @param isMultiParty {@code true} if the call became multiparty, {@code false}
         *      otherwise.
         */
        @Override
        public void onMultipartyStateChanged(boolean isMultiParty) {
            handleMultipartyStateChange(isMultiParty);
        }

        /**
         * Handles the event that the request to merge calls failed.
         */
        @Override
        public void onConferenceMergedFailed() {
            handleConferenceMergeFailed();
        }

        @Override
        public void onExtrasChanged(Bundle extras) {
            mHandler.obtainMessage(MSG_CONNECTION_EXTRAS_CHANGED, extras).sendToTarget();
        }

        /**
         * Handles the phone exiting ECM mode by updating the connection capabilities.  During an
         * ongoing call, if ECM mode is exited, we will re-enable mute for CDMA calls.
         */
        @Override
        public void onExitedEcmMode() {
            handleExitedEcmMode();
        }

        /**
         * Called from {@link ImsPhoneCallTracker} when a request to pull an external call has
         * failed.
         * @param externalConnection
         */
        @Override
        public void onCallPullFailed(com.android.internal.telephony.Connection externalConnection) {
            if (externalConnection == null) {
                return;
            }

            Log.i(this, "onCallPullFailed - pull failed; swapping back to call: %s",
                    externalConnection);

            // Inform the InCallService of the fact that the call pull failed (it may choose to
            // display a message informing the user of the pull failure).
            sendConnectionEvent(Connection.EVENT_CALL_PULL_FAILED, null);

            // Swap the ImsPhoneConnection we used to do the pull for the ImsExternalConnection
            // which originally represented the call.
            setOriginalConnection(externalConnection);

            // Set our state to active again since we're no longer pulling.
            setActiveInternal();
        }

        /**
         * Called from {@link ImsPhoneCallTracker} when a handover to WIFI has failed.
         */
        @Override
        public void onHandoverToWifiFailed() {
            sendConnectionEvent(TelephonyManager.EVENT_HANDOVER_TO_WIFI_FAILED, null);
        }

        /**
         * Informs the {@link android.telecom.ConnectionService} of a connection event raised by the
         * original connection.
         * @param event The connection event.
         * @param extras The extras.
         */
        @Override
        public void onConnectionEvent(String event, Bundle extras) {
            sendConnectionEvent(event, extras);
        }
    };

    protected com.android.internal.telephony.Connection mOriginalConnection;
    protected Call.State mConnectionState = Call.State.IDLE;
    private Bundle mOriginalConnectionExtras = new Bundle();
    private boolean mIsStateOverridden = false;
    private Call.State mOriginalConnectionState = Call.State.IDLE;
    private Call.State mConnectionOverriddenState = Call.State.IDLE;

    private boolean mWasImsConnection;

    /**
     * Tracks the multiparty state of the ImsCall so that changes in the bit state can be detected.
     */
    private boolean mIsMultiParty = false;

    /**
     * The {@link com.android.internal.telephony.Connection} capabilities associated with the
     * current {@link #mOriginalConnection}.
     */
    private int mOriginalConnectionCapabilities;

    /**
     * Determines if the {@link TelephonyConnection} is using wifi.
     * This is used when {@link TelephonyConnection#updateConnectionProperties()} is called to
     * indicate whether a call has the {@link Connection#PROPERTY_WIFI} property.
     */
    private boolean mIsWifi;

    /**
     * Determines the audio quality is high for the {@link TelephonyConnection}.
     * This is used when {@link TelephonyConnection#updateConnectionProperties}} is called to
     * indicate whether a call has the {@link Connection#PROPERTY_HIGH_DEF_AUDIO} property.
     */
    private boolean mHasHighDefAudio;

    /**
     * Indicates that the connection should be treated as an emergency call because the
     * number dialed matches an internal list of emergency numbers. Does not guarantee whether
     * the network will treat the call as an emergency call.
     */
    private boolean mTreatAsEmergencyCall;

    /**
     * For video calls, indicates whether the outgoing video for the call can be paused using
     * the {@link android.telecom.VideoProfile#STATE_PAUSED} VideoState.
     */
    private boolean mIsVideoPauseSupported;

    /**
     * Indicates whether this connection supports being a part of a conference..
     */
    private boolean mIsConferenceSupported;

    /**
     * Indicates whether the carrier supports video conferencing; captures the current state of the
     * carrier config
     * {@link android.telephony.CarrierConfigManager#KEY_SUPPORT_VIDEO_CONFERENCE_CALL_BOOL}.
     */
    private boolean mIsCarrierVideoConferencingSupported;

    /**
     * Indicates whether or not this connection has CDMA Enhanced Voice Privacy enabled.
     */
    private boolean mIsCdmaVoicePrivacyEnabled;

    /**
     * Listeners to our TelephonyConnection specific callbacks
     */
    private final Set<TelephonyConnectionListener> mTelephonyListeners = Collections.newSetFromMap(
            new ConcurrentHashMap<TelephonyConnectionListener, Boolean>(8, 0.9f, 1));

    protected TelephonyConnection(com.android.internal.telephony.Connection originalConnection,
            String callId) {
        setTelecomCallId(callId);
        if (originalConnection != null) {
            setOriginalConnection(originalConnection);
        }
    }

    /**
     * Creates a clone of the current {@link TelephonyConnection}.
     *
     * @return The clone.
     */
    public abstract TelephonyConnection cloneConnection();

    @Override
    public void onCallAudioStateChanged(CallAudioState audioState) {
        // TODO: update TTY mode.
        if (getPhone() != null) {
            getPhone().setEchoSuppressionEnabled();
        }
    }

    @Override
    public void onStateChanged(int state) {
        Log.v(this, "onStateChanged, state: " + Connection.stateToString(state));
        updateStatusHints();
    }

    @Override
    public void onDisconnect() {
        Log.v(this, "onDisconnect");
        PhoneNumberUtils.resetCountryDetectorInfo();
        hangup(android.telephony.DisconnectCause.LOCAL);
    }

    /**
     * Notifies this Connection of a request to disconnect a participant of the conference managed
     * by the connection.
     *
     * @param endpoint the {@link Uri} of the participant to disconnect.
     */
    @Override
    public void onDisconnectConferenceParticipant(Uri endpoint) {
        Log.v(this, "onDisconnectConferenceParticipant %s", endpoint);

        if (mOriginalConnection == null) {
            return;
        }

        mOriginalConnection.onDisconnectConferenceParticipant(endpoint);
    }

    @Override
    public void onSeparate() {
        Log.v(this, "onSeparate");
        if (mOriginalConnection != null) {
            try {
                mOriginalConnection.separate();
            } catch (CallStateException e) {
                Log.e(this, e, "Call to Connection.separate failed with exception");
            }
        }
    }

    @Override
    public void onAbort() {
        Log.v(this, "onAbort");
        hangup(android.telephony.DisconnectCause.LOCAL);
    }

    @Override
    public void onHold() {
        performHold();
    }

    @Override
    public void onUnhold() {
        performUnhold();
    }

    @Override
    public void onAnswer(int videoState) {
        Log.v(this, "onAnswer");
        if (isValidRingingCall() && getPhone() != null) {
            try {
                getPhone().acceptCall(videoState);
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to accept call.");
            }
        }
    }

    @Override
    public void onReject() {
        Log.v(this, "onReject");
        if (isValidRingingCall()) {
            hangup(android.telephony.DisconnectCause.INCOMING_REJECTED);
        }
        super.onReject();
    }

    @Override
    public void onPostDialContinue(boolean proceed) {
        Log.v(this, "onPostDialContinue, proceed: " + proceed);
        if (mOriginalConnection != null) {
            if (proceed) {
                mOriginalConnection.proceedAfterWaitChar();
            } else {
                mOriginalConnection.cancelPostDial();
            }
        }
    }

    /**
     * Handles requests to pull an external call.
     */
    @Override
    public void onPullExternalCall() {
        if ((getConnectionProperties() & Connection.PROPERTY_IS_EXTERNAL_CALL) !=
                Connection.PROPERTY_IS_EXTERNAL_CALL) {
            Log.w(this, "onPullExternalCall - cannot pull non-external call");
            return;
        }

        if (mOriginalConnection != null) {
            mOriginalConnection.pullExternalCall();
        }
    }

    @Override
    public void onConnectionRemoved(TelephonyConnection conn) {
        if (conn != this) {
            mHandler.obtainMessage(MSG_CONNECTION_REMOVED).sendToTarget();
        }
    }

    public void performHold() {
        Log.v(this, "performHold");
        // TODO: Can dialing calls be put on hold as well since they take up the
        // foreground call slot?
        if (Call.State.ACTIVE == mConnectionState) {
            Log.v(this, "Holding active call");
            try {
                Phone phone = mOriginalConnection.getCall().getPhone();
                Call ringingCall = phone.getRingingCall();

                // Although the method says switchHoldingAndActive, it eventually calls a RIL method
                // called switchWaitingOrHoldingAndActive. What this means is that if we try to put
                // a call on hold while a call-waiting call exists, it'll end up accepting the
                // call-waiting call, which is bad if that was not the user's intention. We are
                // cheating here and simply skipping it because we know any attempt to hold a call
                // while a call-waiting call is happening is likely a request from Telecom prior to
                // accepting the call-waiting call.
                // TODO: Investigate a better solution. It would be great here if we
                // could "fake" hold by silencing the audio and microphone streams for this call
                // instead of actually putting it on hold.
                if (ringingCall.getState() != Call.State.WAITING) {
                    phone.switchHoldingAndActive();
                }

                // TODO: Cdma calls are slightly different.
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to put call on hold.");
            }
        } else {
            Log.w(this, "Cannot put a call that is not currently active on hold.");
        }
    }

    public void performUnhold() {
        Log.v(this, "performUnhold");
        if (Call.State.HOLDING == mConnectionState) {
            try {
                // Here's the deal--Telephony hold/unhold is weird because whenever there exists
                // more than one call, one of them must always be active. In other words, if you
                // have an active call and holding call, and you put the active call on hold, it
                // will automatically activate the holding call. This is weird with how Telecom
                // sends its commands. When a user opts to "unhold" a background call, telecom
                // issues hold commands to all active calls, and then the unhold command to the
                // background call. This means that we get two commands...each of which reduces to
                // switchHoldingAndActive(). The result is that they simply cancel each other out.
                // To fix this so that it works well with telecom we add a minor hack. If we
                // have one telephony call, everything works as normally expected. But if we have
                // two or more calls, we will ignore all requests to "unhold" knowing that the hold
                // requests already do what we want. If you've read up to this point, I'm very sorry
                // that we are doing this. I didn't think of a better solution that wouldn't also
                // make the Telecom APIs very ugly.

                if (!hasMultipleTopLevelCalls()) {
                    mOriginalConnection.getCall().getPhone().switchHoldingAndActive();
                } else {
                    Log.i(this, "Skipping unhold command for %s", this);
                }
            } catch (CallStateException e) {
                Log.e(this, e, "Exception occurred while trying to release call from hold.");
            }
        } else {
            Log.w(this, "Cannot release a call that is not already on hold from hold.");
        }
    }

    public void performConference(Connection otherConnection) {
        Log.d(this, "performConference - %s", this);
        if (getPhone() != null) {
            try {
                // We dont use the "other" connection because there is no concept of that in the
                // implementation of calls inside telephony. Basically, you can "conference" and it
                // will conference with the background call.  We know that otherConnection is the
                // background call because it would never have called setConferenceableConnections()
                // otherwise.
                getPhone().conference();
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to conference call.");
            }
        }
    }

    public void performAddParticipant(String participant) {
        Log.d(this, "performAddParticipant - %s", participant);
        if (getPhone() != null) {
            try {
                // We should send AddParticipant request using connection.
                // Basically, you can make call to conference with AddParticipant
                // request on single normal call.
                getPhone().addParticipant(participant);
            } catch (CallStateException e) {
                Log.e(this, e, "Failed to performAddParticipant.");
            }
        }
    }

    /**
     * Builds connection capabilities common to all TelephonyConnections. Namely, apply IMS-based
     * capabilities.
     */
    protected int buildConnectionCapabilities() {
        int callCapabilities = 0;
        if (mOriginalConnection != null && mOriginalConnection.isIncoming()) {
            callCapabilities |= CAPABILITY_SPEED_UP_MT_AUDIO;
        }
        if (!shouldTreatAsEmergencyCall() && isImsConnection() && canHoldImsCalls()) {
            callCapabilities |= CAPABILITY_SUPPORT_HOLD;
            if (getState() == STATE_ACTIVE || getState() == STATE_HOLDING) {
                callCapabilities |= CAPABILITY_HOLD;
            }
        }

        return callCapabilities;
    }

    protected final void updateConnectionCapabilities() {
        int newCapabilities = buildConnectionCapabilities();

        newCapabilities = applyOriginalConnectionCapabilities(newCapabilities);
        newCapabilities = changeBitmask(newCapabilities, CAPABILITY_CAN_PAUSE_VIDEO,
                mIsVideoPauseSupported && isVideoCapable());
        newCapabilities = changeBitmask(newCapabilities, CAPABILITY_CAN_PULL_CALL,
                isExternalConnection() && isPullable());
        newCapabilities = applyConferenceTerminationCapabilities(newCapabilities);
        newCapabilities = applyAddParticipantCapabilities(newCapabilities);

        if (getConnectionCapabilities() != newCapabilities) {
            setConnectionCapabilities(newCapabilities);
        }
    }

    protected int buildConnectionProperties() {
        int connectionProperties = 0;

        // If the phone is in ECM mode, mark the call to indicate that the callback number should be
        // shown.
        Phone phone = getPhone();
        if (phone != null && phone.isInEcm()) {
            connectionProperties |= PROPERTY_EMERGENCY_CALLBACK_MODE;
        }

        return connectionProperties;
    }

    /**
     * Updates the properties of the connection.
     */
    protected final void updateConnectionProperties() {
        int newProperties = buildConnectionProperties();

        newProperties = changeBitmask(newProperties, PROPERTY_HIGH_DEF_AUDIO,
                hasHighDefAudioProperty());
        newProperties = changeBitmask(newProperties, PROPERTY_WIFI, mIsWifi);
        newProperties = changeBitmask(newProperties, PROPERTY_IS_EXTERNAL_CALL,
                isExternalConnection());
        newProperties = changeBitmask(newProperties, PROPERTY_HAS_CDMA_VOICE_PRIVACY,
                mIsCdmaVoicePrivacyEnabled);

        if (getConnectionProperties() != newProperties) {
            setConnectionProperties(newProperties);
        }
    }

    protected final void updateAddress() {
        updateConnectionCapabilities();
        updateConnectionProperties();
        if (mOriginalConnection != null) {
            Uri address;
            boolean showOrigDialString = false;
            Phone phone = getPhone();
            if (phone != null) {
                CarrierConfigManager configManager = (CarrierConfigManager)phone.getContext().
                        getSystemService(Context.CARRIER_CONFIG_SERVICE);
                PersistableBundle pb = configManager.getConfigForSubId(phone.getSubId());
                if (pb != null) {
                    showOrigDialString = pb.getBoolean("config_show_orig_dial_string_for_cdma");
                    Log.d(this, "showOrigDialString: " + showOrigDialString);
                }
            }
            if (showOrigDialString && ((getAddress() != null) && phone != null &&
                    (phone.getPhoneType() == TelephonyManager.PHONE_TYPE_CDMA)) &&
                    !mOriginalConnection.isIncoming()) {
                address = getAddressFromNumber(mOriginalConnection.getOrigDialString());
            } else {
                address = getAddressFromNumber(mOriginalConnection.getAddress());
            }
            int presentation = mOriginalConnection.getNumberPresentation();
            if (!Objects.equals(address, getAddress()) ||
                    presentation != getAddressPresentation()) {
                Log.v(this, "updateAddress, address changed");
                if ((getConnectionProperties() & PROPERTY_IS_DOWNGRADED_CONFERENCE) != 0) {
                    address = null;
                }
                setAddress(address, presentation);
            }

            String name = filterCnapName(mOriginalConnection.getCnapName());
            int namePresentation = mOriginalConnection.getCnapNamePresentation();
            if (!Objects.equals(name, getCallerDisplayName()) ||
                    namePresentation != getCallerDisplayNamePresentation()) {
                Log.v(this, "updateAddress, caller display name changed");
                setCallerDisplayName(name, namePresentation);
            }

            if (PhoneUtils.isEmergencyNumber(mOriginalConnection.getAddress())) {
                mTreatAsEmergencyCall = true;
            }

            // Changing the address of the connection can change whether it is an emergency call or
            // not, which can impact whether it can be part of a conference.
            refreshConferenceSupported();
        }
    }

    void onRemovedFromCallService() {
        // Subclass can override this to do cleanup.
    }

    void setOriginalConnection(com.android.internal.telephony.Connection originalConnection) {
        Log.v(this, "new TelephonyConnection, originalConnection: " + originalConnection);
        clearOriginalConnection();
        mOriginalConnectionExtras.clear();
        mOriginalConnection = originalConnection;
        mOriginalConnection.setTelecomCallId(getTelecomCallId());

        if (getPhone() != null) {
            getPhone().registerForPreciseCallStateChanged(mHandler,
                    MSG_PRECISE_CALL_STATE_CHANGED, null);
            getPhone().registerForHandoverStateChanged(mHandler,
                    MSG_HANDOVER_STATE_CHANGED, null);
            getPhone().registerForRingbackTone(mHandler, MSG_RINGBACK_TONE,
                    null);
            getPhone().registerForDisconnect(mHandler, MSG_DISCONNECT, null);
            getPhone().registerForSuppServiceNotification(mHandler,
                    MSG_SUPP_SERVICE_NOTIFY, null);
            getPhone().registerForOnHoldTone(mHandler, MSG_ON_HOLD_TONE, null);
            getPhone().registerForInCallVoicePrivacyOn(mHandler,
                    MSG_CDMA_VOICE_PRIVACY_ON, null);
            getPhone().registerForInCallVoicePrivacyOff(mHandler,
                    MSG_CDMA_VOICE_PRIVACY_OFF, null);
        }

        mOriginalConnection.addPostDialListener(mPostDialListener);
        mOriginalConnection.addListener(mOriginalConnectionListener);

        if (mOriginalConnection != null && mOriginalConnection.getAddress() != null) {
            mIsEmergencyNumber = PhoneNumberUtils.isEmergencyNumber(mOriginalConnection.
                    getAddress());
        }

        updateAddress();

        // Set video state and capabilities
        setVideoState(mOriginalConnection.getVideoState());
        setOriginalConnectionCapabilities(mOriginalConnection.getConnectionCapabilities());
        setWifi(mOriginalConnection.isWifi());
        setVideoProvider(mOriginalConnection.getVideoProvider());
        setAudioQuality(mOriginalConnection.getAudioQuality());
        setTechnologyTypeExtra();

        // Post update of extras to the handler; extras are updated via the handler to ensure thread
        // safety. The Extras Bundle is cloned in case the original extras are modified while they
        // are being added to mOriginalConnectionExtras in updateExtras.
        Bundle connExtras = mOriginalConnection.getConnectionExtras();
            mHandler.obtainMessage(MSG_CONNECTION_EXTRAS_CHANGED, connExtras == null ? null :
                    new Bundle(connExtras)).sendToTarget();

        if (PhoneUtils.isEmergencyNumber(mOriginalConnection.getAddress())) {
            mTreatAsEmergencyCall = true;
        }

        if (isImsConnection()) {
            mWasImsConnection = true;
        }
        mIsMultiParty = mOriginalConnection.isMultiparty();

        Bundle extrasToPut = new Bundle();
        List<String> extrasToRemove = new ArrayList<>();
        if (mOriginalConnection.isActiveCallDisconnectedOnAnswer()) {
            extrasToPut.putBoolean(Connection.EXTRA_ANSWERING_DROPS_FG_CALL, true);
        } else {
            extrasToRemove.add(Connection.EXTRA_ANSWERING_DROPS_FG_CALL);
        }

        if (shouldSetDisableAddCallExtra()) {
            extrasToPut.putBoolean(Connection.EXTRA_DISABLE_ADD_CALL, true);
        } else {
            extrasToRemove.add(Connection.EXTRA_DISABLE_ADD_CALL);
        }
        putExtras(extrasToPut);
        removeExtras(extrasToRemove);

        // updateState can set mOriginalConnection to null if its state is DISCONNECTED, so this
        // should be executed *after* the above setters have run.
        updateState();
        if (mOriginalConnection == null) {
            Log.w(this, "original Connection was nulled out as part of setOriginalConnection. " +
                    originalConnection);
        }

        fireOnOriginalConnectionConfigured();
    }

    /**
     * Filters the CNAP name to not include a list of names that are unhelpful to the user for
     * Caller ID purposes.
     */
    private String filterCnapName(final String cnapName) {
        if (cnapName == null) {
            return null;
        }
        PersistableBundle carrierConfig = getCarrierConfig();
        String[] filteredCnapNames = null;
        if (carrierConfig != null) {
            filteredCnapNames = carrierConfig.getStringArray(
                    CarrierConfigManager.FILTERED_CNAP_NAMES_STRING_ARRAY);
        }
        if (filteredCnapNames != null) {
            long cnapNameMatches = Arrays.asList(filteredCnapNames)
                    .stream()
                    .filter(filteredCnapName -> filteredCnapName.equals(cnapName.toUpperCase()))
                    .count();
            if (cnapNameMatches > 0) {
                Log.i(this, "filterCnapName: Filtered CNAP Name: " + cnapName);
                return "";
            }
        }
        return cnapName;
    }

    /**
     * Sets the EXTRA_CALL_TECHNOLOGY_TYPE extra on the connection to report back to Telecom.
     */
    private void setTechnologyTypeExtra() {
        if (getPhone() != null) {
            putExtra(TelecomManager.EXTRA_CALL_TECHNOLOGY_TYPE, getPhone().getPhoneType());
        }
    }

    private void refreshDisableAddCall() {
        if (shouldSetDisableAddCallExtra()) {
            putExtra(Connection.EXTRA_DISABLE_ADD_CALL, true);
        } else {
            removeExtras(Connection.EXTRA_DISABLE_ADD_CALL);
        }
    }

    private boolean shouldSetDisableAddCallExtra() {
        if (mOriginalConnection == null) {
            return false;
        }
        boolean carrierShouldAllowAddCall = mOriginalConnection.shouldAllowAddCallDuringVideoCall();
        if (carrierShouldAllowAddCall) {
            return false;
        }
        Phone phone = getPhone();
        if (phone == null) {
            return false;
        }
        boolean isCurrentVideoCall = false;
        boolean wasVideoCall = false;
        boolean isVowifiEnabled = false;
        if (phone instanceof ImsPhone) {
            ImsPhone imsPhone = (ImsPhone) phone;
            ImsCall call = null;
            if (imsPhone.getForegroundCall() != null
                    && imsPhone.getForegroundCall().getImsCall() != null) {
                call = imsPhone.getForegroundCall().getImsCall();
            } else if (imsPhone.getBackgroundCall() != null
                    && imsPhone.getBackgroundCall().getImsCall() != null) {
                call = imsPhone.getBackgroundCall().getImsCall();
            } else if (imsPhone.getRingingCall() != null
                    && imsPhone.getRingingCall().getImsCall() != null) {
                call = imsPhone.getRingingCall().getImsCall();
            }
            if (call != null) {
                isCurrentVideoCall = call.isVideoCall();
                wasVideoCall = call.wasVideoCall();
            }

            isVowifiEnabled = ImsUtil.isWfcEnabled(phone.getContext());
        }

        if (isCurrentVideoCall) {
            return true;
        } else if (wasVideoCall && mIsWifi && !isVowifiEnabled) {
            return true;
        }
        return false;
    }

    private boolean hasHighDefAudioProperty() {
        if (!mHasHighDefAudio) {
            return false;
        }

        boolean isVideoCall = VideoProfile.isVideo(getVideoState());

        PersistableBundle b = getCarrierConfig();
        boolean canWifiCallsBeHdAudio =
                b != null && b.getBoolean(CarrierConfigManager.KEY_WIFI_CALLS_CAN_BE_HD_AUDIO);
        boolean canVideoCallsBeHdAudio =
                b != null && b.getBoolean(CarrierConfigManager.KEY_VIDEO_CALLS_CAN_BE_HD_AUDIO);
        boolean shouldDisplayHdAudio =
                b != null && b.getBoolean(CarrierConfigManager.KEY_DISPLAY_HD_AUDIO_PROPERTY_BOOL);

        if (!shouldDisplayHdAudio) {
            return false;
        }

        if (isVideoCall && !canVideoCallsBeHdAudio) {
            return false;
        }

        if (mIsWifi && !canWifiCallsBeHdAudio) {
            return false;
        }

        return true;
    }

    private boolean canHoldImsCalls() {
        PersistableBundle b = getCarrierConfig();
        // Return true if the CarrierConfig is unavailable
        return !doesDeviceRespectHoldCarrierConfig() || b == null ||
                b.getBoolean(CarrierConfigManager.KEY_ALLOW_HOLD_IN_IMS_CALL_BOOL);
    }

    private PersistableBundle getCarrierConfig() {
        Phone phone = getPhone();
        if (phone == null) {
            return null;
        }
        return PhoneGlobals.getInstance().getCarrierConfigForSubId(phone.getSubId());
    }

    /**
     * Determines if the device will respect the value of the
     * {@link CarrierConfigManager#KEY_ALLOW_HOLD_IN_IMS_CALL_BOOL} configuration option.
     *
     * @return {@code false} if the device always supports holding IMS calls, {@code true} if it
     *      will use {@link CarrierConfigManager#KEY_ALLOW_HOLD_IN_IMS_CALL_BOOL} to determine if
     *      hold is supported.
     */
    private boolean doesDeviceRespectHoldCarrierConfig() {
        Phone phone = getPhone();
        if (phone == null) {
            return true;
        }
        return phone.getContext().getResources().getBoolean(
                com.android.internal.R.bool.config_device_respects_hold_carrier_config);
    }

    /**
     * Whether the connection should be treated as an emergency.
     * @return {@code true} if the connection should be treated as an emergency call based
     * on the number dialed, {@code false} otherwise.
     */
    protected boolean shouldTreatAsEmergencyCall() {
        return mTreatAsEmergencyCall;
    }

    /**
     * Un-sets the underlying radio connection.
     */
    void clearOriginalConnection() {
        if (mOriginalConnection != null) {
            if (getPhone() != null) {
                getPhone().unregisterForPreciseCallStateChanged(mHandler);
                getPhone().unregisterForRingbackTone(mHandler);
                getPhone().unregisterForHandoverStateChanged(mHandler);
                getPhone().unregisterForDisconnect(mHandler);
                getPhone().unregisterForSuppServiceNotification(mHandler);
                getPhone().unregisterForOnHoldTone(mHandler);
                getPhone().unregisterForInCallVoicePrivacyOn(mHandler);
                getPhone().unregisterForInCallVoicePrivacyOff(mHandler);
            }
            mOriginalConnection.removePostDialListener(mPostDialListener);
            mOriginalConnection.removeListener(mOriginalConnectionListener);
            mOriginalConnection = null;
        }
    }

    protected void hangup(int telephonyDisconnectCode) {
        synchronized (mLock) {
            if (mOriginalConnection != null) {
                try {
                    // Hanging up a ringing call requires that we invoke call.hangup() as opposed
                    // to connection.hangup(). Without this change, the party originating the call
                    // will not get sent to voicemail if the user opts to reject the call.
                    if (isValidRingingCall()) {
                        Call call = getCall();
                        if (call != null) {
                            call.hangup();
                        } else {
                            Log.w(this, "Attempting to hangup a connection without backing call.");
                        }
                    } else {
                        // We still prefer to call connection.hangup() for non-ringing calls in
                        // order to support hanging-up specific calls within a conference call. If
                        // we invoked call.hangup() while in a conference, we would end up hanging
                        // up the entire conference call instead of the specific connection.
                        mOriginalConnection.hangup();
                    }
                } catch (CallStateException e) {
                    Log.e(this, e, "Call to Connection.hangup failed with exception");
                    mHangupByUser = false;
                }
            } else {
                if (getState() == STATE_DISCONNECTED) {
                    Log.i(this, "hangup called on an already disconnected call!");
                    close();
                } else {
                    // There are a few cases where mOriginalConnection has not been set yet. For
                    // example, when the radio has to be turned on to make an emergency call,
                    // mOriginalConnection could not be set for many seconds.
                    setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                            android.telephony.DisconnectCause.LOCAL,
                            "Local Disconnect before connection established."));
                    close();
                }
            }
        }
    }

    com.android.internal.telephony.Connection getOriginalConnection() {
        return mOriginalConnection;
    }

    protected Call getCall() {
        if (mOriginalConnection != null) {
            return mOriginalConnection.getCall();
        }
        return null;
    }

    Phone getPhone() {
        Call call = getCall();
        if (call != null) {
            return call.getPhone();
        }
        return null;
    }

    private boolean hasMultipleTopLevelCalls() {
        int numCalls = 0;
        Phone phone = getPhone();
        if (phone != null) {
            if (!phone.getRingingCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getForegroundCall().isIdle()) {
                numCalls++;
            }
            if (!phone.getBackgroundCall().isIdle()) {
                numCalls++;
            }
        }
        return numCalls > 1;
    }

    private com.android.internal.telephony.Connection getForegroundConnection() {
        if (getPhone() != null) {
            return getPhone().getForegroundCall().getEarliestConnection();
        }
        return null;
    }

     /**
     * Checks for and returns the list of conference participants
     * associated with this connection.
     */
    public List<ConferenceParticipant> getConferenceParticipants() {
        if (mOriginalConnection == null) {
            Log.v(this, "Null mOriginalConnection, cannot get conf participants.");
            return null;
        }
        return mOriginalConnection.getConferenceParticipants();
    }

    /**
     * Checks to see the original connection corresponds to an active incoming call. Returns false
     * if there is no such actual call, or if the associated call is not incoming (See
     * {@link Call.State#isRinging}).
     */
    private boolean isValidRingingCall() {
        if (getPhone() == null) {
            Log.v(this, "isValidRingingCall, phone is null");
            return false;
        }

        Call ringingCall = getPhone().getRingingCall();
        if (!ringingCall.getState().isRinging()) {
            Log.v(this, "isValidRingingCall, ringing call is not in ringing state");
            return false;
        }

        if (ringingCall.getEarliestConnection() != mOriginalConnection) {
            Log.v(this, "isValidRingingCall, ringing call connection does not match");
            return false;
        }

        Log.v(this, "isValidRingingCall, returning true");
        return true;
    }

    // Make sure the extras being passed into this method is a COPY of the original extras Bundle.
    // We do not want the extras to be cleared or modified during mOriginalConnectionExtras.putAll
    // below.
    protected void updateExtras(Bundle extras) {
        if (mOriginalConnection != null) {
            if (extras != null) {
                // Check if extras have changed and need updating.
                if (!areBundlesEqual(mOriginalConnectionExtras, extras)) {
                    if (Log.DEBUG) {
                        Log.d(TelephonyConnection.this, "Updating extras:");
                        for (String key : extras.keySet()) {
                            Object value = extras.get(key);
                            if (value instanceof String) {
                                Log.d(this, "updateExtras Key=" + Log.pii(key) +
                                             " value=" + Log.pii((String)value));
                            }
                        }
                    }
                    mOriginalConnectionExtras.clear();

                    mOriginalConnectionExtras.putAll(extras);

                    // Remap any string extras that have a remapping defined.
                    for (String key : mOriginalConnectionExtras.keySet()) {
                        if (sExtrasMap.containsKey(key)) {
                            String newKey = sExtrasMap.get(key);
                            mOriginalConnectionExtras.putString(newKey, extras.getString(key));
                            mOriginalConnectionExtras.remove(key);
                        }
                    }

                    // Ensure extras are propagated to Telecom.
                    putExtras(mOriginalConnectionExtras);

                    // If extras contain Conference support information,
                    // then ensure capabilities are updated and propagated to Telecom.
                    if (mOriginalConnectionExtras.containsKey(
                            QtiCallConstants.CONF_SUPPORT_IND_EXTRA_KEY)) {
                        updateConnectionCapabilities();
                    }

                } else {
                    Log.d(this, "Extras update not required");
                }
            } else {
                Log.d(this, "updateExtras extras: " + Log.pii(extras));
            }
        }
    }

    private static boolean areBundlesEqual(Bundle extras, Bundle newExtras) {
        if (extras == null || newExtras == null) {
            return extras == newExtras;
        }

        if (extras.size() != newExtras.size()) {
            return false;
        }

        for(String key : extras.keySet()) {
            if (key != null) {
                final Object value = extras.get(key);
                final Object newValue = newExtras.get(key);
                if (!Objects.equals(value, newValue)) {
                    return false;
                }
            }
        }
        return true;
    }

    void setStateOverride(Call.State state) {
        mIsStateOverridden = true;
        mConnectionOverriddenState = state;
        // Need to keep track of the original connection's state before override.
        mOriginalConnectionState = mOriginalConnection.getState();
        updateStateInternal();
    }

    void resetStateOverride() {
        mIsStateOverridden = false;
        updateStateInternal();
    }

    void updateStateInternal() {
        if (mOriginalConnection == null) {
            return;
        }
        Call.State newState;
        // If the state is overridden and the state of the original connection hasn't changed since,
        // then we continue in the overridden state, else we go to the original connection's state.
        if (mIsStateOverridden && mOriginalConnectionState == mOriginalConnection.getState()) {
            newState = mConnectionOverriddenState;
        } else {
            newState = mOriginalConnection.getState();
        }
        Log.v(this, "Update state from %s to %s for %s", mConnectionState, newState, this);

        final String number = mOriginalConnection.getAddress();
        final Phone phone = mOriginalConnection.getCall().getPhone();
        int cause = mOriginalConnection.getDisconnectCause();
        final boolean isEmergencyNumber = PhoneUtils.isLocalEmergencyNumber(
                phone.getContext(), number);

        Log.v(this, "Update state from %s to %s for %s", mConnectionState, newState, this);
        if (mConnectionState != newState) {
            mConnectionState = newState;
            switch (newState) {
                case IDLE:
                    break;
                case ACTIVE:
                    setActiveInternal();
                    break;
                case HOLDING:
                    setOnHold();
                    break;
                case DIALING:
                case ALERTING:
                    if (mOriginalConnection != null && mOriginalConnection.isPulledCall()) {
                        setPulling();
                    } else {
                        setDialing();
                    }
                    break;
                case INCOMING:
                case WAITING:
                    setRinging();
                    break;
                case DISCONNECTED:
                    synchronized (mLock) {
                        if(isEmergencyNumber && !mHangupByUser &&
                                (TelephonyManager.getDefault().getPhoneCount() > 1) &&
                                ((cause == android.telephony.DisconnectCause.EMERGENCY_TEMP_FAILURE)
                                || (cause == android.telephony.DisconnectCause.
                                EMERGENCY_PERM_FAILURE))) {
                            // If emergency call failure is received with cause codes
                            // EMERGENCY_TEMP_FAILURE & EMERGENCY_PERM_FAILURE, then redial
                            // on other sub.
                            emergencyRedial(cause, phone);
                            break;
                        } else if (mSsNotification != null) {
                            setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                                    mOriginalConnection.getDisconnectCause(),
                                    mOriginalConnection.getVendorDisconnectCause(),
                                    mSsNotification.notificationType,
                                    mSsNotification.code));
                            mSsNotification = null;
                            DisconnectCauseUtil.mNotificationCode = 0xFF;
                            DisconnectCauseUtil.mNotificationType = 0xFF;
                        } else {
                            setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                                mOriginalConnection.getDisconnectCause(),
                                mOriginalConnection.getVendorDisconnectCause()));
                        }
                    }
                    mHangupByUser = false;
                    resetDisconnectCause();
                    close();
                    break;
                case DISCONNECTING:
                    break;
            }
        }
    }

    void updateState() {
        if (mOriginalConnection == null) {
            return;
        }

        updateStateInternal();
        updateStatusHints();
        updateConnectionCapabilities();
        updateConnectionProperties();
        updateAddress();
        updateMultiparty();
    }

    /**
     * Checks for changes to the multiparty bit.  If a conference has started, informs listeners.
     */
    private void updateMultiparty() {
        if (mOriginalConnection == null) {
            return;
        }

        if (mIsMultiParty != mOriginalConnection.isMultiparty()) {
            mIsMultiParty = mOriginalConnection.isMultiparty();

            if (mIsMultiParty) {
                notifyConferenceStarted();
            }
        }
    }

    /**
     * Handles a failure when merging calls into a conference.
     * {@link com.android.internal.telephony.Connection.Listener#onConferenceMergedFailed()}
     * listener.
     */
    private void handleConferenceMergeFailed(){
        mHandler.obtainMessage(MSG_CONFERENCE_MERGE_FAILED).sendToTarget();
    }

    /**
     * Handles requests to update the multiparty state received via the
     * {@link com.android.internal.telephony.Connection.Listener#onMultipartyStateChanged(boolean)}
     * listener.
     * <p>
     * Note: We post this to the mHandler to ensure that if a conference must be created as a
     * result of the multiparty state change, the conference creation happens on the correct
     * thread.  This ensures that the thread check in
     * {@link com.android.internal.telephony.Phone#checkCorrectThread(android.os.Handler)}
     * does not fire.
     *
     * @param isMultiParty {@code true} if this connection is multiparty, {@code false} otherwise.
     */
    private void handleMultipartyStateChange(boolean isMultiParty) {
        Log.i(this, "Update multiparty state to %s", isMultiParty ? "Y" : "N");
        mHandler.obtainMessage(MSG_MULTIPARTY_STATE_CHANGED, isMultiParty).sendToTarget();
    }

    private void emergencyRedial(int cause, Phone phone) {
        int PhoneIdToCall = phone.getPhoneId();
        if (cause == android.telephony.DisconnectCause.EMERGENCY_PERM_FAILURE) {
            Log.d(this,"EMERGENCY_PERM_FAILURE received on sub:" + PhoneIdToCall);
            // update mIsPermDiscCauseReceived so that next redial doesn't occur
            // on this sub
            mIsPermDiscCauseReceived[phone.getPhoneId()] = true;
            PhoneIdToCall = SubscriptionManager.INVALID_PHONE_INDEX;
        }
        // Check for any subscription on which EMERGENCY_PERM_FAILURE is received
        // if no such sub, then redial should be stopped.
        for (int i = getNextPhoneId(phone.getPhoneId()); i != phone.getPhoneId();
                i = getNextPhoneId(i)) {
            if (mIsPermDiscCauseReceived[i] == false) {
                PhoneIdToCall = i;
                break;
            }
        }

        long subId = SubscriptionController.getInstance().getSubIdUsingPhoneId(PhoneIdToCall);
        if (PhoneIdToCall == SubscriptionManager.INVALID_PHONE_INDEX) {
            Log.d(this,"EMERGENCY_PERM_FAILURE received on all subs, abort redial");
            setDisconnected(DisconnectCauseUtil.toTelecomDisconnectCause(
                    mOriginalConnection.getDisconnectCause(),
                    mOriginalConnection.getVendorDisconnectCause()));
            resetDisconnectCause();
            close();
        } else {
            Log.d(this,"Redial emergency call on subscription " + PhoneIdToCall);
            TelecomManager telecommMgr = (getPhone() != null) ? (TelecomManager)
                    getPhone().getContext().getSystemService(Context.
                            TELECOM_SERVICE) : null;
            if (telecommMgr != null) {
                List<PhoneAccountHandle> phoneAccountHandles =
                        telecommMgr.getCallCapablePhoneAccounts();
                for (PhoneAccountHandle handle : phoneAccountHandles) {
                    String sub = handle.getId();
                    if (Long.toString(subId).equals(sub)){
                        Log.d(this,"EMERGENCY REDIAL");
                        for (TelephonyConnectionListener l : mTelephonyListeners) {
                            l.onEmergencyRedial(this, handle, PhoneIdToCall);
                        }
                    }
                }
            }
        }
    }

    private int getNextPhoneId(int curId) {
        int nextId =  curId + 1;
        if (nextId >= TelephonyManager.getDefault().getPhoneCount()) {
            nextId = 0;
        }
        return nextId;
    }

    private void resetDisconnectCause() {
        for (int i = 0; i < TelephonyManager.getDefault().getPhoneCount(); i++) {
            mIsPermDiscCauseReceived[i] = false;
        }
    }

    private void setActiveInternal() {
        if (getState() == STATE_ACTIVE) {
            Log.w(this, "Should not be called if this is already ACTIVE");
            return;
        }

        // When we set a call to active, we need to make sure that there are no other active
        // calls. However, the ordering of state updates to connections can be non-deterministic
        // since all connections register for state changes on the phone independently.
        // To "optimize", we check here to see if there already exists any active calls.  If so,
        // we issue an update for those calls first to make sure we only have one top-level
        // active call.
        if (getConnectionService() != null) {
            for (Connection current : getConnectionService().getAllConnections()) {
                if (current != this && current instanceof TelephonyConnection) {
                    TelephonyConnection other = (TelephonyConnection) current;
                    if (other.getState() == STATE_ACTIVE) {
                        other.updateState();
                    }
                }
            }
        }
        setActive();
    }

    protected void close() {
        Log.v(this, "close");
        clearOriginalConnection();
        destroy();
    }

    /**
     * Determines if the current connection is video capable.
     *
     * A connection is deemed to be video capable if the original connection capabilities state that
     * both local and remote video is supported.
     *
     * @return {@code true} if the connection is video capable, {@code false} otherwise.
     */
    private boolean isVideoCapable() {
        return can(mOriginalConnectionCapabilities, Capability.SUPPORTS_VT_LOCAL_BIDIRECTIONAL)
                && can(mOriginalConnectionCapabilities,
                Capability.SUPPORTS_VT_REMOTE_BIDIRECTIONAL);
    }

    /**
     * Determines if the current connection is an external connection.
     *
     * A connection is deemed to be external if the original connection capabilities state that it
     * is.
     *
     * @return {@code true} if the connection is external, {@code false} otherwise.
     */
    private boolean isExternalConnection() {
        return can(mOriginalConnectionCapabilities, Capability.IS_EXTERNAL_CONNECTION)
                && can(mOriginalConnectionCapabilities,
                Capability.IS_EXTERNAL_CONNECTION);
    }

    /**
     * Determines if the current connection is pullable.
     *
     * A connection is deemed to be pullable if the original connection capabilities state that it
     * is.
     *
     * @return {@code true} if the connection is pullable, {@code false} otherwise.
     */
    private boolean isPullable() {
        return can(mOriginalConnectionCapabilities, Capability.IS_EXTERNAL_CONNECTION)
                && can(mOriginalConnectionCapabilities, Capability.IS_PULLABLE);
    }

    /**
     * Sets whether or not CDMA enhanced call privacy is enabled for this connection.
     */
    private void setCdmaVoicePrivacy(boolean isEnabled) {
        if(mIsCdmaVoicePrivacyEnabled != isEnabled) {
            mIsCdmaVoicePrivacyEnabled = isEnabled;
            updateConnectionProperties();
        }
    }

    /**
     * Applies capabilities specific to conferences termination to the
     * {@code ConnectionCapabilities} bit-mask.
     *
     * @param capabilities The {@code ConnectionCapabilities} bit-mask.
     * @return The capabilities with the IMS conference capabilities applied.
     */
    private int applyConferenceTerminationCapabilities(int capabilities) {
        int currentCapabilities = capabilities;

        // An IMS call cannot be individually disconnected or separated from its parent conference.
        // If the call was IMS, even if it hands over to GMS, these capabilities are not supported.
        if (!mWasImsConnection) {
            currentCapabilities |= CAPABILITY_DISCONNECT_FROM_CONFERENCE;
            currentCapabilities |= CAPABILITY_SEPARATE_FROM_CONFERENCE;
        }

        return currentCapabilities;
    }

    /**
     * Applies the add participant capabilities to the {@code CallCapabilities} bit-mask.
     *
     * @param callCapabilities The {@code CallCapabilities} bit-mask.
     * @return The capabilities with the add participant capabilities applied.
     */
    private int applyAddParticipantCapabilities(int callCapabilities) {
        int currentCapabilities = callCapabilities;
        if (isAddParticipantCapable()) {
            currentCapabilities = applyCapability(currentCapabilities,
                    Connection.CAPABILITY_ADD_PARTICIPANT);
        } else {
            currentCapabilities = removeCapability(currentCapabilities,
                    Connection.CAPABILITY_ADD_PARTICIPANT);
        }

        return currentCapabilities;
    }

    private boolean isAddParticipantCapable() {
        boolean isCapable = getPhone() != null &&
               (getPhone().getPhoneType() == PhoneConstants.PHONE_TYPE_IMS) &&
               !mIsEmergencyNumber && (mConnectionState == Call.State.ACTIVE
               || mConnectionState == Call.State.HOLDING);

        /**
         * For individual IMS calls, if the extra for remote conference support is
         *     - indicated, then consider the same for add participant capability
         *     - not indicated, then the add participant capability is same as before.
         */
        if (isCapable && (mOriginalConnection != null) && !mIsMultiParty) {
            isCapable = mOriginalConnectionExtras.getBoolean(
                    QtiCallConstants.CONF_SUPPORT_IND_EXTRA_KEY, true);
            Log.i(this, "isAddParticipantCapable: lower layer indication=" + isCapable);
        }
        return isCapable;
    }

    private int applyCapability(int capabilities, int capability) {
        int newCapabilities = capabilities | capability;
        return newCapabilities;
    }

    private int removeCapability(int capabilities, int capability) {
        int newCapabilities = capabilities & ~capability;
        return newCapabilities;
    }

    /**
     * Stores the new original connection capabilities, and applies them to the current connection,
     * notifying any listeners as necessary. 
     *
     * @param connectionCapabilities The original connection capabilties.
     */
    public void setOriginalConnectionCapabilities(int connectionCapabilities) {
        mOriginalConnectionCapabilities = connectionCapabilities;
        updateConnectionCapabilities();
        updateConnectionProperties();
    }

    /**
     * Called to apply the capabilities present in the {@link #mOriginalConnection} to this
     * {@link Connection}.  Provides a mapping between the capabilities present in the original
     * connection (see {@link com.android.internal.telephony.Connection.Capability}) and those in
     * this {@link Connection}.
     *
     * @param capabilities The capabilities bitmask from the {@link Connection}.
     * @return the capabilities bitmask with the original connection capabilities remapped and
     *      applied.
     */
    public int applyOriginalConnectionCapabilities(int capabilities) {
        // We only support downgrading to audio if both the remote and local side support
        // downgrading to audio.
        boolean supportsDowngradeToAudio = can(mOriginalConnectionCapabilities,
                Capability.SUPPORTS_DOWNGRADE_TO_VOICE_LOCAL |
                        Capability.SUPPORTS_DOWNGRADE_TO_VOICE_REMOTE);
        capabilities = changeBitmask(capabilities,
                CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO, !supportsDowngradeToAudio);

        capabilities = changeBitmask(capabilities, CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL,
                can(mOriginalConnectionCapabilities, Capability.SUPPORTS_VT_REMOTE_BIDIRECTIONAL));

        capabilities = changeBitmask(capabilities, CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL,
                can(mOriginalConnectionCapabilities, Capability.SUPPORTS_VT_LOCAL_BIDIRECTIONAL));

        return capabilities;
    }

    /**
     * Sets whether the call is using wifi. Used when rebuilding the capabilities to set or unset
     * the {@link Connection#PROPERTY_WIFI} property.
     */
    public void setWifi(boolean isWifi) {
        mIsWifi = isWifi;
        updateConnectionProperties();
        updateStatusHints();
        refreshDisableAddCall();
    }

    /**
     * Whether the call is using wifi.
     */
    boolean isWifi() {
        return mIsWifi;
    }

    /**
     * Sets the current call audio quality. Used during rebuild of the properties
     * to set or unset the {@link Connection#PROPERTY_HIGH_DEF_AUDIO} property.
     *
     * @param audioQuality The audio quality.
     */
    public void setAudioQuality(int audioQuality) {
        mHasHighDefAudio = audioQuality ==
                com.android.internal.telephony.Connection.AUDIO_QUALITY_HIGH_DEFINITION;
        updateConnectionProperties();
    }

    void resetStateForConference() {
        if (getState() == Connection.STATE_HOLDING) {
            resetStateOverride();
        }
    }

    boolean setHoldingForConference() {
        if (getState() == Connection.STATE_ACTIVE) {
            setStateOverride(Call.State.HOLDING);
            return true;
        }
        return false;
    }

    /**
     * For video calls, sets whether this connection supports pausing the outgoing video for the
     * call using the {@link android.telecom.VideoProfile#STATE_PAUSED} VideoState.
     *
     * @param isVideoPauseSupported {@code true} if pause state supported, {@code false} otherwise.
     */
    public void setVideoPauseSupported(boolean isVideoPauseSupported) {
        mIsVideoPauseSupported = isVideoPauseSupported;
    }

    /**
     * @return {@code true} if this connection supports pausing the outgoing video.
     */
    public boolean getVideoPauseSupported() {
        return mIsVideoPauseSupported;
    }

    /**
     * Sets whether this connection supports conference calling.
     * @param isConferenceSupported {@code true} if conference calling is supported by this
     *                                         connection, {@code false} otherwise.
     */
    public void setConferenceSupported(boolean isConferenceSupported) {
        mIsConferenceSupported = isConferenceSupported;
    }

    /**
     * @return {@code true} if this connection supports merging calls into a conference.
     */
    public boolean isConferenceSupported() {
        return mIsConferenceSupported;
    }

    /**
     * Whether the original connection is an IMS connection.
     * @return {@code True} if the original connection is an IMS connection, {@code false}
     *     otherwise.
     */
    protected boolean isImsConnection() {
        com.android.internal.telephony.Connection originalConnection = getOriginalConnection();
        return originalConnection != null &&
                originalConnection.getPhoneType() == PhoneConstants.PHONE_TYPE_IMS;
    }

    /**
     * Whether the original connection was ever an IMS connection, either before or now.
     * @return {@code True} if the original connection was ever an IMS connection, {@code false}
     *     otherwise.
     */
    public boolean wasImsConnection() {
        return mWasImsConnection;
    }

    private static Uri getAddressFromNumber(String number) {
        // Address can be null for blocked calls.
        if (number == null) {
            number = "";
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    /**
     * Changes a capabilities bit-mask to add or remove a capability.
     *
     * @param bitmask The bit-mask.
     * @param bitfield The bit-field to change.
     * @param enabled Whether the bit-field should be set or removed.
     * @return The bit-mask with the bit-field changed.
     */
    private int changeBitmask(int bitmask, int bitfield, boolean enabled) {
        if (enabled) {
            return bitmask | bitfield;
        } else {
            return bitmask & ~bitfield;
        }
    }

    private void updateStatusHints() {
        boolean isIncoming = isValidRingingCall();
        if (mIsWifi && (isIncoming || getState() == STATE_ACTIVE) &&
                (getPhone() != null)) {
            int labelId = isIncoming
                    ? R.string.status_hint_label_incoming_wifi_call
                    : R.string.status_hint_label_wifi_call;

            Context context = getPhone().getContext();
            setStatusHints(new StatusHints(
                    context.getString(labelId),
                    Icon.createWithResource(
                            context.getResources(),
                            R.drawable.ic_signal_wifi_4_bar_24dp),
                    null /* extras */));
        } else {
            setStatusHints(null);
        }
    }

    /**
     * Register a listener for {@link TelephonyConnection} specific triggers.
     * @param l The instance of the listener to add
     * @return The connection being listened to
     */
    public final TelephonyConnection addTelephonyConnectionListener(TelephonyConnectionListener l) {
        mTelephonyListeners.add(l);
        // If we already have an original connection, let's call back immediately.
        // This would be the case for incoming calls.
        if (mOriginalConnection != null) {
            fireOnOriginalConnectionConfigured();
        }
        return this;
    }

    /**
     * Remove a listener for {@link TelephonyConnection} specific triggers.
     * @param l The instance of the listener to remove
     * @return The connection being listened to
     */
    public final TelephonyConnection removeTelephonyConnectionListener(
            TelephonyConnectionListener l) {
        if (l != null) {
            mTelephonyListeners.remove(l);
        }
        return this;
    }

    /**
     * Fire a callback to the various listeners for when the original connection is
     * set in this {@link TelephonyConnection}
     */
    private final void fireOnOriginalConnectionConfigured() {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onOriginalConnectionConfigured(this);
        }
    }

    private final void fireOnOriginalConnectionRetryDial() {
        for (TelephonyConnectionListener l : mTelephonyListeners) {
            l.onOriginalConnectionRetry(this);
        }
    }

    /**
     * Handles exiting ECM mode.
     */
    protected void handleExitedEcmMode() {
        updateConnectionProperties();
    }

    /**
     * Determines whether the connection supports conference calling.  A connection supports
     * conference calling if it:
     * 1. Is not an emergency call.
     * 2. Carrier supports conference calls.
     * 3. If call is a video call, carrier supports video conference calls.
     * 4. If call is a wifi call and VoWIFI is disabled and carrier supports merging these calls.
     */
    private void refreshConferenceSupported() {
        boolean isVideoCall = VideoProfile.isVideo(getVideoState());
        Phone phone = getPhone();
        if (phone == null) {
            return;
        }
        boolean isIms = phone.getPhoneType() == PhoneConstants.PHONE_TYPE_IMS;
        boolean isVoWifiEnabled = false;
        if (isIms) {
            ImsPhone imsPhone = (ImsPhone) phone;
            isVoWifiEnabled = ImsUtil.isWfcEnabled(phone.getContext());
        }
        PhoneAccountHandle phoneAccountHandle = isIms ? PhoneUtils
                .makePstnPhoneAccountHandle(phone.getDefaultPhone())
                : PhoneUtils.makePstnPhoneAccountHandle(phone);
        TelecomAccountRegistry telecomAccountRegistry = TelecomAccountRegistry
                .getInstance(getPhone().getContext());
        boolean isConferencingSupported = telecomAccountRegistry
                .isMergeCallSupported(phoneAccountHandle);
        mIsCarrierVideoConferencingSupported = telecomAccountRegistry
                .isVideoConferencingSupported(phoneAccountHandle);
        boolean isMergeOfWifiCallsAllowedWhenVoWifiOff = telecomAccountRegistry
                .isMergeOfWifiCallsAllowedWhenVoWifiOff(phoneAccountHandle);

        Log.v(this, "refreshConferenceSupported : isConfSupp=%b, isVidConfSupp=%b, " +
                "isMergeOfWifiAllowed=%b, isWifi=%b, isVoWifiEnabled=%b", isConferencingSupported,
                mIsCarrierVideoConferencingSupported, isMergeOfWifiCallsAllowedWhenVoWifiOff,
                isWifi(), isVoWifiEnabled);
        boolean isConferenceSupported = true;
        if (mTreatAsEmergencyCall) {
            isConferenceSupported = false;
            Log.d(this, "refreshConferenceSupported = false; emergency call");
        } else if (!isConferencingSupported) {
            isConferenceSupported = false;
            Log.d(this, "refreshConferenceSupported = false; carrier doesn't support conf.");
        } else if (isVideoCall && !mIsCarrierVideoConferencingSupported) {
            isConferenceSupported = false;
            Log.d(this, "refreshConferenceSupported = false; video conf not supported.");
        } else if (!isMergeOfWifiCallsAllowedWhenVoWifiOff && isWifi() && !isVoWifiEnabled) {
            isConferenceSupported = false;
            Log.d(this,
                    "refreshConferenceSupported = false; can't merge wifi calls when voWifi off.");
        } else {
            Log.d(this, "refreshConferenceSupported = true.");
        }

        if (isConferenceSupported != isConferenceSupported()) {
            setConferenceSupported(isConferenceSupported);
            notifyConferenceSupportedChanged(isConferenceSupported);
        }
    }
    /**
     * Provides a mapping from extras keys which may be found in the
     * {@link com.android.internal.telephony.Connection} to their equivalents defined in
     * {@link android.telecom.Connection}.
     *
     * @return Map containing key mappings.
     */
    private static Map<String, String> createExtrasMap() {
        Map<String, String> result = new HashMap<String, String>();
        result.put(ImsCallProfile.EXTRA_CHILD_NUMBER,
                android.telecom.Connection.EXTRA_CHILD_ADDRESS);
        result.put(ImsCallProfile.EXTRA_DISPLAY_TEXT,
                android.telecom.Connection.EXTRA_CALL_SUBJECT);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Creates a string representation of this {@link TelephonyConnection}.  Primarily intended for
     * use in log statements.
     *
     * @return String representation of the connection.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[TelephonyConnection objId:");
        sb.append(System.identityHashCode(this));
        sb.append(" telecomCallID:");
        sb.append(getTelecomCallId());
        sb.append(" type:");
        if (isImsConnection()) {
            sb.append("ims");
        } else if (this instanceof com.android.services.telephony.GsmConnection) {
            sb.append("gsm");
        } else if (this instanceof CdmaConnection) {
            sb.append("cdma");
        }
        sb.append(" state:");
        sb.append(Connection.stateToString(getState()));
        sb.append(" capabilities:");
        sb.append(capabilitiesToString(getConnectionCapabilities()));
        sb.append(" properties:");
        sb.append(propertiesToString(getConnectionProperties()));
        sb.append(" address:");
        sb.append(Log.pii(getAddress()));
        sb.append(" originalConnection:");
        sb.append(mOriginalConnection);
        sb.append(" partOfConf:");
        if (getConference() == null) {
            sb.append("N");
        } else {
            sb.append("Y");
        }
        sb.append(" confSupported:");
        sb.append(mIsConferenceSupported ? "Y" : "N");
        sb.append("]");
        return sb.toString();
    }
}
