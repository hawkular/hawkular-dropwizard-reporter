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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.hawkular.metrics.reporter.http.HawkularHttpClient;
import org.hawkular.metrics.reporter.http.HawkularJson;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;

/**
 * @author Joel Takvorian
 */
class MetricsTagger implements MetricRegistryListener {

    static final String METRIC_TYPE_COUNTER = "counters";
    static final String METRIC_TYPE_GAUGE = "gauges";

    private final Optional<String> prefix;
    private final Map<String, String> globalTags;
    private final Map<String, Map<String, String>> perMetricTags;
    private final boolean enableAutoTagging;
    private final HawkularHttpClient hawkularClient;

    MetricsTagger(Optional<String> prefix, Map<String, String> globalTags, Map<String, Map<String, String>>
            perMetricTags, boolean enableAutoTagging, HawkularHttpClient hawkularClient, MetricRegistry registry) {
        this.prefix = prefix;
        this.globalTags = globalTags;
        this.perMetricTags = perMetricTags;
        this.enableAutoTagging = enableAutoTagging;
        this.hawkularClient = hawkularClient;

        // Initialize with existing metrics
        registry.getGauges().forEach(this::onGaugeAdded);
        registry.getCounters().forEach(this::onCounterAdded);
        registry.getHistograms().forEach(this::onHistogramAdded);
        registry.getTimers().forEach(this::onTimerAdded);
        registry.getMeters().forEach(this::onMeterAdded);

        registry.addListener(this);
    }

    private void tagMetric(String baseName, MetricComposer<?,?> metricComposer, String tagKey) {
        String nameWithSuffix = metricComposer.getMetricNameWithSuffix(baseName);
        String fullName = prefix.map(p -> p + nameWithSuffix).orElse(nameWithSuffix);
        Map<String, String> tags = new LinkedHashMap<>(globalTags);
        if (enableAutoTagging) {
            tags.put(tagKey, metricComposer.getSuffix());
        }
        // Don't use prefixed name for per-metric tagging
        if (perMetricTags.containsKey(baseName)) {
            tags.putAll(perMetricTags.get(baseName));
        }
        if (perMetricTags.containsKey(nameWithSuffix)) {
            tags.putAll(perMetricTags.get(nameWithSuffix));
        }
        if (!tags.isEmpty()) {
            try {
                hawkularClient.putTags("/" + metricComposer.getMetricType()
                        + "/" + fullName + "/tags", HawkularJson.tagsToString(tags));
            } catch (IOException e) {
                throw new RuntimeException("Could not tag metric " + baseName, e);
            }
        }
    }

    private void tagMetric(String metricType, String baseName) {
        String fullName = prefix.map(p -> p + baseName).orElse(baseName);
        Map<String, String> tags = new LinkedHashMap<>(globalTags);
        // Don't use prefixed name for per-metric tagging
        if (perMetricTags.containsKey(baseName)) {
            tags.putAll(perMetricTags.get(baseName));
        }
        if (!tags.isEmpty()) {
            try {
                hawkularClient.putTags("/" + metricType
                        + "/" + fullName + "/tags", HawkularJson.tagsToString(tags));
            } catch (IOException e) {
                throw new RuntimeException("Could not tag metric " + baseName, e);
            }
        }
    }

    @Override public void onGaugeAdded(String name, Gauge<?> gauge) {
        tagMetric(METRIC_TYPE_GAUGE, name);
    }

    @Override public void onGaugeRemoved(String name) {
    }

    @Override public void onCounterAdded(String name, Counter counter) {
        tagMetric(METRIC_TYPE_COUNTER, name);
    }

    @Override public void onCounterRemoved(String name) {
    }

    @Override public void onHistogramAdded(String name, Histogram histogram) {
        for (MetricComposer<?,?> metricComposer : MetricComposers.COUNTINGS) {
            tagMetric(name, metricComposer, "histogram");
        }
        for (MetricComposer<?,?> metricComposer : MetricComposers.SAMPLING) {
            tagMetric(name, metricComposer, "histogram");
        }
    }

    @Override public void onHistogramRemoved(String name) {
    }

    @Override public void onMeterAdded(String name, Meter meter) {
        for (MetricComposer<?,?> metricComposer : MetricComposers.COUNTINGS) {
            tagMetric(name, metricComposer, "meter");
        }
        for (MetricComposer<?,?> metricComposer : MetricComposers.METERED) {
            tagMetric(name, metricComposer, "meter");
        }
    }

    @Override public void onMeterRemoved(String name) {
    }

    @Override public void onTimerAdded(String name, Timer timer) {
        for (MetricComposer<?,?> metricComposer : MetricComposers.COUNTINGS) {
            tagMetric(name, metricComposer, "timer");
        }
        for (MetricComposer<?,?> metricComposer : MetricComposers.METERED) {
            tagMetric(name, metricComposer, "timer");
        }
        for (MetricComposer<?,?> metricComposer : MetricComposers.SAMPLING) {
            tagMetric(name, metricComposer, "timer");
        }
    }

    @Override public void onTimerRemoved(String name) {
    }

    Map<String, String> getGlobalTags() {
        return globalTags;
    }

    Map<String, Map<String, String>> getPerMetricTags() {
        return perMetricTags;
    }

    boolean isEnableAutoTagging() {
        return enableAutoTagging;
    }
}
