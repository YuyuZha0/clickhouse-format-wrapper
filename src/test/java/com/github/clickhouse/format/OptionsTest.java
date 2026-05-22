package com.github.clickhouse.format;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.MultiMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class OptionsTest {

  private ObjectMapper mapper;

  @Before
  public void setUp() {
    mapper = new ObjectMapper();
  }

  // ---------- fromMultiMap ----------

  @Test
  public void fromMultiMap_parsesBooleansIntegersAndStrings() {
    MultiMap m = MultiMap.caseInsensitiveMultiMap();
    m.add("hilite", "true");
    m.add("oneline", "true");
    m.add("multiquery", "false");
    m.add("seed", "HelloWorld");
    m.add("allowSettingsAfterFormatInInsert", "false");
    m.add("maxParserDepth", "9999");

    Options o = Options.fromMultiMap(mapper, m);

    assertEquals(Boolean.TRUE, o.getHilite());
    assertEquals(Boolean.TRUE, o.getOneline());
    assertEquals(Boolean.FALSE, o.getMultiquery());
    assertEquals(Boolean.FALSE, o.getAllowSettingsAfterFormatInInsert());
    assertEquals(Integer.valueOf(9999), o.getMaxParserDepth());
    assertEquals("HelloWorld", o.getSeed());

    // Unset fields stay null.
    assertNull(o.getBackslash());
    assertNull(o.getObfuscate());
    assertNull(o.getMaxQuerySize());
    assertNull(o.getMaxLineLength());
    assertNull(o.getComments());
  }

  @Test
  public void fromMultiMap_emptyMap_yieldsAllNullOptions() {
    Options o = Options.fromMultiMap(mapper, MultiMap.caseInsensitiveMultiMap());

    // appendTo() on an all-null Options is the cleanest "everything unset" assertion.
    assertEquals(Collections.emptyList(), o.appendTo(new ArrayList<>()));
  }

  @Test
  public void fromMultiMap_unknownKeysAreIgnored() {
    MultiMap m = MultiMap.caseInsensitiveMultiMap();
    m.add("hilite", "true");
    m.add("notARealOption", "boom");
    m.add("--cmd-injection", "ls /");
    m.add("seedz", "typo");

    Options o = Options.fromMultiMap(mapper, m);

    assertEquals(Boolean.TRUE, o.getHilite());
    assertNull(o.getSeed());
    assertNull(o.getOneline());
  }

  @Test
  public void fromMultiMap_acceptsIntegerStringsForNumericFields() {
    MultiMap m = MultiMap.caseInsensitiveMultiMap();
    m.add("maxLineLength", "120");
    m.add("maxQuerySize", "262144");
    m.add("maxParserDepth", "1000");

    Options o = Options.fromMultiMap(mapper, m);

    assertEquals(Integer.valueOf(120), o.getMaxLineLength());
    assertEquals(Integer.valueOf(262144), o.getMaxQuerySize());
    assertEquals(Integer.valueOf(1000), o.getMaxParserDepth());
  }

  // ---------- appendTo ----------

  @Test
  public void appendTo_emptyOptions_appendsNothing() {
    assertEquals(Collections.emptyList(), new Options().appendTo(new ArrayList<>()));
  }

  @Test
  public void appendTo_booleanTrue_emitsBareFlag() {
    Options o = new Options();
    o.setHilite(true);
    o.setOneline(true);

    assertEquals(Arrays.asList("--hilite", "--oneline"), o.appendTo(new ArrayList<>()));
  }

  @Test
  public void appendTo_booleanFalseAndNull_areBothSkipped() {
    Options o = new Options();
    o.setHilite(true);
    o.setOneline(false); // false → skipped
    // comments left null → skipped

    assertEquals(Collections.singletonList("--hilite"), o.appendTo(new ArrayList<>()));
  }

  @Test
  public void appendTo_camelCaseFieldsAreMappedToSnakeCaseFlags() {
    Options o = new Options();
    o.setAllowSettingsAfterFormatInInsert(true);
    o.setMaxLineLength(120);
    o.setMaxQuerySize(262144);
    o.setMaxParserDepth(1000);

    // Order tracks the field declaration order in Options.java.
    assertEquals(
        Arrays.asList(
            "--max_line_length", "120",
            "--allow_settings_after_format_in_insert",
            "--max_query_size", "262144",
            "--max_parser_depth", "1000"),
        o.appendTo(new ArrayList<>()));
  }

  @Test
  public void appendTo_mixedBooleansAndValues_inDeclarationOrder() {
    Options o = new Options();
    o.setHilite(true);
    o.setOneline(true);
    o.setMultiquery(false); // skipped
    o.setSeed("HelloWorld");
    o.setMaxQuerySize(100);

    assertEquals(
        Arrays.asList(
            "--hilite", "--oneline", "--seed", "HelloWorld", "--max_query_size", "100"),
        o.appendTo(new ArrayList<>()));
  }

  @Test
  public void appendTo_seedWithShellMetacharactersIsPassedThroughVerbatim() {
    // ProcessBuilder delivers argv directly to execve — no shell to interpret these — so
    // spaces, `;`, `&`, glob chars, quotes etc. are safe to pass through unmodified.
    Options o = new Options();
    o.setSeed("a b; rm -rf / & echo $HOME `whoami`");

    assertEquals(
        Arrays.asList("--seed", "a b; rm -rf / & echo $HOME `whoami`"),
        o.appendTo(new ArrayList<>()));
  }

  @Test
  public void appendTo_seedWithAlphanumericIsKeptVerbatim() {
    Options o = new Options();
    o.setSeed("good_seed-123");

    assertEquals(Arrays.asList("--seed", "good_seed-123"), o.appendTo(new ArrayList<>()));
  }

  @Test(expected = IllegalArgumentException.class)
  public void appendTo_seedWithNullByteIsRejected() {
    // Null bytes truncate C-string args at the execve boundary — refuse rather than truncate.
    Options o = new Options();
    o.setSeed("bad\0value");

    o.appendTo(new ArrayList<>());
  }

  @Test(expected = IllegalArgumentException.class)
  public void appendTo_seedWithControlCharIsRejected() {
    // Newlines / tabs / other control chars corrupt anything that reads argv line-based.
    Options o = new Options();
    o.setSeed("line1\nline2");

    o.appendTo(new ArrayList<>());
  }

  @Test
  public void appendTo_returnsTheSameCollectionItWasGiven() {
    Options o = new Options();
    o.setHilite(true);

    List<String> sink = new ArrayList<>();
    sink.add("clickhouse-format");

    List<String> returned = o.appendTo(sink);

    assertSame(sink, returned);
    assertEquals(Arrays.asList("clickhouse-format", "--hilite"), sink);
  }

  // ---------- round-trip ----------

  @Test
  public void roundTrip_fromMultiMapThenAppendTo() {
    MultiMap m = MultiMap.caseInsensitiveMultiMap();
    m.add("hilite", "true");
    m.add("seed", "abc-123");
    m.add("maxLineLength", "80");

    List<String> argv = Options.fromMultiMap(mapper, m).appendTo(new ArrayList<>());

    assertTrue(argv.contains("--hilite"));
    assertEquals(80, Integer.parseInt(argv.get(argv.indexOf("--max_line_length") + 1)));
    assertEquals("abc-123", argv.get(argv.indexOf("--seed") + 1));
  }
}
