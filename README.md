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

* At some point in your application (generally at startup), create a _DropwizardRegistry_ and a _HawkularReporter_

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

For more information about Dropwizard Metrics usage, please refer to the official documentation: http://metrics
.dropwizard.io/
