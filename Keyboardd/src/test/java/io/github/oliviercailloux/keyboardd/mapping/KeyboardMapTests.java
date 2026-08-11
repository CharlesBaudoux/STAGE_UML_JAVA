package io.github.oliviercailloux.keyboardd.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.github.oliviercailloux.jaris.io.PathUtils;
import io.github.oliviercailloux.jaris.xml.DomHelper;
import io.github.oliviercailloux.keyboardd.mapping.KeysymEntry.Mnemonic;
import io.github.oliviercailloux.keyboardd.representable.SvgKeyboard;
import io.github.oliviercailloux.keyboardd.xkeys.Xkeys;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

@SuppressWarnings("checkstyle:LocalVariableName")
public class KeyboardMapTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(KeyboardMapTests.class);

  private static final String ALPHA_UCP_HEX = "3B1";
  private static final String A_GREEK_UCP_HEX = "391";
  private static final String DELTA_UCP_HEX = "394";

  @Test
  void testReadPart() throws Exception {
    CharSource source = Resources.asCharSource(KeyboardMapTests.class.getResource("fr mine"),
        StandardCharsets.UTF_8);
    ImmutableList<String> lines = source.readLines();
    CharSource sourceReduced =
        CharSource.wrap(lines.subList(0, 23).stream().collect(Collectors.joining("\n")));
    KeyboardMap kbMap = XkbSymbolsReader.read(sourceReduced);

    int alphaUcp = Integer.parseInt(ALPHA_UCP_HEX, 16);
    assertEquals("α".codePointAt(0), alphaUcp);
    int aGreekUcp = Integer.parseInt(A_GREEK_UCP_HEX, 16);
    assertEquals("Α".codePointAt(0), aGreekUcp);

    assertEquals(ImmutableSet.of("AD01"), kbMap.names());

    assertEquals(new KeysymEntry.Mnemonic("a"), kbMap.entries("AD01").get(0));
    assertEquals(new KeysymEntry.Mnemonic("A"), kbMap.entries("AD01").get(1));
    assertEquals(new KeysymEntry.Mnemonic("ae"), kbMap.entries("AD01").get(2));
    assertEquals(new KeysymEntry.Mnemonic("AE"), kbMap.entries("AD01").get(3));
    assertEquals(new KeysymEntry.Ucp(945), kbMap.entries("AD01").get(4));
    assertEquals(new KeysymEntry.Ucp(913), kbMap.entries("AD01").get(5));

    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromMnemonic("a"));
    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromMnemonic("A"));
    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromMnemonic("ae"));
    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromMnemonic("AE"));
    assertEquals(ImmutableSet.of(), kbMap.namesFromMnemonic("Unknown"));
    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromUcp(alphaUcp));
    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromUcp(aGreekUcp));
    assertEquals(ImmutableSet.of(), kbMap.namesFromUcp(100));
    assertEquals(ImmutableSet.of(), kbMap.namesFromCode(100));
  }

  @Test
  void testReadOss() throws Exception {
    CharSource source = Resources.asCharSource(KeyboardMapTests.class.getResource("fr(oss)"),
        StandardCharsets.UTF_8);
    KeyboardMap kbMap = XkbSymbolsReader.read(source);
    assertEquals(new KeysymEntry.Mnemonic("a"), kbMap.entries("AD01").get(0));
  }

  @Test
  void testRead() throws Exception {
    CharSource source = Resources.asCharSource(KeyboardMapTests.class.getResource("fr mine"),
        StandardCharsets.UTF_8);
    KeyboardMap kbMap = XkbSymbolsReader.read(source);

    int alphaUcp = Integer.parseInt(ALPHA_UCP_HEX, 16);
    assertEquals("α".codePointAt(0), alphaUcp);
    int deltaUcp = Integer.parseInt(DELTA_UCP_HEX, 16);
    assertEquals("Δ".codePointAt(0), deltaUcp);

    assertEquals(56, kbMap.names().size());

    assertEquals(new KeysymEntry.Mnemonic("a"), kbMap.entries("AD01").get(0));
    assertEquals(new KeysymEntry.Mnemonic("A"), kbMap.entries("AD01").get(1));
    assertEquals(new KeysymEntry.Mnemonic("ae"), kbMap.entries("AD01").get(2));
    assertEquals(new KeysymEntry.Mnemonic("AE"), kbMap.entries("AD01").get(3));
    assertEquals(new KeysymEntry.Ucp(945), kbMap.entries("AD01").get(4));
    assertEquals(new KeysymEntry.Ucp(913), kbMap.entries("AD01").get(5));

    assertEquals(ImmutableSet.of("AD01", "NMLK"), kbMap.namesFromMnemonic("a"));
    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromMnemonic("A"));
    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromMnemonic("ae"));
    assertEquals(ImmutableSet.of("AD01"), kbMap.namesFromMnemonic("AE"));
    assertEquals(ImmutableSet.of("AE02"), kbMap.namesFromMnemonic("2"));
    assertEquals(ImmutableSet.of(), kbMap.namesFromMnemonic("Unknown"));
    assertEquals(ImmutableSet.of("AD01", "KP0", "KP1", "KP2"), kbMap.namesFromUcp(alphaUcp));
    assertEquals(ImmutableSet.of("AC03"), kbMap.namesFromUcp(deltaUcp));
    assertEquals(ImmutableSet.of(), kbMap.namesFromUcp(100));
    assertEquals(ImmutableSet.of(), kbMap.namesFromCode(100));
  }

  @Test
  void testRead0x() throws Exception {
    CharSource source = Resources.asCharSource(KeyboardMapTests.class.getResource("fr(oss)"),
        StandardCharsets.UTF_8);
    KeyboardMap kbMap = XkbSymbolsReader.read(source);

    assertEquals(ImmutableSet.of(), kbMap.namesFromCode(30));
    assertEquals(ImmutableSet.of("AB07"), kbMap.namesFromCode(0x1002026));
  }

  @Test
  void testReadAliasesWrong() throws Exception {
    CharSource source = Resources.asCharSource(
        KeyboardMapTests.class.getResource("Symbols using aliases wrong"), StandardCharsets.UTF_8);
    KeyboardMap kbMap = XkbSymbolsReader.read(source);
    ImmutableMap<String, String> canonicalByAlias = Xkeys.latest().canonicalByAlias();
    IllegalStateException thrown =
        assertThrows(IllegalStateException.class, () -> kbMap.canonicalize(canonicalByAlias));
    LOGGER.debug("Thrown message: “{}”.", thrown.getMessage());
    assertTrue(thrown.getMessage().contains("BKSL"));
  }

  @Test
  void testReadAliases() throws Exception {
    CharSource source = Resources.asCharSource(
        KeyboardMapTests.class.getResource("Symbols using aliases good"), StandardCharsets.UTF_8);
    KeyboardMap kbMap = XkbSymbolsReader.read(source);
    assertEquals(ImmutableSet.of("AD03", "AC12"), kbMap.names());
    assertEquals(ImmutableList.of(new KeysymEntry.Mnemonic("e")), kbMap.entries("AD03"));
    assertEquals(ImmutableList.of(new KeysymEntry.Mnemonic("b")), kbMap.entries("AC12"));

    ImmutableMap<String, String> canonicalByAlias = Xkeys.latest().canonicalByAlias();
    KeyboardMap canonicalized = kbMap.canonicalize(canonicalByAlias);
    assertEquals(ImmutableSet.of("AD03", "BKSL"), canonicalized.names());
    assertEquals(ImmutableList.of(new KeysymEntry.Mnemonic("e")), canonicalized.entries("AD03"));
    assertEquals(ImmutableList.of(new KeysymEntry.Mnemonic("b")), canonicalized.entries("BKSL"));
  }

  @Test
  void testReadCommon() throws Exception {
    KeyboardMap kbMap = XkbSymbolsReader.common();
    assertTrue(kbMap.names().contains("RTRN"));
    assertTrue(kbMap.names().contains("LSGT"));
    assertTrue(kbMap.names().contains("OUTP"));
    assertEquals(ImmutableList.of(new KeysymEntry.Mnemonic("Return")), kbMap.entries("RTRN"));
    assertEquals(
        ImmutableList.of(new KeysymEntry.Mnemonic("less"), new KeysymEntry.Mnemonic("greater"),
            new KeysymEntry.Mnemonic("bar"), new KeysymEntry.Mnemonic("brokenbar")),
        kbMap.entries("LSGT"));
    assertEquals(ImmutableList.of(new KeysymEntry.Mnemonic("XF86Display")), kbMap.entries("OUTP"));
  }

  @Test
  void testReadUs() throws Exception {
    KeyboardMap kbMap = XkbSymbolsReader.us();
    assertTrue(kbMap.names().contains("TLDE"));
    assertTrue(kbMap.names().contains("AD01"));
    assertTrue(kbMap.names().contains("AB10"));
    assertFalse(kbMap.names().contains("LSGT"));
    assertEquals(
        ImmutableList.of(new KeysymEntry.Mnemonic("grave"), new KeysymEntry.Mnemonic("asciitilde")),
        kbMap.entries("TLDE"));
    assertEquals(ImmutableList.of(new KeysymEntry.Mnemonic("q"), new KeysymEntry.Mnemonic("Q")),
        kbMap.entries("AD01"));
    assertEquals(
        ImmutableList.of(new KeysymEntry.Mnemonic("slash"), new KeysymEntry.Mnemonic("question")),
        kbMap.entries("AB10"));
  }

  @Test
  void testReadUsIntl() throws Exception {
    KeyboardMap kbMap = XkbSymbolsReader.usIntl();
    assertTrue(kbMap.names().contains("TLDE"));
    assertTrue(kbMap.names().contains("AD01"));
    assertTrue(kbMap.names().contains("AB10"));
    assertTrue(kbMap.names().contains("LSGT"));
    assertEquals(ImmutableList.of(new KeysymEntry.Mnemonic("dead_grave"),
        new KeysymEntry.Mnemonic("dead_tilde"), new KeysymEntry.Mnemonic("grave"),
        new KeysymEntry.Mnemonic("asciitilde")), kbMap.entries("TLDE"));
    assertEquals(
        ImmutableList.of(new KeysymEntry.Mnemonic("q"), new KeysymEntry.Mnemonic("Q"),
            new KeysymEntry.Mnemonic("adiaeresis"), new KeysymEntry.Mnemonic("Adiaeresis")),
        kbMap.entries("AD01"));
    assertEquals(
        ImmutableList.of(new KeysymEntry.Mnemonic("backslash"), new KeysymEntry.Mnemonic("bar"),
            new KeysymEntry.Mnemonic("backslash"), new KeysymEntry.Mnemonic("bar")),
        kbMap.entries("LSGT"));
  }

  @Test
  void testOverwrite() throws Exception {
    KeyboardMap map = XkbSymbolsReader.common().overwrite(XkbSymbolsReader.us());
    assertTrue(map.entries("LCTL").size() == 1);
    assertEquals(ImmutableList.of(new Mnemonic("Control_L")), map.entries("LCTL"));
  }
}
