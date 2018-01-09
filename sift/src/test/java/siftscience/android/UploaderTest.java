// Copyright (c) 2017 Sift Science. All rights reserved.

package siftscience.android;

import com.sift.api.representations.AndroidAppStateJson;
import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.OngoingStubbing;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
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

import static org.junit.Assert.assertEquals;
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

    @Test
    public void testSerializeBatches() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        String batches = "{\"batches\":[[" +
                "{\"time\":1513206382563,\"user_id\":\"USER_ID\",\"installation_id\":\"a4c7e6b6cae420e9\"," +
                "\"android_app_state\":{\"activity_class_name\":\"HelloSift\",\"sdk_version\":\"0.9.7\"," +
                "\"battery_level\":0.5,\"battery_state\":2,\"battery_health\":2,\"plug_state\":1," +
                "\"network_addresses\":[\"10.0.2.15\",\"fe80::5054:ff:fe12:3456\"]}}],[]]}";

        // Test that we can archive back to the expected string
        String batchesArchive = new Uploader(batches, 0L, executor, CONFIG_PROVIDER, client).archive();
        assertEquals(batches, batchesArchive);
    }

    @Test
    public void testUnarchiveLegacyBatches() throws IOException, NoSuchFieldException,
            IllegalAccessException, InterruptedException {
        OkHttpClient client = mock(OkHttpClient.class);
        String legacyBatches = "{\"batches\":[[" +
                "{\"time\":1513206382563,\"user_id\":\"USER_ID\",\"installation_id\":\"a4c7e6b6cae420e9\"," +
                "\"android_app_state\":{\"activity_class_name\":\"HelloSift\",\"sdk_version\":\"0.9.7\"," +
                "\"battery_level\":0.5,\"battery_state\":2,\"battery_health\":2,\"plug_state\":1," +
                "\"network_addresses\":[\"10.0.2.15\",\"fe80::5054:ff:fe12:3456\"]}}],[]]}";

        Uploader uploader = new Uploader(legacyBatches, 0L, executor, CONFIG_PROVIDER, client);

        executor.shutdown();
        executor.awaitTermination(3, TimeUnit.SECONDS);

        Field field = uploader.getClass().getDeclaredField("batches");
        field.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        Object batches = field.get(uploader);
        field = batches.getClass().getDeclaredField("batches");
        field.setAccessible(true);
        List<List<MobileEventJson>> batchesList = (List<List<MobileEventJson>>) field.get(batches);

        assertEquals(batchesList.get(0).get(0), MobileEventJson.newBuilder()
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
                .build());

        assertEquals(batchesList.get(1), Collections.emptyList());
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
