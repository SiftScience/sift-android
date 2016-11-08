// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SiftTest {

    private static class MemorySharedPreferences implements SharedPreferences {

        private Map<String, String> fields = Maps.newHashMap();

        private class Editor implements SharedPreferences.Editor {

            // Make a copy of fields.
            private final Map<String, String> newFields = Maps.newHashMap(fields);

            @Override
            public Editor putString(String key, String defValue) {
                newFields.put(key, defValue);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> defValues) {
                throw new AssertionError();  // Not implemented.
            }

            @Override
            public Editor putInt(String key, int defValue) {
                throw new AssertionError();  // Not implemented.
            }

            @Override
            public Editor putLong(String key, long defValue) {
                throw new AssertionError();  // Not implemented.
            }

            @Override
            public Editor putFloat(String key, float defValue) {
                throw new AssertionError();  // Not implemented.
            }

            @Override
            public Editor putBoolean(String key, boolean defValue) {
                throw new AssertionError();  // Not implemented.
            }

            @Override
            public Editor remove(String key) {
                newFields.remove(key);
                return this;
            }

            @Override
            public Editor clear() {
                newFields.clear();
                return this;
            }

            @Override
            public boolean commit() {
                fields = newFields;
                return true;
            }

            @Override
            public void apply() {
                fields = newFields;
            }
        }

        @Override
        public Map<String, String> getAll() {
            return fields;
        }

        @Nullable
        @Override
        public String getString(String key, String defValue) {
            String value = fields.get(key);
            return value != null ? value : defValue;
        }

        @Nullable
        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            throw new AssertionError();  // Not implemented.
        }

        @Override
        public int getInt(String key, int defValue) {
            throw new AssertionError();  // Not implemented.
        }

        @Override
        public long getLong(String key, long defValue) {
            throw new AssertionError();  // Not implemented.
        }

        @Override
        public float getFloat(String key, float defValue) {
            throw new AssertionError();  // Not implemented.
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            throw new AssertionError();  // Not implemented.
        }

        @Override
        public boolean contains(String key) {
            return fields.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new Editor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
            throw new AssertionError();  // Not implemented.
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
            throw new AssertionError();  // Not implemented.
        }
    }

    @Test
    public void testSift() throws Exception {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift sift = new Sift(
                mockContext(preferences), mock(ListeningScheduledExecutorService.class));

        assertNotNull(sift.getConfig());
        // Verify default values.
        assertNull(sift.getConfig().accountId);
        assertNull(sift.getConfig().beaconKey);
        assertNotNull(sift.getConfig().serverUrlFormat);

        assertNull(sift.getUserId());

        // There is always a default queue.
        assertNotNull(sift.getQueue());

        assertNull(sift.getQueue("some-queue"));
        assertNotNull(sift.createQueue("some-queue", new Queue.Config.Builder().build()));
        try {
            sift.createQueue("some-queue", new Queue.Config.Builder().build());
            fail();
        } catch (IllegalStateException e) {
            assertEquals("queue exists: some-queue", e.getMessage());
        }

        assertTrue(preferences.fields.isEmpty());
    }

    @Test
    public void testSave() throws Exception {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift sift1 =
                new Sift(mockContext(preferences), mock(ListeningScheduledExecutorService.class));
        assertTrue(preferences.fields.isEmpty());

        sift1.getQueue().append(new Event("type", "path", ImmutableMap.of("key", "value")));
        sift1.save();
        assertEquals(
                ImmutableSet.of(
                        "config",
                        "queue/siftscience.android.default",
                        "uploader",
                        "user_id"
                ),
                preferences.fields.keySet()
        );

        // Load saved Sift instance state.
        Sift sift2 =
                new Sift(mockContext(preferences), mock(ListeningScheduledExecutorService.class));
        assertEquals(sift1.getConfig(), sift2.getConfig());
        assertNull(sift2.getUserId());

        sift2.setConfig(new Sift.Config.Builder()
                .withAccountId("account-id")
                .withBeaconKey("beacon-key")
                .build());
        sift2.setUserId("user-id");
        sift2.createQueue("some-queue", new Queue.Config.Builder()
                .withAcceptSameEventAfter(1)
                .withUploadWhenMoreThan(2)
                .withAcceptSameEventAfter(3)
                .build());
        Queue q2 = sift2.getQueue("some-queue");
        assertNotNull(q2);

        sift2.save();
        assertEquals(
                ImmutableSet.of(
                        "config",
                        "queue/siftscience.android.default",
                        "queue/some-queue",
                        "uploader",
                        "user_id"
                ),
                preferences.fields.keySet()
        );

        // Load saved Sift instance state again.
        Sift sift3 =
                new Sift(mockContext(preferences), mock(ListeningScheduledExecutorService.class));
        assertNotEquals(sift1.getConfig(), sift3.getConfig());
        assertEquals(sift2.getConfig(), sift3.getConfig());
        assertEquals("user-id", sift3.getUserId());
        Queue q3 = sift3.getQueue("some-queue");
        assertNotNull(q3);
        assertEquals(q2.getConfig(), q3.getConfig());
    }

    private Context mockContext(SharedPreferences preferences) {
        Context context = mock(Context.class);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences);
        return context;
    }
}
