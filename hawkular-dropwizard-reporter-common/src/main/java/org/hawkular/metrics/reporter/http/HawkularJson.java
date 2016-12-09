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
import java.util.function.BiFunction;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/**
 * @author Joel Takvorian
 */
public final class HawkularJson {

    private HawkularJson() {
    }

    private static JsonObject doubleDataPoint(long timestamp, double value) {
        return Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("value", value)
                .build();
    }

    private static JsonObject longDataPoint(long timestamp, long value) {
        return Json.createObjectBuilder()
                .add("timestamp", timestamp)
                .add("value", value)
                .build();
    }

    private static JsonObject metricJson(String name, JsonArray dataPoints) {
        return Json.createObjectBuilder()
                .add("id", name)
                .add("dataPoints", dataPoints)
                .build();
    }

    private static JsonObject metricJson(String name, JsonObject dataPoint) {
        return metricJson(name, Json.createArrayBuilder().add(dataPoint).build());
    }

    public static String gaugesToString(Long timestamp, Map<String, Double> metricsPoints) {
        return metricsToString(timestamp, metricsPoints, HawkularJson::doubleDataPoint);
    }

    public static String countersToString(Long timestamp, Map<String, Long> metricsPoints) {
        return metricsToString(timestamp, metricsPoints, HawkularJson::longDataPoint);
    }

    public static String tagsToString(Map<String, String> tags) throws IOException {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        tags.forEach(jsonObjectBuilder::add);
        return jsonObjectBuilder.build().toString();
    }

    private static <T> String metricsToString(Long timestamp, Map<String, T> metricsPoints, BiFunction<Long, T, JsonObject> bf) {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        metricsPoints.entrySet().stream()
                .map(e -> HawkularJson.metricJson(e.getKey(), bf.apply(timestamp, e.getValue())))
                .forEach(builder::add);
        return builder.build().toString();
    }
}
