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
import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class CommandLineSqlFormatter {
  private static final File CLICKHOUSE_HOME = initClickhouseHome();
  static final String DEFAULT_BIN_NAME = "clickhouse-format";
  private static final int TIMEOUT_SECONDS = 30;

  private final Vertx vertx;
  private final String binName;

  @Inject
  public CommandLineSqlFormatter(Vertx vertx) {
    this(vertx, DEFAULT_BIN_NAME);
  }

  // Overridable binary path — production wiring uses the @Inject ctor above;
  // tests pass a fake (e.g. `cat`, `false`, or a temp shell script).
  public CommandLineSqlFormatter(Vertx vertx, String binName) {
    this.vertx = vertx;
    this.binName = binName;
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

  private static final int PROBE_TIMEOUT_SECONDS = 5;

  public static boolean isCommandAvailable() {
    Process process = null;
    try {
      process =
          createProcessBuilder(Lists.newArrayList(DEFAULT_BIN_NAME, "--query", "select 1"))
              // Discard child stdout/stderr at the OS level — no pipes to drain, no deadlock risk.
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();

      if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        log.warn(
            "\"{}\" probe timed out after {}s", DEFAULT_BIN_NAME, PROBE_TIMEOUT_SECONDS);
        return false;
      }

      int exit = process.exitValue();
      if (exit != 0) {
        log.warn("\"{}\" probe exited with code {}", DEFAULT_BIN_NAME, exit);
      }
      return exit == 0;
    } catch (IOException e) {
      log.warn("\"{}\" probe failed to start: {}", DEFAULT_BIN_NAME, e.getMessage());
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("\"{}\" probe interrupted", DEFAULT_BIN_NAME);
      return false;
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }

  public Future<Buffer> format(@NonNull Buffer input, @NonNull Options options) {
    return vertx.executeBlocking(
        promise -> {
          try {
            Buffer output = formatWithProcess(input, options);
            promise.complete(output);
          } catch (Exception e) {
            promise.fail(e);
          }
        },
        false);
  }

  private Buffer formatWithProcess(@NonNull Buffer input, @NonNull Options options)
      throws SqlFormatException, IOException, InterruptedException {
    List<String> command = buildCommand(options);
    Process process = createProcessBuilder(command).start();

    // Drain stdout/stderr concurrently with the stdin write so the child never blocks on a full
    // pipe buffer (~64 KiB on Linux). Without this, ANSI-highlighted output of large queries
    // deadlocks the process and triggers the timeout below.
    AtomicReference<Buffer> stdoutRef = new AtomicReference<>();
    AtomicReference<Buffer> stderrRef = new AtomicReference<>();
    AtomicReference<IOException> readErrorRef = new AtomicReference<>();

    Thread stdoutPump =
        Thread.startVirtualThread(
            () -> {
              try (InputStream is = process.getInputStream()) {
                stdoutRef.set(inputStreamToBuffer(is));
              } catch (IOException e) {
                readErrorRef.compareAndSet(null, e);
              }
            });
    Thread stderrPump =
        Thread.startVirtualThread(
            () -> {
              try (InputStream is = process.getErrorStream()) {
                stderrRef.set(inputStreamToBuffer(is));
              } catch (IOException e) {
                readErrorRef.compareAndSet(null, e);
              }
            });

    try {
      writeInput(process, input);
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        throw new SqlFormatException(-1, Buffer.buffer("Process timeout"));
      }

      stdoutPump.join();
      stderrPump.join();

      IOException readError = readErrorRef.get();
      if (readError != null) {
        throw readError;
      }

      int exitCode = process.exitValue();
      if (exitCode != 0) {
        throw new SqlFormatException(exitCode, stderrRef.get());
      }
      return stdoutRef.get();
    } finally {
      process.destroyForcibly();
    }
  }

  private void writeInput(Process process, Buffer input) throws IOException {
    try (OutputStream outputStream = process.getOutputStream()) {
      ByteBuf src = input.getByteBuf().duplicate();
      int len = src.readableBytes();
      if (len > 0) {
        src.readBytes(outputStream, len);
      }
      outputStream.flush();
    }
  }

  private List<String> buildCommand(Options options) {
    List<String> command = new ArrayList<>();
    command.add(binName);
    options.appendTo(command);
    return command;
  }
}
