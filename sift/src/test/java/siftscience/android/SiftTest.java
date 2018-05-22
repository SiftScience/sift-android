// Copyright (c) 2018 Sift Science. All rights reserved.

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
import java.lang.reflect.Field;
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

        SiftImpl sift = new SiftImpl(
                mockContext(preferences), null, null, false, null, false, mockTaskManager());

        assertNotNull(sift.getConfig());
        // Verify default values
        assertNull(sift.getConfig().accountId);
        assertNull(sift.getConfig().beaconKey);
        assertNotNull(sift.getConfig().serverUrlFormat);
        assertFalse(sift.getConfig().disallowLocationCollection);

        assertNull(sift.getUserId());

        // There is always a default queue
        assertNotNull(sift.getQueue(SiftImpl.DEVICE_PROPERTIES_QUEUE_IDENTIFIER));

        assertNull(sift.getQueue("some-queue"));
        assertNotNull(sift.createQueue("some-queue", new Queue.Config.Builder().build()));
        try {
            sift.createQueue("some-queue", new Queue.Config.Builder().build());
            fail();
        } catch (IllegalStateException e) {
            assertEquals("Queue exists: some-queue", e.getMessage());
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

        SiftImpl sift = new SiftImpl(
                mockContext(preferences), c, null, false, null, false, mockTaskManager());

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

        SiftImpl sift1 =
                new SiftImpl(mockContext(preferences), null, null, false,
                        null, false, mockTaskManager());
        assertTrue(preferences.fields.isEmpty());

        sift1.getQueue(SiftImpl.DEVICE_PROPERTIES_QUEUE_IDENTIFIER)
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
        SiftImpl sift2 =
                new SiftImpl(mockContext(preferences), null, null, false,
                        null, false, mockTaskManager());
        assertEquals(sift1.getConfig(), sift2.getConfig());
        assertNull(sift2.getUserId());

        sift2.setConfig(new Sift.Config.Builder()
                .withAccountId("account-id")
                .withBeaconKey("beacon-key")
                .build());
        sift2.setUserId("user-id");
        Queue q2 = sift2.getQueue(SiftImpl.DEVICE_PROPERTIES_QUEUE_IDENTIFIER);
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
        SiftImpl sift3 =
                new SiftImpl(mockContext(preferences), null, null, false,
                       null, false, mockTaskManager());
        assertNotEquals(sift1.getConfig(), sift3.getConfig());
        assertEquals(sift2.getConfig(), sift3.getConfig());
        assertEquals("user-id", sift3.getUserId());
        Queue q3 = sift3.getQueue(SiftImpl.DEVICE_PROPERTIES_QUEUE_IDENTIFIER);
        assertNotNull(q3);
        assertEquals(q2.getConfig(), q3.getConfig());
    }

    @Test
    public void testUnsetUserId() throws Exception {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        SiftImpl sift = new SiftImpl(mockContext(preferences), null, null, false,
                null, false, mockTaskManager());

        sift.setUserId("gary");

        sift.getQueue(SiftImpl.APP_STATE_QUEUE_IDENTIFIER).append(MobileEventJson.newBuilder().build());
        // Append twice because the first one gets uploaded and flushed
        sift.getQueue(SiftImpl.APP_STATE_QUEUE_IDENTIFIER).append(MobileEventJson.newBuilder().build());
        MobileEventJson event = sift.getQueue(SiftImpl.APP_STATE_QUEUE_IDENTIFIER).flush().get(0);
        assertEquals("gary", event.userId);

        sift.unsetUserId();

        sift.getQueue(SiftImpl.APP_STATE_QUEUE_IDENTIFIER).append(MobileEventJson.newBuilder().build());
        event = sift.getQueue(SiftImpl.APP_STATE_QUEUE_IDENTIFIER).flush().get(0);
        assertNull(event.userId);
    }

    @Test
    public void testLifecycle() {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.open(mockContext(preferences),
                new Sift.Config.Builder().withDisallowLocationCollection(true).build());
        Sift.pause();
        Sift.close();
        Sift.resume(mockContext(preferences));
        Sift.setUserId("gary");
    }

    @Test
    public void testUnboundSetUserId() throws NoSuchFieldException, IllegalAccessException,
            InterruptedException {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.setUserId("gary");
        Sift.open(mockContext(preferences),
                new Sift.Config.Builder().withDisallowLocationCollection(true).build());

        Thread.sleep(100);

        Field field = Sift.class.getDeclaredField("instance");
        field.setAccessible(true);
        SiftImpl sift = (SiftImpl) field.get(Sift.class);

        assertEquals("gary", sift.getUserId());
    }

    @Test
    public void testUnboundUnsetUserId() throws NoSuchFieldException, IllegalAccessException,
            InterruptedException {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        MemorySharedPreferences.Editor editor = preferences.edit();
        editor.putString("user_id", "gary");
        editor.apply();

        Sift.unsetUserId();
        Sift.open(mockContext(preferences),
                new Sift.Config.Builder().withDisallowLocationCollection(true).build());

        Thread.sleep(100);

        Field field = Sift.class.getDeclaredField("instance");
        field.setAccessible(true);
        SiftImpl sift = (SiftImpl) field.get(Sift.class);

        assertEquals(null, sift.getUserId());
    }

    @Test
    public void testOpenOverUnboundConfig() throws NoSuchFieldException, IllegalAccessException,
            InterruptedException {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.Config c1 = new Sift.Config.Builder().withAccountId("foo").build();
        Sift.Config c2 = new Sift.Config.Builder().withAccountId("bar").build();

        MemorySharedPreferences.Editor editor = preferences.edit();
        editor.putString("config", Sift.GSON.toJson(c1));
        editor.apply();

        Sift.setConfig(c1);

        Sift.open(mockContext(preferences), c2);

        Thread.sleep(100);

        Field field = Sift.class.getDeclaredField("instance");
        field.setAccessible(true);
        SiftImpl sift = (SiftImpl) field.get(Sift.class);

        // If there's an opened user id, use opened over archived and unbound
        assertEquals(c2.accountId, sift.getConfig().accountId);
    }

    @Test
    public void testUnboundOverArchivedConfig() throws NoSuchFieldException, IllegalAccessException,
            InterruptedException {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.Config c1 = new Sift.Config.Builder().withAccountId("foo").build();
        Sift.Config c2 = new Sift.Config.Builder().withAccountId("bar").build();

        MemorySharedPreferences.Editor editor = preferences.edit();
        editor.putString("config", Sift.GSON.toJson(c1));
        editor.apply();

        Sift.setConfig(c2);

        Sift.open(mockContext(preferences));

        Thread.sleep(100);

        Field field = Sift.class.getDeclaredField("instance");
        field.setAccessible(true);
        SiftImpl sift = (SiftImpl) field.get(Sift.class);

        // If there's an unbound user id and no opened, use unbound over archived
        assertEquals(c2.accountId, sift.getConfig().accountId);
    }

    @Test
    public void testOpenOverArchivedConfig() throws NoSuchFieldException, IllegalAccessException,
            InterruptedException {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.Config c1 = new Sift.Config.Builder().withAccountId("foo").build();
        Sift.Config c2 = new Sift.Config.Builder().withAccountId("bar").build();

        MemorySharedPreferences.Editor editor = preferences.edit();
        editor.putString("config", Sift.GSON.toJson(c1));
        editor.apply();

        Sift.open(mockContext(preferences), c2);

        Thread.sleep(100);

        Field field = Sift.class.getDeclaredField("instance");
        field.setAccessible(true);
        SiftImpl sift = (SiftImpl) field.get(Sift.class);

        // If there's an opened user id and no unbound, use opened over archived
        assertEquals(c2.accountId, sift.getConfig().accountId);
    }

    @Test
    public void testArchivedConfigFallback() throws NoSuchFieldException, IllegalAccessException,
            InterruptedException {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.Config c1 = new Sift.Config.Builder().withAccountId("bar").build();

        MemorySharedPreferences.Editor editor = preferences.edit();
        editor.putString("config", Sift.GSON.toJson(c1));
        editor.apply();

        Sift.open(mockContext(preferences));

        Thread.sleep(100);

        Field field = Sift.class.getDeclaredField("instance");
        field.setAccessible(true);
        SiftImpl sift = (SiftImpl) field.get(Sift.class);

        // If there's no opened or unbound user id, use archived
        assertEquals(c1.accountId, sift.getConfig().accountId);
    }

    @Test
    public void testChangeConfig() throws NoSuchFieldException, IllegalAccessException,
            InterruptedException {
        MemorySharedPreferences preferences = new MemorySharedPreferences();

        Sift.Config c1 = new Sift.Config.Builder().withAccountId("prod").build();
        Sift.Config c2 = new Sift.Config.Builder().withAccountId("sandbox").build();

        MemorySharedPreferences.Editor editor = preferences.edit();
        editor.putString("config", Sift.GSON.toJson(c2));
        editor.apply();

        Sift.open(mockContext(preferences), c1);

        Thread.sleep(100);

        Field field = Sift.class.getDeclaredField("instance");
        field.setAccessible(true);
        SiftImpl sift = (SiftImpl) field.get(Sift.class);

        assertEquals(c1.accountId, sift.getConfig().accountId);

        Sift.setConfig(c2);

        Thread.sleep(100);

        assertEquals(c2.accountId, sift.getConfig().accountId);
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
