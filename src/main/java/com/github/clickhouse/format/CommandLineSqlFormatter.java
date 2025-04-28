package com.github.clickhouse.format;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CommandLineSqlFormatter {
  private static final File CLICKHOUSE_HOME = initClickhouseHome();
  private static final String BIN_NAME = "clickhouse-format";
  private static final int MAX_CONCURRENT_FORMATTING = 10;
  private static final int TIMEOUT_SECONDS = 30;

  private final AtomicInteger concurrentCount = new AtomicInteger(0);
  private final Vertx vertx;

  @Inject
  public CommandLineSqlFormatter(Vertx vertx) {
    this.vertx = vertx;
  }

  private static File initClickhouseHome() {
    String clickhouseHome = System.getenv("CLICKHOUSE_HOME");
    if (clickhouseHome != null && !clickhouseHome.isEmpty()) {
      File clickhouseHomeFile = new File(clickhouseHome);
      if (clickhouseHomeFile.exists() && clickhouseHomeFile.isDirectory()) {
        log.info("CLICKHOUSE_HOME is set to \"{}\".", clickhouseHome);
        return clickhouseHomeFile;
      }
    }
    return null;
  }

  private static Buffer inputStreamToBuffer(InputStream inputStream) throws IOException {
    ByteBuf byteBuf = VertxByteBufAllocator.DEFAULT.heapBuffer();
    try (ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
      ByteStreams.copy(inputStream, outputStream);
      return Buffer.buffer(byteBuf);
    }
  }

  private static ProcessBuilder createProcessBuilder(List<String> command) {
    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.redirectErrorStream(false);
    if (CLICKHOUSE_HOME != null) {
      processBuilder.directory(CLICKHOUSE_HOME);
    }
    return processBuilder;
  }

  public static boolean isCommandAvailable() {
    try {
      Process process =
          createProcessBuilder(Lists.newArrayList(BIN_NAME, "--query", "select 1")).start();
      return process.waitFor() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  public Future<Buffer> format(@NonNull Buffer input, @NonNull Options options) {
    if (concurrentCount.incrementAndGet() >= MAX_CONCURRENT_FORMATTING) {
      concurrentCount.decrementAndGet();
      return Future.failedFuture(new IllegalStateException("Too many concurrent formatting tasks"));
    }
    return vertx.executeBlocking(
        promise -> {
          try {
            Buffer output = formatWithProcess(input, options);
            promise.complete(output);
          } catch (Exception e) {
            promise.fail(e);
          } finally {
            concurrentCount.decrementAndGet();
          }
        },
        false);
  }

  private Buffer formatWithProcess(@NonNull Buffer input, @NonNull Options options)
      throws SqlFormatException, IOException, InterruptedException {
    List<String> command = buildCommand(options);
    Process process = createProcessBuilder(command).start();

    try {
      writeInput(process, input);
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new SqlFormatException(-1, Buffer.buffer("Process timeout"));
      }

      int exitCode = process.exitValue();
      if (exitCode != 0) {
        try (InputStream errorStream = process.getErrorStream()) {
          throw new SqlFormatException(exitCode, inputStreamToBuffer(errorStream));
        }
      }

      try (InputStream inputStream = process.getInputStream()) {
        return inputStreamToBuffer(inputStream);
      }
    } finally {
      process.destroyForcibly();
    }
  }

  private void writeInput(Process process, Buffer input) throws IOException {
    try (OutputStream outputStream = process.getOutputStream()) {
      outputStream.write(input.getBytes());
      outputStream.flush();
    }
  }

  private List<String> buildCommand(Options options) {
    List<String> command = new ArrayList<>();
    command.add(BIN_NAME);
    options.appendTo(command);
    return command;
  }
}
