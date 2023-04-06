package com.github.clickhouse.format;

import lombok.Getter;

public class SqlFormatException extends Exception {

  private static final long serialVersionUID = 7722426406847061018L;
  @Getter private final int code;

  public SqlFormatException(int code, String message) {
    super(message, null, false, false);
    this.code = code;
  }
}
