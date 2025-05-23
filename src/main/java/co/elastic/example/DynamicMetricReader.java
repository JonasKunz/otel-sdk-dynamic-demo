package co.elastic.example;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricReader;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class DynamicMetricReader implements MetricReader {

    private MetricReader delegate;
    private final AggregationTemporalitySelector temporalitySelector;
    private CollectionRegistration collectionRegistration;

    public DynamicMetricReader(AggregationTemporalitySelector temporalitySelector) {
        this.temporalitySelector = Objects.requireNonNull(temporalitySelector, "temporalitySelector must not be null");
    }

    public synchronized void setDelegate(MetricReader newDelegate) {
        if (delegate == newDelegate) {
            return;
        }
        if (delegate != null) {
            delegate.shutdown().join(10, TimeUnit.SECONDS);
        }
        this.delegate = newDelegate;
        if (newDelegate != null && collectionRegistration != null) {
            newDelegate.register(collectionRegistration);
        }
    }


    @Override
    public synchronized AggregationTemporality getAggregationTemporality(InstrumentType instrumentType) {
        return temporalitySelector.getAggregationTemporality(instrumentType);
    }

    @Override
    public synchronized void register(CollectionRegistration collectionRegistration) {
        this.collectionRegistration = collectionRegistration;
        if (delegate != null) {
            delegate.register(collectionRegistration);
        }
    }

    @Override
    public synchronized CompletableResultCode forceFlush() {
        return delegate != null 
                ? delegate.forceFlush() 
                : CompletableResultCode.ofSuccess();
    }

    @Override
    public synchronized CompletableResultCode shutdown() {
        return delegate != null 
                ? delegate.shutdown() 
                : CompletableResultCode.ofSuccess();
    }
}