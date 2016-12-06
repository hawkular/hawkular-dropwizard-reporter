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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.hawkular.metrics.reporter.http.HawkularHttpClient;
import org.hawkular.metrics.reporter.http.HawkularJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(HawkularReporter.class);

    private final Optional<String> prefix;
    private final Clock clock;
    private final HawkularHttpClient hawkularClient;
    private final MetricsTagger metricsTagger;

    HawkularReporter(MetricRegistry registry, HawkularHttpClient hawkularClient, Optional<String> prefix, Map<String,
            String> globalTags, Map<String, Map<String, String>> perMetricTags, boolean enableAutoTagging, TimeUnit
            rateUnit, TimeUnit durationUnit, MetricFilter filter) {
        super(registry, "hawkular-reporter", filter, rateUnit, durationUnit);

        this.prefix = prefix;
        this.clock = Clock.defaultClock();
        this.hawkularClient = hawkularClient;
        metricsTagger = new MetricsTagger(prefix, globalTags, perMetricTags, enableAutoTagging, hawkularClient,
                registry);
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

        DataAccumulator accu = new DataAccumulator();
        processGauges(accu, gauges);
        processCounters(accu, counters);
        processMeters(accu, meters);
        processHistograms(accu, histograms);
        processTimers(accu, timers);

        try {
            if (!accu.getCounters().isEmpty()) {
                hawkularClient.postMetric("counters", HawkularJson.countersToString(timestamp, accu.getCounters()));
            }
            if (!accu.getGauges().isEmpty()) {
                hawkularClient.postMetric("gauges", HawkularJson.gaugesToString(timestamp, accu.getGauges()));
            }
        } catch (IOException e) {
            LOG.error("Could not post metrics data", e);
        }
        accu.getCountersTags().forEach((metricId, tags) -> {
            try {
                hawkularClient.putTags("/counters/" + metricId + "/tags", HawkularJson.tagsToString(tags));
            } catch (IOException e) {
                LOG.error("Could not post tags data", e);
            }
        });
        accu.getGaugesTags().forEach((metricId, tags) -> {
            try {
                hawkularClient.putTags("/gauges/" + metricId + "/tags", HawkularJson.tagsToString(tags));
            } catch (IOException e) {
                LOG.error("Could not post tags data", e);
            }
        });
    }

    private static void processGauges(DataAccumulator builder, Map<String, Gauge> gauges) {
        for (Map.Entry<String, Gauge> e : gauges.entrySet()) {
            builder.addGauge(e.getKey(), e.getValue().getValue());
        }
    }

    private static void processCounters(DataAccumulator builder, Map<String, Counter> counters) {
        for (Map.Entry<String, Counter> e : counters.entrySet()) {
            builder.addCounter(e.getKey(), e.getValue().getCount());
        }
    }

    private static void processMeters(DataAccumulator builder, Map<String, Meter> meters) {
        for (Map.Entry<String, Meter> e : meters.entrySet()) {
            MetricComposers.COUNTINGS.forEach(metricComposer -> builder.addSubCounter(metricComposer, e));
            MetricComposers.METERED.forEach(metricComposer -> builder.addSubGauge(metricComposer, e));
        }
    }

    private static void processHistograms(DataAccumulator builder, Map<String, Histogram> histograms) {
        for (Map.Entry<String, Histogram> e : histograms.entrySet()) {
            MetricComposers.COUNTINGS.forEach(metricComposer -> builder.addSubCounter(metricComposer, e));
            MetricComposers.SAMPLING.forEach(metricComposer -> builder.addSubGauge(metricComposer, e));
        }
    }

    private static void processTimers(DataAccumulator builder, Map<String, Timer> timers) {
        for (Map.Entry<String, Timer> e : timers.entrySet()) {
            MetricComposers.COUNTINGS.forEach(metricComposer -> builder.addSubCounter(metricComposer, e));
            MetricComposers.METERED.forEach(metricComposer -> builder.addSubGauge(metricComposer, e));
            MetricComposers.SAMPLING.forEach(metricComposer -> builder.addSubGauge(metricComposer, e));
        }
    }

    public Optional<String> getPrefix() {
        return prefix;
    }

    public Map<String, String> getGlobalTags() {
        return metricsTagger.getGlobalTags();
    }

    public HawkularHttpClient getHawkularClient() {
        return hawkularClient;
    }

    public Map<String, Map<String, String>> getPerMetricTags() {
        return metricsTagger.getPerMetricTags();
    }

    public boolean isEnableAutoTagging() {
        return metricsTagger.isEnableAutoTagging();
    }

    /**
     * Create a new builder for an {@link HawkularReporter}
     * @param registry the Dropwizard Metrics registry
     * @param tenant the Hawkular tenant ID
     */
    public static HawkularReporterBuilder builder(MetricRegistry registry, String tenant) {
        return new HawkularReporterBuilder(registry, tenant);
    }

    private class DataAccumulator {
        private Map<String, Double> gauges = new HashMap<>();
        private Map<String, Long> counters = new HashMap<>();
        private Map<String, Map<String, String>> gaugesTags = new HashMap<>();
        private Map<String, Map<String, String>> countersTags = new HashMap<>();

        private DataAccumulator() {
        }

        private Map<String, Double> getGauges() {
            return gauges;
        }

        private Map<String, Long> getCounters() {
            return counters;
        }

        private Map<String, Map<String, String>> getGaugesTags() {
            return gaugesTags;
        }

        private Map<String, Map<String, String>> getCountersTags() {
            return countersTags;
        }

        private DataAccumulator addCounter(String name, long l) {
            String fullName = prefix.map(p -> p + name).orElse(name);
            counters.put(fullName, l);
            return this;
        }

        private DataAccumulator addGauge(String name, Object value) {
            String fullName = prefix.map(p -> p + name).orElse(name);
            if (value instanceof BigDecimal) {
                gauges.put(fullName, ((BigDecimal) value).doubleValue());
            } else if (value != null && value.getClass().isAssignableFrom(Double.class)
                    && !Double.isNaN((Double) value) && Double.isFinite((Double) value)) {
                gauges.put(fullName, (Double) value);
            }
            return this;
        }

        private <T> DataAccumulator addSubCounter(MetricComposer<T,Long> metricComposer,
                                                  Map.Entry<String, ? extends T> counterEntry) {
            String nameWithSuffix = metricComposer.getMetricNameWithSuffix(counterEntry.getKey());
            String fullName = prefix.map(p -> p + nameWithSuffix).orElse(nameWithSuffix);
            counters.put(fullName, metricComposer.getData(counterEntry.getValue()));
            return this;
        }

        private <T> DataAccumulator addSubGauge(MetricComposer<T,Object> metricComposer,
                                                Map.Entry<String, ? extends T> gaugeEntry) {
            String nameWithSuffix = metricComposer.getMetricNameWithSuffix(gaugeEntry.getKey());
            String fullName = prefix.map(p -> p + nameWithSuffix).orElse(nameWithSuffix);
            Object value = metricComposer.getData(gaugeEntry.getValue());
            if (value instanceof BigDecimal) {
                gauges.put(fullName, ((BigDecimal) value).doubleValue());
            } else if (value != null && value.getClass().isAssignableFrom(Double.class)
                    && !Double.isNaN((Double) value) && Double.isFinite((Double) value)) {
                gauges.put(fullName, (Double) value);
            }
            return this;
        }
    }
}
