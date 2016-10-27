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
package org.hawkular.client.http;

import java.io.IOException;
import java.util.Map;

/**
 * Persister interface for Hawkular Metrics (Could be Http client, or straightly itnegrated hawkular service)
 * @author Joel Takvorian
 */
public interface HawkularMetricsPersister {
    void writeGaugesData(Long timestamp, Map<String, Double> metricsPoints) throws IOException;
    void writeCountersData(Long timestamp, Map<String, Long> metricsPoints) throws IOException;
    void writeGaugesTags(String metricId, Map<String, String> tags) throws IOException;
    void writeCountersTags(String metricId, Map<String, String> tags) throws IOException;
    void addProperties(Map<String, String> properties);
}
