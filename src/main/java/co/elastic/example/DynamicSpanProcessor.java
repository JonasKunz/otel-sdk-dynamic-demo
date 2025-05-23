package co.elastic.example;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.context.Context;

import java.util.concurrent.TimeUnit;

public class DynamicSpanProcessor implements SpanProcessor {

    private SpanProcessor delegate;

    public synchronized void setDelegate(SpanProcessor newDelegate) {
        if (newDelegate == delegate) {
            return;
        }
        if (newDelegate != null) {
            if (newDelegate.isStartRequired()) {
                throw new IllegalArgumentException("Delegate must not require start callback");
            }
            if (!newDelegate.isEndRequired()) {
                throw new IllegalArgumentException("Delegate must require end callback");
            }
        }
        if (this.delegate != null) {
            this.delegate.shutdown().join(10, TimeUnit.SECONDS);
        }
        this.delegate = newDelegate;
    }

    @Override
    public synchronized void onEnd(ReadableSpan span) {
        // TODO: don't synchronize this method, instead use a queue
        // This method is called synchronously from application code, so we must not impose any latency here
        // especially with the setDelegate waiting on the delegate to be shut-down
        if (this.delegate != null) {
            delegate.onEnd(span);
        }
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // No-op: only delegates that do not require start are supported
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public synchronized boolean isEndRequired() {
        return true;
    }

    @Override
    public synchronized CompletableResultCode shutdown() {
        if (this.delegate != null) {
            return this.delegate.shutdown();
        }
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public synchronized CompletableResultCode forceFlush() {
        if (this.delegate != null) {
            return this.delegate.shutdown();
        }
        return CompletableResultCode.ofSuccess();
    }
}