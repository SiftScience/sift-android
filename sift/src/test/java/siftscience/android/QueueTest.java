// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import org.junit.After;
import org.junit.Test;

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
    public void testAppend() {
        long now = 1001;

        ListeningScheduledExecutorService executor = mock(ListeningScheduledExecutorService.class);
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        List<Event> expect = ImmutableList.of(
                new Event("type", "path-1", null),
                new Event("type", "path-2", null),
                new Event("type", "path-3", null)
        );

        Queue queue = new Queue(null, executor, USER_ID_PROVIDER, uploadRequester);

        for (Event event : expect) {
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
    public void testAcceptSameEventAfter() {
        ListeningScheduledExecutorService executor = mock(ListeningScheduledExecutorService.class);
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, executor, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withAcceptSameEventAfter(60000).build());

        Event event0, event;

        Time.currentTime = 1000;
        event0 = new Event("type", "path-1", null);
        queue.append(event0);

        Time.currentTime++;
        event = new Event("type", "path-1", null);
        assertEquals(Time.currentTime, event.time);
        queue.append(event);

        Time.currentTime++;
        event = new Event("type", "path-1", null);
        assertEquals(Time.currentTime, event.time);
        queue.append(event);

        verifyZeroInteractions(executor);
        verifyZeroInteractions(uploadRequester);
        assertEquals(ImmutableList.of(event0), queue.transfer());

        Time.currentTime = 1000 + 60000;
        event = new Event("type", "path-1", null);
        assertEquals(Time.currentTime, event.time);
        queue.append(event);

        verifyZeroInteractions(executor);
        verifyZeroInteractions(uploadRequester);
        assertEquals(ImmutableList.of(event), queue.transfer());
    }

    @Test
    public void testUploadWhenMoreThan() {
        long now = 1001;

        ListeningScheduledExecutorService executor = mock(ListeningScheduledExecutorService.class);
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, executor, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withUploadWhenMoreThan(1).build());

        Event event = new Event("type", "path-1", null);

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
    public void testUploadWhenOlderThan() {
        ListeningScheduledExecutorService executor = mock(ListeningScheduledExecutorService.class);
        Queue.UploadRequester uploadRequester = mock(Queue.UploadRequester.class);

        Queue queue = new Queue(null, executor, USER_ID_PROVIDER, uploadRequester);
        queue.setConfig(new Queue.Config.Builder().withUploadWhenOlderThan(1).build());

        Time.currentTime = 1000;
        Event event = new Event("type", "path-1", null);
        queue.append(event);
        assertFalse(queue.isEventsReadyForUpload(Time.currentTime));
        Time.currentTime += 2;
        assertTrue(queue.isEventsReadyForUpload(Time.currentTime));
    }
}
