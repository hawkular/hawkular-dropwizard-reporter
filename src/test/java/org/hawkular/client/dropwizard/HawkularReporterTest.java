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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.util.DoubleComparator;
import org.hawkular.client.core.ClientResponse;
import org.hawkular.client.core.HawkularClient;
import org.hawkular.metrics.model.DataPoint;
import org.junit.Test;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.util.concurrent.AtomicDouble;

/**
 * @author Joel Takvorian
 */
public class HawkularReporterTest {

    private final MetricRegistry registry = new MetricRegistry();
    private final String defaultTenant = "unit-test";
    private final HawkularClient defaultClient = HawkularClient.builder(defaultTenant).build();

    @Test
    public void shouldReportCounter() {
        String metricName = randomName();
        HawkularReporter reporter = HawkularReporter.builder(registry, defaultTenant).build();

        final Counter counter = registry.counter(metricName);
        counter.inc(5);
        reporter.report();

        ClientResponse<List<DataPoint<Long>>>
                response = defaultClient.metrics().counter().findCounterData(metricName, null, null, null, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getEntity()).extracting(DataPoint::getValue).containsExactly(5L);

        counter.inc(8);
        reporter.report();

        response = defaultClient.metrics().counter().findCounterData(metricName, null, null, null, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getEntity()).extracting(DataPoint::getValue).containsExactly(13L, 5L);
    }

    @Test
    public void shouldReportGauge() throws InterruptedException {
        String metricName = randomName();
        HawkularReporter reporter = HawkularReporter.builder(registry, defaultTenant).build();

        final AtomicDouble gauge = new AtomicDouble(10);
        registry.register(metricName, (Gauge<Double>) gauge::get);
        reporter.report();
        gauge.set(7.1);
        Thread.sleep(50);
        reporter.report();
        gauge.set(13.4);
        Thread.sleep(50);
        reporter.report();

        ClientResponse<List<DataPoint<Double>>>
                response = defaultClient.metrics().gauge().findGaugeDataWithId(metricName, null, null, null, null, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getEntity())
                .extracting(DataPoint::getValue)
                .usingElementComparator(new DoubleComparator(0.001))
                .containsExactly(13.4, 7.1, 10d);
    }

    @Test
    public void shouldReportMeter() throws InterruptedException {
        String metricName = randomName();
        HawkularReporter reporter = HawkularReporter.builder(registry, defaultTenant)
                .metersRate(MetersRate.MEAN)
                .build();

        Meter meter = registry.meter(metricName);
        meter.mark(1000);
        Thread.sleep(100);
        meter.mark(1000);
        reporter.report();

        ClientResponse<List<DataPoint<Double>>>
                response = defaultClient.metrics().gauge().findGaugeDataWithId(metricName, null, null, null, null, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getEntity()).hasSize(1);
        Double rate = response.getEntity().get(0).getValue();
        // Should be around 15000 ~ 18000, never more than 20000
        assertThat(rate).isBetween(10000d, 20000d);
    }

    @Test
    public void shouldReportWithPrefix() {
        String metricName = randomName();
        HawkularReporter reporter = HawkularReporter
                .builder(registry, defaultTenant)
                .prefixedWith("prefix-")
                .build();

        registry.register(metricName, (Gauge<Double>) () -> 5d);
        reporter.report();

        ClientResponse<List<DataPoint<Double>>>
                response = defaultClient.metrics().gauge().findGaugeDataWithId(metricName, null, null, null, null, null);

        // Wrong metric name
        assertThat(response.getStatusCode()).isEqualTo(204);

        response = defaultClient.metrics().gauge().findGaugeDataWithId("prefix-" + metricName, null, null, null, null,
                null);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    public void shouldConfigureHawkularClient() throws URISyntaxException {
        URI uri = new URI("https://someting:443/hawkular");
        HawkularReporter reporter = HawkularReporter
                .builder(registry, "other-tenant")
                .hawkularInfo(client -> client.tokenAuthentication("123456").uri(uri))
                .build();

        assertThat(reporter.hawkularClient.getClientInfo().getEndpointUri()).isEqualTo(uri);
        assertThat(reporter.hawkularClient.getClientInfo().getHeaders()).containsOnly(
                entry("Authorization", "Bearer 123456"),
                entry("Hawkular-Tenant", "other-tenant"));
    }

    private static String randomName() {
        return RandomStringUtils.randomAlphanumeric(8).toLowerCase();
    }
}
