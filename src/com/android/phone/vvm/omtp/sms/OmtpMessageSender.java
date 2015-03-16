/*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
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
package com.android.phone.vvm.omtp.sms;

import android.annotation.Nullable;
import android.app.PendingIntent;
import android.telephony.SmsManager;

/**
 * Interface to send client originated OMTP messages to the OMTP server.
 * <p>
 * The interface uses {@link PendingIntent} instead of a call back to notify when the message is
 * sent. This is primarily to keep the implementation simple and reuse what the underlying
 * {@link SmsManager} interface provides.
 */
public interface OmtpMessageSender {
    /**
     * Sends a request to the VVM server to activate VVM for the current subscriber.
     *
     * @param sentIntent If not NULL this PendingIntent is broadcast when the message is
     *            successfully sent, or failed.
     */
    public void requestVvmActivation(@Nullable PendingIntent sentIntent);

    /**
     * Sends a request to the VVM server to deactivate VVM for the current subscriber.
     *
     * @param sentIntent If not NULL this PendingIntent is broadcast when the message is
     *            successfully sent, or failed.
     */
    public void requestVvmDeactivation(@Nullable PendingIntent sentIntent);

    /**
     * Send a request to the VVM server to get account status of the current subscriber.
     *
     * @param sentIntent If not NULL this PendingIntent is broadcast when the message is
     *            successfully sent, or failed.
     */
    public void requestVvmStatus(@Nullable PendingIntent sentIntent);
}