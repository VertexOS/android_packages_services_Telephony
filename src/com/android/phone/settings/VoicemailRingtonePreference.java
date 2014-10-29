package com.android.phone.settings;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.preference.RingtonePreference;
import android.util.AttributeSet;

import com.android.phone.PhoneGlobals;
import com.android.phone.common.util.SettingsUtil;

/**
 * Looks up the voicemail ringtone's name asynchronously and updates the preference's summary when
 * it is created or updated.
 */
public class VoicemailRingtonePreference extends RingtonePreference {
    private static final int MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY = 1;

    private Runnable mVoicemailRingtoneLookupRunnable;
    private Handler mVoicemailRingtoneLookupComplete;

    public VoicemailRingtonePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mVoicemailRingtoneLookupComplete = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY:
                        setSummary((CharSequence) msg.obj);
                        break;
                }
            }
        };

        final Preference preference = this;
        mVoicemailRingtoneLookupRunnable = new Runnable() {
            @Override
            public void run() {
                SettingsUtil.updateRingtoneName(
                        preference.getContext(),
                        mVoicemailRingtoneLookupComplete,
                        RingtoneManager.TYPE_NOTIFICATION,
                        preference,
                        MSG_UPDATE_VOICEMAIL_RINGTONE_SUMMARY);
            }
        };

        updateRingtoneName();
    }

    @Override
    protected void onSaveRingtone(Uri ringtoneUri) {
        super.onSaveRingtone(ringtoneUri);
        updateRingtoneName();
    }

    private void updateRingtoneName() {
        new Thread(mVoicemailRingtoneLookupRunnable).start();
    }
}
