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

import javax.validation.constraints.NotNull;

import org.hibernate.validator.constraints.NotEmpty;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

import io.dropwizard.metrics.BaseReporterFactory;

/**
 * @author Joel Takvorian
 */
@JsonTypeName("hawkular")
public class HawkularReporterFactory extends BaseReporterFactory {
    @NotEmpty
    private String uri = "http://localhost:8080";
    @NotNull
    private String tenant;
    private String prefix;
    private String username;
    private String password;
    private String bearerToken;

    public HawkularReporterFactory() {
    }

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

    @JsonProperty
    public String getBearerToken() {
        return bearerToken;
    }

    @JsonProperty
    public void setBearerToken(String bearerToken) {
        this.bearerToken = bearerToken;
    }

    @JsonProperty
    public String getPrefix() {
        return this.prefix;
    }

    @JsonProperty
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public ScheduledReporter build(MetricRegistry registry) {
        HawkularReporterBuilder builder = HawkularReporter.builder(registry, tenant)
                .filter(this.getFilter())
                .convertRatesTo(this.getRateUnit())
                .convertDurationsTo(this.getDurationUnit())
                .uri(uri);
        if (prefix != null) {
            builder.prefixedWith(prefix);
        }
        if (username != null && password != null) {
            // TODO: manage basic auth
//            builder.basicAuth(username, password);
        }
        if (bearerToken != null) {
            builder.bearerToken(bearerToken);
        }
        return builder.build();
    }
}
