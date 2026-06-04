package com.tradej.core.tracing;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight OpenTelemetry span factory for the hot path.
 *
 * <p>This implementation intentionally avoids a mandatory OpenTelemetry
 * dependency. When tracing is disabled the factory is a no-op; when enabled
 * it delegates to a plugged-in supplier so that the hot path stays free of
 * container specific startup logic.
 */
public final class SpanFactory {

 private static final AtomicReference<Supplier> SUPPLIER = new AtomicReference<>(noop());

 public static Supplier supplier() {
  return SUPPLIER.get();
 }

 public static void register(Supplier supplier) {
  SUPPLIER.compareAndSet(noop(), supplier);
 }

 private static Supplier noop() {
  return () -> NoOpSpan.INSTANCE;
 }

 private SpanFactory() {}

 @FunctionalInterface
 public interface Supplier {

  Span start();
 }

 public interface Span extends AutoCloseable {

  String spanName();

  void setAttribute(String key, String value);

  void setAttribute(String key, long value);

  void recordException(Throwable throwable);

  default void setAttribute(String key, boolean value) {
   setAttribute(key, Boolean.toString(value));
  }

  @Override
  void close();
 }

 public enum NoOpSpan implements Span {
  INSTANCE;

  @Override
  public String spanName() {
   return "noop";
  }

  @Override
  public void setAttribute(String key, String value) {
   // no-op
  }

  @Override
  public void setAttribute(String key, long value) {
   // no-op
  }

  @Override
  public void recordException(Throwable throwable) {
   // no-op
  }

  @Override
  public void close() {
   // no-op
  }
 }
}
