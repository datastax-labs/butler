/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.refs;

/**
 * Simple interface for a reference to a value.
 *
 * <p>Meant to be used with lambda (and yes, that's not the most elegant thing ever, but it is
 * sometimes convenient).
 *
 * <p>This is not thread safe (use {@link java.util.concurrent.atomic.AtomicReference} or related if
 * you need thread-safe).
 */
public interface Ref<T> {
  T get();

  void set(T t);

  static <T> Ref<T> of(T initialValue) {
    return new DefaultRef<>(initialValue);
  }

  static <T> Ref<T> ref(Class<T> type) {
    return new DefaultRef<>();
  }
}
