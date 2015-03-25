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

import android.content.Context;
import android.util.Log;

import com.android.phone.common.mail.FixedLengthInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Subclass of {@link ImapString} used for literals backed by a temp file.
 */
public class ImapTempFileLiteral extends ImapString {
    private final String TAG = "ImapTempFileLiteral";

    /* package for test */ final File mFile;

    /** Size is purely for toString() */
    private final int mSize;

    /* package */  ImapTempFileLiteral(FixedLengthInputStream stream) throws IOException {
        mSize = stream.getLength();
        mFile = File.createTempFile("imap", ".tmp", TempDirectory.getTempDirectory());

        // Unfortunately, we can't really use deleteOnExit(), because temp filenames are random
        // so it'd simply cause a memory leak.
        // deleteOnExit() simply adds filenames to a static list and the list will never shrink.
        // mFile.deleteOnExit();
        OutputStream out = new FileOutputStream(mFile);
        StreamUtils.copy(stream, out);
        out.close();
    }

    /**
     * Copies utility methods for working with byte arrays and I/O streams from guava library.
     */
    public static class StreamUtils {
        private static final int BUF_SIZE = 0x1000; // 4K
        /**
         * Copies all bytes from the input stream to the output stream.
         * Does not close or flush either stream.
         *
         * @param from the input stream to read from
         * @param to the output stream to write to
         * @return the number of bytes copied
         * @throws IOException if an I/O error occurs
         */
        public static long copy(InputStream from, OutputStream to) throws IOException {
            checkNotNull(from);
            checkNotNull(to);
            byte[] buf = new byte[BUF_SIZE];
            long total = 0;
            while (true) {
              int r = from.read(buf);
              if (r == -1) {
                break;
              }
              to.write(buf, 0, r);
              total += r;
            }
            return total;
        }

        /**
         * Reads all bytes from an input stream into a byte array.
         * Does not close the stream.
         *
         * @param in the input stream to read from
         * @return a byte array containing all the bytes from the stream
         * @throws IOException if an I/O error occurs
         */
        public static byte[] toByteArray(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            copy(in, out);
            return out.toByteArray();
        }

        /**
         * Ensures that an object reference passed as a parameter to the calling method is not null.
         *
         * @param reference an object reference
         * @return the non-null reference that was validated
         * @throws NullPointerException if {@code reference} is null
         */
        public static <T> T checkNotNull(T reference) {
            if (reference == null) {
                throw new NullPointerException();
            }
            return reference;
        }
    }

    /**
     * Make sure we delete the temp file.
     *
     * We should always be calling {@link ImapResponse#destroy()}, but it's here as a last resort.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            destroy();
        } finally {
            super.finalize();
        }
    }

    @Override
    public InputStream getAsStream() {
        checkNotDestroyed();
        try {
            return new FileInputStream(mFile);
        } catch (FileNotFoundException e) {
            // It's probably possible if we're low on storage and the system clears the cache dir.
            Log.w(TAG, "ImapTempFileLiteral: Temp file not found");

            // Return 0 byte stream as a dummy...
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    @Override
    public String getString() {
        checkNotDestroyed();
        try {
            byte[] bytes = StreamUtils.toByteArray(getAsStream());
            // Prevent crash from OOM; we've seen this, but only rarely and not reproducibly
            if (bytes.length > ImapResponseParser.LITERAL_KEEP_IN_MEMORY_THRESHOLD) {
                throw new IOException();
            }
            return new String(bytes, "US-ASCII");
        } catch (IOException e) {
            Log.w(TAG, "ImapTempFileLiteral: Error while reading temp file", e);
            return "";
        }
    }

    @Override
    public void destroy() {
        try {
            if (!isDestroyed() && mFile.exists()) {
                mFile.delete();
            }
        } catch (RuntimeException re) {
            // Just log and ignore.
            Log.w(TAG, "Failed to remove temp file: " + re.getMessage());
        }
        super.destroy();
    }

    @Override
    public String toString() {
        return String.format("{%d byte literal(file)}", mSize);
    }

    public boolean tempFileExistsForTest() {
        return mFile.exists();
    }

   /**
    * TempDirectory caches the directory used for caching file.  It is set up during application
    * initialization.
    */
   public static class TempDirectory {
       private static File sTempDirectory = null;

       public static void setTempDirectory(Context context) {
           sTempDirectory = context.getCacheDir();
       }

       public static File getTempDirectory() {
           if (sTempDirectory == null) {
               throw new RuntimeException(
                       "TempDirectory not set.  " +
                       "If in a unit test, call Email.setTempDirectory(context) in setUp().");
           }
           return sTempDirectory;
       }
   }
}