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
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.UserManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

public class RestrictedSwitchPreference extends SwitchPreference {
    private final Context mContext;
    private final Drawable mRestrictedPadlock;
    private final int mRestrictedPadlockPadding;
    private boolean mDisabledByAdmin;

    public RestrictedSwitchPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        mRestrictedPadlock = mContext
                .getDrawable(R.drawable.ic_settings_lock_outline);
        final int iconSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.restricted_lock_icon_size);
        mRestrictedPadlock.setBounds(0, 0, iconSize, iconSize);
        mRestrictedPadlockPadding = mContext.getResources()
                .getDimensionPixelSize(R.dimen.restricted_lock_icon_padding);
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
            if (mDisabledByAdmin) {
                view.setEnabled(true);
                titleView.setCompoundDrawablesRelative(null, null, mRestrictedPadlock, null);
                titleView.setCompoundDrawablePadding(mRestrictedPadlockPadding);
            } else {
                titleView.setCompoundDrawablesRelative(null, null, null, null);
            }
        }
    }

    public void checkRestrictionAndSetDisabled(String userRestriction) {
        final UserManager mUm = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        if (mUm.hasUserRestriction(userRestriction)) {
            mDisabledByAdmin = true;
            setEnabled(false);
        } else {
            mDisabledByAdmin = false;
        }
    }

    @Override
    public void performClick(PreferenceScreen preferenceScreen) {
        if (mDisabledByAdmin) {
            Intent intent = new Intent(Settings.ACTION_SHOW_ADMIN_SUPPORT_DETAILS);
            mContext.startActivity(intent);
        } else {
            super.performClick(preferenceScreen);
        }
    }
}
