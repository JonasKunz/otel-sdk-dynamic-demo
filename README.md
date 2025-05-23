# OpenTelemetry SDK Dynamic Demo

This project demonstrates a how to programmatically setup a minimal OpenTelemetry SDK with the capability of runtime-reconfiguration of exporters.
The demo uses the logging exporters, but those can be easily replaced with any others (e.g. OTLP).

## Features

- Setup a `GlobalOpenTelemetry` instance at the start of the program, which does nothing but buffering metrics
- In the background, it generates some spans and metrics (once per second)
- Later on, exporters can be configured via stdin
  - Because metrics are aggregated in-memory, no metric data is lost even with a delayed exporter registration
  - The same is possible for spans (e.g. buffering them in memory), but is not implemented in this demo

## Usage

### Building

This is a Maven-based Java project. To build:

```sh
./mvnw clean package
```

### Running

Run the main class (e.g., from your IDE or terminal):

```sh
java -jar target/otel-sdk-dynamic-demo-*.jar
```

### Configuring at Runtime

When the application starts, it will print available parameters that can be set via stdin:

```
Available parameters via stdin:
  spanBatchSize: integer, sets the batch size for span exporter (set to 0 to disable span export).
  metricsInterval: integer (ms), sets the metric export interval (set to 0 to disable metrics export).
In order to configure, type: varName=value (e.g., spanBatchSize=512)
```

Type your configuration commands into the console while the app is running. For example:

```
spanBatchSize=256
metricsInterval=10000
spanBatchSize=0     # disables span export
metricsInterval=0   # disables metrics export
```