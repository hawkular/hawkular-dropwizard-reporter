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
package org.hawkular.metrics.dropwizard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.iterable.Extractor;
import org.assertj.core.util.DoubleComparator;
import org.hawkular.metrics.reporter.http.HawkularHttpResponse;
import org.hawkular.metrics.reporter.http.JdkHawkularHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

/**
 * Note: these integration tests require an Hawkular Metrics server running on localhost:8080
 * On CI it is done through a docker container described in Travis YAML
 * @author Joel Takvorian
 */
public class HawkularReporterITest {

    private static final String USERNAME = "jdoe";
    private static final String PASSWORD = "password";

    private final MetricRegistry registry = new MetricRegistry();
    private final String defaultTenant = "unit-test";
    private final JdkHawkularHttpClient defaultClient = new JdkHawkularHttpClient("http://localhost:8080");
    private final Extractor<Object, Double> doubleExtractor = e -> (Double) ((JSONObject)e).get("value");
    private final Extractor<Object, Integer> intExtractor = e -> (Integer) ((JSONObject)e).get("value");

    @Before
    public void setup() {
        defaultClient.addHeaders(Collections.singletonMap("Hawkular-Tenant", defaultTenant));
        String encoded = Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes());
        defaultClient.addHeaders(Collections.singletonMap("Authorization", "Basic " + encoded));
    }

    @Test
    public void shouldReportCounter() throws IOException {
        String metricName = randomName();
        HawkularReporter reporter = HawkularReporter.builder(registry, defaultTenant)
                .basicAuth(USERNAME, PASSWORD)
                .build();

        final Counter counter = registry.counter(metricName);
        counter.inc(5);
        reporter.report();

        HawkularHttpResponse response = defaultClient.readMetric("counters", metricName);

        assertThat(response.getResponseCode()).isEqualTo(200);
        JSONArray result = new JSONArray(response.getContent());
        assertThat(result).extracting(intExtractor).containsExactly(5);

        counter.inc(8);
        reporter.report();

        response = defaultClient.readMetric("counters", metricName);

        assertThat(response.getResponseCode()).isEqualTo(200);
        result = new JSONArray(response.getContent());
        assertThat(result).extracting(intExtractor).containsExactly(13, 5);
    }

    @Test
    public void shouldReportGauge() throws InterruptedException, IOException {
        String metricName = randomName();
        HawkularReporter reporter = HawkularReporter.builder(registry, defaultTenant)
                .basicAuth(USERNAME, PASSWORD)
                .build();

        final AtomicReference<Double> gauge = new AtomicReference<>(10d);
        registry.register(metricName, (Gauge<Double>) gauge::get);
        reporter.report();
        gauge.set(7.1);
        Thread.sleep(50);
        reporter.report();
        gauge.set(13.4);
        Thread.sleep(50);
        reporter.report();

        HawkularHttpResponse response = defaultClient.readMetric("gauges", metricName);

        assertThat(response.getResponseCode()).isEqualTo(200);
        JSONArray result = new JSONArray(response.getContent());
        assertThat(result).extracting(doubleExtractor)
                .usingElementComparator(new DoubleComparator(0.001))
                .containsExactly(13.4, 7.1, 10d);
    }

    @Test
    public void shouldReportMeter() throws InterruptedException, IOException {
        String metricName = randomName();
        HawkularReporter reporter = HawkularReporter.builder(registry, defaultTenant)
                .basicAuth(USERNAME, PASSWORD)
                .build();

        Meter meter = registry.meter(metricName);
        meter.mark(1000);
        Thread.sleep(100);
        meter.mark(1000);
        reporter.report();

        HawkularHttpResponse response = defaultClient.readMetric("gauges", metricName + ".mean");

        assertThat(response.getResponseCode()).isEqualTo(200);
        JSONArray result = new JSONArray(response.getContent());
        assertThat(result).hasSize(1);
        Double rate = doubleExtractor.extract(result.get(0));
        // Should be around 15000 ~ 18000, never more than 20000
        assertThat(rate).isBetween(10000d, 20000d);

        // It must also have posted a counter
        response = defaultClient.readMetric("counters", metricName + ".count");

        assertThat(response.getResponseCode()).isEqualTo(200);
        result = new JSONArray(response.getContent());
        assertThat(result).extracting(intExtractor).containsExactly(2000);
    }

    @Test
    public void shouldReportWithPrefix() throws IOException {
        String metricName = randomName();
        HawkularReporter reporter = HawkularReporter.builder(registry, defaultTenant)
                .basicAuth(USERNAME, PASSWORD)
                .prefixedWith("prefix-")
                .build();

        registry.register(metricName, (Gauge<Double>) () -> 5d);
        reporter.report();

        HawkularHttpResponse response = defaultClient.readMetric("gauges", metricName);

        // Wrong metric name
        assertThat(response.getResponseCode()).isEqualTo(204);

        response = defaultClient.readMetric("gauges", "prefix-" + metricName);
        assertThat(response.getResponseCode()).isEqualTo(200);
    }

    private static String randomName() {
        return RandomStringUtils.randomAlphanumeric(8).toLowerCase();
    }
}
