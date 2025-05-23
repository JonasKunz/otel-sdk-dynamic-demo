package co.elastic.example;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;

public class Main {

    private static DynamicMetricReader dynamicMetricReader;
    private static DynamicSpanProcessor dynamicSpanProcessor;

    public static void main(String[] args) throws InterruptedException {
        // Set up the global OpenTelemetry SDK
        setupOpenTelemetry();

        // Simulate processing requests in a loop
        generateMetricsAndSpans();

        readUserInput();
    }

    private static void generateMetricsAndSpans() {

        Tracer tracer = GlobalOpenTelemetry.getTracer("demo-tracer");
        Meter meter = GlobalOpenTelemetry.getMeter("demo-meter");

        // Create a counter metric
        LongCounter requestCounter = meter
                .counterBuilder("requests")
                .setDescription("Counts the number of requests processed")
                .setUnit("1")
                .build();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(
                () -> fakeRequest(tracer, requestCounter),
                0, 1, TimeUnit.SECONDS);
    }

    private static void readUserInput() {
        System.out.println("Available parameters via stdin:\n" +
            "  spanBatchSize: integer, sets the batch size for span exporter (set to 0 to disable span export).\n" +
            "  metricsInterval: integer (ms), sets the metric export interval (set to 0 to disable metrics export).\n" +
            "In order to configure, type: varName=value (e.g., spanBatchSize=512)\n");
        Pattern p = Pattern.compile("^(?<name>\\w+)=(?<value>.+)$");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            Matcher m = p.matcher(line);
            if (m.matches()) {
                String varName = m.group("name");
                String value = m.group("value");
                switch (varName) {
                    case "spanBatchSize":
                        setSpanExporterBatchSize(Integer.parseInt(value));
                        break;
                    case "metricsInterval":
                        setMetricIntervalMillis(Integer.parseInt(value));
                        break;
                    default:
                        System.out.println("Warning: ignoring unknown variable " + varName);
                        break;
                }
            } else {
                System.out.println("Error: expected input format is 'varName=value', got '" + line + "'");
            }
        }
    }

    private static void setMetricIntervalMillis(int millis) {
        // For demo purposes we use a logging exporter, but it could also be a OTLP exporter which can be replaced dynamically
        if (millis <= 0) {
            System.out.println("Disabling metrics export");
            dynamicMetricReader.setDelegate(null);
        } else {
            System.out.println("Enabling metrics at an interval of "+millis+"ms");
            dynamicMetricReader.setDelegate(
                    PeriodicMetricReader.builder(LoggingMetricExporter.create())
                            .setInterval(millis, TimeUnit.MILLISECONDS)
                            .build()
            );
        }
    }

    private static void setSpanExporterBatchSize(int batchSize) {
        if (batchSize <= 0) {
            System.out.println("Disabling span export");
            dynamicSpanProcessor.setDelegate(null);
        } else {
            System.out.println("Enabling span export with a batch size of "+batchSize);
            // For demo purposes we use a logging exporter, but it could also be a OTLP exporter which can be replaced dynamically
            dynamicSpanProcessor.setDelegate(
                    BatchSpanProcessor.builder(LoggingSpanExporter.create())
                            .setMaxExportBatchSize(batchSize)
                            .build()
            );
        }
    }

    private static void setupOpenTelemetry() {
        // Create a resource with service metadata
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        SERVICE_NAME, "otel-sdk-demo",
                        SERVICE_VERSION, "1.0.0"
                )));

        dynamicSpanProcessor = new DynamicSpanProcessor();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(dynamicSpanProcessor)
                .build();

        // Note that the temporality cannot be changed dynamically
        // You'll have to decide at startup whether you want cumulative or delta
        dynamicMetricReader = new DynamicMetricReader(AggregationTemporalitySelector.deltaPreferred());
        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(dynamicMetricReader)
                .build();

        // Build the OpenTelemetry SDK and set it as the global instance
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal();

        // Add a shutdown hook to properly close the SDK
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down OpenTelemetry");
            sdk.close();
        }));
    }

    private static void fakeRequest(Tracer tracer, LongCounter requestCounter) {
        // Start a new trace
        Span parentSpan = tracer.spanBuilder("processRequest")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("request.id", "foo-bar")
                .startSpan();

        try (Scope scope = parentSpan.makeCurrent()) {
            // Record the request in our counter metric
            requestCounter.add(1);
            TimeUnit.SECONDS.sleep(1);
            parentSpan.setStatus(StatusCode.OK);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            parentSpan.end();
        }
    }

}