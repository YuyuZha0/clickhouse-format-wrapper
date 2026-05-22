package com.github.clickhouse.format;

import io.vertx.core.buffer.Buffer;
import lombok.Getter;

import java.io.Serial;

public class SqlFormatException extends Exception {

  @Serial
  private static final long serialVersionUID = 7722426406847061018L;
  @Getter private final int code;
  @Getter private final Buffer stderr;

  public SqlFormatException(int code, Buffer stderr) {
    super(null, null, false, false);
    this.code = code;
    this.stderr = stderr;
  }
}
