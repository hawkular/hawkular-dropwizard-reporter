# Hawkular Dropwizard Reporter

## Usage

To use the Hawkular Dropwizard Reporter in any Java application:

* Pre-requisite: you must have a running instance of Hawkular Services or Hawkular Metrics. For more information see http://www.hawkular.org/

* Add a dependency to artifact _org.hawkular.client:hawkular-dropwizard-reporter_. Example with maven:
````
    <dependency>
        <groupId>org.hawkular.client</groupId>
        <artifactId>hawkular-dropwizard-reporter</artifactId>
        <version>${version}</version>
    </dependency>
````

* At some point in your application (generally at startup), create a _MetricRegistry_ and a _HawkularReporter_

Example:
````
        MetricRegistry registry = new MetricRegistry();
        HawkularReporter reporter = HawkularReporter.builder(registry, "my-tenant")
                .uri("http://myserver:8081")
                .build();
        reporter.start(1, TimeUnit.SECONDS);

        Meter meter = registry.meter("my.meter");

        // Later on, as appropriate:
        meter.mark();
````

For more information about Dropwizard Metrics usage, please refer to the official documentation: http://metrics.dropwizard.io/

### Using another HTTP client

The embedded HTTP client is designed to be as light as possible in terms of JAR dependencies. So, no Apache, no
Jetty... just a basic JDK URLConnection.

If you want to use a different HTTP client, you would just have to implement the interface `org.hawkular.client.http
.HawkularHttpClient` and pass an instance to the builder:
````
        HawkularReporter reporter = HawkularReporter.builder(registry, "my-tenant")
                // ...
                .useHttpClient(uri -> new MyCoolHttpClient(uri))
                .build();
````

## Usage in a Dropwizard application

You can use the Hawkular Dropwizard Reporter in a Dropwizard application by simply editing your app .yml file and
configure a reporter of type _hawkular_. For example:
````
metrics:
  reporters:
    - type: hawkular
      uri: http://myserver:8081
      tenant: my-tenant
````

You must also add a module dependency to hawkular-dropwizard-reporter-factory (note that it is NOT the same module as
 above):
````
    <dependency>
        <groupId>org.hawkular.client</groupId>
        <artifactId>hawkular-dropwizard-reporter-factory</artifactId>
        <version>${version}</version>
    </dependency>
````

## Usage as an "addthis" plugin in Cassandra

* Copy and edit `sample/hawkular-cassandra-example.yml` to `[cassandra]/conf`
* Copy the shaded JAR artifact `hawkular-dropwizard-reporter-xxx-shaded.jar` to `[cassandra]/lib`
* Start Cassandra with `-Dcassandra.metricsReporterConfigFile=hawkular-cassandra-example.yml`

Note: this usage requires a version of the `reporter-config3` JAR, in Cassandra, that is not merged upstream yet. See
 fork here: https://github.com/jotak/metrics-reporter-config
