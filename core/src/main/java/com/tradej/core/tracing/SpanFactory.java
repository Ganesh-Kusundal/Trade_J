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
 private static final AtomicReference<NamedSupplier> NAMED_SUPPLIER = new AtomicReference<>(null);

 public static Supplier supplier() {
  return SUPPLIER.get();
 }

 public static void register(Supplier supplier) {
  SUPPLIER.compareAndSet(noop(), supplier);
 }

 /**
  * Register a named span supplier that creates spans with a specific operation name.
  * Used for tracing broker calls, event processing, and other named operations.
  */
 public static void registerNamed(NamedSupplier supplier) {
  NAMED_SUPPLIER.set(supplier);
 }

 /**
  * Start a named span. Falls back to the default supplier if no named supplier is registered.
  *
  * @param name the operation name (e.g. "broker.quote", "event.publish")
  * @return a Span that should be closed when the operation completes
  */
 public static Span startSpan(String name) {
  NamedSupplier ns = NAMED_SUPPLIER.get();
  if (ns != null) {
   return ns.start(name);
  }
  return SUPPLIER.get().start();
 }

 private static Supplier noop() {
  return () -> NoOpSpan.INSTANCE;
 }

 private SpanFactory() {}

 @FunctionalInterface
 public interface Supplier {

  Span start();
 }

 /**
  * Named span supplier for creating spans with operation names.
  */
 @FunctionalInterface
 public interface NamedSupplier {
  Span start(String name);
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
