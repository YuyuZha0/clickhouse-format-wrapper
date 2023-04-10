package com.github.clickhouse.format;

import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.VertxByteBufAllocator;
import lombok.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class CommandLineSqlFormatter {

  private static final ScheduledExecutorService WATCH_DOG =
      Executors.newSingleThreadScheduledExecutor(
          new ThreadFactoryBuilder()
              .setNameFormat("clickhouse-format-watch-dog-%d")
              .setDaemon(true)
              .build());
  private static final File CLICKHOUSE_HOME;
  private static final String BIN_NAME = "clickhouse-format";
  private static final int MAX_CONCURRENT_FORMATTING = 10;

  static {
    String clickhouseHome = System.getenv("CLICKHOUSE_HOME");
    if (clickhouseHome != null && !clickhouseHome.isEmpty()) {
      File clickhouseHomeFile = new File(clickhouseHome);
      if (clickhouseHomeFile.exists() && clickhouseHomeFile.isDirectory()) {
        CLICKHOUSE_HOME = clickhouseHomeFile;
      } else {
        CLICKHOUSE_HOME = null;
      }
    } else {
      CLICKHOUSE_HOME = null;
    }
  }

  private final Semaphore semaphore = new Semaphore(MAX_CONCURRENT_FORMATTING);

  private static Buffer inputStreamToBuffer(InputStream inputStream) throws IOException {
    ByteBuf byteBuf = VertxByteBufAllocator.DEFAULT.heapBuffer();
    try (OutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
      ByteStreams.copy(inputStream, outputStream);
    }
    return Buffer.buffer(byteBuf);
  }

  private static void setClickhouseHome(ProcessBuilder processBuilder) {
    if (CLICKHOUSE_HOME != null) {
      processBuilder.directory(CLICKHOUSE_HOME);
    }
  }

  private static ScheduledFuture<?> registerTimeout(Process process) {
    return WATCH_DOG.schedule(
        () -> {
          if (process.isAlive()) {
            process.destroy();
          }
        },
        30,
        TimeUnit.SECONDS);
  }

  public static boolean isCommandAvailable() {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder();
      processBuilder.command(BIN_NAME, "--query", "select 1");
      processBuilder.redirectErrorStream(false);
      setClickhouseHome(processBuilder);
      Process process = processBuilder.start();
      int code = process.waitFor();
      return code == 0;
    } catch (Exception e) {
      return false;
    }
  }

  public Buffer format(@NonNull Buffer input, @NonNull Options options) throws Exception {

    try {
      semaphore.acquire();
      return format0(input, options);
    } finally {
      semaphore.release();
    }
  }

  private Buffer format0(@NonNull Buffer input, @NonNull Options options)
      throws SqlFormatException, IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder();
    processBuilder.command(buildCommand(options));
    processBuilder.redirectErrorStream(false);
    setClickhouseHome(processBuilder);

    Process process = processBuilder.start();
    ScheduledFuture<?> timeoutFuture = registerTimeout(process);
    try (OutputStream outputStream = process.getOutputStream()) {
      outputStream.write(input.getBytes());
    }
    int code = process.waitFor();
    if (!timeoutFuture.isDone()) {
      timeoutFuture.cancel(true);
    }
    if (code != 0) {
      try (InputStream inputStream = process.getErrorStream()) {
        throw new SqlFormatException(code, inputStreamToBuffer(inputStream));
      }
    }
    try (InputStream inputStream = process.getInputStream()) {
      return inputStreamToBuffer(inputStream);
    }
  }

  private List<String> buildCommand(Options options) {
    List<String> command = new ArrayList<>();
    command.add(BIN_NAME);
    options.appendTo(command);
    command.add("<");
    return command;
  }
}
