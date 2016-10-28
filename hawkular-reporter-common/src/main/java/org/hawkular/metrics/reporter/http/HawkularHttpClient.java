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

import static org.hawkular.metrics.reporter.http.HawkularJson.doubleDataPoint;
import static org.hawkular.metrics.reporter.http.HawkularJson.longDataPoint;
import static org.hawkular.metrics.reporter.http.HawkularJson.metricJson;

import java.io.IOException;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import org.hawkular.metrics.reporter.persister.HawkularMetricsPersister;

/**
 * Http client interface for Hawkular, in case someone would like to use other than the default one
 * @author Joel Takvorian
 */
public abstract class HawkularHttpClient implements HawkularMetricsPersister {

    public abstract void addHeaders(Map<String, String> headers);
    public abstract HawkularHttpResponse postMetric(String type, String jsonBody) throws IOException;
    public abstract HawkularHttpResponse putTags(String resourcePath, String jsonBody) throws IOException;

    @Override
    public void addProperties(Map<String, String> properties) {
        addHeaders(properties);
    }

    @Override
    public void writeGaugesData(Long timestamp, Map<String, Double> metricsPoints) throws IOException {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        metricsPoints.entrySet().stream()
                .map(e -> metricJson(e.getKey(), doubleDataPoint(timestamp, e.getValue())))
                .forEach(builder::add);
        postMetric("gauges", builder.build().toString());
    }

    @Override
    public void writeCountersData(Long timestamp, Map<String, Long> metricsPoints) throws IOException {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        metricsPoints.entrySet().stream()
                .map(e -> metricJson(e.getKey(), longDataPoint(timestamp, e.getValue())))
                .forEach(builder::add);
        postMetric("counters", builder.build().toString());
    }

    @Override
    public void writeGaugesTags(String metricId, Map<String, String> tags) throws IOException {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        tags.forEach(jsonObjectBuilder::add);
        putTags("/gauges/" + metricId + "/tags", jsonObjectBuilder.build().toString());
    }

    @Override
    public void writeCountersTags(String metricId, Map<String, String> tags) throws IOException {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        tags.forEach(jsonObjectBuilder::add);
        putTags("/counters/" + metricId + "/tags", jsonObjectBuilder.build().toString());
    }
}
