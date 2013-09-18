/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.telephony.Call;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.PhoneConstants;

import com.google.android.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to manage the "Respond via Message" feature for incoming calls.
 *
 * @see com.android.phone.InCallScreen.internalRespondViaSms()
 */
public class RejectWithTextMessageManager {

    private static final String TAG = RejectWithTextMessageManager.class.getSimpleName();
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    private static final String PERMISSION_SEND_RESPOND_VIA_MESSAGE =
            "android.permission.SEND_RESPOND_VIA_MESSAGE";

    /** The array of "canned responses"; see loadCannedResponses(). */
    private String[] mCannedResponses;

    /** SharedPreferences file name for our persistent settings. */
    private static final String SHARED_PREFERENCES_NAME = "respond_via_sms_prefs";

    private Intent mIntent;

    private ArrayList<ComponentName> mComponentsWithPermission = new ArrayList<ComponentName>();

            // Preference keys for the 4 "canned responses"; see RespondViaSmsManager$Settings.
    // Since (for now at least) the number of messages is fixed at 4, and since
    // SharedPreferences can't deal with arrays anyway, just store the messages
    // as 4 separate strings.
    private static final int NUM_CANNED_RESPONSES = 4;
    private static final String KEY_CANNED_RESPONSE_PREF_1 = "canned_response_pref_1";
    private static final String KEY_CANNED_RESPONSE_PREF_2 = "canned_response_pref_2";
    private static final String KEY_CANNED_RESPONSE_PREF_3 = "canned_response_pref_3";
    private static final String KEY_CANNED_RESPONSE_PREF_4 = "canned_response_pref_4";
    /* package */ static final String KEY_INSTANT_TEXT_DEFAULT_COMPONENT =
            "instant_text_def_component";

    /* package */ static final String TAG_ALL_SMS_SERVICES = "com.android.phone.AvailablePackages";
    /* package */ static final String TAG_SEND_SMS = "com.android.phone.MessageIntent";

    /**
     * Read the (customizable) canned responses from SharedPreferences,
     * or from defaults if the user has never actually brought up
     * the Settings UI.
     *
     * This method does disk I/O (reading the SharedPreferences file)
     * so don't call it from the main thread.
     *
     * @see com.android.phone.RejectWithTextMessageManager.Settings
     */
    public static ArrayList<String> loadCannedResponses() {
        if (DBG) log("loadCannedResponses()...");

        final SharedPreferences prefs = PhoneGlobals.getInstance().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        final Resources res = PhoneGlobals.getInstance().getResources();

        final ArrayList<String> responses = new ArrayList<String>(NUM_CANNED_RESPONSES);

        // Note the default values here must agree with the corresponding
        // android:defaultValue attributes in respond_via_sms_settings.xml.

        responses.add(0, prefs.getString(KEY_CANNED_RESPONSE_PREF_1,
                                       res.getString(R.string.respond_via_sms_canned_response_1)));
        responses.add(1, prefs.getString(KEY_CANNED_RESPONSE_PREF_2,
                                       res.getString(R.string.respond_via_sms_canned_response_2)));
        responses.add(2, prefs.getString(KEY_CANNED_RESPONSE_PREF_3,
                                       res.getString(R.string.respond_via_sms_canned_response_3)));
        responses.add(3, prefs.getString(KEY_CANNED_RESPONSE_PREF_4,
                                       res.getString(R.string.respond_via_sms_canned_response_4)));
        return responses;
    }

    private void sendTextAndExit() {
        // Send the selected message immediately with no user interaction.
        if (mIntent.getComponent() != null) {
            PhoneGlobals.getInstance().startService(mIntent);
        }

        // ...and show a brief confirmation to the user (since
        // otherwise it's hard to be sure that anything actually
        // happened.)
        // TODO(klp): Ask the InCallUI to show a confirmation


        // TODO: If the device is locked, this toast won't actually ever
        // be visible!  (That's because we're about to dismiss the call
        // screen, which means that the device will return to the
        // keyguard.  But toasts aren't visible on top of the keyguard.)
        // Possible fixes:
        // (1) Is it possible to allow a specific Toast to be visible
        //     on top of the keyguard?
        // (2) Artifically delay the dismissCallScreen() call by 3
        //     seconds to allow the toast to be seen?
        // (3) Don't use a toast at all; instead use a transient state
        //     of the InCallScreen (perhaps via the InCallUiState
        //     progressIndication feature), and have that state be
        //     visible for 3 seconds before calling dismissCallScreen().
    }

