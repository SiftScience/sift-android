// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android;

import com.sift.api.representations.AndroidAppStateJson;
import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class QueueTest {

    @After
    public void tearDown() {
        Time.currentTime = 0;
    }

    private static final Queue.UserIdProvider USER_ID_PROVIDER = new Queue.UserIdProvider() {
        @Override
        public String getUserId() {
            return "user-id";
        }
    };

    @Test
    public void testAppend() throws IOException {
        long now = 1001;

        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        MobileEventJson event0 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .withUserId("gary")
                .build();

        MobileEventJson event1 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo1")
                        .withDeviceManufacturer("bar1")
                        .withDeviceModel("baz1")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .withUserId("gary")
                .build();

        MobileEventJson event2 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo2")
                        .withDeviceManufacturer("bar2")
                        .withDeviceModel("baz2")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .withUserId("gary")
                .build();

        List<MobileEventJson> expect = new LinkedList<>(Arrays.asList(event0, event1, event2));

        Queue queue = new Queue(null, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withUploadWhenMoreThan(10).build());

        for (MobileEventJson event : expect) {
            queue.append(event);
        }
        assertFalse(queue.isEventsReadyForUpload(now));
        verifyZeroInteractions(uploadRequester);

        String archive = queue.archive();

        assertEquals(expect, queue.transfer());

        // Test un-archived events
        Queue another = new Queue(archive, USER_ID_PROVIDER, uploadRequester);
        assertEquals(expect, another.transfer());
    }

    @Test
    public void testAcceptSameEventAfter() throws IOException {
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withAcceptSameEventAfter(60000).build());

        MobileEventJson event0, event1;

        Time.currentTime = 1000;
        event0 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(Time.currentTime)
                .withUserId("gary")
                .build();
        queue.append(event0);

        Time.currentTime++;
        event1 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(Time.currentTime)
                .withUserId("gary")
                .build();
        assertTrue(Time.currentTime == event1.time);
        queue.append(event1);

        Time.currentTime++;
        event1 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(Time.currentTime)
                .withUserId("gary")
                .build();
        assertTrue(Time.currentTime == event1.time);
        queue.append(event1);

        verifyZeroInteractions(uploadRequester);

        assertEquals(Collections.singletonList(event0), queue.transfer());

        Time.currentTime = 1000 + 60000;
        event1 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(Time.currentTime)
                .withUserId("gary")
                .build();
        assertTrue(Time.currentTime == event1.time);
        queue.append(event1);

        verifyZeroInteractions(uploadRequester);
        assertEquals(Collections.singletonList(event1), queue.transfer());
    }

    @Test
    public void testUploadWhenMoreThan() throws IOException {
        long now = 1001;

        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withUploadWhenMoreThan(1).build());

        MobileEventJson event = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        assertFalse(queue.isEventsReadyForUpload(now));
        verifyZeroInteractions(uploadRequester);

        queue.append(event);
        assertFalse(queue.isEventsReadyForUpload(now));
        verifyZeroInteractions(uploadRequester);

        queue.append(event);
        assertTrue(queue.isEventsReadyForUpload(now));
        verify(uploadRequester).requestUpload();
    }

    @Test
    public void testUploadWhenOlderThan() throws IOException {
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withUploadWhenOlderThan(1).build());

        Time.currentTime = 1000;

        MobileEventJson event = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        queue.append(event);
        assertFalse(queue.isEventsReadyForUpload(Time.currentTime));
        Time.currentTime += 2;
        assertTrue(queue.isEventsReadyForUpload(Time.currentTime));
    }

    // Ensures that the first event appended will get uploaded
    @Test
    public void testUploadFirstEvent() throws IOException {
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withUploadWhenMoreThan(5)
                .withUploadWhenOlderThan(10000).build());

        MobileEventJson event = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        // Should have uploaded
        queue.append(event);
        verify(uploadRequester).requestUpload();
    }

    // Checks that appending after waiting will request an upload
    @Test
    public void testUploadEventAfterWait() throws IOException, InterruptedException {
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, USER_ID_PROVIDER, uploadRequester);
        // note that this TTL is 1 second
        queue.setConfig(new Queue.Config.Builder().withUploadWhenMoreThan(5)
                .withUploadWhenOlderThan(1000).build());

        MobileEventJson event = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        // Should have uploaded the first event
        queue.append(event);
        verify(uploadRequester).requestUpload();
        reset(uploadRequester);

        // Sleep for 2 seconds (in excess of TTL)
        Thread.sleep(2000);

        // Should have uploaded the second event (sufficiently stale)
        queue.append(event);
        verify(uploadRequester).requestUpload();
    }

    // Checks that appending without waiting will not request an upload
    @Test
    public void testUploadEventWithoutWait() throws IOException {
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withUploadWhenMoreThan(5)
                .withUploadWhenOlderThan(10000).build());

        MobileEventJson event = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        // Should have uploaded the first event
        queue.append(event);
        verify(uploadRequester).requestUpload();
        reset(uploadRequester);

        // Should not have uploaded the second event (not stale enough)
        queue.append(event);
        verify(uploadRequester, never()).requestUpload();
    }

    @Test
    public void testSerializeQueueState() throws IOException, NoSuchFieldException, IllegalAccessException {
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        String queueState = "{\"config\":{\"accept_same_event_after\":1,\"upload_when_more_than\":2," +
                "\"upload_when_older_than\":3},\"queue\":[],\"last_event\":{\"time\":1513206382563," +
                "\"user_id\":\"USER_ID\",\"installation_id\":\"a4c7e6b6cae420e9\"," +
                "\"android_app_state\":{\"activity_class_name\":\"HelloSift\"," +
                "\"sdk_version\":\"0.9.7\",\"battery_level\":0.5,\"battery_state\":2," +
                "\"battery_health\":2,\"plug_state\":1," +
                "\"network_addresses\":[\"10.0.2.15\",\"fe80::5054:ff:fe12:3456\"]}}," +
                "\"last_upload_timestamp\":1513206386326}";

        // First, test that we can construct a State from the archive
        Object q = new Queue(queueState, USER_ID_PROVIDER, uploadRequester)
                .unarchive(queueState);

        Field field = q.getClass().getDeclaredField("config");
        field.setAccessible(true);
        Queue.Config config = (Queue.Config) field.get(q);

        assertEquals(config, new Queue.Config.Builder()
                .withAcceptSameEventAfter(1)
                .withUploadWhenMoreThan(2)
                .withUploadWhenOlderThan(3)
                .build()
        );

        field = q.getClass().getDeclaredField("lastEvent");
        field.setAccessible(true);
        MobileEventJson lastEvent = (MobileEventJson) field.get(q);

        assertEquals(lastEvent, MobileEventJson.newBuilder()
                .withTime(1513206382563L)
                .withUserId("USER_ID")
                .withInstallationId("a4c7e6b6cae420e9")
                .withAndroidAppState(AndroidAppStateJson.newBuilder()
                        .withActivityClassName("HelloSift")
                        .withSdkVersion("0.9.7")
                        .withBatteryLevel(0.5)
                        .withBatteryState(2L)
                        .withBatteryHealth(2L)
                        .withPlugState(1L)
                        .withNetworkAddresses(Arrays.asList("10.0.2.15", "fe80::5054:ff:fe12:3456"))
                        .build()
                )
                .build()
        );

        // Next, test that we can archive back to the expected string
        String archive = new Queue(queueState, USER_ID_PROVIDER, uploadRequester).archive();
        assertEquals(archive, queueState);
    }

    @Test
    public void testUnarchiveLegacyQueueState() throws IOException, NoSuchFieldException, IllegalAccessException {
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        String legacyQueueState = "{\"config\":{\"acceptSameEventAfter\":1,\"uploadWhenMoreThan\":2," +
                "\"uploadWhenOlderThan\":3},\"queue\":[],\"lastEvent\":{\"time\":1513206382563," +
                "\"user_id\":\"USER_ID\",\"installation_id\":\"a4c7e6b6cae420e9\"," +
                "\"android_app_state\":{\"activity_class_name\":\"HelloSift\"," +
                "\"sdk_version\":\"0.9.7\",\"battery_level\":0.5,\"battery_state\":2," +
                "\"battery_health\":2,\"plug_state\":1," +
                "\"network_addresses\":[\"10.0.2.15\",\"fe80::5054:ff:fe12:3456\"]}}," +
                "\"lastUploadTimestamp\":1513206386326}";

        Object o = new Queue(null, USER_ID_PROVIDER, uploadRequester)
                .unarchive(legacyQueueState);

        Field field = o.getClass().getDeclaredField("config");
        field.setAccessible(true);
        Queue.Config config = (Queue.Config) field.get(o);

        assertEquals(config, new Queue.Config.Builder()
                .withAcceptSameEventAfter(1)
                .withUploadWhenMoreThan(2)
                .withUploadWhenOlderThan(3)
                .build()
        );

        field = o.getClass().getDeclaredField("lastEvent");
        field.setAccessible(true);
        MobileEventJson lastEvent = (MobileEventJson) field.get(o);

        assertEquals(lastEvent, MobileEventJson.newBuilder()
                .withTime(1513206382563L)
                .withUserId("USER_ID")
                .withInstallationId("a4c7e6b6cae420e9")
                .withAndroidAppState(AndroidAppStateJson.newBuilder()
                        .withActivityClassName("HelloSift")
                        .withSdkVersion("0.9.7")
                        .withBatteryLevel(0.5)
                        .withBatteryState(2L)
                        .withBatteryHealth(2L)
                        .withPlugState(1L)
                        .withNetworkAddresses(Arrays.asList("10.0.2.15", "fe80::5054:ff:fe12:3456"))
                        .build()
                )
                .build()
        );

        field = o.getClass().getDeclaredField("lastUploadTimestamp");
        field.setAccessible(true);
        long lastUploadTimestamp = (long) field.get(o);

        assertEquals(lastUploadTimestamp, 1513206386326L);

        field = o.getClass().getDeclaredField("queue");
        field.setAccessible(true);
        List queue = (List) field.get(o);

        assertEquals(queue.size(), 0);
    }
}
