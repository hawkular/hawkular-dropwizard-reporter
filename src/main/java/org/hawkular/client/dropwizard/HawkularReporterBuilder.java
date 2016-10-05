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

import java.util.Optional;
import java.util.function.Consumer;

import org.hawkular.client.core.HawkularClient;
import org.hawkular.client.core.HawkularClientBuilder;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;

public class HawkularReporterBuilder {

    private final MetricRegistry registry;
    private final HawkularClientBuilder hawkularClientBuilder;
    private Optional<String> prefix = Optional.empty();
    private MetricFilter filter = MetricFilter.ALL;
    private Integer dataRetention = null;
    private MetersRate metersRate = MetersRate.ONE_MINUTE;

    public HawkularReporterBuilder(MetricRegistry registry, String tenant) {
        this.registry = registry;
        this.hawkularClientBuilder = HawkularClient.builder(tenant);
    }

    /**
     * Configure a prefix for each metric name. Optional, but useful to identify single hosts
     */
    public HawkularReporterBuilder prefixedWith(String prefix) {
        this.prefix = Optional.of(prefix);
        return this;
    }

    /**
     * Configure a special MetricFilter, which defines what metrics are reported
     */
    public HawkularReporterBuilder filter(MetricFilter filter) {
        this.filter = filter;
        return this;
    }

    /**
     * Configure data retention
     */
    public HawkularReporterBuilder dataRetention(Integer dataRetention) {
        this.dataRetention = dataRetention;
        return this;
    }

    /**
     * Configure meters rate
     */
    public HawkularReporterBuilder metersRate(MetersRate metersRate) {
        this.metersRate = metersRate;
        return this;
    }

    /**
     * Configure the HawkularClient
     */
    public HawkularReporterBuilder hawkularInfo(Consumer<HawkularClientBuilder> hawkularClientBuilderConsumer) {
        hawkularClientBuilderConsumer.accept(hawkularClientBuilder);
        return this;
    }

    public HawkularReporter build() {
        return new HawkularReporter(registry, hawkularClientBuilder.build(), prefix, filter, dataRetention,
                metersRate);
    }
}
