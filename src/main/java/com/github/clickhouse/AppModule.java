package com.github.clickhouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

final class AppModule extends AbstractModule {

  @Override
  protected void configure() {
    Names.bindProperties(binder(), System.getenv());
    bind(ObjectMapper.class).toInstance(new ObjectMapper());
  }
}
