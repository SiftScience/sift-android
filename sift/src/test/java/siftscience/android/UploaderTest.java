// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android;

import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.OngoingStubbing;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

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

    @Test
    public void testUploadNothing() throws Exception {
        OkHttpClient client = mock(OkHttpClient.class);
        Uploader uploader = new Uploader(null, 0L, executor, CONFIG_PROVIDER, client);
        uploader.upload(new LinkedList<MobileEventJson>());
        verifyZeroInteractions(client);
    }

    @Test
    public void testUpload() throws Exception {
        assertTrue(Uploader.REJECTION_LIMIT > 2);
        // Don't nest mocking calls; mockito can't handle that
        Call[] calls = new Call[] {makeCall(429), makeCall(429), makeCall(200)};

        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any(Request.class)))
                .thenReturn(calls[0])
                .thenReturn(calls[1])
                .thenReturn(calls[2]);

        Uploader uploader = new Uploader(null, 0L, executor, CONFIG_PROVIDER, client);
        uploader.onRequestCompletion = new Semaphore(0);
        uploader.onRequestRejection = new Semaphore(0);


        MobileEventJson event = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo")
                        .withDeviceManufacturer("bar")
                        .withDeviceModel("baz")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        uploader.upload(new LinkedList<>(Arrays.asList(event)));

        assertTrue(uploader.onRequestCompletion.tryAcquire(1, TimeUnit.SECONDS));
        assertTrue(uploader.onRequestCompletion.availablePermits() == 0);

        assertTrue(uploader.onRequestRejection.availablePermits() == 0);

        verify(client, times(calls.length)).newCall(any(Request.class));
    }

    @Test
    public void testUploadTooManyRejections() throws Exception {
        assertTrue(Uploader.REJECTION_LIMIT == 3);
        // Don't nest mocking calls; mockito can't handle that
        Call[] calls = new Call[] {
                makeCall(429),
                makeCall(429),
                makeCall(429),
                makeCall(429),
                makeCall(429),

        };

        OkHttpClient client = mock(OkHttpClient.class);
        OngoingStubbing<Call> stub = when(client.newCall(any(Request.class)));
        for (Call call : calls) {
            stub = stub.thenReturn(call);
        }

        Uploader uploader = new Uploader(null, 0L, executor, CONFIG_PROVIDER, client);
        uploader.onRequestCompletion = new Semaphore(0);
        uploader.onRequestRejection = new Semaphore(0);

        MobileEventJson event = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo")
                        .withDeviceManufacturer("bar")
                        .withDeviceModel("baz")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        uploader.upload(new LinkedList<>(Arrays.asList(event)));

        assertTrue(uploader.onRequestCompletion.availablePermits() == 0);
        assertTrue(uploader.onRequestRejection.tryAcquire(1, TimeUnit.SECONDS));
        assertTrue(uploader.onRequestRejection.availablePermits() == 0);

        // The last two calls are in excess of the rejection limit
        verify(client, times(Uploader.REJECTION_LIMIT)).newCall(any(Request.class));
    }

    @Test
    public void testUploadManyBatches() throws Exception {
        // Create mocks for 3 batches
        // Don't nest mocking calls; mockito can't handle that
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

        MobileEventJson event0 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo0")
                        .withDeviceManufacturer("bar0")
                        .withDeviceModel("baz0")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        MobileEventJson event1 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo1")
                        .withDeviceManufacturer("bar1")
                        .withDeviceModel("baz1")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        MobileEventJson event2 = MobileEventJson.newBuilder()
                .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                        .withAndroidId("foo2")
                        .withDeviceManufacturer("bar2")
                        .withDeviceModel("baz2")
                        .build()
                )
                .withTime(System.currentTimeMillis())
                .build();

        // Upload 3 batches
        uploader.upload(new LinkedList<>(Arrays.asList(event0)));
        uploader.upload(new LinkedList<>(Arrays.asList(event1)));
        uploader.upload(new LinkedList<>(Arrays.asList(event2)));

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
                .body(ResponseBody.create(null, ""))
                .build();
    }
}
