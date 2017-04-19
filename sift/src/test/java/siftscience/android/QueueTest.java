// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
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

        ListeningScheduledExecutorService executor = mock(ListeningScheduledExecutorService.class);
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

        List<MobileEventJson> expect = ImmutableList.of(event0, event1, event2);

        Queue queue = new Queue(null, executor, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withUploadWhenMoreThan(10).build());

        for (MobileEventJson event : expect) {
            queue.append(event);
        }
        assertFalse(queue.isEventsReadyForUpload(now));
        verifyZeroInteractions(executor);
        verifyZeroInteractions(uploadRequester);

        String archive = queue.archive();

        assertEquals(expect, queue.transfer());

        // Test un-archived events.
        Queue another = new Queue(archive, executor, USER_ID_PROVIDER, uploadRequester);
        assertEquals(expect, another.transfer());
    }

    @Test
    public void testAcceptSameEventAfter() throws IOException {
        ListeningScheduledExecutorService executor = mock(ListeningScheduledExecutorService.class);
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, executor, USER_ID_PROVIDER, uploadRequester);
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

        verifyZeroInteractions(executor);
        verifyZeroInteractions(uploadRequester);

        assertEquals(ImmutableList.of(event0), queue.transfer());

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

        verifyZeroInteractions(executor);
        verifyZeroInteractions(uploadRequester);
        assertEquals(ImmutableList.of(event1), queue.transfer());
    }

    @Test
    public void testUploadWhenMoreThan() throws IOException {
        long now = 1001;

        ListeningScheduledExecutorService executor = mock(ListeningScheduledExecutorService.class);
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, executor, USER_ID_PROVIDER, uploadRequester);
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
        verifyZeroInteractions(executor);
        verifyZeroInteractions(uploadRequester);

        queue.append(event);
        assertFalse(queue.isEventsReadyForUpload(now));
        verifyZeroInteractions(executor);
        verifyZeroInteractions(uploadRequester);

        queue.append(event);
        assertTrue(queue.isEventsReadyForUpload(now));
        verify(uploadRequester).requestUpload();
    }

    @Test
    public void testUploadWhenOlderThan() throws IOException {
        ListeningScheduledExecutorService executor = mock(ListeningScheduledExecutorService.class);
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, executor, USER_ID_PROVIDER, uploadRequester);
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
}
