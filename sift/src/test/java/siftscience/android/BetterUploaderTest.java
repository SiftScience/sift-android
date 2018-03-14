package siftscience.android;

import com.sift.api.representations.AndroidDevicePropertiesJson;
import com.sift.api.representations.MobileEventJson;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Created by gary on 3/8/18.
 */

public class BetterUploaderTest {
    private static final BetterUploader.ConfigProvider CONFIG_PROVIDER =
            new BetterUploader.ConfigProvider() {
        @Override
        public Sift.Config getConfig() {
            return new Sift.Config.Builder()
                    .withAccountId("foo")
                    .withBeaconKey("bar")
                    .build();
        }
    };

    private static final MobileEventJson TEST_EVENT = MobileEventJson.newBuilder()
            .withAndroidDeviceProperties(AndroidDevicePropertiesJson.newBuilder()
                    .withAndroidId("foo")
                    .withDeviceManufacturer("bar")
                    .withDeviceModel("baz")
                    .build()
            )
            .withTime(System.currentTimeMillis())
            .withUserId("gary")
            .build();

    @Test
    public void testUpload200() {
        BetterUploader bu = new BetterUploader(mockTaskManager(), CONFIG_PROVIDER);
        bu.upload(Collections.singletonList(TEST_EVENT));

    }

    @Test
    public void testUpload400() {

    }

    @Test
    public void testUploadOtherErrorExhaustRetries() {

    }

    @Test
    public void testUploadOtherEventualSuccess() {

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
