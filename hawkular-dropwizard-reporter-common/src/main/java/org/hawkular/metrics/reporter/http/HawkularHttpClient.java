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
package org.hawkular.metrics.reporter.http;

import java.io.IOException;
import java.util.Map;

/**
 * Http client interface for Hawkular, in case someone would like to use other than the default one
 * @author Joel Takvorian
 */
public interface HawkularHttpClient {
    void addHeaders(Map<String, String> headers);
    HawkularHttpResponse postMetrics(String jsonBody) throws IOException;
    HawkularHttpResponse putTags(String resourcePath, String jsonBody) throws IOException;
}
