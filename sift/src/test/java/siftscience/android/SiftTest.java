// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import com.sift.api.representations.MobileEventJson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SiftTest {
    private ScheduledExecutorService executor;

    @Before
    public void setUp() {
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    private static class MemorySharedPreferences implements SharedPreferences {

        private Map<String, String> fields = new HashMap<>();

        private class Editor implements SharedPreferences.Editor {

            // Make a copy of fields
            private final Map<String, String> newFields = new HashMap<>(fields);

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
            throw new AssertionError();  // Not implemented
        }

        @Override
        public int getInt(String key, int defValue) {
            throw new AssertionError();  // Not implemented
        }

        @Override
        public long getLong(String key, long defValue) {
            throw new AssertionError();  // Not implemented
        }

        @Override
        public float getFloat(String key, float defValue) {
            throw new AssertionError();  // Not implemented
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            throw new AssertionError();  // Not implemented
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
            throw new AssertionError();  // Not implemented
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener onSharedPreferenceChangeListener) {
            throw new AssertionError();  // Not implemented
        }
    }

    @Test
    public void testSift() throws Exception {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift sift = new Sift(
                mockContext(preferences), null, mockTaskManager());

        assertNotNull(sift.getConfig());
        // Verify default values
        assertNull(sift.getConfig().accountId);
        assertNull(sift.getConfig().beaconKey);
        assertNotNull(sift.getConfig().serverUrlFormat);
        assertFalse(sift.getConfig().disallowLocationCollection);

        assertNull(sift.getUserId());

        // There is always a default queue
        assertNotNull(sift.getQueue(Sift.DEVICE_PROPERTIES_QUEUE_IDENTIFIER));

        assertNull(sift.getQueue("some-queue"));
        assertNotNull(sift.createQueue("some-queue", new Queue.Config.Builder().build()));
        try {
            sift.createQueue("some-queue", new Queue.Config.Builder().build());
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Queue exists: \"some-queue\"", e.getMessage());
        }

        assertTrue(preferences.fields.isEmpty());
    }

    @Test
    public void testUnarchiveUnknownProperty() throws IOException {
        String jsonAsString =
                "{\"accountId\":\"foo\"," +
                "\"beaconKey\":\"bar\"," +
                "\"serverUrlFormat\":\"baz\"," +
                "\"unknown\":\"property\"," +
                "\"disallowLocationCollection\":true}";

        Sift.Config c = Sift.GSON.fromJson(jsonAsString, Sift.Config.class);

        // Unknown properties should be dropped
        assertEquals(c, new Sift.Config.Builder()
                .withAccountId("foo")
                .withBeaconKey("bar")
                .withServerUrlFormat("baz")
                .withDisallowLocationCollection(true)
                .build()
        );
    }

    @Test
    public void testSerializeConfig() throws IOException {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.Config c = new Sift.Config.Builder()
                .withAccountId("a")
                .withBeaconKey("b")
                .withServerUrlFormat("s")
                .withDisallowLocationCollection(false)
                .build();

        Sift sift = new Sift(
                mockContext(preferences), c, mockTaskManager());

        String configString = sift.archiveConfig();

        assertEquals(configString,
                "{\"account_id\":\"a\"," +
                        "\"beacon_key\":\"b\"," +
                        "\"server_url_format\":\"s\"," +
                        "\"disallow_location_collection\":false}");

        assertEquals(Sift.GSON.fromJson(configString, Sift.Config.class), c);
    }

    @Test
    public void testUnarchiveLegacyConfig() throws IOException {
        String legacyConfig = "{\"accountId\":\"a\"," +
                "\"beaconKey\":\"b\"," +
                "\"disallowLocationCollection\":false," +
                "\"serverUrlFormat\":\"s\"}";

        Sift.Config c = Sift.GSON.fromJson(legacyConfig, Sift.Config.class);

        assertEquals(c, new Sift.Config.Builder()
                .withAccountId("a")
                .withBeaconKey("b")
                .withServerUrlFormat("s")
                .withDisallowLocationCollection(false)
                .build());
    }

    @Test
    public void testSave() throws Exception {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.open(mockContext(preferences),
                new Sift.Config.Builder().withDisallowLocationCollection(true).build());

        Sift sift1 =
                new Sift(mockContext(preferences), null,
                        mockTaskManager());
        assertTrue(preferences.fields.isEmpty());

        sift1.getQueue(Sift.DEVICE_PROPERTIES_QUEUE_IDENTIFIER)
                .append(MobileEventJson.newBuilder().build());
        sift1.save();
        assertEquals(
                new HashSet<>(Arrays.asList(
                        "config",
                        "queue/siftscience.android.app",
                        "queue/siftscience.android.device",
                        "user_id"
                )),
                preferences.fields.keySet()
        );

        // Load saved Sift instance state
        Sift sift2 =
                new Sift(mockContext(preferences), null,
                        mockTaskManager());
        assertEquals(sift1.getConfig(), sift2.getConfig());
        assertNull(sift2.getUserId());

        sift2.setConfig(new Sift.Config.Builder()
                .withAccountId("account-id")
                .withBeaconKey("beacon-key")
                .build());
        sift2.setUserId("user-id");
        Queue q2 = sift2.getQueue(Sift.DEVICE_PROPERTIES_QUEUE_IDENTIFIER);
        assertNotNull(q2);

        sift2.save();
        assertEquals(
                new HashSet<>(Arrays.asList(
                        "config",
                        "queue/siftscience.android.app",
                        "queue/siftscience.android.device",
                        "user_id"
                )),
                preferences.fields.keySet()
        );

        // Load saved Sift instance state again
        Sift sift3 =
                new Sift(mockContext(preferences), null,
                        mockTaskManager());
        assertNotEquals(sift1.getConfig(), sift3.getConfig());
        assertEquals(sift2.getConfig(), sift3.getConfig());
        assertEquals("user-id", sift3.getUserId());
        Queue q3 = sift3.getQueue(Sift.DEVICE_PROPERTIES_QUEUE_IDENTIFIER);
        assertNotNull(q3);
        assertEquals(q2.getConfig(), q3.getConfig());
    }

    @Test
    public void testUnsetUserId() throws Exception {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift sift = new Sift(mockContext(preferences), null,
                mockTaskManager());

        sift.setUserId("gary");

        sift.getQueue(Sift.APP_STATE_QUEUE_IDENTIFIER).append(MobileEventJson.newBuilder().build());
        // Append twice because the first one gets uploaded and flushed
        sift.getQueue(Sift.APP_STATE_QUEUE_IDENTIFIER).append(MobileEventJson.newBuilder().build());
        MobileEventJson event = sift.getQueue(Sift.APP_STATE_QUEUE_IDENTIFIER).flush().get(0);
        assertEquals(event.userId, "gary");

        sift.unsetUserId();

        sift.getQueue(Sift.APP_STATE_QUEUE_IDENTIFIER).append(MobileEventJson.newBuilder().build());
        event = sift.getQueue(Sift.APP_STATE_QUEUE_IDENTIFIER).flush().get(0);
        assertNull(event.userId);
    }

    private Context mockContext(SharedPreferences preferences) {
        Context ctx = mock(Context.class);
        when(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences);
        when(ctx.getApplicationContext()).thenReturn(ctx);
        return ctx;
    }

    private TaskManager mockTaskManager() {
        TaskManager tm = mock(TaskManager.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ((Runnable) args[0]).run();
                return null;
            }
        }).when(tm).submit(any(Runnable.class));

        return tm;
    }
}
