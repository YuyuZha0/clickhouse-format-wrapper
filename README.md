# clickhouse-format-wrapper

A thin web wrapper around the [`clickhouse-format`](https://clickhouse.com/docs/en/operations/utilities/clickhouse-format)
CLI — a browser-based ClickHouse SQL formatter with ANSI syntax highlighting, obfuscation, and the full set of
flags the upstream tool exposes.

**Live demo:** <https://elasticdogs.com/clickhouse-format/>

---

## Features

- Format any ClickHouse SQL query directly from the browser — `Ctrl+Enter` to format, or enable **Auto Format**.
- Full parity with `clickhouse-format` flags: highlight, single-line, multi-query, obfuscate (seeded), backslash
  line-continuation, line-length & parser-depth tuning.
- ANSI-highlighted output rendered as HTML via [`ansi_up`](https://github.com/drudru/ansi_up).
- Light / dark theme with CodeMirror integration.
- Copy-to-clipboard, undo / redo, persistent theme preference.
- `POST /api/format` is a plain HTTP endpoint — usable from `curl`, scripts, or other tools.

---

## Quick start

### Docker

The image bundles a JDK 21 runtime plus the `clickhouse-format` binary.

```bash
mvn -DskipTests package
docker build -t chfw .
docker run --rm -p 7353:7353 chfw
```

Open <http://localhost:7353/>.

### Local (no Docker)

Requires JDK 21 and the `clickhouse-format` binary on `PATH`.

```bash
# macOS
brew install clickhouse

# Debian / Ubuntu
sudo apt-get install clickhouse-client    # or clickhouse-common-static
```

Then:

```bash
mvn -DskipTests package
java -jar target/clickhouse-format-wrapper-1.0-SNAPSHOT.jar
```

The app fails fast at startup if `clickhouse-format` is not on `PATH`.

### Configuration

| Env var       | Default | Notes              |
| ------------- | ------- | ------------------ |
| `SERVER_PORT` | `7353`  | HTTP listen port   |

---

## HTTP API

### `POST /api/format`

Format a SQL query. Options are passed as query-string parameters; the SQL is the raw request body.

```bash
curl -s -X POST 'http://localhost:7353/api/format?hilite=false&oneline=true' \
     -H 'Content-Type: text/plain' \
     --data 'select count() from system.tables where database = :db'
```

| Option                             | Type    | Maps to `clickhouse-format` flag          |
| ---------------------------------- | ------- | ----------------------------------------- |
| `hilite`                           | bool    | `--hilite`                                |
| `comments`                         | bool    | `--comments`                              |
| `oneline`                          | bool    | `--oneline`                               |
| `multiquery`                       | bool    | `--multiquery`                            |
| `backslash`                        | bool    | `--backslash`                             |
| `obfuscate`                        | bool    | `--obfuscate`                             |
| `allowSettingsAfterFormatInInsert` | bool    | `--allow_settings_after_format_in_insert` |
| `seed`                             | string  | `--seed`                                  |
| `maxLineLength`                    | integer | `--max_line_length`                       |
| `maxQuerySize`                     | integer | `--max_query_size`                        |
| `maxParserDepth`                   | integer | `--max_parser_depth`                      |

Body size is capped at 3 MiB. Boolean flags are omitted when `false`.

### `GET /health`

Returns `200 OK` with a `time=<epoch-ms>` body — suitable for liveness probes.

---

## Development

### Run the tests

```bash
mvn test
```

### Hot frontend iteration

The UI is a single static file at `src/main/resources/webroot/index.html` packaged into the shaded jar. For
faster iteration, edit it and re-run `mvn -DskipTests package && java -jar target/clickhouse-format-wrapper-1.0-SNAPSHOT.jar`
— no Docker rebuild needed.

### Project layout

```
src/main/java/com/github/clickhouse/
├── App.java                          # Vert.x verticle + routing
├── AppModule.java                    # Guice wiring
├── handler/FormattingHandler.java    # HTTP → CLI bridge
└── format/
    ├── CommandLineSqlFormatter.java  # process wrapper around `clickhouse-format`
    ├── Options.java                  # query-string → CLI flag mapping
    └── SqlFormatException.java
```

---

## Tech stack

- **Server:** [Vert.x](https://vertx.io/) 4.4 on JDK 21 (native epoll on Linux x86_64)
- **DI:** Guice 5.1
- **Logging:** SLF4J + Logback
- **Frontend:** [CodeMirror](https://codemirror.net/) 5, Bootstrap 4, jQuery, `ansi_up`
- **Build:** Maven Shade (single-jar executable)
