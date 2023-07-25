package siftscience.android;

import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.util.Base64;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MultipleUploaderTest {

    private List<AccountKey> accountKeys;

    private Uploader.ConfigProvider configProvider;
    private String requestPath;
    private TaskManager taskManager;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(WireMockConfiguration.wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort());

    private final MobileEventJson TEST_EVENT = new MobileEventJson()
            .withAndroidDeviceProperties(new AndroidDevicePropertiesJson()
                    .withAndroidId("foo")
                    .withDeviceManufacturer("bar")
                    .withDeviceModel("baz")
            )
            .withTime(System.currentTimeMillis())
            .withUserId("gary");

    @Before
    public void setUp() {
        String urlFormat = String.format("http://localhost:%d/v3/accounts/%%s/mobile_events",
                wireMockRule.port());

        requestPath = "/v3/accounts/%s/mobile_events";

        accountKeys = new ArrayList<AccountKey>();
        accountKeys.add(new AccountKey("ACCOUNT_ID_1", "BEACON_KEY_1"));
        accountKeys.add(new AccountKey("ACCOUNT_ID_2", "BEACON_KEY_2"));
        final Sift.Config config = new Sift.Config.Builder()
                .withAccountKeys(accountKeys)
                .withServerUrlFormat(urlFormat)
                .build();

        configProvider = new Uploader.ConfigProvider() {
            @Override
            public Sift.Config getConfig() {
                return config;
            }
        };

        taskManager = mockTaskManager();
    }

    @Test
    public void testUploadNothing() {
        Uploader bu = new Uploader(taskManager, configProvider);
        bu.upload(Collections.<MobileEventJson>emptyList());
        assertThat(WireMock.findUnmatchedRequests(), Matchers.empty());
    }

    @Test
    public void testUpload200() throws Exception {
        for (int i = 0; i < accountKeys.size(); i++) {
            WireMock.stubFor(makeCall(200, accountKeys.get(i)));
        }

        Uploader bu = new Uploader(taskManager, configProvider);
        bu.upload(Collections.singletonList(TEST_EVENT));

        for (int i = 0; i < accountKeys.size(); i++) {
            WireMock.verify(1, WireMock.putRequestedFor(WireMock.urlEqualTo(String.format(requestPath, accountKeys.get(i).accountId))));
        }
        assertThat(WireMock.findUnmatchedRequests(), Matchers.empty());
    }

    @Test
    public void testUpload400() throws Exception {
        for (int i = 0; i < accountKeys.size(); i++) {
            WireMock.stubFor(makeCall(400, accountKeys.get(i)));
        }

        Uploader bu = new Uploader(taskManager, configProvider);
        bu.upload(Collections.singletonList(TEST_EVENT));

        for (int i = 0; i < accountKeys.size(); i++) {
            WireMock.verify(1, WireMock.putRequestedFor(WireMock.urlEqualTo(String.format(requestPath, accountKeys.get(i).accountId))));
        }
        assertThat(WireMock.findUnmatchedRequests(), Matchers.empty());
    }

    @Test
    public void testUploadOtherErrorExhaustRetries() throws Exception {
        for (int i = 0; i < accountKeys.size(); i++) {
            WireMock.stubFor(makeCall(429, accountKeys.get(i)));
        }

        Uploader bu = new Uploader(taskManager, configProvider);
        bu.upload(Collections.singletonList(TEST_EVENT));

        for (int i = 0; i < accountKeys.size(); i++) {
            // ((3 - 3) ^ 2) * 3 = 0
            verify(taskManager, times(2)).schedule(any(Runnable.class), Mockito.eq(0l),
                    Mockito.eq(TimeUnit.SECONDS));

            // ((3 - 2) ^ 2) * 3 = 3
            verify(taskManager, times(2)).schedule(any(Runnable.class), Mockito.eq(3l),
                    Mockito.eq(TimeUnit.SECONDS));

            // ((3 - 1) ^ 2) * 3 = 12
            verify(taskManager, times(2)).schedule(any(Runnable.class), Mockito.eq(12l),
                    Mockito.eq(TimeUnit.SECONDS));

            WireMock.verify(3, WireMock.putRequestedFor(WireMock.urlEqualTo(String.format(requestPath, accountKeys.get(i).accountId))));
        }
        assertThat(WireMock.findUnmatchedRequests(), Matchers.empty());
    }

    @Test
    public void testUploadOtherEventualSuccess() throws Exception {
        for (int i = 0; i < accountKeys.size(); i++) {
            WireMock.stubFor(makeCall(429, accountKeys.get(i))
                    .inScenario(accountKeys.get(i).accountId)
                    .whenScenarioStateIs(Scenario.STARTED)
                    .willSetStateTo("failure"));
        }

        for (int i = 0; i < accountKeys.size(); i++) {
            WireMock.stubFor(makeCall(200, accountKeys.get(i))
                    .inScenario(accountKeys.get(i).accountId)
                    .whenScenarioStateIs("failure")
                    .willSetStateTo("success")
            );
        }

        Uploader bu = new Uploader(taskManager, configProvider);
        bu.upload(Collections.singletonList(TEST_EVENT));

        for (int i = 0; i < accountKeys.size(); i++) {
            WireMock.verify(2, WireMock.putRequestedFor(WireMock.urlEqualTo(String.format(requestPath, accountKeys.get(i).accountId))));
        }
        assertThat(WireMock.findUnmatchedRequests(), Matchers.empty());
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

        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ((Runnable) args[0]).run();
                return null;
            }
        }).when(tm).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        return tm;
    }

    private MappingBuilder makeCall(int code, AccountKey accountKey) throws Exception {
        String encodedBeaconKey = Base64.encodeToString(
                accountKey.beaconKey.getBytes("ASCII"), Base64.NO_WRAP);

        return WireMock.put(WireMock.urlEqualTo(String.format(requestPath, accountKey.accountId)))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withHeader("Authorization", WireMock.equalTo("Basic " + encodedBeaconKey))
                .withHeader("Content-Encoding", WireMock.equalTo("gzip"))
                .withHeader("Content-Type", WireMock.equalTo("application/json"))
                .willReturn(makeResponse(code));
    }

    private ResponseDefinitionBuilder makeResponse(int code) {
        return WireMock.aResponse().withStatus(code);
    }
}
