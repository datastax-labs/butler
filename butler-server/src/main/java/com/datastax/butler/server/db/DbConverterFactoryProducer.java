/*
 * Copyright DataStax, Inc.
 *
 * Please see the included license file for details.
 */
package com.datastax.butler.server.db;

import com.datastax.butler.commons.issues.IssueId;
import com.datastax.butler.commons.jenkins.TestVariant;
import org.simpleflatmapper.converter.AbstractConverterFactoryProducer;
import org.simpleflatmapper.converter.ConverterFactory;
import org.simpleflatmapper.util.Consumer;

public class DbConverterFactoryProducer extends AbstractConverterFactoryProducer {

  @Override
  public void produce(Consumer<? super ConverterFactory<?, ?>> consumer) {
    constantConverter(consumer, String.class, TestVariant.class, TestVariant::fromString);
    constantConverter(consumer, String.class, IssueId.class, IssueId::fromString);
  }
}
