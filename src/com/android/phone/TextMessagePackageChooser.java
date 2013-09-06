/*

 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class TextMessagePackageChooser extends Activity {
    private static final String TAG = TextMessagePackageChooser.class.getSimpleName();

    /** SharedPreferences file name for our persistent settings. */
    private static final String SHARED_PREFERENCES_NAME = "respond_via_sms_prefs";

    private int mIconSize = -1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ArrayList<ComponentName> components = getIntent().getParcelableArrayListExtra(
                RejectWithTextMessageManager.TAG_ALL_SMS_SERVICES);
        BaseAdapter adapter = new PackageSelectionAdapter(this, components);

        PackageClickListener clickListener = new PackageClickListener(components);

        final CharSequence title = getResources().getText(
                com.android.internal.R.string.whichApplication);
        LayoutInflater inflater =
                (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final View view = inflater.inflate(com.android.internal.R.layout.always_use_checkbox,
                null);
        final CheckBox alwaysUse = (CheckBox) view.findViewById(
                com.android.internal.R.id.alwaysUse);
        alwaysUse.setText(com.android.internal.R.string.alwaysUse);
        alwaysUse.setOnCheckedChangeListener(clickListener);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setCancelable(true)
                .setOnCancelListener(new RespondViaSmsCancelListener())
                .setAdapter(adapter, clickListener)
                .setView(view);
                       
        builder.create().show();
    }
    
    private class PackageSelectionAdapter extends BaseAdapter {
        private final LayoutInflater mInflater;
        private final List<ComponentName> mComponents;

        public PackageSelectionAdapter(Context context, List<ComponentName> components) {
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mComponents = components;
        }

        @Override
        public int getCount() {
            return mComponents.size();
        }

        @Override
        public Object getItem(int position) {
            return mComponents.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(
                        com.android.internal.R.layout.activity_chooser_view_list_item, parent,
                        false);
            }

            final ComponentName component = mComponents.get(position);
            final String packageName = component.getPackageName();
            final PackageManager packageManager = getPackageManager();

            // Set the application label
            final TextView text = (TextView) convertView.findViewById(
                    com.android.internal.R.id.title);

            text.setText("");
            try {
                final ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                final CharSequence label = packageManager.getApplicationLabel(appInfo);
                if (label != null) {
                    text.setText(label);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Failed to load app label because package was not found.");
            }

            // Set the application icon
            final ImageView icon = (ImageView) convertView.findViewById(android.R.id.icon);
            Drawable drawable = null;
            try {
                drawable = getPackageManager().getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Failed to load icon because it wasn't found.");
            }
            if (drawable == null) {
                drawable = getPackageManager().getDefaultActivityIcon();
            }
            icon.setImageDrawable(drawable);
            ViewGroup.LayoutParams lp = (ViewGroup.LayoutParams) icon.getLayoutParams();
            lp.width = lp.height = getIconSize();

            return convertView;
        }

    }

    private class PackageClickListener implements DialogInterface.OnClickListener,
            CompoundButton.OnCheckedChangeListener {
        final private List<ComponentName> mComponents;
        private boolean mMakeDefault = false;

        public PackageClickListener(List<ComponentName> components) {
            mComponents = components;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            final ComponentName component = mComponents.get(which);

            if (mMakeDefault) {
                final SharedPreferences prefs = PhoneGlobals.getInstance().getSharedPreferences(
                        SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
                prefs.edit().putString(
                        RejectWithTextMessageManager.KEY_INSTANT_TEXT_DEFAULT_COMPONENT,
                        component.flattenToString()).apply();
            }

            final Intent messageIntent = (Intent) getIntent().getParcelableExtra(
                    RejectWithTextMessageManager.TAG_SEND_SMS);
            if (messageIntent != null) {
                messageIntent.setComponent(component);
                PhoneGlobals.getInstance().startService(messageIntent);
            }
            finish();
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Log.i(TAG, "mMakeDefault : " + isChecked);
            mMakeDefault = isChecked;
        }
    }

    /**
     * OnCancelListener for the "Respond via SMS" popup.
     */
    public class RespondViaSmsCancelListener implements DialogInterface.OnCancelListener {
        public RespondViaSmsCancelListener() {
        }

        /**
         * Handles the user canceling the popup, either by touching
         * outside the popup or by pressing Back.
         */
        @Override
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    }

    private int getIconSize() {
      if (mIconSize < 0) {
          final ActivityManager am =
              (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
          mIconSize = am.getLauncherLargeIconSize();
      }

      return mIconSize;
    }
}
