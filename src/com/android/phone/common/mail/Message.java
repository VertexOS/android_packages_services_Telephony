/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.phone.common.mail;

import com.android.internal.annotations.VisibleForTesting;

import java.util.HashSet;

/**
 * Object to represent an email message.
 */
public class Message {
    public static final String FLAG_SEEN = "seen";
    public static final String FLAG_DELETED = "deleted";
    public static final String FLAG_FLAGGED = "flagged";
    public static final String FLAG_ANSWERED = "answered";

    protected String mUid;
    private HashSet<String> mFlags = null;

    public String getUid() {
        return mUid;
    }

    public void setUid(String uid) {
        mUid = uid;
    }

    private HashSet<String> getFlagSet() {
        if (mFlags == null) {
            mFlags = new HashSet<String>();
        }
        return mFlags;
    }

    /**
     * Set/clear a flag directly, without involving overrides of {@link #setFlag} in subclasses.
     * Only used for testing.
     */
    @VisibleForTesting
    private final void setFlagDirectlyForTest(String flag, boolean set) throws MessagingException {
        if (set) {
            getFlagSet().add(flag);
        } else {
            getFlagSet().remove(flag);
        }
    }

    public void setFlag(String flag, boolean set) throws MessagingException {
        setFlagDirectlyForTest(flag, set);
    }

    /**
     * This method calls setFlag(Flag, boolean)
     * @param flags
     * @param set
     */
    public void setFlags(String[] flags, boolean set) throws MessagingException {
        for (String flag : flags) {
            setFlag(flag, set);
        }
    }
}
