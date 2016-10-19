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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.hawkular.client.http.HawkularHttpClient;
import org.hawkular.client.http.JdkHawkularHttpClient;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;

public class HawkularReporterBuilder {

    private static final String KEY_HEADER_TENANT = "Hawkular-Tenant";
    private static final String KEY_HEADER_AUTHORIZATION = "Authorization";

    private final MetricRegistry registry;
    private String uri = "http://localhost:8080";
    private Map<String, String> headers = new HashMap<>();
    private Optional<String> prefix = Optional.empty();
    private MetricFilter filter = MetricFilter.ALL;
    private TimeUnit rateUnit = TimeUnit.SECONDS;
    private TimeUnit durationUnit = TimeUnit.MILLISECONDS;
    private Optional<Function<String, HawkularHttpClient>> httpClientProvider = Optional.empty();

    public HawkularReporterBuilder(MetricRegistry registry, String tenant) {
        this.registry = registry;
        headers.put(KEY_HEADER_TENANT, tenant);
    }

    public HawkularReporterBuilder uri(String uri) {
        this.uri = uri;
        return this;
    }

    public HawkularReporterBuilder bearerToken(String token) {
        headers.put(KEY_HEADER_AUTHORIZATION, "Bearer " + token);
        return this;
    }

    public HawkularReporterBuilder addHeader(String key, String value) {
        headers.put(key, value);
        return this;
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

    public HawkularReporterBuilder convertRatesTo(TimeUnit rateUnit) {
        this.rateUnit = rateUnit;
        return this;
    }

    public HawkularReporterBuilder convertDurationsTo(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
        return this;
    }

    /**
     * Use a custom {@link HawkularHttpClient}
     * @param httpClientProvider function that provides a custom {@link HawkularHttpClient} from input URI as String
     */
    public HawkularReporterBuilder useHttpClient(Function<String, HawkularHttpClient> httpClientProvider) {
        this.httpClientProvider = Optional.of(httpClientProvider);
        return this;
    }

    public HawkularReporter build() {
        HawkularHttpClient client = httpClientProvider
                .map(provider -> provider.apply(uri))
                .orElseGet(() -> new JdkHawkularHttpClient(uri));
        client.addHeaders(headers);
        return new HawkularReporter(registry, client, prefix, rateUnit, durationUnit, filter);
    }
}
