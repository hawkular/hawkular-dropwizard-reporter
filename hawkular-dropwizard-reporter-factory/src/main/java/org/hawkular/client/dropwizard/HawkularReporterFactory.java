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

import java.util.Map;

import javax.validation.constraints.NotNull;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import io.dropwizard.metrics.BaseReporterFactory;

/**
 * @author Joel Takvorian
 */
@JsonTypeName("hawkular")
public class HawkularReporterFactory extends BaseReporterFactory implements HawkularReporterNullableConfig {
    @NotNull
    private String tenant;
    private String uri;
    private String prefix;
    private String username;
    private String password;
    private String bearerToken;
    private Map<String, String> headers;
    private Map<String, String> globalTags;
    private Map<String, Map<String, String>> perMetricTags;
    private Long tagsCacheDuration;
    private Boolean autoTagging;

    public HawkularReporterFactory() {
    }

    @Override
    @JsonProperty
    public String getUri() {
        return uri;
    }

    @JsonProperty
    public void setUri(String uri) {
        this.uri = uri;
    }

    @JsonProperty
    public String getTenant() {
        return tenant;
    }

    @JsonProperty
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    @JsonProperty
    public String getUsername() {
        return username;
    }

    @JsonProperty
    public void setUsername(String username) {
        this.username = username;
    }

    @JsonProperty
    public String getPassword() {
        return password;
    }

    @JsonProperty
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    @JsonProperty
    public String getBearerToken() {
        return bearerToken;
    }

    @JsonProperty
    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    @Override
    @JsonProperty
    public String getPrefix() {
        return this.prefix;
    }

    @JsonProperty
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    @JsonProperty
    public Map<String, String> getHeaders() {
        return headers;
    }

    @JsonProperty
    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    @Override
    @JsonProperty
    public Map<String, String> getGlobalTags() {
        return globalTags;
    }

    @JsonProperty
    public void setGlobalTags(Map<String, String> globalTags) {
        this.globalTags = globalTags;
    }

    @Override
    @JsonProperty
    public Map<String, Map<String, String>> getPerMetricTags() {
        return perMetricTags;
    }

    @JsonProperty
    public void setPerMetricTags(
            Map<String, Map<String, String>> perMetricTags) {
        this.perMetricTags = perMetricTags;
    }

    @Override
    @JsonProperty
    public Long getTagsCacheDuration() {
        return tagsCacheDuration;
    }

    @JsonProperty
    public void setTagsCacheDuration(Long tagsCacheDuration) {
        this.tagsCacheDuration = tagsCacheDuration;
    }

    @Override
    @JsonProperty
    public Boolean getAutoTagging() {
        return autoTagging;
    }

    @JsonProperty
    public void setAutoTagging(Boolean autoTagging) {
        this.autoTagging = autoTagging;
    }

    @Override
    public ScheduledReporter build(MetricRegistry registry) {
        return HawkularReporter.builder(registry, tenant)
                .withNullableConfig(this)
                .filter(this.getFilter())
                .convertRatesTo(this.getRateUnit())
                .convertDurationsTo(this.getDurationUnit())
                .build();
    }
}
