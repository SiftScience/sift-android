// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Base64;
import android.util.Log;

import com.sift.api.representations.ListRequestJson;
import com.sift.api.representations.MobileEventJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 *
 */
public class BetterUploader {
    private static final String TAG = BetterUploader.class.getName();

    @VisibleForTesting
    static final int MAX_RETRIES = 3;
    private static final long BACKOFF_MULTIPLIER = TimeUnit.SECONDS.toSeconds(3);
    private static final long BACKOFF_EXPONENT = 2;
    private static final TimeUnit BACKOFF_UNIT = TimeUnit.SECONDS;
    private TaskManager taskManager;
    private ConfigProvider configProvider;

    private static final Charset US_ASCII = Charset.forName("US-ASCII");
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final int MAX_BYTES = 4096;

    interface ConfigProvider {
        Sift.Config getConfig();
    }

    BetterUploader(TaskManager taskManager, ConfigProvider configProvider) {
        this.taskManager = taskManager;
        this.configProvider = configProvider;
    }

    public void upload(List<MobileEventJson> batch) {
        Log.d(TAG, String.format("Upload batch of size %d", batch.size()));

        // Kick-off the first upload
        try {
            byte[] requestBody = buildRequest(batch);
            if (requestBody != null) {
                this.doUpload(requestBody, MAX_RETRIES);
            }
        } catch (IOException e) {
            Log.e(TAG, "Encountered IOException in upload", e);
        }
    }

    private void doUpload(byte[] requestBody, int retriesRemaining) {
        if (retriesRemaining == 0) {
            return;
        }

        this.taskManager.schedule(
                new UploadTask(this, requestBody, retriesRemaining),
                (long) (Math.pow(MAX_RETRIES - retriesRemaining, BACKOFF_EXPONENT) * BACKOFF_MULTIPLIER),
                BACKOFF_UNIT
        );
    }

    /** Builds an HTTP request for the specified event batch */
    @Nullable
    private byte[] buildRequest(List<MobileEventJson> batch) throws IOException {
        if (batch.isEmpty()) {
            return null;
        }

        Sift.Config config = configProvider.getConfig();

        if (config == null) {
            Log.d(TAG, "Missing Sift.Config object");
            return null;
        }

        if (config.accountId == null || config.beaconKey == null || config.serverUrlFormat == null) {
            Log.d(TAG, "Missing account ID, beacon key, and/or server URL format");
            return null;
        }

        Log.d(TAG, String.format("Built HTTP request for batch of size=%d: %s", batch.size(), batch.toString()));
        return makeRequestBody(batch);
    }

    @Nullable
    private byte[] makeRequestBody(List<MobileEventJson> batch) throws IOException {
        if (batch == null || batch.isEmpty()) {
            return null;
        }

        ListRequestJson<MobileEventJson> request = ListRequestJson.<MobileEventJson>newBuilder()
                .withData(batch)
                .build();

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        OutputStream gzip = new GZIPOutputStream(os);
        Writer writer = new OutputStreamWriter(gzip, UTF8);
        Sift.GSON.toJson(request, writer);
        writer.close();

        return os.toByteArray();
    }

    private String readInputStreamAsString(InputStream in, int maxBytes) throws IOException {
        byte[] bytes = new byte[maxBytes];
        int position = 0;
        int read;

        while (position < bytes.length &&
                (read = in.read(bytes, 0, bytes.length - position)) >= 0) {
            position += read;
        }

        return new String(bytes, 0, position, UTF8);
    }

    private class UploadTask implements Runnable {
        private BetterUploader uploader;
        private final byte[] requestBody;
        private int retriesRemaining;

        UploadTask(BetterUploader uploader, byte[] requestBody, int retriesRemaining) {
            this.uploader = uploader;
            this.requestBody = requestBody;
            this.retriesRemaining = retriesRemaining;
        }

        @Override
        public void run() {
            Log.d(TAG, "Sending HTTP request");
            try {
                Sift.Config config = configProvider.getConfig();
                URL url = new URL(String.format(config.serverUrlFormat, config.accountId));

                String encodedBeaconKey = Base64.encodeToString(
                        config.beaconKey.getBytes(US_ASCII), Base64.NO_WRAP);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("PUT");
                connection.setRequestProperty("Authorization", "Basic " + encodedBeaconKey);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Content-Encoding", "gzip");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(this.requestBody.length);
                connection.setDoOutput(true);
                connection.setDoInput(true);

                connection.connect();

                try {
                    OutputStream out = connection.getOutputStream();

                    try {
                        out.write(this.requestBody);
                    } finally {
                        out.close();
                    }

                    int code = connection.getResponseCode();
                    String body = null;
                    InputStream in;

                    if (code >= 400) {
                        in = connection.getErrorStream();
                    } else {
                        in = connection.getInputStream();
                    }

                    if (in != null) {
                        try {
                            body = readInputStreamAsString(in, MAX_BYTES);
                        } finally {
                            in.close();
                        }
                    }

                    if (code == 200) {
                        Log.d(TAG,"HTTP 200");
                        return;
                    } else if (code == 400) {
                        Log.d(TAG, String.format(
                                "HTTP error: status=%d response=%s", code, body));
                    } else {
                        Log.d(TAG, String.format(
                                "HTTP error: status=%d response=%s", code, body));
                        this.uploader.doUpload(requestBody, this.retriesRemaining - 1);
                    }
                } finally {
                    connection.disconnect();
                }
            } catch (IOException e) {
                Log.e(TAG, "Network error in UploadTask", e);
            }
        }
    }
}
