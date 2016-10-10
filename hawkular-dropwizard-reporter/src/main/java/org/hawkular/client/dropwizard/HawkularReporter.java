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

import static java.util.stream.Collectors.toList;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import org.hawkular.client.http.HawkularHttpClient;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;

/**
 * Dropwizard Reporter used to report to a Hawkular Metrics data store
 */
public class HawkularReporter extends ScheduledReporter {

    private final Optional<String> prefix;
    private final Clock clock;
    private final HawkularHttpClient hawkularClient;

    HawkularReporter(MetricRegistry registry, HawkularHttpClient hawkularClient, Optional<String> prefix, TimeUnit
            rateUnit, TimeUnit durationUnit, MetricFilter filter) {
        super(registry, "hawkular-reporter", filter, rateUnit, durationUnit);

        this.prefix = prefix;
        this.clock = Clock.defaultClock();
        this.hawkularClient = hawkularClient;
    }

    @Override
    public void report(SortedMap<String, Gauge> gauges,
                       SortedMap<String, Counter> counters,
                       SortedMap<String, Histogram> histograms,
                       SortedMap<String, Meter> meters,
                       SortedMap<String, Timer> timers) {

        if (gauges.isEmpty() && counters.isEmpty() && histograms.isEmpty() && meters.isEmpty() && timers.isEmpty()) {
            return;
        }

        final long timestamp = clock.getTime();

        List<JsonObject> json = gauges.entrySet().stream()
                .filter(e -> isValidGauge(e.getValue().getValue()))
                .map(e -> {
                    final JsonObject dataPoint;
                    if (e.getValue().getValue() instanceof BigDecimal) {
                        dataPoint = bigdDataPoint(timestamp, (BigDecimal) e.getValue().getValue());
                    } else {
                        dataPoint = doubleDataPoint(timestamp, (Double) e.getValue().getValue());
                    }
                    return metricJson(e.getKey(), Json.createArrayBuilder().add(dataPoint).build());
                })
                .collect(toList());
        if (!json.isEmpty()) {
            postMetric("gauges", json);
        }

        json = counters.entrySet().stream()
                .map(e -> metricJson(e.getKey(),
                        Json.createArrayBuilder()
                                .add(longDataPoint(timestamp, e.getValue().getCount()))
                                .build()))
                .collect(toList());
        if (!json.isEmpty()) {
            postMetric("counters", json);
        }

        json = meters.entrySet().stream()
                .flatMap(e -> {
                    // Extract several metrics from Meter value
                    JsonObject oneMinuteMetric = metricJson(
                            e.getKey() + ".1min",
                            Json.createArrayBuilder()
                                .add(doubleDataPoint(timestamp, e.getValue().getOneMinuteRate()))
                                .build());
                    JsonObject fiveMinuteMetric = metricJson(
                            e.getKey() + ".5min",
                            Json.createArrayBuilder()
                                    .add(doubleDataPoint(timestamp, e.getValue().getFiveMinuteRate()))
                                    .build());
                    JsonObject fifteenMinuteMetric = metricJson(
                            e.getKey() + ".15min",
                            Json.createArrayBuilder()
                                    .add(doubleDataPoint(timestamp, e.getValue().getFifteenMinuteRate()))
                                    .build());
                    JsonObject meanMetric = metricJson(
                            e.getKey() + ".mean",
                            Json.createArrayBuilder()
                                    .add(doubleDataPoint(timestamp, e.getValue().getMeanRate()))
                                    .build());
                    return Stream.of(oneMinuteMetric, fiveMinuteMetric, fifteenMinuteMetric, meanMetric);
                })
                .collect(toList());
        if (!json.isEmpty()) {
            postMetric("gauges", json);
        }

        // Also extract counter metric from Meter value
        json = meters.entrySet().stream()
                .map(e -> metricJson(
                            e.getKey() + ".count",
                            Json.createArrayBuilder()
                                    .add(longDataPoint(timestamp, e.getValue().getCount()))
                                    .build()))
                .collect(toList());
        if (!json.isEmpty()) {
            postMetric("counters", json);
        }

        // TODO: timers
    }

    private void postMetric(String type, List<JsonObject> objects) {
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        objects.forEach(arrayBuilder::add);
        hawkularClient.postMetric(type, arrayBuilder.build().toString());
    }

    private static boolean isValidGauge(Object value) {
        return value instanceof BigDecimal
                || (value != null && value.getClass().isAssignableFrom(Double.class)
                    && !Double.isNaN((Double) value) && Double.isFinite((Double) value));
    }

    private static JsonObject bigdDataPoint(long timestamp, BigDecimal value) {
        return Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("value", value)
                .build();
    }

    private static JsonObject doubleDataPoint(long timestamp, double value) {
        return Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("value", value)
                .build();
    }

    private static JsonObject longDataPoint(long timestamp, long value) {
        return Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("value", value)
                .build();
    }

    private JsonObject metricJson(String name, JsonArray dataPoints) {
        String prefixedName = prefix.map(p -> p + name).orElse(name);
        return Json.createObjectBuilder()
                .add("id", prefixedName)
                .add("dataPoints", dataPoints)
                .build();
    }

    public static HawkularReporterBuilder builder(MetricRegistry registry, String tenant) {
        return new HawkularReporterBuilder(registry, tenant);
    }

}
