package io.github.oliviercailloux.keyboardd.mnemonics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.io.CharSource;
import io.github.oliviercailloux.keyboardd.TestResources;
import io.github.oliviercailloux.keyboardd.mnemonics.KeysymReader.ParsedMnemonic;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeysymReaderTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(KeysymReaderTests.class);

  @Test
  public void testReadComment() throws Exception {
    CharSource source =
        CharSource.wrap("#define XKB_KEY_VoidSymbol  0xffffff  /* Void symbol */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(
        ImmutableSet.of(
            ParsedMnemonic.commented("VoidSymbol", Integer.parseInt("ffffff", 16), "Void symbol")),
        mns);
  }

  @Test
  public void testReadCommentQ() throws Exception {
    CharSource source = CharSource.wrap("#define XKB_KEY_XF86Option   0x1008ff6c  /* ?? */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet
        .of(ParsedMnemonic.commented("XF86Option", Integer.parseInt("1008ff6c", 16), "??")), mns);
  }

  @Test
  public void testReadCommentDont() throws Exception {
    CharSource source = CharSource
        .wrap("#define XKB_KEY_XF86RotationPB  0x1008ff75  /* don't use                   */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(
        ParsedMnemonic.commented("XF86RotationPB", Integer.parseInt("1008ff75", 16), "don't use")),
        mns);
  }

  @Test
  public void testReadCommentSpaces() throws Exception {
    CharSource source =
        CharSource.wrap("#define XKB_KEY_XF86Info     0x10081166      /*         KEY_INFO */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(
        ImmutableSet
            .of(ParsedMnemonic.commented("XF86Info", Integer.parseInt("10081166", 16), "KEY_INFO")),
        mns);
  }

  @Test
  public void testReadCommentv() throws Exception {
    CharSource source = CharSource.wrap(
        "#define XKB_KEY_XF86BrightnessAuto  0x100810f4      /* v3.16   KEY_BRIGHTNESS_AUTO */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.commented("XF86BrightnessAuto",
        Integer.parseInt("100810f4", 16), "v3.16   KEY_BRIGHTNESS_AUTO")), mns);
  }

  @Test
  public void testReadCommentU() throws Exception {
    CharSource source = CharSource.wrap(
        "#define XKB_KEY_XF86ScrollClick     0x1008ff7a  /* Use XKB mousekeys instead   */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.commented("XF86ScrollClick",
        Integer.parseInt("1008ff7a", 16), "Use XKB mousekeys instead")), mns);
  }

  @Test
  public void testReadNoComment() throws Exception {
    CharSource source = CharSource.wrap("#define XKB_KEY_Scroll_Lock                   0xff14\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(
        ImmutableSet.of(ParsedMnemonic.noComment("Scroll_Lock", Integer.parseInt("ff14", 16))),
        mns);
  }

  @Test
  public void testReadUnicode() throws Exception {
    CharSource source =
        CharSource.wrap("#define XKB_KEY_Tab   0xff09  /* U+0009 CHARACTER TABULATION */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.unicode("Tab", Integer.parseInt("ff09", 16), 9)),
        mns);
  }

  /** This fails if restricting the code regexp to lower case letters. */
  @Test
  public void testReadUnicodeUpperCase() throws Exception {
    CharSource source =
        CharSource.wrap("#define XKB_KEY_containsas  0x100220B  /* U+220B CONTAINS AS MEMBER */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.unicode("containsas",
        Integer.parseInt("100220B", 16), Integer.parseInt("220B", 16))), mns);
  }

  /** This fails if restricting the code regexp to lower case letters. */
  @Test
  public void testReadUnicodeUpperCaseU() throws Exception {
    CharSource source = CharSource
        .wrap("#define XKB_KEY_braille_dots_24  0x100280a  /* U+280a BRAILLE PATTERN DOTS-24 */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.unicode("braille_dots_24",
        Integer.parseInt("100280a", 16), Integer.parseInt("280a", 16))), mns);
  }

  @Test
  public void testReadUnicodeSpecific() throws Exception {
    CharSource source = CharSource.wrap("#define XKB_KEY_KP_Space   0xff80  /*<U+0020 SPACE>*/\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.specificUnicode("KP_Space",
        Integer.parseInt("ff80", 16), Integer.parseInt("20", 16))), mns);
  }

  @Test
  public void testReadDeprecatedAlias() throws Exception {
    CharSource source = CharSource
        .wrap("#define XKB_KEY_KP_Page_Up  0xff9a  /* deprecated alias for KP_Prior */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.deprecatedComment("KP_Page_Up",
        Integer.parseInt("ff9a", 16), "deprecated alias for KP_Prior")), mns);
  }

  @Test
  public void testReadAlias() throws Exception {
    CharSource source =
        CharSource.wrap("#define XKB_KEY_ISO_Group_Shift  0xff7e  /* Alias for Mode_switch */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.alias("ISO_Group_Shift",
        Integer.parseInt("ff7e", 16), "Mode_switch")), mns);
  }

  @Test
  public void testReadDeprecatedComment() throws Exception {
    CharSource source = CharSource
        .wrap("#define XKB_KEY_dead_small_schwa  0xfe8a  /* deprecated, remove in 2025 */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.deprecatedComment("dead_small_schwa",
        Integer.parseInt("fe8a", 16), "deprecated, remove in 2025")), mns);
  }

  @Test
  public void testReadDeprecatedCommentMissp() throws Exception {
    CharSource source =
        CharSource.wrap("#define XKB_KEY_guillemotleft  0x00ab  /* deprecated misspelling */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.deprecatedComment("guillemotleft",
        Integer.parseInt("ab", 16), "deprecated misspelling")), mns);
  }

  @Test
  public void testReadDeprecated() throws Exception {
    CharSource source = CharSource.wrap("#define XKB_KEY_quoteleft  0x0060  /* deprecated */\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(
        ParsedMnemonic.deprecatedComment("quoteleft", Integer.parseInt("60", 16), "deprecated")),
        mns);
  }

  @Test
  public void testReadUnicodeDeprecated() throws Exception {
    CharSource source = CharSource.wrap(
        "#define XKB_KEY_topleftradical  0x08a2  /*(U+250C BOX DRAWINGS LIGHT DOWN AND RIGHT)*/\n");
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(source);
    assertEquals(ImmutableSet.of(ParsedMnemonic.deprecatedUnicode("topleftradical",
        Integer.parseInt("8a2", 16), Integer.parseInt("250C", 16))), mns);
  }

  @Test
  public void testLatest() throws Exception {
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.latest();
    assertTrue(mns.size() >= 2572);
    {
      ImmutableSet<ParsedMnemonic> noComment =
          mns.stream().filter(s -> s.comment().isEmpty() && s.unicode().isEmpty())
              .collect(ImmutableSet.toImmutableSet());
      assertTrue(noComment.size() >= 353);
    }
    {
      ImmutableSet<ParsedMnemonic> unicodeS =
          mns.stream().filter(s -> s.specific()).collect(ImmutableSet.toImmutableSet());
      assertTrue(unicodeS.size() >= 20);
    }
    {
      ImmutableSet<ParsedMnemonic> unicodeD =
          mns.stream().filter(s -> s.unicode().isPresent() && s.deprecated())
              .collect(ImmutableSet.toImmutableSet());
      assertTrue(unicodeD.size() >= 43);
    }
    {
      ImmutableSet<ParsedMnemonic> matching =
          mns.stream().filter(s -> !s.comment().isEmpty()).collect(ImmutableSet.toImmutableSet());
      assertTrue(matching.size() >= 524);
    }
    {
      ImmutableSet<ParsedMnemonic> matching =
          mns.stream().filter(s -> !s.comment().isEmpty() && !s.deprecated())
              .collect(ImmutableSet.toImmutableSet());
      assertTrue(matching.size() >= 421);
    }
    {
      ImmutableSet<ParsedMnemonic> matching =
          mns.stream().filter(s -> s.unicode().isPresent() && !s.deprecated() && !s.specific())
              .collect(ImmutableSet.toImmutableSet());
      assertTrue(matching.size() >= 1632);
    }
  }

  @Test
  public void test238() throws Exception {
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.parse(TestResources.charSource(
        "mnemonics/xkbcommon-keysyms - 238d13.h"));
    /* grep -c "^#define X" "xkbcommon-keysyms - 238d13.h" */
    assertEquals(2572, mns.size());

    { /* grep "^#define X" "xkbcommon-keysyms - 238d13.h" | grep -c -v "/\*" */
      ImmutableSet<ParsedMnemonic> noComment =
          mns.stream().filter(s -> s.comment().isEmpty() && s.unicode().isEmpty())
              .collect(ImmutableSet.toImmutableSet());
      assertEquals(353, noComment.size());
    }
    { /* grep "^#define" "xkbcommon-keysyms - 238d13.h" | grep -c "/\*<" */
      ImmutableSet<ParsedMnemonic> unicodeS =
          mns.stream().filter(s -> s.specific()).collect(ImmutableSet.toImmutableSet());
      assertEquals(20, unicodeS.size());
    }
    { /* grep "^#define" "xkbcommon-keysyms - 238d13.h" | grep -c "/\*(" */
      ImmutableSet<ParsedMnemonic> unicodeD =
          mns.stream().filter(s -> s.unicode().isPresent() && s.deprecated())
              .collect(ImmutableSet.toImmutableSet());
      assertEquals(43, unicodeD.size());
    }
    { /* grep "^#define" "xkbcommon-keysyms - 238d13.h" | grep "/\*" | grep -c -v "U+" */
      ImmutableSet<ParsedMnemonic> matching =
          mns.stream().filter(s -> !s.comment().isEmpty()).collect(ImmutableSet.toImmutableSet());
      assertEquals(524, matching.size());
    }
    { /*
       * grep "^#define" "xkbcommon-keysyms - 238d13.h" | grep "/\*" | grep -v "/\* deprecated" |
       * grep -c -v "U+" – no, not so simple
       */
      // ImmutableSet<ParsedMnemonic> matching =
      //     mns.stream().filter(s -> !s.comment().isEmpty() && !s.deprecated())
      //         .collect(ImmutableSet.toImmutableSet());
      // assertEquals(421, matching.size());
    }
    { /* grep "^#define" "xkbcommon-keysyms - 238d13.h" | grep -c "/\* U+" */
      ImmutableSet<ParsedMnemonic> matching =
          mns.stream().filter(s -> s.unicode().isPresent() && !s.deprecated() && !s.specific())
              .collect(ImmutableSet.toImmutableSet());
      assertEquals(1632, matching.size());
    }
  }

  @Test
  public void testUcpNotToUniqueKeysymCodeIfIncludingDeprecatedOrSpecific() throws Exception {
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.latest();
    ImmutableSetMultimap<Integer, ParsedMnemonic> mnsByUcp =
        mns.stream().filter(m -> m.unicode().isPresent()).collect(ImmutableSetMultimap
            .toImmutableSetMultimap(m -> m.unicode().orElseThrow(VerifyException::new), m -> m));
    {
      int ucp = "+".codePoints().boxed().collect(MoreCollectors.onlyElement());
      ImmutableSet<ParsedMnemonic> mnsForUcp = mnsByUcp.get(ucp);
      assertEquals(2, mnsForUcp.size());
      UnmodifiableIterator<ParsedMnemonic> iterator = mnsForUcp.iterator();
      final ParsedMnemonic mn1 = iterator.next();
      final ParsedMnemonic mn2 = iterator.next();
      assertFalse(iterator.hasNext());
      assertEquals("KP_Add", mn1.mnemonic());
      assertTrue(mn1.specific());
      assertEquals("plus", mn2.mnemonic());
      assertFalse(mn2.specific());
      assertFalse(mn2.deprecated());
      assertNotEquals(mn1.code(), mn2.code());
    }
    {
      int ucp = ".".codePoints().boxed().collect(MoreCollectors.onlyElement());
      ImmutableSet<ParsedMnemonic> mnsForUcp = mnsByUcp.get(ucp);
      assertEquals(3, mnsForUcp.size());
      UnmodifiableIterator<ParsedMnemonic> iterator = mnsForUcp.iterator();
      final ParsedMnemonic mn1 = iterator.next();
      final ParsedMnemonic mn2 = iterator.next();
      final ParsedMnemonic mn3 = iterator.next();
      assertFalse(iterator.hasNext());
      assertEquals("KP_Decimal", mn1.mnemonic());
      assertTrue(mn1.specific());
      assertEquals("period", mn2.mnemonic());
      assertFalse(mn2.specific());
      assertFalse(mn2.deprecated());
      assertEquals("decimalpoint", mn3.mnemonic());
      assertTrue(mn3.deprecated());
      assertNotEquals(mn1.code(), mn2.code());
    }
    {
      int ucp = "<".codePoints().boxed().collect(MoreCollectors.onlyElement());
      ImmutableSet<ParsedMnemonic> mnsForUcp = mnsByUcp.get(ucp);
      assertEquals(2, mnsForUcp.size());
      UnmodifiableIterator<ParsedMnemonic> iterator = mnsForUcp.iterator();
      final ParsedMnemonic mn1 = iterator.next();
      final ParsedMnemonic mn2 = iterator.next();
      assertFalse(iterator.hasNext());
      assertEquals("less", mn1.mnemonic());
      assertFalse(mn1.specific());
      assertFalse(mn1.deprecated());
      assertEquals("leftcaret", mn2.mnemonic());
      assertTrue(mn2.deprecated());
      assertNotEquals(mn1.code(), mn2.code());
    }
    {
      int ucp = "_".codePoints().boxed().collect(MoreCollectors.onlyElement());
      ImmutableSet<ParsedMnemonic> mnsForUcp = mnsByUcp.get(ucp);
      assertEquals(2, mnsForUcp.size());
      UnmodifiableIterator<ParsedMnemonic> iterator = mnsForUcp.iterator();
      final ParsedMnemonic mn1 = iterator.next();
      final ParsedMnemonic mn2 = iterator.next();
      assertFalse(iterator.hasNext());
      assertEquals("underscore", mn1.mnemonic());
      assertFalse(mn1.specific());
      assertFalse(mn1.deprecated());
      assertEquals("underbar", mn2.mnemonic());
      assertTrue(mn2.deprecated());
      assertNotEquals(mn1.code(), mn2.code());
    }
    /*
     * … and more: KP_Add to KP_Divide, KP_0 to KP_9, KP_Return, KP_Tab, KP_Space, KP_Equal,
     * KP_Separator, KP_Decimal, macron and overbar, topleftradical and upleftcorner, horizconnector
     * and horizlinescan5, includedin and leftshoe, …
     */
  }

  /** See https://github.com/xkbcommon/libxkbcommon/issues/433 */
  @Test
  public void testUcpNotToUniqueKeysymCode() throws Exception {
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.latest();
    ImmutableSetMultimap<Integer,
        ParsedMnemonic> mnsByUcp = mns.stream().filter(m -> !m.deprecated())
            .filter(m -> !m.specific()).filter(m -> m.unicode().isPresent())
            .collect(ImmutableSetMultimap.toImmutableSetMultimap(
                m -> m.unicode().orElseThrow(VerifyException::new), m -> m));
    ImmutableSet<Integer> ambiguous = mnsByUcp.keys().stream()
        .filter(c -> mnsByUcp.get(c).size() >= 2).collect(ImmutableSet.toImmutableSet());
    int ucpRoot = "√".codePoints().boxed().collect(MoreCollectors.onlyElement());
    int ucpPartialDifferential = "∂".codePoints().boxed().collect(MoreCollectors.onlyElement());
    assertEquals(ImmutableSet.of(ucpRoot, ucpPartialDifferential), ambiguous);
  }

  @Test
  public void testDeprecatedFirst() throws Exception {
    int ucpBoxDrawingsLightDownAndRight =
        "┌".codePoints().boxed().collect(MoreCollectors.onlyElement());
    ImmutableSet<ParsedMnemonic> mns = KeysymReader.latest();
    ImmutableSet<ParsedMnemonic> mnsForUcp = mns.stream().filter(m -> m.unicode().isPresent())
        .filter(m -> m.unicode().get() == ucpBoxDrawingsLightDownAndRight)
        .collect(ImmutableSet.toImmutableSet());
    assertEquals(2, mnsForUcp.size());
    UnmodifiableIterator<ParsedMnemonic> iterator = mnsForUcp.iterator();
    final ParsedMnemonic mn1 = iterator.next();
    assertEquals("topleftradical", mn1.mnemonic());
    assertTrue(mn1.deprecated());
    final ParsedMnemonic mn2 = iterator.next();
    assertEquals("upleftcorner", mn2.mnemonic());
    assertFalse(mn2.deprecated());
  }
}
