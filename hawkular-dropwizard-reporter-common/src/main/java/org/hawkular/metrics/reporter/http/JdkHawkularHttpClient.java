/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.metrics.reporter.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link HawkularHttpClient}, using the JDK HTTP client.
 * This class does not aim to be any generic. It's very tied to what is needed for the dropwizard reporter.
 * @author Joel Takvorian
 */
public class JdkHawkularHttpClient implements HawkularHttpClient {

    private final String uri;
    private final Map<String, String> headers = new HashMap<>();

    public JdkHawkularHttpClient(String uri) {
        this.uri = uri + "/hawkular/metrics";
    }

    @Override
    public void addHeaders(Map<String, String> headers) {
        this.headers.putAll(headers);
    }

    @Override
    public HawkularHttpResponse postMetrics(String jsonBody) throws IOException {
        URL url = new URL(uri + "/metrics/raw");
        return send("POST", url, jsonBody.getBytes());
    }

    @Override
    public HawkularHttpResponse putTags(String resourcePath, String jsonBody) throws IOException {
        URL url = new URL(uri + resourcePath);
        return send("PUT", url, jsonBody.getBytes());
    }

    public HawkularHttpResponse readMetric(String type, String name) throws IOException {
        URL url = new URL(uri + "/" + type + "/" + name + "/raw");
        return get(url);
    }

    private HawkularHttpResponse send(String verb, URL url, byte[] content) throws IOException {
        InputStream is = null;
        byte[] data = null;
        int responseCode;
        ByteArrayOutputStream baos = null;
        try {
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestMethod(verb);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", String.valueOf(content.length));
            headers.forEach(connection::setRequestProperty);
            OutputStream os = connection.getOutputStream();
            os.write(content);
            os.close();
            responseCode = connection.getResponseCode();
            is = connection.getInputStream();
            final byte[] buffer = new byte[2 * 1024];
            baos = new ByteArrayOutputStream();
            int n;
            while ((n = is.read(buffer)) >= 0) {
                baos.write(buffer, 0, n);
            }
            data = baos.toByteArray();
        } finally {
            if (is != null) {
                is.close();
            }
            if (baos != null) {
                baos.close();
            }
        }
        return new HawkularHttpResponse(new String(data), responseCode);
    }

    private HawkularHttpResponse get(URL url) throws IOException {
        InputStream is = null;
        byte[] data = null;
        int responseCode;
        ByteArrayOutputStream baos = null;
        try {
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            headers.forEach(connection::setRequestProperty);
            responseCode = connection.getResponseCode();
            is = connection.getInputStream();
            final byte[] buffer = new byte[2 * 1024];
            baos = new ByteArrayOutputStream();
            int n;
            while ((n = is.read(buffer)) >= 0) {
                baos.write(buffer, 0, n);
            }
            data = baos.toByteArray();
        } finally {
            if (is != null) {
                is.close();
            }
            if (baos != null) {
                baos.close();
            }
        }
        return new HawkularHttpResponse(new String(data), responseCode);
    }
}
