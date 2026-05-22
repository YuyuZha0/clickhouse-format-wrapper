package com.github.clickhouse.format;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Drives CommandLineSqlFormatter against POSIX utilities (cat, false) and tiny shell scripts
 * instead of the real clickhouse-format binary — so the suite runs anywhere there's a /bin/sh.
 */
public class CommandLineSqlFormatterTest {

  @BeforeClass
  public static void requirePosixShell() {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    assumeTrue("requires a POSIX shell + cat/head/tr", os.contains("nux") || os.contains("mac"));
  }

  private Vertx vertx;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  // ---------- happy path ----------

  @Test
  public void stdinFlowsThroughBinaryToStdout() throws Exception {
    // `cat` echoes stdin → stdout, giving us a deterministic identity transform.
    CommandLineSqlFormatter formatter = new CommandLineSqlFormatter(vertx, "cat");

    Buffer out = await(formatter.format(Buffer.buffer("SELECT * FROM t;\n"), new Options()));

    assertEquals("SELECT * FROM t;\n", out.toString());
  }

  @Test
  public void emptyInputProducesEmptyOutput() throws Exception {
    CommandLineSqlFormatter formatter = new CommandLineSqlFormatter(vertx, "cat");

    Buffer out = await(formatter.format(Buffer.buffer(""), new Options()));

    assertEquals(0, out.length());
  }

  // ---------- argv construction ----------

  @Test
  public void optionsAreTranslatedToArgvInDeclarationOrder() throws Exception {
    // A fake binary that ignores stdin and echoes each argv element on its own line.
    Path fake =
        writeExecutable(
            "#!/bin/sh\n"
                + "cat >/dev/null\n"
                + "for a in \"$@\"; do echo \"$a\"; done\n");

    Options opts = new Options();
    opts.setHilite(true);
    opts.setOneline(true);
    opts.setSeed("abc-123");
    opts.setMaxLineLength(120);

    CommandLineSqlFormatter formatter = new CommandLineSqlFormatter(vertx, fake.toString());
    String stdout = await(formatter.format(Buffer.buffer(""), opts)).toString();

    assertEquals(
        String.join(
                "\n",
                "--hilite",
                "--oneline",
                "--max_line_length",
                "120",
                "--seed",
                "abc-123")
            + "\n",
        stdout);
  }

  // ---------- error mapping ----------

  @Test
  public void nonZeroExitCarriesCodeAndStderrThroughSqlFormatException() throws Exception {
    Path fake =
        writeExecutable(
            "#!/bin/sh\n"
                + "cat >/dev/null\n"
                + "printf 'kaboom: arg=%s\\n' \"$1\" 1>&2\n"
                + "exit 7\n");

    Options opts = new Options();
    opts.setHilite(true);

    CommandLineSqlFormatter formatter = new CommandLineSqlFormatter(vertx, fake.toString());

    SqlFormatException sfe = expectFailure(formatter.format(Buffer.buffer("ignored"), opts));
    assertEquals(7, sfe.getCode());
    assertTrue(
        "stderr should contain 'kaboom: arg=--hilite' but was: " + sfe.getStderr(),
        sfe.getStderr().toString().contains("kaboom: arg=--hilite"));
  }

  @Test
  public void missingBinaryFails() throws Exception {
    CommandLineSqlFormatter formatter =
        new CommandLineSqlFormatter(vertx, "/definitely/not/a/binary-xyz");

    try {
      await(formatter.format(Buffer.buffer("x"), new Options()));
      fail("expected failure");
    } catch (ExecutionException e) {
      // ProcessBuilder.start() throws IOException → propagated as the future's cause.
      assertTrue(
          "expected IOException-ish cause, got " + e.getCause(),
          e.getCause() instanceof java.io.IOException);
    }
  }

  // ---------- the deadlock fix ----------

  @Test
  public void largeStdoutDoesNotDeadlock() throws Exception {
    // Emit ~256 KiB on stdout — well over the typical 64 KiB pipe buffer.
    // Without the concurrent stdout pump, the child blocks on write(stdout),
    // waitFor() never returns, and the 30 s timeout would fire.
    int bytes = 256 * 1024;
    Path fake =
        writeExecutable(
            "#!/bin/sh\n"
                + "cat >/dev/null\n"
                + "head -c "
                + bytes
                + " </dev/zero | tr '\\0' 'a'\n");

    CommandLineSqlFormatter formatter = new CommandLineSqlFormatter(vertx, fake.toString());
    Buffer out = await(formatter.format(Buffer.buffer(""), new Options()));

    assertEquals(bytes, out.length());
    assertEquals('a', out.getByte(0));
    assertEquals('a', out.getByte(bytes - 1));
  }

  @Test
  public void largeStderrAlongsideStdoutDoesNotDeadlock() throws Exception {
    // Both stdout AND stderr above the pipe-buffer threshold, then non-zero exit.
    // Exercises both pumps simultaneously.
    int bytes = 128 * 1024;
    Path fake =
        writeExecutable(
            "#!/bin/sh\n"
                + "cat >/dev/null\n"
                + "head -c "
                + bytes
                + " </dev/zero | tr '\\0' 'a' 1>&2\n"
                + "head -c "
                + bytes
                + " </dev/zero | tr '\\0' 'b'\n"
                + "exit 9\n");

    CommandLineSqlFormatter formatter = new CommandLineSqlFormatter(vertx, fake.toString());
    SqlFormatException sfe = expectFailure(formatter.format(Buffer.buffer(""), new Options()));

    assertEquals(9, sfe.getCode());
    assertEquals(bytes, sfe.getStderr().length());
  }

  @Test
  public void largeInputIsForwardedIntact() throws Exception {
    // Input also over pipe-buffer size — verifies writeInput keeps up with the pumps.
    int bytes = 200 * 1024;
    byte[] payload = new byte[bytes];
    for (int i = 0; i < bytes; i++) {
      payload[i] = (byte) ('a' + (i % 26));
    }

    CommandLineSqlFormatter formatter = new CommandLineSqlFormatter(vertx, "cat");
    Buffer out = await(formatter.format(Buffer.buffer(payload), new Options()));

    assertEquals(bytes, out.length());
    assertEquals(payload[0], out.getByte(0));
    assertEquals(payload[bytes - 1], out.getByte(bytes - 1));
  }

  // ---------- helpers ----------

  private static <T> T await(Future<T> fut) throws Exception {
    return fut.toCompletionStage().toCompletableFuture().get(15, SECONDS);
  }

  private static SqlFormatException expectFailure(Future<?> fut) throws Exception {
    try {
      await(fut);
    } catch (ExecutionException e) {
      assertTrue(
          "expected SqlFormatException, got " + e.getCause(),
          e.getCause() instanceof SqlFormatException);
      return (SqlFormatException) e.getCause();
    }
    fail("expected failure");
    throw new AssertionError("unreachable");
  }

  private static Path writeExecutable(String script) throws Exception {
    Path p = Files.createTempFile("ch-fmt-fake-", ".sh");
    Files.write(p, script.getBytes(StandardCharsets.UTF_8));
    if (!p.toFile().setExecutable(true)) {
      throw new IllegalStateException("could not chmod +x: " + p);
    }
    p.toFile().deleteOnExit();
    return p;
  }
}
