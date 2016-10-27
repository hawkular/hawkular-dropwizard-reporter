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
package org.hawkular.client.dropwizard;

import static java.util.stream.Collectors.toMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.iterable.Extractor;
import org.hawkular.client.http.HawkularHttpClient;
import org.hawkular.client.http.HawkularHttpResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;

/**
 * @author Joel Takvorian
 */
public class HawkularReporterTest {

    private final MetricRegistry registry = new MetricRegistry();
    private final Extractor<Object, String> idFromRoot = e -> ((JSONObject)e).getString("id");
    private final Extractor<Object, Integer> valueFromDataPoints = e -> ((JSONObject)e).getInt("value");
    private final Extractor<Object, Integer> valueFromRoot = e -> valueFromDataPoints.extract(((JSONObject)e)
            .getJSONArray("dataPoints").get(0));
    private final HttpClientMock client = new HttpClientMock();

    @Test
    public void shouldReportSimpleCounterWithoutTag() {
        HawkularReporter reporter = HawkularReporter.builder(registry, "unit-test").useHawkularPersistence(uri -> client).build();

        final Counter counter = registry.counter("my.counter");
        counter.inc();
        reporter.report();

        assertThat(client.getPostedMetrics()).extracting(Pair::getLeft).containsExactly("counters");
        JSONArray json = new JSONArray(client.getPostedMetrics().get(0).getRight());
        assertThat(json).extracting(idFromRoot).containsExactly("my.counter");
        assertThat(json).extracting(valueFromRoot).containsExactly(1);
        assertThat(client.getPostedTags()).isEmpty();
    }

    @Test
    public void shouldReportCountersWithTags() {
        HawkularReporter reporter = HawkularReporter.builder(registry, "unit-test")
                .useHawkularPersistence(uri -> client)
                .globalTags(Collections.singletonMap("global-tag", "abc"))
                .perMetricTags(Collections.singletonMap("my.second.counter",
                        Collections.singletonMap("metric-tag", "def")))
                .build();

        final Counter counter1 = registry.counter("my.first.counter");
        final Counter counter2 = registry.counter("my.second.counter");
        counter1.inc();
        counter2.inc();
        reporter.report();

        assertThat(client.getPostedMetrics()).extracting(Pair::getLeft).containsExactly("counters");
        JSONArray json = new JSONArray(client.getPostedMetrics().get(0).getRight());
        assertThat(json).extracting(idFromRoot).containsOnly("my.first.counter", "my.second.counter");
        assertThat(json).extracting(valueFromRoot).containsExactly(1, 1);

        assertThat(client.getPostedTags()).containsOnly(
                Pair.of("/counters/my.first.counter/tags", "{\"global-tag\":\"abc\"}"),
                Pair.of("/counters/my.second.counter/tags", "{\"global-tag\":\"abc\",\"metric-tag\":\"def\"}"));
    }

    @Test
    public void shouldReportHistogram() {
        HawkularReporter reporter = HawkularReporter.builder(registry, "unit-test").useHawkularPersistence(uri -> client).build();

        final Histogram histogram = registry.histogram("my.histogram");
        histogram.update(3);
        histogram.update(8);
        histogram.update(7);
        histogram.update(1);
        histogram.update(8);
        histogram.update(4);
        reporter.report();

        assertThat(client.getPostedMetrics()).extracting(Pair::getLeft).containsExactly("counters", "gauges");
        JSONArray countersJson = new JSONArray(client.getPostedMetrics().get(0).getRight());
        assertThat(countersJson).extracting(idFromRoot).containsExactly("my.histogram.count");
        assertThat(countersJson).extracting(valueFromRoot).containsExactly(6);

        JSONArray gaugesJson = new JSONArray(client.getPostedMetrics().get(1).getRight());
        Map<String, Integer> values = StreamSupport.stream(gaugesJson.spliterator(), false)
                .collect(toMap(idFromRoot::extract, valueFromRoot::extract));
        // Note: we extract int values here for simplicity, but actual values are double. The goal is not to test
        // Dropwizard algorithm for metrics generation, so we don't bother with accuracy.
        assertThat(values).containsOnly(
                entry("my.histogram.mean", 5),
                entry("my.histogram.median", 7),
                entry("my.histogram.stddev", 2),
                entry("my.histogram.75perc", 8),
                entry("my.histogram.95perc", 8),
                entry("my.histogram.98perc", 8),
                entry("my.histogram.99perc", 8),
                entry("my.histogram.999perc", 8));

        assertThat(client.getPostedTags()).containsOnly(
                Pair.of("/counters/my.histogram.count/tags", "{\"histogram\":\"count\"}"),
                Pair.of("/gauges/my.histogram.mean/tags", "{\"histogram\":\"mean\"}"),
                Pair.of("/gauges/my.histogram.min/tags", "{\"histogram\":\"min\"}"),
                Pair.of("/gauges/my.histogram.max/tags", "{\"histogram\":\"max\"}"),
                Pair.of("/gauges/my.histogram.stddev/tags", "{\"histogram\":\"stddev\"}"),
                Pair.of("/gauges/my.histogram.median/tags", "{\"histogram\":\"median\"}"),
                Pair.of("/gauges/my.histogram.75perc/tags", "{\"histogram\":\"75perc\"}"),
                Pair.of("/gauges/my.histogram.95perc/tags", "{\"histogram\":\"95perc\"}"),
                Pair.of("/gauges/my.histogram.98perc/tags", "{\"histogram\":\"98perc\"}"),
                Pair.of("/gauges/my.histogram.99perc/tags", "{\"histogram\":\"99perc\"}"),
                Pair.of("/gauges/my.histogram.999perc/tags", "{\"histogram\":\"999perc\"}"));
    }

    private static class HttpClientMock extends HawkularHttpClient {
        private List<Pair<String, String>> postedMetrics = new ArrayList<>();
        private List<Pair<String, String>> postedTags = new ArrayList<>();

        @Override public void addHeaders(Map<String, String> headers) {}

        @Override public HawkularHttpResponse postMetric(String type, String jsonBody) throws IOException {
            postedMetrics.add(Pair.of(type, jsonBody));
            return null;
        }

        @Override public HawkularHttpResponse putTags(String resourcePath, String jsonBody) throws IOException {
            postedTags.add(Pair.of(resourcePath, jsonBody));
            return null;
        }

        List<Pair<String, String>> getPostedMetrics() {
            return postedMetrics;
        }

        List<Pair<String, String>> getPostedTags() {
            return postedTags;
        }
    }
}
