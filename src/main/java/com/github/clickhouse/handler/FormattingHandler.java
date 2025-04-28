package com.github.clickhouse.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.clickhouse.format.CommandLineSqlFormatter;
import com.github.clickhouse.format.Options;
import com.github.clickhouse.format.SqlFormatException;
import com.google.common.net.MediaType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

@Singleton
public final class FormattingHandler implements Handler<RoutingContext> {

  private static final CharSequence TEXT_PLAIN =
      AsciiString.of(MediaType.PLAIN_TEXT_UTF_8.toString());
  private final CommandLineSqlFormatter formatter;
  private final ObjectMapper objectMapper;

  @Inject
  public FormattingHandler(ObjectMapper objectMapper, CommandLineSqlFormatter formatter) {
    this.objectMapper = objectMapper;
    this.formatter = formatter;
  }

  private static void endWithCodeAndMsg(
      HttpServerResponse response, HttpResponseStatus status, String msg) {
    response
        .putHeader(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN)
        .setStatusCode(status.code())
        .setStatusMessage(status.reasonPhrase())
        .end(msg);
  }

  @Override
  public void handle(RoutingContext context) {
    HttpServerRequest request = context.request();
    HttpServerResponse response = context.response();
    Options options;
    try {
      options = Options.fromMultiMap(objectMapper, request.params());
    } catch (Exception e) {
      endWithCodeAndMsg(response, HttpResponseStatus.BAD_REQUEST, e.getMessage());
      return;
    }
    Buffer body = context.body().buffer();
    if (body == null || body.length() == 0) {
      endWithCodeAndMsg(response, HttpResponseStatus.BAD_REQUEST, "Empty body");
      return;
    }

    if (body.length() > 1024 * 1024) {
      endWithCodeAndMsg(response, HttpResponseStatus.BAD_REQUEST, "Body too large");
      return;
    }

    formatter
        .format(body, options)
        .onComplete(
            (AsyncResult<Buffer> result) -> {
              if (result.succeeded()) {
                response
                    .setStatusCode(HttpResponseStatus.OK.code())
                    .putHeader(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN)
                    .end(result.result());
              } else {
                Throwable cause = result.cause();
                if (cause instanceof SqlFormatException) {
                  response
                      .setStatusCode(HttpResponseStatus.NOT_ACCEPTABLE.code())
                      .putHeader(HttpHeaderNames.CONTENT_TYPE, TEXT_PLAIN)
                      .end(((SqlFormatException) cause).getStderr());
                } else {
                  endWithCodeAndMsg(
                      response, HttpResponseStatus.INTERNAL_SERVER_ERROR, cause.getMessage());
                }
              }
            });
  }
}
