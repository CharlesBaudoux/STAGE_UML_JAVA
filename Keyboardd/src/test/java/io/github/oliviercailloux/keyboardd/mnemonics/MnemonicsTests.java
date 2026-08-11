package io.github.oliviercailloux.keyboardd.mnemonics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.keyboardd.TestResources;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MnemonicsTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(MnemonicsTests.class);

  @Test
  public void testDiff() throws Exception {
    Mnemonics latest = Mnemonics.latest();
    Mnemonics mnemonics238 =
        Mnemonics.fromSource(TestResources.charSource("mnemonics/xkbcommon-keysyms - 238d13.h"));
    assertEquals(2629, latest.byMnemonic().size());
    assertEquals(2572, mnemonics238.byMnemonic().size());
  }

  @Test
  public void testLatest() throws Exception {
    Mnemonics latest = Mnemonics.latest();
    LOGGER.debug("Keys: {}.", latest.byMnemonic());

    assertThrows(IllegalArgumentException.class, () -> latest.canonical("NOT THERE"));
    final CanonicalMnemonic voidSymbolCan = latest.canonical("VoidSymbol");
    final CanonicalMnemonic aCan = latest.canonical("A");
    final CanonicalMnemonic priorCan = latest.canonical("Prior");
    final CanonicalMnemonic extCan = latest.canonical("Ext16bit_L");
    final CanonicalMnemonic modeSwitchCan = latest.canonical("Mode_switch");
    final CanonicalMnemonic deadTildeCan = latest.canonical("dead_tilde");
    final CanonicalMnemonic codeCan = latest.canonical("Codeinput");
    final CanonicalMnemonic apostropheCan = latest.canonical("apostrophe");

    assertEquals("VoidSymbol", voidSymbolCan.mnemonic());
    assertEquals("A", aCan.mnemonic());
    assertEquals("Prior", priorCan.mnemonic());
    assertEquals("Ext16bit_L", extCan.mnemonic());
    assertEquals("Mode_switch", modeSwitchCan.mnemonic());
    assertEquals("dead_tilde", deadTildeCan.mnemonic());
    assertEquals("Codeinput", codeCan.mnemonic());
    assertEquals("apostrophe", apostropheCan.mnemonic());

    ImmutableMap<String, CanonicalMnemonic> mnemonics = latest.byMnemonic();
    ImmutableSet<String> canonicals = mnemonics.values().stream().map(CanonicalMnemonic::mnemonic)
        .collect(ImmutableSet.toImmutableSet());
    assertFalse(canonicals.contains("NOT THERE"));
    assertTrue(canonicals.contains("VoidSymbol"));
    assertTrue(canonicals.contains("A"));
    assertTrue(canonicals.contains("Prior"));
    assertFalse(canonicals.contains("Page_Up"));
    assertTrue(canonicals.contains("Ext16bit_L"));
    assertTrue(canonicals.contains("Mode_switch"));
    assertFalse(canonicals.contains("script_switch"));
    assertTrue(canonicals.contains("dead_tilde"));
    assertTrue(canonicals.contains("Codeinput"));
    assertFalse(canonicals.contains("Kanji_Bangou"));
    assertFalse(canonicals.contains("Hangul_Codeinput"));
    assertTrue(canonicals.contains("apostrophe"));

    assertFalse(mnemonics.keySet().contains("NOT THERE"));
    assertEquals(voidSymbolCan, mnemonics.get("VoidSymbol"));
    assertEquals(aCan, mnemonics.get("A"));
    assertEquals(priorCan, mnemonics.get("Prior"));
    assertEquals(priorCan, mnemonics.get("Page_Up"));
    assertEquals(extCan, mnemonics.get("Ext16bit_L"));
    assertEquals(modeSwitchCan, mnemonics.get("Mode_switch"));
    assertEquals(modeSwitchCan, mnemonics.get("script_switch"));
    assertEquals(deadTildeCan, mnemonics.get("dead_tilde"));
    assertEquals(codeCan, mnemonics.get("Codeinput"));
    assertEquals(apostropheCan, mnemonics.get("apostrophe"));
    assertEquals(apostropheCan, mnemonics.get("quoteright"));

    assertEquals(priorCan, latest.canonical("Page_Up"));
    assertEquals(modeSwitchCan, latest.canonical("script_switch"));
    assertEquals(apostropheCan, latest.canonical("quoteright"));

    assertFalse(voidSymbolCan.deprecated());
    assertFalse(aCan.deprecated());
    assertFalse(priorCan.deprecated());
    assertTrue(extCan.deprecated());
    assertFalse(modeSwitchCan.deprecated());
    assertFalse(deadTildeCan.deprecated());
    assertFalse(codeCan.deprecated());
    assertFalse(apostropheCan.deprecated());

    assertEquals(ImmutableSet.of("Page_Up", "SunPageUp"), priorCan.deprecatedAliases());
    // assertEquals(ImmutableSet.of(), deadTildeCan.deprecatedAliases());
    assertEquals(ImmutableSet.of("Kanji_Bangou", "Hangul_Codeinput"), codeCan.deprecatedAliases());
    assertEquals(ImmutableSet.of("quoteright"), apostropheCan.deprecatedAliases());
    assertEquals(ImmutableSet.of("SunAltGraph"), modeSwitchCan.deprecatedAliases());

    assertEquals(ImmutableSet.of(), aCan.nonDeprecatedAliases());
    assertEquals(ImmutableSet.of(), priorCan.nonDeprecatedAliases());
    assertTrue(modeSwitchCan.nonDeprecatedAliases().contains("script_switch"));
    assertEquals(ImmutableSet.of("dead_perispomeni"), deadTildeCan.nonDeprecatedAliases());
    assertEquals(ImmutableSet.of(), codeCan.nonDeprecatedAliases());
    assertEquals(ImmutableSet.of(), apostropheCan.nonDeprecatedAliases());

    assertEquals(ImmutableSet.of(), aCan.aliases());
    assertEquals(ImmutableSet.of("SunPageUp", "Page_Up"), priorCan.aliases());
    assertTrue(modeSwitchCan.aliases().contains("script_switch"));

    ImmutableBiMap<Integer, CanonicalMnemonic> byCode = latest.byCode();
    assertEquals("BackSpace", byCode.get(0xFF08).mnemonic());
    assertEquals("space", byCode.get(0x20).mnemonic());
    assertEquals("space", byCode.get(32).mnemonic());
    assertEquals("A", byCode.get(0x41).mnemonic());
    assertEquals("Wcircumflex", byCode.get(0x1000174).mnemonic());
    assertEquals("squareroot", byCode.get(0x100221a).mnemonic());
    assertEquals("radical", byCode.get(0x08d6).mnemonic());

    ImmutableBiMap<Integer, CanonicalMnemonic> byUcp = latest.byUcp();
    ImmutableSet<String> withUcp = byUcp.values().stream().map(CanonicalMnemonic::mnemonic)
        .collect(ImmutableSet.toImmutableSet());
    assertFalse(withUcp.contains("VoidSymbol"));
    assertFalse(withUcp.contains("KP_Space"));
    assertEquals("space", byUcp.get(UcpByCodeTests.ucp(" ")).mnemonic());
    assertEquals("A", byUcp.get(UcpByCodeTests.ucp("A")).mnemonic());
    assertEquals("Wcircumflex", byUcp.get(UcpByCodeTests.ucp("Ŵ")).mnemonic());
    assertEquals("squareroot", byUcp.get(UcpByCodeTests.ucp("√")).mnemonic());
  }

  @Test
  public void testLatestNonDepr() throws Exception {
    Mnemonics latest = Mnemonics.latest().withoutDeprecated();

    assertThrows(IllegalArgumentException.class, () -> latest.canonical("NOT THERE"));
    assertThrows(IllegalArgumentException.class, () -> latest.canonical("Ext16bit_L"));
    final CanonicalMnemonic voidSymbolCan = latest.canonical("VoidSymbol");
    final CanonicalMnemonic aCan = latest.canonical("A");
    final CanonicalMnemonic priorCan = latest.canonical("Prior");
    final CanonicalMnemonic modeSwitchCan = latest.canonical("Mode_switch");

    assertEquals("VoidSymbol", voidSymbolCan.mnemonic());
    assertEquals("A", aCan.mnemonic());
    assertEquals("Prior", priorCan.mnemonic());
    assertEquals("Mode_switch", modeSwitchCan.mnemonic());

    ImmutableMap<String, CanonicalMnemonic> mnemonics = latest.byMnemonic();
    ImmutableSet<String> canonicals = mnemonics.values().stream().map(CanonicalMnemonic::mnemonic)
        .collect(ImmutableSet.toImmutableSet());
    assertFalse(canonicals.contains("NOT THERE"));
    assertFalse(canonicals.contains("Ext16bit_L"));
    assertTrue(canonicals.contains("VoidSymbol"));
    assertTrue(canonicals.contains("A"));
    assertTrue(canonicals.contains("Prior"));
    assertFalse(canonicals.contains("Page_Up"));
    assertTrue(canonicals.contains("Mode_switch"));
    assertFalse(canonicals.contains("script_switch"));

    assertFalse(mnemonics.keySet().contains("NOT THERE"));
    assertFalse(mnemonics.keySet().contains("Ext16bit_L"));
    assertFalse(mnemonics.keySet().contains("Page_Up"));
    assertEquals(voidSymbolCan, mnemonics.get("VoidSymbol"));
    assertEquals(aCan, mnemonics.get("A"));
    assertEquals(priorCan, mnemonics.get("Prior"));
    assertEquals(modeSwitchCan, mnemonics.get("Mode_switch"));
    assertEquals(modeSwitchCan, mnemonics.get("script_switch"));

    assertEquals(modeSwitchCan, latest.canonical("script_switch"));

    assertFalse(voidSymbolCan.deprecated());
    assertFalse(aCan.deprecated());
    assertFalse(priorCan.deprecated());
    assertFalse(modeSwitchCan.deprecated());

    assertEquals(ImmutableSet.of(), priorCan.deprecatedAliases());
    assertEquals(ImmutableSet.of(), modeSwitchCan.deprecatedAliases());

    assertEquals(ImmutableSet.of(), aCan.nonDeprecatedAliases());
    assertEquals(ImmutableSet.of(), priorCan.nonDeprecatedAliases());
    assertTrue(modeSwitchCan.nonDeprecatedAliases().contains("script_switch"));

    assertEquals(ImmutableSet.of(), aCan.aliases());
    assertEquals(ImmutableSet.of(), priorCan.aliases());
    assertTrue(modeSwitchCan.aliases().contains("script_switch"));

    ImmutableBiMap<Integer, CanonicalMnemonic> byCode = latest.byCode();
    assertEquals("BackSpace", byCode.get(0xFF08).mnemonic());
    assertEquals("space", byCode.get(0x20).mnemonic());
    assertEquals("space", byCode.get(32).mnemonic());
    assertEquals("A", byCode.get(0x41).mnemonic());
    assertEquals("Wcircumflex", byCode.get(0x1000174).mnemonic());

    ImmutableBiMap<Integer, CanonicalMnemonic> byUcp = latest.byUcp();
    ImmutableSet<String> withUcp = byUcp.values().stream().map(CanonicalMnemonic::mnemonic)
        .collect(ImmutableSet.toImmutableSet());
    assertFalse(withUcp.contains("VoidSymbol"));
    assertFalse(withUcp.contains("KP_Space"));
    assertEquals("space", byUcp.get(UcpByCodeTests.ucp(" ")).mnemonic());
    assertEquals("A", byUcp.get(UcpByCodeTests.ucp("A")).mnemonic());
    assertEquals("Wcircumflex", byUcp.get(UcpByCodeTests.ucp("Ŵ")).mnemonic());
  }
}
