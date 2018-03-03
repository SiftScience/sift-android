package siftscience.android;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Base64;
import android.util.Log;

import com.sift.api.representations.ListRequestJson;
import com.sift.api.representations.MobileEventJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by gary on 3/2/18.
 */
public class BetterUploader {
    private static final String TAG = BetterUploader.class.getName();

    @VisibleForTesting
    static final int REJECTION_LIMIT = 3;
    private static final long BACKOFF_MULTIPLIER = TimeUnit.SECONDS.toMillis(2);
    private static final long BACKOFF_EXPONENT = 2;
    private static final TimeUnit BACKOFF_UNIT = TimeUnit.MILLISECONDS;
    private TaskManager taskManager;
    private ConfigProvider configProvider;

    private static final MediaType JSON = MediaType.parse("application/json");

    // StandardCharsets.US_ASCII is defined in API level 19 and we are
    // targeting API level 16.
    private static final Charset US_ASCII = Charset.forName("US-ASCII");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private OkHttpClient client;

    interface ConfigProvider {
        Sift.Config getConfig();
    }

    BetterUploader(TaskManager taskManager, ConfigProvider configProvider) {
        this.taskManager = taskManager;
        this.configProvider = configProvider;
        this.client = new OkHttpClient();
    }

    public void upload(List<MobileEventJson> batch) {
        // Kick-off the first upload
        try {
            Request request = buildRequest(batch);
            this.doUpload(request, 0);
        } catch (IOException e) {
            Log.e(TAG, "Encountered IOException in doUpload", e);
        }
    }

    private void doUpload(Request request, int attempt) {
        if (attempt > REJECTION_LIMIT) {
            return;
        }

        this.taskManager.schedule(
                new UploadTask(this, request, attempt),
                (long) (Math.pow(attempt, BACKOFF_EXPONENT) * BACKOFF_MULTIPLIER),
                BACKOFF_UNIT
        );
    }

    /** Builds an HTTP request for the specified event batch */
    @Nullable
    private Request buildRequest(List<MobileEventJson> batch) throws IOException {
        if (batch.isEmpty()) {
            return null;
        }

        Sift.Config config = configProvider.getConfig();
        if (config == null) {
            Log.d(TAG, "Missing Sift.Config object");
            return null;
        }

        if (config.accountId == null || config.beaconKey == null || config.serverUrlFormat == null) {
            Log.w(TAG, "Missing account ID, beacon key, and/or server URL format");
            return null;
        }

        Log.d(TAG, String.format("Built HTTP request for batch size=%d: %s", batch.size(), batch.toString()));

        String encodedBeaconKey =  Base64.encodeToString(config.beaconKey.getBytes(US_ASCII),
                Base64.NO_WRAP);

        ListRequestJson<MobileEventJson> request = ListRequestJson.<MobileEventJson>newBuilder()
                .withData(batch)
                .build();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        OutputStream gzip = new GZIPOutputStream(os);
        Writer writer = new OutputStreamWriter(gzip, UTF8);
        Sift.GSON.toJson(request, writer);
        writer.close();

        return new Request.Builder()
                .url(String.format(config.serverUrlFormat, config.accountId))
                .header("Authorization", "Basic " + encodedBeaconKey)
                .header("Content-Encoding", "gzip")
                .put(RequestBody.create(JSON, os.toByteArray()))
                .build();
    }

    private class UploadTask implements Runnable {
        private BetterUploader uploader;
        private Request request;
        private int attempt;

        UploadTask(BetterUploader uploader, Request request, int attempt) {
            this.uploader = uploader;
            this.request = request;
            this.attempt = attempt;
        }

        @Override
        public void run() {
            Log.d(TAG, "Sending HTTP request");
            // try-with needs API level 19+ :(
            Response response = null;
            try {
                response = this.uploader.client.newCall(this.request).execute();
                int code = response.code();
                String body = response.body() == null ? null : response.body().string();
                response.close();

                if (code == 200) {
                    Log.d(TAG,"HTTP 200");
                    return;
                } else if (code == 400) {
                    Log.d(TAG, String.format(
                            "HTTP error: status=%d response=%s", code, body));
                } else {
                    Log.d(TAG, String.format(
                            "HTTP error: status=%d response=%s", code, body));
                    this.uploader.doUpload(this.request, this.attempt + 1);
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error in UploadTask", e);
            }
        }
    }
}
