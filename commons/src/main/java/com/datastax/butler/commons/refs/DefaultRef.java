/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.commons.refs;

class DefaultRef<T> implements Ref<T> {
  private T value;

  DefaultRef() {}

  DefaultRef(T initialValue) {
    this.value = initialValue;
  }

  @Override
  public T get() {
    return value;
  }

  @Override
  public void set(T t) {
    this.value = t;
  }
}
