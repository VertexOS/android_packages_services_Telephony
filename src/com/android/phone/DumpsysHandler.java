
package com.android.phone;

import com.android.phone.vvm.omtp.LocalLogHelper;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Handles "adb shell dumpsys phone" and bug report dump.
 */
public class DumpsysHandler {

    public static void dump(FileDescriptor fd, final PrintWriter writer, String[] args) {
        // Dump OMTP visual voicemail log.
        LocalLogHelper.dump(fd, writer, args);
    }
}
