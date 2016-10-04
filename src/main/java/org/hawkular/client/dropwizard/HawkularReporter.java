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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.hawkular.client.core.HawkularClient;
import org.hawkular.metrics.model.DataPoint;
import org.hawkular.metrics.model.MetricId;
import org.hawkular.metrics.model.MetricType;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;

/**
 * TODO: Manage notifiers
 */
public class HawkularReporter extends ScheduledReporter {

    private final Optional<String> prefix;
    private final Clock clock;
    @VisibleForTesting
    final HawkularClient hawkularClient;
    private final Integer dataRetention;
    private final Function<Meter, Double> metersRate;

    HawkularReporter(MetricRegistry registry, HawkularClient hawkularClient, Optional<String> prefix,
                             MetricFilter filter, Integer dataRetention, MetersRate metersRate) {
        super(registry, "hawkular-reporter", filter, TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS);

        this.prefix = prefix;
        this.clock = Clock.defaultClock();
        this.hawkularClient = hawkularClient;
        this.dataRetention = dataRetention;
        switch (metersRate) {
            case ONE_MINUTE:
                this.metersRate = Meter::getOneMinuteRate;
                break;
            case FIVE_MINUTES:
                this.metersRate = Meter::getFiveMinuteRate;
                break;
            case FIFTEEN_MINUTES:
                this.metersRate = Meter::getFifteenMinuteRate;
                break;
            case MEAN:
            default:
                this.metersRate = Meter::getMeanRate;
                break;
        }
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

        List<org.hawkular.metrics.model.Metric<Double>> gaugesData = gauges.entrySet().stream()
                .filter(e -> e.getValue().getValue() != null
                        && e.getValue().getValue().getClass().isAssignableFrom(Double.class))
                .map(e -> toHawkularMetric(MetricType.GAUGE, e.getKey(), timestamp, (Double) e.getValue().getValue()))
                .collect(toList());
        if (!gaugesData.isEmpty()) {
            hawkularClient.metrics().gauge().addGaugeData(gaugesData);
        }

        List<org.hawkular.metrics.model.Metric<Long>> countersData = counters.entrySet().stream()
                .map(e -> toHawkularMetric(MetricType.COUNTER, e.getKey(), timestamp, e.getValue().getCount()))
                .collect(toList());
        if (!countersData.isEmpty()) {
            hawkularClient.metrics().counter().addCounterData(countersData);
        }

        List<org.hawkular.metrics.model.Metric<Double>> meterData = meters.entrySet().stream()
                .map(e -> toHawkularMetric(MetricType.GAUGE, e.getKey(), timestamp, metersRate.apply(e.getValue())))
                .collect(toList());
        if (!meterData.isEmpty()) {
            hawkularClient.metrics().gauge().addGaugeData(meterData);
        }
    }

    private <T> org.hawkular.metrics.model.Metric<T> toHawkularMetric(MetricType<T> metricType, String name, long
            timestamp, T value) {
        String prefixedName = prefix.map(p -> p + name).orElse(name);
        MetricId<T> id = new MetricId<>(hawkularClient.getTenant(), metricType, prefixedName);
        return new org.hawkular.metrics.model.Metric<>(id, Collections.singletonList(new DataPoint<>(timestamp,
                value)), dataRetention);
    }

    public static HawkularReporterBuilder builder(MetricRegistry registry, String tenant) {
        return new HawkularReporterBuilder(registry, tenant);
    }

}
