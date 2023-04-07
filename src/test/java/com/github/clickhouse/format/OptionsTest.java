package com.github.clickhouse.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.MultiMap;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

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
        Arrays.asList(
            "--hilite", "--oneline", "--seed", "HelloWorld", "--max_query_size", "100"),
        options.appendTo(new ArrayList<>()));
  }
}
