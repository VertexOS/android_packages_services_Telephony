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

package com.android.phone;

import android.content.Context;
import android.os.UserManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

public class RestrictedSwitchPreference extends SwitchPreference {
    private final Context mContext;
    private boolean mDisabledByAdmin;
    private EnforcedAdmin mEnforcedAdmin;

    public RestrictedSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
    }

    public RestrictedSwitchPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RestrictedSwitchPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.switchPreferenceStyle);
    }

    public RestrictedSwitchPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindView(View view) {
        super.onBindView(view);
        final TextView titleView = (TextView) view.findViewById(com.android.internal.R.id.title);
        if (titleView != null) {
            RestrictedLockUtils.setTextViewPadlock(mContext, titleView, mDisabledByAdmin);
            if (mDisabledByAdmin) {
                view.setEnabled(true);
            }
        }
    }

    public void checkRestrictionAndSetDisabled(String userRestriction) {
        setDisabledByAdmin(RestrictedLockUtils.checkIfRestrictionEnforced(mContext, userRestriction,
                UserManager.get(mContext).getUserHandle()));
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (enabled && mDisabledByAdmin) {
            setDisabledByAdmin(null);
        } else {
            super.setEnabled(enabled);
        }
    }

    public void setDisabledByAdmin(EnforcedAdmin admin) {
        final boolean disabled = (admin != null ? true : false);
        mEnforcedAdmin = admin;
        if (mDisabledByAdmin != disabled) {
            mDisabledByAdmin = disabled;
            setEnabled(!disabled);
        }
    }

    @Override
    public void performClick(PreferenceScreen preferenceScreen) {
        if (mDisabledByAdmin) {
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(mContext, mEnforcedAdmin);
        } else {
            super.performClick(preferenceScreen);
        }
    }
}
