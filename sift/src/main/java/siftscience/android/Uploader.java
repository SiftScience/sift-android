// Copyright (c) 2018 Sift Science. All rights reserved.

package siftscience.android;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.util.Base64;
import android.util.Log;
import sun.rmi.runtime.Log;

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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Stateless utility class for sending MobileEventJson batches to Sift backend
 */
public class Uploader {
    private static final String TAG = Uploader.class.getName();
    private static final long BACKOFF_MULTIPLIER = TimeUnit.SECONDS.toSeconds(3);
    private static final long BACKOFF_EXPONENT = 2;
    private static final TimeUnit BACKOFF_UNIT = TimeUnit.SECONDS;
    private static final Charset US_ASCII = StandardCharsets.US_ASCII;
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    private static final int MAX_BYTES = 4096;

    @VisibleForTesting
    static final int MAX_RETRIES = 3;

    private final TaskManager taskManager;
    private final ConfigProvider configProvider;

    interface ConfigProvider {
        Sift.Config getConfig();
    }

    static class Request {
        private String method;
        private URL url;
        private Map<String, String> headers;
        private byte[] body;

        Request(String method, URL url, Map headers, byte[] body) {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
        }

        static class Builder {
            private String method;
            private URL url;
            private Map<String, String> headers;
            private byte[] body;

            Request.Builder withMethod(String method) {
                this.method = method;
                return this;
            }


            Request.Builder withUrl(URL url) {
                this.url = url;
                return this;
            }

            Request.Builder withHeaders(Map headers) {
                this.headers = headers;
                return this;
            }

            Request.Builder withBody(byte[] body) {
                this.body = body;
                return this;
            }

            public Request build() {
                return new Request(method, url, headers, body);
            }
        }
    }

    Uploader(TaskManager taskManager, ConfigProvider configProvider) {
        this.taskManager = taskManager;
        this.configProvider = configProvider;
    }

    public void upload(List<MobileEventJson> batch) {
        // Kick-off the first upload
        try {
            Request request = makeRequest(batch);
            if (request != null) {
                Log.d(TAG, String.format("Uploading batch of size %d", batch.size()));
                this.doUpload(request, MAX_RETRIES);
            }
        } catch (IOException e) {
            Log.e(TAG, "Encountered IOException in upload", e);
        }
    }

    private void doUpload(Request request, int retriesRemaining) {
        if (retriesRemaining == 0) {
            return;
        }

        this.taskManager.schedule(
                new UploadTask(this, request, retriesRemaining),
                (long) (Math.pow(MAX_RETRIES - retriesRemaining, BACKOFF_EXPONENT) * BACKOFF_MULTIPLIER),
                BACKOFF_UNIT
        );
    }

    /** Builds a Request for the specified event batch */
    @Nullable
    private Request makeRequest(List<MobileEventJson> batch) throws IOException {
        if (batch == null || batch.isEmpty()) {
            Log.d(TAG, "Mobile events batch is empty");
            return null;
        }

        Sift.Config config = configProvider.getConfig();

        if (config == null) {
            Log.d(TAG, "Missing Sift.Config object");
            return null;
        }

        if (!config.isValid()) {
            Log.d(TAG, "Sift.Config is not valid");
            return null;
        }

        URL url = new URL(String.format(config.serverUrlFormat, config.accountId));

        final String encodedBeaconKey = Base64.encodeToString(
                config.beaconKey.getBytes(US_ASCII), Base64.NO_WRAP);

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Basic " + encodedBeaconKey);
        headers.put("Accept", "application/json");
        headers.put("Content-Encoding", "gzip");
        headers.put("Content-Type", "application/json");

        ListRequestJson request = new ListRequestJson()
                .withData(Collections.<Object>unmodifiableList(batch));

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        OutputStream gzip = new GZIPOutputStream(os);
        Writer writer = new OutputStreamWriter(gzip, UTF8);
        Sift.GSON.toJson(request, writer);
        writer.close();

        Log.d(TAG, String.format("Built HTTP request for batch of size %d", batch.size()));

        return new Request.Builder()
                .withMethod("PUT")
                .withUrl(url)
                .withHeaders(headers)
                .withBody(os.toByteArray())
                .build();
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
        private Uploader uploader;
        private final Request request;
        private int retriesRemaining;

        UploadTask(Uploader uploader, Request request, int retriesRemaining) {
            this.uploader = uploader;
            this.request = request;
            this.retriesRemaining = retriesRemaining;
        }

        @Override
        public void run() {
            Log.d(TAG, "Sending HTTP request");
            try {
                HttpURLConnection connection = (HttpURLConnection) this.request.url.openConnection();
                connection.setRequestMethod(this.request.method);
                for (Map.Entry<String, String> header : this.request.headers.entrySet()) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
                connection.setFixedLengthStreamingMode(this.request.body.length);
                connection.setDoOutput(true);
                connection.setDoInput(true);

                connection.connect();

                try {
                    OutputStream out = connection.getOutputStream();

                    try {
                        out.write(this.request.body);
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
                    } else if (code == 400) {
                        Log.d(TAG, String.format(
                                "HTTP error: status=%d response=%s", code, body));
                    } else {
                        Log.d(TAG, String.format(
                                "HTTP error: status=%d response=%s", code, body));
                        this.uploader.doUpload(request, this.retriesRemaining - 1);
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
