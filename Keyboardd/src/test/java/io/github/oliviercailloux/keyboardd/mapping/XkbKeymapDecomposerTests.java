package io.github.oliviercailloux.keyboardd.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class XkbKeymapDecomposerTests {
  @Test
  void testDecomposePc() throws Exception {
    CharSource source = Resources.asCharSource(KeyboardMapTests.class.getResource("pc - aa709f"),
        StandardCharsets.UTF_8);
    ImmutableMap<String, String> bySymbolsMap = XkbKeymapDecomposer.bySymbolsMap(source);
    assertEquals(ImmutableSet.of("pc105"), bySymbolsMap.keySet());
    assertTrue(bySymbolsMap.get("pc105").contains("key  <ESC> {[  Escape  ]};"));
    assertTrue(bySymbolsMap.get("pc105").contains("// Extra Korean keys:"));
  }

  @Test
  void testDecomposeUs() throws Exception {
    CharSource source = Resources.asCharSource(KeyboardMapTests.class.getResource("us - f7eb40"),
        StandardCharsets.UTF_8);
    ImmutableMap<String, String> bySymbolsMap = XkbKeymapDecomposer.bySymbolsMap(source);
    assertTrue(bySymbolsMap.keySet().contains("basic"), bySymbolsMap.keySet().toString());
    assertTrue(bySymbolsMap.keySet().contains("intl"));
    assertTrue(bySymbolsMap.get("basic").contains("key <TLDE>\t{[   grave,\t asciitilde\t]};"));
    assertTrue(bySymbolsMap.get("intl")
        .contains("key <TLDE> { [dead_grave, dead_tilde,         grave,       asciitilde ] };"));
  }

  @Test
  void testDecomposeFr() throws Exception {
    CharSource source =
        Resources.asCharSource(KeyboardMapTests.class.getResource("fr"), StandardCharsets.UTF_8);
    ImmutableMap<String, String> bySymbolsMap = XkbKeymapDecomposer.bySymbolsMap(source);
    assertTrue(bySymbolsMap.keySet().contains("basic"), bySymbolsMap.keySet().toString());
    assertTrue(bySymbolsMap.keySet().contains("olpc"));
    assertTrue(bySymbolsMap.keySet().contains("nodeadkeys"));
    assertTrue(bySymbolsMap.keySet().contains("oss"));
    assertTrue(bySymbolsMap.get("basic")
        .contains("key <AE01>\t{ [ ampersand,          1,  onesuperior,   exclamdown ]\t};"));
    assertTrue(
        bySymbolsMap.get("oss").contains("key <AE05>\t{ [        parenleft,                5,"
            + "          bracketleft,             0x1002013 ] };"));
  }
}
