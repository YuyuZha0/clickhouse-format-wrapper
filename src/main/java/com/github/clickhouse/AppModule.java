package com.github.clickhouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import io.vertx.core.Vertx;
import lombok.NonNull;

final class AppModule extends AbstractModule {

  private final Vertx vertx;

  AppModule(@NonNull Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    Names.bindProperties(binder(), System.getenv());
    bind(ObjectMapper.class).toInstance(new ObjectMapper());
    bind(Vertx.class).toInstance(vertx);
  }
}
