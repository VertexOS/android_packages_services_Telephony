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
package com.android.phone.common.mail.store.imap;

/**
 * Utility methods for use with IMAP.
 */
public class ImapUtility {
    /**
     * Apply quoting rules per IMAP RFC,
     * quoted          = DQUOTE *QUOTED-CHAR DQUOTE
     * QUOTED-CHAR     = <any TEXT-CHAR except quoted-specials> / "\" quoted-specials
     * quoted-specials = DQUOTE / "\"
     *
     * This is used primarily for IMAP login, but might be useful elsewhere.
     *
     * NOTE:  Not very efficient - you may wish to preflight this, or perhaps it should check
     * for trouble chars before calling the replace functions.
     *
     * @param s The string to be quoted.
     * @return A copy of the string, having undergone quoting as described above
     */
    public static String imapQuoted(String s) {

        // First, quote any backslashes by replacing \ with \\
        // regex Pattern:  \\    (Java string const = \\\\)
        // Substitute:     \\\\  (Java string const = \\\\\\\\)
        String result = s.replaceAll("\\\\", "\\\\\\\\");

        // Then, quote any double-quotes by replacing " with \"
        // regex Pattern:  "    (Java string const = \")
        // Substitute:     \\"  (Java string const = \\\\\")
        result = result.replaceAll("\"", "\\\\\"");

        // return string with quotes around it
        return "\"" + result + "\"";
    }
}