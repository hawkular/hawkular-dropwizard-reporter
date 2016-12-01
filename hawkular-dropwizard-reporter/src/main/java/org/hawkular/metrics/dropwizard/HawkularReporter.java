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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final Map<String, String> globalTags;
    private final Map<String, Map<String, String>> perMetricTags;
    private final Map<String, Long> taggedMetricsCache = new HashMap<>();
    private final long tagsCacheDuration;
    private final boolean enableAutoTagging;

    HawkularReporter(MetricRegistry registry, HawkularHttpClient hawkularClient, Optional<String> prefix, Map<String,
            String> globalTags, Map<String, Map<String, String>> perMetricTags, long tagsCacheDuration, boolean
            enableAutoTagging, TimeUnit rateUnit, TimeUnit durationUnit, MetricFilter filter) {
        super(registry, "hawkular-reporter", filter, rateUnit, durationUnit);

        this.prefix = prefix;
        this.clock = Clock.defaultClock();
        this.hawkularClient = hawkularClient;
        this.globalTags = globalTags;
        this.perMetricTags = perMetricTags;
        this.tagsCacheDuration = tagsCacheDuration;
        this.enableAutoTagging = enableAutoTagging;
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

        DataAccumulator accu = new DataAccumulator(timestamp);
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

        // Cache eviction
        evictFromCache(timestamp);
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
            processCounting(builder, e.getKey(), e.getValue(), "meter");
            processMetered(builder, e.getKey(), e.getValue(), "meter");
        }
    }

    private static void processHistograms(DataAccumulator builder, Map<String, Histogram> histograms) {
        for (Map.Entry<String, Histogram> e : histograms.entrySet()) {
            processCounting(builder, e.getKey(), e.getValue(), "histogram");
            processSampling(builder, e.getKey(), e.getValue(), "histogram");
        }
    }

    private static void processTimers(DataAccumulator builder, Map<String, Timer> timers) {
        for (Map.Entry<String, Timer> e : timers.entrySet()) {
            processCounting(builder, e.getKey(), e.getValue(), "timer");
            processMetered(builder, e.getKey(), e.getValue(), "timer");
            processSampling(builder, e.getKey(), e.getValue(), "timer");
        }
    }

    private static void processCounting(DataAccumulator builder, String name, Counting counting, String subKey) {
        builder.addSubCounter(name, counting.getCount(), subKey, "count");
    }

    private static void processMetered(DataAccumulator builder, String name, Metered metered, String subKey) {
        builder.addSubGauge(name, metered.getOneMinuteRate(), subKey, "1min")
                .addSubGauge(name, metered.getFiveMinuteRate(), subKey, "5min")
                .addSubGauge(name, metered.getFifteenMinuteRate(), subKey, "15min")
                .addSubGauge(name, metered.getMeanRate(), subKey, "mean");
    }

    private static void processSampling(DataAccumulator builder, String name, Sampling sampling, String subKey) {
        builder.addSubGauge(name, sampling.getSnapshot().getMin(), subKey, "min")
                .addSubGauge(name, sampling.getSnapshot().getMax(), subKey, "max")
                .addSubGauge(name, sampling.getSnapshot().getMean(), subKey, "mean")
                .addSubGauge(name, sampling.getSnapshot().getMedian(), subKey, "median")
                .addSubGauge(name, sampling.getSnapshot().getStdDev(), subKey, "stddev")
                .addSubGauge(name, sampling.getSnapshot().get75thPercentile(), subKey, "75perc")
                .addSubGauge(name, sampling.getSnapshot().get95thPercentile(), subKey, "95perc")
                .addSubGauge(name, sampling.getSnapshot().get98thPercentile(), subKey, "98perc")
                .addSubGauge(name, sampling.getSnapshot().get99thPercentile(), subKey, "99perc")
                .addSubGauge(name, sampling.getSnapshot().get999thPercentile(), subKey, "999perc");
    }

    private void evictFromCache(long now) {
        long evictingThreshold = now - tagsCacheDuration;
        taggedMetricsCache.entrySet().removeIf(entry -> entry.getValue() < evictingThreshold);
    }

    public Optional<String> getPrefix() {
        return prefix;
    }

    public Map<String, String> getGlobalTags() {
        return globalTags;
    }

    public HawkularHttpClient getHawkularClient() {
        return hawkularClient;
    }

    public Map<String, Map<String, String>> getPerMetricTags() {
        return perMetricTags;
    }

    public Map<String, Long> getTaggedMetricsCache() {
        return taggedMetricsCache;
    }

    public long getTagsCacheDuration() {
        return tagsCacheDuration;
    }

    public boolean isEnableAutoTagging() {
        return enableAutoTagging;
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
        private final long timestamp;
        private Map<String, Double> gauges = new HashMap<>();
        private Map<String, Long> counters = new HashMap<>();
        private Map<String, Map<String, String>> gaugesTags = new HashMap<>();
        private Map<String, Map<String, String>> countersTags = new HashMap<>();

        private DataAccumulator(long timestamp) {
            this.timestamp = timestamp;
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
            addTags(countersTags, fullName, name, new HashMap<>());
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
            addTags(gaugesTags, fullName, name, new HashMap<>());
            return this;
        }

        private DataAccumulator addSubCounter(String baseName, long l, String subKey, String subValue) {
            String name = baseName + "." + subValue;
            String fullName = prefix.map(p -> p + name).orElse(name);
            counters.put(fullName, l);
            addTags(countersTags, fullName, name, Collections.singletonMap(subKey, subValue));
            return this;
        }

        private DataAccumulator addSubGauge(String baseName, Object value, String subKey, String subValue) {
            String name = baseName + "." + subValue;
            String fullName = prefix.map(p -> p + name).orElse(name);
            if (value instanceof BigDecimal) {
                gauges.put(fullName, ((BigDecimal) value).doubleValue());
            } else if (value != null && value.getClass().isAssignableFrom(Double.class)
                    && !Double.isNaN((Double) value) && Double.isFinite((Double) value)) {
                gauges.put(fullName, (Double) value);
            }
            addTags(gaugesTags, fullName, name, Collections.singletonMap(subKey, subValue));
            return this;
        }

        private void addTags(Map<String, Map<String, String>> accuMap, String fullName, String shortName, Map<String,
                String> autoTags) {
            // Check tags; don't tag a metric that has already been tagged
            if (!taggedMetricsCache.containsKey(fullName)) {
                taggedMetricsCache.put(fullName, timestamp);
                Map<String, String> metricTags = new LinkedHashMap<>(globalTags);
                if (enableAutoTagging) {
                    metricTags.putAll(autoTags);
                }
                // Don't use prefixed name for per-metric tagging
                if (perMetricTags.containsKey(shortName)) {
                    metricTags.putAll(perMetricTags.get(shortName));
                }
                if (!metricTags.isEmpty()) {
                    accuMap.put(fullName, metricTags);
                }
            }
        }
    }
}
