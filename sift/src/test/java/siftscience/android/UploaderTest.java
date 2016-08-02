// Copyright (c) 2016 Sift Science. All rights reserved.

package siftscience.android;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.OngoingStubbing;

import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class UploaderTest {

    private static final Sift.Config CONFIG = new Sift.Config.Builder()
            .withAccountId("account-id")
            .withBeaconKey("beacon-key")
            .build();

    private static final Uploader.ConfigProvider CONFIG_PROVIDER = new Uploader.ConfigProvider() {
        @Override
        public Sift.Config getConfig() {
            return CONFIG;
        }
    };

    private ListeningScheduledExecutorService executor;

    @Before
    public void setUp() {
        executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadScheduledExecutor());
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void testUploadNothing() throws Exception {
        OkHttpClient client = mock(OkHttpClient.class);
        Uploader uploader = new Uploader(null, 0L, executor, CONFIG_PROVIDER, client);
        uploader.upload(ImmutableList.<Event>of());
        verifyZeroInteractions(client);
    }

    @Test
    public void testUpload() throws Exception {
        assertTrue(Uploader.REJECTION_LIMIT > 2);
        // Don't nest mocking calls; mockito can't handle that.
        Call[] calls = new Call[] {makeCall(400), makeCall(400), makeCall(200)};

        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any(Request.class)))
                .thenReturn(calls[0])
                .thenReturn(calls[1])
                .thenReturn(calls[2]);

        Uploader uploader = new Uploader(null, 0L, executor, CONFIG_PROVIDER, client);
        uploader.onRequestCompletion = new Semaphore(0);
        uploader.onRequestRejection = new Semaphore(0);

        uploader.upload(ImmutableList.of(new Event("type", "path-1", null)));

        assertTrue(uploader.onRequestCompletion.tryAcquire(1, TimeUnit.SECONDS));
        assertTrue(uploader.onRequestCompletion.availablePermits() == 0);

        assertTrue(uploader.onRequestRejection.availablePermits() == 0);

        verify(client, times(calls.length)).newCall(any(Request.class));
    }

    @Test
    public void testUploadTooManyRejections() throws Exception {
        assertTrue(Uploader.REJECTION_LIMIT == 3);
        // Don't nest mocking calls; mockito can't handle that.
        Call[] calls = new Call[] {
                makeCall(400),
                makeCall(404),  // 404s are not counted for rejections.
                makeCall(400),
                makeCall(404),
                makeCall(400),  // The third rejection.
                makeCall(404),
                makeCall(400),
                makeCall(404),
        };

        OkHttpClient client = mock(OkHttpClient.class);
        OngoingStubbing<Call> stub = when(client.newCall(any(Request.class)));
        for (Call call : calls) {
            stub = stub.thenReturn(call);
        }

        Uploader uploader = new Uploader(null, 0L, executor, CONFIG_PROVIDER, client);
        uploader.onRequestCompletion = new Semaphore(0);
        uploader.onRequestRejection = new Semaphore(0);

        uploader.upload(ImmutableList.of(new Event("type", "path-1", null)));

        assertTrue(uploader.onRequestCompletion.availablePermits() == 0);

        assertTrue(uploader.onRequestRejection.tryAcquire(1, TimeUnit.SECONDS));
        assertTrue(uploader.onRequestRejection.availablePermits() == 0);

        verify(client, times(5)).newCall(any(Request.class));
    }

    @Test
    public void testUploadManyBatches() throws Exception {
        // Create mocks for 3 batches.
        // Don't nest mocking calls; mockito can't handle that.
        Call[] calls = new Call[] {
                makeCall(200),
                makeCall(200),
                makeCall(200),
        };

        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any(Request.class)))
                .thenReturn(calls[0])
                .thenReturn(calls[1]);

        Uploader uploader = new Uploader(null, 0L, executor, CONFIG_PROVIDER, client);
        uploader.onRequestCompletion = new Semaphore(0);
        uploader.onRequestRejection = new Semaphore(0);

        // Upload 3 batches.
        uploader.upload(ImmutableList.of(new Event("type", "path-1", null)));
        uploader.upload(ImmutableList.of(new Event("type", "path-2", null)));
        uploader.upload(ImmutableList.of(new Event("type", "path-3", null)));

        assertTrue(uploader.onRequestCompletion.tryAcquire(1, TimeUnit.SECONDS));
        assertTrue(uploader.onRequestCompletion.tryAcquire(1, TimeUnit.SECONDS));
        assertTrue(uploader.onRequestCompletion.tryAcquire(1, TimeUnit.SECONDS));
        assertTrue(uploader.onRequestCompletion.availablePermits() == 0);

        assertTrue(uploader.onRequestRejection.availablePermits() == 0);

        verify(client, times(3)).newCall(any(Request.class));
    }

    private Call makeCall(int code) throws Exception {
        Call call = mock(Call.class);
        doReturn(makeResponse(code)).when(call).execute();
        return call;
    }

    private Response makeResponse(int code) {
        return new Response.Builder()
                .request(new Request.Builder().url("http://siftscience.com/").build())
                .protocol(Protocol.HTTP_1_0)
                .code(code)
                .build();
    }
}
