package com.android.phone.vvm.omtp;

import android.content.ComponentName;
import android.telecom.PhoneAccountHandle;
import android.test.AndroidTestCase;
import android.util.ArraySet;

import java.util.Arrays;

public class VisualVoicemailPreferencesTest extends AndroidTestCase {

    public void testWriteRead() {
        VisualVoicemailPreferences preferences = new VisualVoicemailPreferences(getContext(),
                createFakeHandle("testWriteRead"));
        preferences.edit()
                .putBoolean("boolean", true)
                .putFloat("float", 0.5f)
                .putInt("int", 123)
                .putLong("long", 456)
                .putString("string", "foo")
                .putStringSet("stringset", new ArraySet<>(Arrays.asList("bar", "baz")))
                .apply();

        assertTrue(preferences.contains("boolean"));
        assertTrue(preferences.contains("float"));
        assertTrue(preferences.contains("int"));
        assertTrue(preferences.contains("long"));
        assertTrue(preferences.contains("string"));
        assertTrue(preferences.contains("stringset"));

        assertEquals(true, preferences.getBoolean("boolean", false));
        assertEquals(0.5f, preferences.getFloat("float", 0));
        assertEquals(123, preferences.getInt("int", 0));
        assertEquals(456, preferences.getLong("long", 0));
        assertEquals("foo", preferences.getString("string", null));
        assertEquals(new ArraySet<>(Arrays.asList("bar", "baz")),
                preferences.getStringSet("stringset", null));
    }

    public void testReadDefault() {
        VisualVoicemailPreferences preferences = new VisualVoicemailPreferences(getContext(),
                createFakeHandle("testReadDefault"));

        assertFalse(preferences.contains("boolean"));
        assertFalse(preferences.contains("float"));
        assertFalse(preferences.contains("int"));
        assertFalse(preferences.contains("long"));
        assertFalse(preferences.contains("string"));
        assertFalse(preferences.contains("stringset"));

        assertEquals(true, preferences.getBoolean("boolean", true));
        assertEquals(2.5f, preferences.getFloat("float", 2.5f));
        assertEquals(321, preferences.getInt("int", 321));
        assertEquals(654, preferences.getLong("long", 654));
        assertEquals("foo2", preferences.getString("string", "foo2"));
        assertEquals(new ArraySet<>(Arrays.asList("bar2", "baz2")),
                preferences.getStringSet(
                        "stringset", new ArraySet<>(Arrays.asList("bar2", "baz2"))));
    }

    public void testReadDefaultNull() {
        VisualVoicemailPreferences preferences = new VisualVoicemailPreferences(getContext(),
                createFakeHandle("testReadDefaultNull"));
        assertNull(preferences.getString("string", null));
        assertNull(preferences.getStringSet("stringset", null));
    }

    public void testDifferentHandle() {
        VisualVoicemailPreferences preferences1 = new VisualVoicemailPreferences(getContext(),
                createFakeHandle("testDifferentHandle1"));
        VisualVoicemailPreferences preferences2 = new VisualVoicemailPreferences(getContext(),
                createFakeHandle("testDifferentHandle1"));

        preferences1.edit().putString("string", "foo");
        assertFalse(preferences2.contains("string"));
    }

    private PhoneAccountHandle createFakeHandle(String id) {
        return new PhoneAccountHandle(new ComponentName(getContext(), this.getClass()), id);
    }
}
