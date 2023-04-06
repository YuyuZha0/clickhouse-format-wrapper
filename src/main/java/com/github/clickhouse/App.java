package com.github.clickhouse;

import com.github.clickhouse.handler.FormattingHandler;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public final class App extends AbstractVerticle {

  private static final int DEFAULT_PORT = 7353;

  static {
    System.setProperty(
        "vertx.logger-delegate-factory-class-name", SLF4JLogDelegateFactory.class.getName());
    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
  }

  private final FormattingHandler formattingHandler;
  private int port = DEFAULT_PORT;
  private HttpServer httpServer;

  @Inject
  public App(FormattingHandler formattingHandler) {
    this.formattingHandler = formattingHandler;
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
    Injector injector = Guice.createInjector(new AppModule());
    App app = injector.getInstance(App.class);
    vertx.deployVerticle(
        app,
        ar -> {
          if (ar.succeeded()) {
            String id = ar.result();
            log.info("Deploy verticle successfully: {}", id);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> vertx.undeploy(id)));
          } else {
            log.error("Deploy with unexpected exception: ", ar.cause());
          }
        });
  }

  @Inject(optional = true)
  public void setPort(@Named("SERVER_PORT") int port) {
    this.port = port;
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    Router router = Router.router(vertx);
    BodyHandler bodyHandler = BodyHandler.create(false);
    bodyHandler.setBodyLimit(3 * 1024 * 1024); // 3M
    router.get("/").handler(ctx -> ctx.reroute("/static/index.html"));
    router.route("/static/*").handler(StaticHandler.create("webroot").setCachingEnabled(true));
    router.post("/api/format").handler(bodyHandler).handler(formattingHandler);
    router
        .route("/health")
        .handler(
            ctx -> ctx.response().setStatusCode(200).end("time=" + System.currentTimeMillis()));
    HttpServer httpServer =
        vertx.createHttpServer(
            new HttpServerOptions().setTcpNoDelay(true).setTcpFastOpen(true).setTcpQuickAck(true));
    httpServer.requestHandler(router);
    httpServer.listen(
        port,
        ar -> {
          if (ar.succeeded()) {
            log.info("Start HTTP server listening on port: {}", ar.result().actualPort());
            App.this.httpServer = ar.result();
            startPromise.tryComplete();
          } else {
            log.error("Start HTTP server with unexpected exception: ", ar.cause());
            startPromise.tryFail(ar.cause());
          }
        });
  }

  @Override
  public void stop(Promise<Void> stopPromise) throws Exception {
    if (httpServer != null) {
      httpServer.close(stopPromise);
    } else {
      stopPromise.tryComplete();
    }
  }
}
