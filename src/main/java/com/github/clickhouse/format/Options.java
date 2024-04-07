package com.github.clickhouse.format;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import io.vertx.core.MultiMap;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 *
 *
 * <pre>
 * Allowed options:
 *   --query arg                              query to format
 *   -h [ --help ]                            produce help message
 *   --comments                               keep comments in the output
 *   --hilite                                 add syntax highlight with ANSI terminal escape sequences
 *   --oneline                                format in single line
 *   --max_line_length arg (=0)               format in single line queries with length less than specified
 *   -q [ --quiet ]                           just check syntax, no output on success
 *   -n [ --multiquery ]                      allow multiple queries in the same file
 *   --obfuscate                              obfuscate instead of formatting
 *   --backslash                              add a backslash at the end of each line of the formatted query
 *   --allow_settings_after_format_in_insert  Allow SETTINGS after FORMAT, but note, that this is not always safe
 *   --seed arg                               seed (arbitrary string) that determines the result of obfuscation
 *   --max_query_size arg                     The maximum number of bytes of a query string parsed by the SQL parser. Data in the VALUES clause of INSERT
 *                                            queries is processed by a separate stream parser (that consumes O(1) RAM) and not affected by this restriction.
 *   --max_parser_depth arg                   Maximum parser depth (recursion depth of recursive descend parser).
 *   </pre>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Options implements Serializable {

  private static final Pattern ALLOWED_STRING_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]+$");

  private static final Map<String, Field> CACHED_FIELDS;
  private static final long serialVersionUID = -1793995872382083833L;

  static {
    Map<String, Field> fields = new LinkedHashMap<>();
    for (Field field : Options.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      field.setAccessible(true);
      fields.put(field.getName(), field);
      if (field.isAnnotationPresent(JsonProperty.class)) {
        fields.put(field.getAnnotation(JsonProperty.class).value(), field);
      }
    }
    CACHED_FIELDS = ImmutableMap.copyOf(fields);
  }

  private Boolean hilite;
  private Boolean comments;
  private Boolean oneline;
  private Integer maxLineLength;
  private Boolean multiquery;
  private Boolean backslash;
  private Boolean allowSettingsAfterFormatInInsert;
  private Boolean obfuscate;
  private String seed;
  private Integer maxQuerySize;
  private Integer maxParserDepth;

  public static Options fromMultiMap(
      @NonNull ObjectMapper objectMapper, @NonNull MultiMap multiMap) {
    Options options = new Options();
    Map<String, List<String>> tempMap =
        multiMap.entries().stream()
            .filter(entry -> CACHED_FIELDS.containsKey(entry.getKey()))
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    for (Map.Entry<String, List<String>> entry : tempMap.entrySet()) {
      try {
        Object value;
        if (entry.getValue().size() == 1) {
          value = entry.getValue().get(0);
        } else {
          value = entry.getValue();
        }
        Field field = CACHED_FIELDS.get(entry.getKey());
        field.set(options, objectMapper.convertValue(value, field.getType()));
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    return options;
  }

  private static String wrap(Object value) {
    if (value instanceof String) {
      String s = (String) value;
      // avoid command line injection
      if (ALLOWED_STRING_PATTERN.matcher(s).matches()) {
        return s;
      }
      return "";
    }
    return value.toString();
  }

  public <T extends Collection<String>> T appendTo(@NonNull T command) {
    for (Map.Entry<String, Field> entry : CACHED_FIELDS.entrySet()) {
      try {
        Object value = entry.getValue().get(this);
        if (value == null || Boolean.FALSE.equals(value)) {
          continue;
        }
        command.add("--" + CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entry.getKey()));
        if (value instanceof Boolean) {
          continue;
        }
        command.add(wrap(value));
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    return command;
  }
}
