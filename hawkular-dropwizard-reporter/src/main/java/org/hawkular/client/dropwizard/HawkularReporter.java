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

import static org.hawkular.client.json.HawkularJson.bigdDataPoint;
import static org.hawkular.client.json.HawkularJson.doubleDataPoint;
import static org.hawkular.client.json.HawkularJson.longDataPoint;
import static org.hawkular.client.json.HawkularJson.metricJson;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import org.hawkular.client.http.HawkularHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Counting;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Sampling;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;

/**
 * Dropwizard Reporter used to report to a Hawkular Metrics data store
 */
public class HawkularReporter extends ScheduledReporter {

    private static final Logger LOG = LoggerFactory.getLogger(HawkularReporter.class);

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
        if (gauges.isEmpty() && counters.isEmpty() && histograms.isEmpty() && meters.isEmpty() &&
                timers.isEmpty()) {
            return;
        }

        final long timestamp = clock.getTime();

        JsonBuilder builder = new JsonBuilder(timestamp, prefix);
        processGauges(builder, gauges);
        processCounters(builder, counters);
        processMeters(builder, meters);
        processHistograms(builder, histograms);
        processTimers(builder, timers);

        builder.getMetricsMap().forEach((type, arrayBuilder) -> {
            try {
                hawkularClient.postMetric(type, arrayBuilder.build().toString());
            } catch (IOException e) {
                LOG.error("Could not post metric data", e);
            }
        });
    }

    private void processGauges(JsonBuilder builder, Map<String, Gauge> gauges) {
        for (Map.Entry<String, Gauge> e : gauges.entrySet()) {
            builder.addGauge(e.getKey(), e.getValue().getValue());
        }
    }

    private void processCounters(JsonBuilder builder, Map<String, Counter> counters) {
        for (Map.Entry<String, Counter> e : counters.entrySet()) {
            builder.addCounter(e.getKey(), e.getValue().getCount());
        }
    }

    private void processMeters(JsonBuilder builder, Map<String, Meter> meters) {
        for (Map.Entry<String, Meter> e : meters.entrySet()) {
            processCounting(builder, e.getKey(), e.getValue());
            processMetered(builder, e.getKey(), e.getValue());
        }
    }

    private void processHistograms(JsonBuilder builder, Map<String, Histogram> histograms) {
        for (Map.Entry<String, Histogram> e : histograms.entrySet()) {
            processCounting(builder, e.getKey(), e.getValue());
            processSampling(builder, e.getKey(), e.getValue());
        }
    }

    private void processTimers(JsonBuilder builder, Map<String, Timer> timers) {
        for (Map.Entry<String, Timer> e : timers.entrySet()) {
            processCounting(builder, e.getKey(), e.getValue());
            processMetered(builder, e.getKey(), e.getValue());
            processSampling(builder, e.getKey(), e.getValue());
        }
    }

    private static void processCounting(JsonBuilder builder, String name, Counting counting) {
        builder.addCounter(name + ".count", counting.getCount());
    }

    private static void processMetered(JsonBuilder builder, String name, Metered metered) {
        builder.addGauge(name + ".1min", metered.getOneMinuteRate());
        builder.addGauge(name + ".5min", metered.getFiveMinuteRate());
        builder.addGauge(name + ".15min", metered.getFifteenMinuteRate());
        builder.addGauge(name + ".mean", metered.getMeanRate());
    }

    private static void processSampling(JsonBuilder builder, String name, Sampling sampling) {
        builder.addCounter(name + ".min", sampling.getSnapshot().getMin());
        builder.addCounter(name + ".max", sampling.getSnapshot().getMax());
        builder.addGauge(name + ".mean", sampling.getSnapshot().getMean());
        builder.addGauge(name + ".median", sampling.getSnapshot().getMedian());
        builder.addGauge(name + ".stddev", sampling.getSnapshot().getStdDev());
        builder.addGauge(name + ".75perc", sampling.getSnapshot().get75thPercentile());
        builder.addGauge(name + ".95perc", sampling.getSnapshot().get95thPercentile());
        builder.addGauge(name + ".98perc", sampling.getSnapshot().get98thPercentile());
        builder.addGauge(name + ".99perc", sampling.getSnapshot().get99thPercentile());
        builder.addGauge(name + ".999perc", sampling.getSnapshot().get999thPercentile());
    }

    public static HawkularReporterBuilder builder(MetricRegistry registry, String tenant) {
        return new HawkularReporterBuilder(registry, tenant);
    }

    private static class JsonBuilder {
        private final long timestamp;
        private final Optional<String> prefix;
        private Map<String, JsonArrayBuilder> metricsMap = new HashMap<>();

        private JsonBuilder(long timestamp, Optional<String> prefix) {
            this.timestamp = timestamp;
            this.prefix = prefix;
        }

        private void addCounter(String name, long l) {
            _add("counters", name, longDataPoint(timestamp, l));
        }

        private void addGauge(String name, Object value) {
            if (value instanceof BigDecimal) {
                _add("gauges", name, bigdDataPoint(timestamp, (BigDecimal) value));
            } else if (value != null && value.getClass().isAssignableFrom(Double.class)
                    && !Double.isNaN((Double) value) && Double.isFinite((Double) value)) {
                _add("gauges", name, doubleDataPoint(timestamp, (Double) value));
            }
        }

        private void _add(String type, String name, JsonObject dataPoint) {
            final JsonArrayBuilder metrics;
            if (metricsMap.containsKey(type)) {
                metrics = metricsMap.get(type);
            } else {
                metrics = Json.createArrayBuilder();
                metricsMap.put(type, metrics);
            }
            metrics.add(metricJson(prefix, name, dataPoint));
        }

        private Map<String,JsonArrayBuilder> getMetricsMap() {
            return metricsMap;
        }
    }
}
