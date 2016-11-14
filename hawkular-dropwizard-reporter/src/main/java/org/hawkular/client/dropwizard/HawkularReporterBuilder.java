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

import java.util.Base64;
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
    private final Map<String, String> globalTags = new HashMap<>();
    private final Map<String, Map<String, String>> perMetricTags = new HashMap<>();
    private long tagsCacheDuration = 1000 * 60 * 10; // In milliseconds; default: 10min
    private boolean enableAutoTagging = true;

    /**
     * Create a new builder for an {@link HawkularReporter}
     * @param registry the Dropwizard Metrics registry
     * @param tenant the Hawkular tenant ID
     */
    public HawkularReporterBuilder(MetricRegistry registry, String tenant) {
        this.registry = registry;
        headers.put(KEY_HEADER_TENANT, tenant);
    }

    /**
     * This is a shortcut function to use with automatically populated pojos such as coming from yaml config
     */
    public HawkularReporterBuilder withNullableConfig(HawkularReporterNullableConfig config) {
        if (config.getUri() != null) {
            this.uri(config.getUri());
        }
        if (config.getPrefix() != null) {
            this.prefixedWith(config.getPrefix());
        }
        if (config.getBearerToken() != null) {
            this.bearerToken(config.getBearerToken());
        }
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(this::addHeader);
        }
        if (config.getGlobalTags() != null) {
            this.globalTags(config.getGlobalTags());
        }
        if (config.getPerMetricTags() != null) {
            this.perMetricTags(config.getPerMetricTags());
        }
        if (config.getTagsCacheDuration() != null) {
            this.tagsCacheDuration(config.getTagsCacheDuration());
        }
        if (config.getAutoTagging() != null && !config.getAutoTagging()) {
            this.disableAutoTagging();
        }
        if (config.getUsername() != null && config.getPassword() != null) {
            this.basicAuth(config.getUsername(), config.getPassword());
        }
        return this;
    }

    /**
     * Set the URI for the Hawkular connection. Default URI is http://localhost:8080
     * @param uri base uri - do not include Hawkular Metrics path (/hawkular/metrics)
     */
    public HawkularReporterBuilder uri(String uri) {
        this.uri = uri;
        return this;
    }

    /**
     * Set username and password for basic HTTP authentication
     * @param username basic auth. username
     * @param password basic auth. password
     */
    public HawkularReporterBuilder basicAuth(String username, String password) {
        String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        headers.put(KEY_HEADER_AUTHORIZATION, "Basic " + encoded);
        return this;
    }

    /**
     * Set the bearer token for the Authorization header in Hawkular HTTP connections. Can be used, for instance, for
     * OpenShift connections
     * @param token the bearer token
     */
    public HawkularReporterBuilder bearerToken(String token) {
        headers.put(KEY_HEADER_AUTHORIZATION, "Bearer " + token);
        return this;
    }

    /**
     * Add a custom header to Hawkular HTTP connections
     * @param key header name
     * @param value header value
     */
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

    /**
     * Set dropwizard rates conversion
     */
    public HawkularReporterBuilder convertRatesTo(TimeUnit rateUnit) {
        this.rateUnit = rateUnit;
        return this;
    }

    /**
     * Set dropwizard duration conversion
     */
    public HawkularReporterBuilder convertDurationsTo(TimeUnit durationUnit) {
        this.durationUnit = durationUnit;
        return this;
    }

    /**
     * Set all global tags at once. All metrics generated by this reporter instance will be tagged as such.
     * It overrides any global tag that was already set.
     * @param tags global tags
     */
    public HawkularReporterBuilder globalTags(Map<String, String> tags) {
        this.globalTags.clear();
        this.globalTags.putAll(tags);
        return this;
    }

    /**
     * Set a global tag. All metrics generated by this reporter instance will be tagged as such.
     * @param key tag key
     * @param value tag value
     */
    public HawkularReporterBuilder addGlobalTag(String key, String value) {
        this.globalTags.put(key, value);
        return this;
    }

    /**
     * Set all per-metric tags at once. It overrides any per-metric tag that was already set.
     * @param tags per-metric tags
     */
    public HawkularReporterBuilder perMetricTags(Map<String, Map<String, String>> tags) {
        this.perMetricTags.clear();
        this.perMetricTags.putAll(tags);
        return this;
    }

    /**
     * Set a tag on a given metric name
     * @param metric the metric name
     * @param key tag key
     * @param value tag value
     */
    public HawkularReporterBuilder addMetricTag(String metric, String key, String value) {
        final Map<String, String> tags;
        if (perMetricTags.containsKey(metric)) {
            tags = perMetricTags.get(metric);
        } else {
            tags = new HashMap<>();
            perMetricTags.put(metric, tags);
        }
        tags.put(key, value);
        return this;
    }

    /**
     * Disable auto-tagging. By default, it is enabled.<br/>
     * When enabled, some metric types such as Meters or Timers will automatically generate additional information as
     * tags. For instance, a Meter metric will generate a tag "meter:5min" on its 5-minutes-rate component.
     */
    public HawkularReporterBuilder disableAutoTagging() {
        enableAutoTagging = false;
        return this;
    }

    /**
     * Set the eviction duration of tags cache (in milliseconds)<br/>
     * This cache is used to prevent the reporter from tagging the metrics when they were already tagged<br/>
     * Default duration is 10 minutes
     * @param milliseconds number of milliseconds before eviction
     */
    public HawkularReporterBuilder tagsCacheDuration(long milliseconds) {
        tagsCacheDuration = milliseconds;
        return this;
    }

    /**
     * Set the eviction duration of tags cache, in minutes<br/>
     * This cache is used to prevent the reporter from tagging the metrics when they were already tagged<br/>
     * Default duration is 10 minutes
     * @param minutes number of minutes before eviction
     */
    public HawkularReporterBuilder tagsCacheDurationInMinutes(long minutes) {
        tagsCacheDuration = TimeUnit.MILLISECONDS.convert(minutes, TimeUnit.MINUTES);
        return this;
    }

    /**
     * Set the eviction duration of tags cache, in hours<br/>
     * This cache is used to prevent the reporter from tagging the metrics when they were already tagged<br/>
     * Default duration is 10 minutes
     * @param hours number of hours before eviction
     */
    public HawkularReporterBuilder tagsCacheDurationInHours(long hours) {
        tagsCacheDuration = TimeUnit.MILLISECONDS.convert(hours, TimeUnit.HOURS);
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

    /**
     * Build the {@link HawkularReporter}
     */
    public HawkularReporter build() {
        HawkularHttpClient client = httpClientProvider
                .map(provider -> provider.apply(uri))
                .orElseGet(() -> new JdkHawkularHttpClient(uri));
        client.addHeaders(headers);
        return new HawkularReporter(registry, client, prefix, globalTags, perMetricTags, tagsCacheDuration,
                enableAutoTagging, rateUnit, durationUnit, filter);
    }
}
