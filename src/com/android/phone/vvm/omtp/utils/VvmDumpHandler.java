package com.android.phone.vvm.omtp.utils;

import android.content.Context;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import com.android.internal.util.IndentingPrintWriter;
import com.android.phone.vvm.omtp.OmtpVvmCarrierConfigHelper;
import com.android.phone.vvm.omtp.VvmLog;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class VvmDumpHandler {

    public static void dump(Context context, FileDescriptor fd, PrintWriter writer,
            String[] args) {
        IndentingPrintWriter indentedWriter = new IndentingPrintWriter(writer, "  ");
        indentedWriter.println("******* OmtpVvm *******");
        indentedWriter.println("======= Configs =======");
        indentedWriter.increaseIndent();
        for (PhoneAccountHandle handle : TelecomManager.from(context)
                .getCallCapablePhoneAccounts()) {
            int subId = PhoneAccountHandleConverter.toSubId(handle);
            OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, subId);
            indentedWriter.println(config.toString());
        }
        indentedWriter.decreaseIndent();
        indentedWriter.println("======== Logs =========");
        VvmLog.dump(fd, indentedWriter, args);
    }
}
