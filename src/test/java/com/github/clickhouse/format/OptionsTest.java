package com.github.clickhouse.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import io.vertx.core.MultiMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OptionsTest {

  @Test
  public void testFromMultiMap() {
    MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
    multiMap.add("hilite", "true");
    multiMap.add("oneline", "true");
    multiMap.add("multiquery", "false");

    multiMap.add("seed", "HelloWorld");

    multiMap.add("allowSettingsAfterFormatInInsert", "false");
    multiMap.add("maxParserDepth", "9999");

    Options options = Options.fromMultiMap(new ObjectMapper(), multiMap);

    assertEquals(Boolean.TRUE, options.getHilite());
    assertEquals(Boolean.TRUE, options.getOneline());
    assertEquals(Boolean.FALSE, options.getMultiquery());
    assertEquals(Boolean.FALSE, options.getAllowSettingsAfterFormatInInsert());
    assertEquals(Integer.valueOf(9999), options.getMaxParserDepth());
    assertEquals("HelloWorld", options.getSeed());

    assertNull(options.getBackslash());
    assertNull(options.getObfuscate());
    assertNull(options.getMaxQuerySize());
  }

  @Test
  public void testAppendTo() {
    Options options = new Options();
    options.setHilite(true);
    options.setOneline(true);
    options.setMultiquery(false);

    options.setSeed("HelloWorld");
    options.setMaxQuerySize(100);

    assertEquals(
        Arrays.asList("--hilite", "--oneline", "--seed", "HelloWorld", "--max_query_size", "100"),
        options.appendTo(new ArrayList<>()));
  }

  @Test
  public void split() {
    String s =
        "AFTER | ALIAS | ALL | ALTER | AND | ANTI | ANY | ARRAY | AS | ASCENDING | ASOF | AST | ASYNC | ATTACH | BETWEEN | BOTH | BY | CASE\n"
            + "    | CAST | CHECK | CLEAR | CLUSTER | CODEC | COLLATE | COLUMN | COMMENT | CONSTRAINT | CREATE | CROSS | CUBE | CURRENT | DATABASE\n"
            + "    | DATABASES | DATE | DEDUPLICATE | DEFAULT | DELAY | DELETE | DESCRIBE | DESC | DESCENDING | DETACH | DICTIONARIES | DICTIONARY | DISK\n"
            + "    | DISTINCT | DISTRIBUTED | DROP | ELSE | END | ENGINE | EVENTS | EXISTS | EXPLAIN | EXPRESSION | EXTRACT | FETCHES | FINAL | FIRST\n"
            + "    | FLUSH | FOR | FOLLOWING | FOR | FORMAT | FREEZE | FROM | FULL | FUNCTION | GLOBAL | GRANULARITY | GROUP | HAVING | HIERARCHICAL | ID\n"
            + "    | IF | ILIKE | IN | INDEX | INJECTIVE | INNER | INSERT | INTERVAL | INTO | IS | IS_OBJECT_ID | JOIN | JSON_FALSE | JSON_TRUE | KEY\n"
            + "    | KILL | LAST | LAYOUT | LEADING | LEFT | LIFETIME | LIKE | LIMIT | LIVE | LOCAL | LOGS | MATERIALIZE | MATERIALIZED | MAX | MERGES\n"
            + "    | MIN | MODIFY | MOVE | MUTATION | NO | NOT | NULLS | OFFSET | ON | OPTIMIZE | OR | ORDER | OUTER | OUTFILE | OVER | PARTITION\n"
            + "    | POPULATE | PRECEDING | PREWHERE | PRIMARY | RANGE | RELOAD | REMOVE | RENAME | REPLACE | REPLICA | REPLICATED | RIGHT | ROLLUP | ROW\n"
            + "    | ROWS | SAMPLE | SELECT | SEMI | SENDS | SET | SETTINGS | SHOW | SOURCE | START | STOP | SUBSTRING | SYNC | SYNTAX | SYSTEM | TABLE\n"
            + "    | TABLES | TEMPORARY | TEST | THEN | TIES | TIMEOUT | TIMESTAMP | TOTALS | TRAILING | TRIM | TRUNCATE | TO | TOP | TTL | TYPE\n"
            + "    | UNBOUNDED | UNION | UPDATE | USE | USING | UUID | VALUES | VIEW | VOLUME | WATCH | WHEN | WHERE | WINDOW | WITH";

    Pattern pattern = Pattern.compile("[A-Z]+");
    Matcher matcher = pattern.matcher(s);
    List<String> list = new ArrayList<>();
    while (matcher.find()) {
      list.add(matcher.group());
    }
    System.out.println(Joiner.on("', '").appendTo(new StringBuilder("['"), list).append("']"));
  }
}
