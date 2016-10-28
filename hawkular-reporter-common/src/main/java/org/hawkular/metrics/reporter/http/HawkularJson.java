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

import java.math.BigDecimal;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

/**
 * @author Joel Takvorian
 */
public final class HawkularJson {

    private HawkularJson() {
    }

    public static JsonObject bigdDataPoint(long timestamp, BigDecimal value) {
        return Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("value", value)
                .build();
    }

    public static JsonObject doubleDataPoint(long timestamp, double value) {
        return Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("value", value)
                .build();
    }

    public static JsonObject longDataPoint(long timestamp, long value) {
        return Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("value", value)
                .build();
    }

    public static JsonObject metricJson(String name, JsonArray dataPoints) {
        return Json.createObjectBuilder()
                .add("id", name)
                .add("dataPoints", dataPoints)
                .build();
    }

    public static JsonObject metricJson(String name, JsonObject dataPoint) {
        return metricJson(name, Json.createArrayBuilder().add(dataPoint).build());
    }
}