    /**
     * Queries the System to determine what packages contain services that can handle the instant
     * text response Action AND have permissions to do so.
     */
    private static ArrayList<ComponentName> getPackagesWithInstantTextPermission() {
        final PackageManager packageManager = PhoneGlobals.getInstance().getPackageManager();

        final ArrayList<ComponentName> componentsWithPermission = new ArrayList<ComponentName>();

        // Get list of all services set up to handle the Instant Text intent.
        final List<ResolveInfo> infos = packageManager.queryIntentServices(
                getInstantTextIntent("", null, null), 0);

        // Collect all the valid services
        for (ResolveInfo resolveInfo : infos) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            if (serviceInfo == null) {
                Log.w(TAG, "Ignore package without proper service.");
                continue;
            }

            // A Service is valid only if it requires the permission
            // PERMISSION_SEND_RESPOND_VIA_MESSAGE
            if (PERMISSION_SEND_RESPOND_VIA_MESSAGE.equals(serviceInfo.permission)) {
                componentsWithPermission.add(new ComponentName(serviceInfo.packageName,
                    serviceInfo.name));
            }
        }

        return componentsWithPermission;
    }

    /**
     * @param phoneNumber Must not be null.
     * @param message Can be null. If message is null, the returned Intent will be configured to
     * launch the SMS compose UI. If non-null, the returned Intent will cause the specified message
     * to be sent with no interaction from the user.
     * @param component The component that should handle this intent.
     * @return Service Intent for the instant response.
     */
    private static Intent getInstantTextIntent(String phoneNumber, String message,
            ComponentName component) {
        final Uri uri = Uri.fromParts(Constants.SCHEME_SMSTO, phoneNumber, null);
        final Intent intent = new Intent(TelephonyManager.ACTION_RESPOND_VIA_MESSAGE, uri);
        if (message != null) {
            intent.putExtra(Intent.EXTRA_TEXT, message);
        } else {
            intent.putExtra("exit_on_sent", true);
            intent.putExtra("showUI", true);
        }
        if (component != null) {
            intent.setComponent(component);
        }
        return intent;
    }

    private boolean getSmsService() {
        if (DBG) log("sendTextToDefaultActivity()...");
        final PackageManager packageManager = PhoneGlobals.getInstance().getPackageManager();

        // Check to see if the default component to receive this intent is already saved
        // and check to see if it still has the corrent permissions.
        final SharedPreferences prefs = PhoneGlobals.getInstance().
                getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        final String flattenedName = prefs.getString(KEY_INSTANT_TEXT_DEFAULT_COMPONENT, null);
        if (flattenedName != null) {
            if (DBG) log("Default package was found." + flattenedName);

            final ComponentName componentName = ComponentName.unflattenFromString(flattenedName);
            ServiceInfo serviceInfo = null;
            try {
                serviceInfo = packageManager.getServiceInfo(componentName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Default service does not have permission.");
            }

            if (serviceInfo != null &&
                    PERMISSION_SEND_RESPOND_VIA_MESSAGE.equals(serviceInfo.permission)) {
                mIntent.setComponent(componentName);
                return true;
            } else {
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove(KEY_INSTANT_TEXT_DEFAULT_COMPONENT);
                editor.apply();
            }
        }

        mComponentsWithPermission = getPackagesWithInstantTextPermission();

        final int size = mComponentsWithPermission.size();
        if (size == 0) {
            Log.e(TAG, "No appropriate package receiving the Intent. Don't send anything");
            return false;
        } else if (size == 1) {
            mIntent.setComponent(mComponentsWithPermission.get(0));
            return true;
        } else {
            Log.v(TAG, "Choosing from one of the apps");
            final Intent intent = new Intent(Intent.ACTION_VIEW, null);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS |
                    Intent.FLAG_ACTIVITY_NO_ANIMATION  |
                    Intent.FLAG_ACTIVITY_NO_HISTORY |
                    Intent.FLAG_FROM_BACKGROUND);
            intent.setClass(PhoneGlobals.getInstance(), TextMessagePackageChooser.class);
            intent.putExtra(TAG_ALL_SMS_SERVICES, mComponentsWithPermission);
            intent.putExtra(TAG_SEND_SMS, mIntent);
            PhoneGlobals.getInstance().startActivity(intent);
            return false;
            // return componentsWithPermission.get(0);
        }
    }

    public void rejectCallWithMessage(Call call, String message) {
        mComponentsWithPermission.clear();
        mIntent = getInstantTextIntent(call.getLatestConnection().getAddress(), message, null);
        if (getSmsService())  {
            sendTextAndExit();
        }
    }

    /**
     * @return true if the "Respond via SMS" feature should be enabled
     * for the specified incoming call.
     *
     * The general rule is that we *do* allow "Respond via SMS" except for
     * the few (relatively rare) cases where we know for sure it won't
     * work, namely:
     *   - a bogus or blank incoming number
     *   - a call from a SIP address
     *   - a "call presentation" that doesn't allow the number to be revealed
     *
     * In all other cases, we allow the user to respond via SMS.
     *
     * Note that this behavior isn't perfect; for example we have no way
     * to detect whether the incoming call is from a landline (with most
     * networks at least), so we still enable this feature even though
     * SMSes to that number will silently fail.
     */
    public static boolean allowRespondViaSmsForCall(
            com.android.services.telephony.common.Call call, Connection conn) {
        if (DBG) log("allowRespondViaSmsForCall(" + call + ")...");

        // First some basic sanity checks:
        if (call == null) {
            Log.w(TAG, "allowRespondViaSmsForCall: null ringingCall!");
            return false;
        }
        if (!(call.getState() == com.android.services.telephony.common.Call.State.INCOMING) &&
                !(call.getState() ==
                        com.android.services.telephony.common.Call.State.CALL_WAITING)) {
            // The call is in some state other than INCOMING or WAITING!
            // (This should almost never happen, but it *could*
            // conceivably happen if the ringing call got disconnected by
            // the network just *after* we got it from the CallManager.)
            Log.w(TAG, "allowRespondViaSmsForCall: ringingCall not ringing! state = "
                    + call.getState());
            return false;
        }

        if (conn == null) {
            // The call doesn't have any connections! (Again, this can
            // happen if the ringing call disconnects at the exact right
            // moment, but should almost never happen in practice.)
            Log.w(TAG, "allowRespondViaSmsForCall: null Connection!");
            return false;
        }

        // Check the incoming number:
        final String number = conn.getAddress();
        if (DBG) log("- number: '" + number + "'");
        if (TextUtils.isEmpty(number)) {
            Log.w(TAG, "allowRespondViaSmsForCall: no incoming number!");
            return false;
        }
        if (PhoneNumberUtils.isUriNumber(number)) {
            // The incoming number is actually a URI (i.e. a SIP address),
            // not a regular PSTN phone number, and we can't send SMSes to
            // SIP addresses.
            // (TODO: That might still be possible eventually, though. Is
            // there some SIP-specific equivalent to sending a text message?)
            Log.i(TAG, "allowRespondViaSmsForCall: incoming 'number' is a SIP address.");
            return false;
        }

        // Finally, check the "call presentation":
        int presentation = conn.getNumberPresentation();
        if (DBG) log("- presentation: " + presentation);
        if (presentation == PhoneConstants.PRESENTATION_RESTRICTED) {
            // PRESENTATION_RESTRICTED means "caller-id blocked".
            // The user isn't allowed to see the number in the first
            // place, so obviously we can't let you send an SMS to it.
            Log.i(TAG, "allowRespondViaSmsForCall: PRESENTATION_RESTRICTED.");
            return false;
        }

        // Allow the feature only when there's a destination for it.
        if (getPackagesWithInstantTextPermission().size() < 1) {
            return false;
        }

        // TODO: with some carriers (in certain countries) you *can* actually
        // tell whether a given number is a mobile phone or not. So in that
        // case we could potentially return false here if the incoming call is
        // from a land line.

        // If none of the above special cases apply, it's OK to enable the
        // "Respond via SMS" feature.
        return true;
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
