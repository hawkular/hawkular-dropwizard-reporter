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

import static org.hawkular.metrics.dropwizard.MetricsTagger.METRIC_TYPE_COUNTER;
import static org.hawkular.metrics.dropwizard.MetricsTagger.METRIC_TYPE_GAUGE;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.codahale.metrics.Counting;
import com.codahale.metrics.Metered;
import com.codahale.metrics.Sampling;

/**
 * @author Joel Takvorian
 */
public final class MetricComposers {

    static final List<MetricComposer<Counting, Long>> COUNTINGS;
    static final List<MetricComposer<Metered, Object>> METERED;
    static final List<MetricComposer<Sampling, Object>> SAMPLING;

    static {
        COUNTINGS = new ArrayList<>(1);
        COUNTINGS.add(metricComposer(Counting::getCount, "count", METRIC_TYPE_COUNTER));
        METERED = new ArrayList<>(4);
        METERED.add(metricComposer(Metered::getOneMinuteRate, "1min", METRIC_TYPE_GAUGE));
        METERED.add(metricComposer(Metered::getFiveMinuteRate, "5min", METRIC_TYPE_GAUGE));
        METERED.add(metricComposer(Metered::getFifteenMinuteRate, "15min", METRIC_TYPE_GAUGE));
        METERED.add(metricComposer(Metered::getMeanRate, "mean", METRIC_TYPE_GAUGE));
        SAMPLING = new ArrayList<>(10);
        SAMPLING.add(metricComposer(s -> s.getSnapshot().getMin(), "min", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().getMax(), "max", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().getMean(), "mean", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().getMedian(), "median", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().getStdDev(), "stddev", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().get75thPercentile(), "75perc", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().get95thPercentile(), "95perc", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().get98thPercentile(), "98perc", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().get99thPercentile(), "99perc", METRIC_TYPE_GAUGE));
        SAMPLING.add(metricComposer(s -> s.getSnapshot().get999thPercentile(), "999perc", METRIC_TYPE_GAUGE));
    }

    private MetricComposers() {
    }

    private static <T,U> MetricComposer<T,U> metricComposer(Function<T,U> getter, String suffix, String type) {
        return new MetricComposer<T, U>() {
            @Override public U getData(T input) {
                return getter.apply(input);
            }

            @Override public String getSuffix() {
                return suffix;
            }

            @Override public String getMetricType() {
                return type;
            }
        };
    }
}
