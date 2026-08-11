package io.github.oliviercailloux.keyboardd.representable;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.keyboardd.mapping.KeysymEntry;
import io.github.oliviercailloux.keyboardd.mapping.KeysymEntry.Mnemonic;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalKeysymEntry;
import io.github.oliviercailloux.keyboardd.mnemonics.CanonicalMnemonic;
import io.github.oliviercailloux.keyboardd.mnemonics.ImplicitUcp;
import java.util.Optional;

/**
 * Thanks to http://xahlee.info/comp/unicode_computing_symbols.html
 * (https://www.fileformat.info/info/unicode/block/control_pictures/index.htm Unicode Block 'Control
 * Pictures' might be useful as well)
 */
class DefaultRepresentations {
  private static final ImmutableMap<String, String> MN_TO_STR = mnToStr();
  /** If ucp key has canonical mnemonic, then that mnemonic is in MN_TO_STR. */
  private static final ImmutableMap<Integer, String> UCP_TO_STR = ucpToStr();

  private static ImmutableMap<String, String> mnToStr() {
    final ImmutableMap.Builder<String, String> reprsBuilder = new ImmutableMap.Builder<>();
    /*
     * “NoSymbol means "don't overwrite it"”,
     * https://who-t.blogspot.com/2020/09/user-specific-xkb-configuration-putting.html
     */
    reprsBuilder.put("NoSymbol", "");
    reprsBuilder.put("VoidSymbol", "");
    reprsBuilder.put("Escape", "⎋");
    reprsBuilder.put("XF86_Switch_VT_1", "VT1");
    reprsBuilder.put("XF86_Switch_VT_2", "VT2");
    reprsBuilder.put("XF86_Switch_VT_3", "VT3");
    reprsBuilder.put("XF86_Switch_VT_4", "VT4");
    reprsBuilder.put("XF86_Switch_VT_5", "VT5");
    reprsBuilder.put("XF86_Switch_VT_6", "VT6");
    reprsBuilder.put("XF86_Switch_VT_7", "VT7");
    reprsBuilder.put("XF86_Switch_VT_8", "VT8");
    reprsBuilder.put("XF86_Switch_VT_9", "VT9");
    reprsBuilder.put("XF86_Switch_VT_10", "VT10");
    reprsBuilder.put("XF86_Switch_VT_11", "VT11");
    reprsBuilder.put("XF86_Switch_VT_12", "VT12");
    reprsBuilder.put("Print", "⎙");
    reprsBuilder.put("Sys_Req", "🗲");
    reprsBuilder.put("Scroll_Lock", "🔒");
    reprsBuilder.put("Pause", "⏸");
    reprsBuilder.put("Break", "⎊");
    reprsBuilder.put("Delete", "⌦");
    reprsBuilder.put("Home", "⇱");
    reprsBuilder.put("Insert", "⎀");
    reprsBuilder.put("End", "⇲");
    reprsBuilder.put("Prior", "⇞");
    reprsBuilder.put("Next", "⇟");
    // reprsBuilder.put("twosuperior", "²");
    // reprsBuilder.put("threesuperior", "³");
    // reprsBuilder.put("onesuperior", "¹");
    reprsBuilder.put("deadcedilla", "̧");
    // reprsBuilder.put("ampersand", "&");
    reprsBuilder.put("deadcaron", "̌");
    reprsBuilder.put("deadogonek", "̨");
    // reprsBuilder.put("eacute", "é");
    // reprsBuilder.put("asciitilde", "~");
    // reprsBuilder.put("Eacute", "É");
    // reprsBuilder.put("quotedbl", "\"");
    // reprsBuilder.put("numbersign", "#");
    reprsBuilder.put("deadbreve", "̆");
    // reprsBuilder.put("apostrophe", "'");
    // reprsBuilder.put("braceleft", "{");
    // reprsBuilder.put("parenleft", "(");
    // reprsBuilder.put("bracketleft", "[");
    // reprsBuilder.put("minus", "-");
    // reprsBuilder.put("bar", "|");
    // reprsBuilder.put("egrave", "è");
    // reprsBuilder.put("grave", "`");
    // reprsBuilder.put("Egrave", "È");
    // reprsBuilder.put("underscore", "_");
    // reprsBuilder.put("backslash", "\\");
    // reprsBuilder.put("trademark", "™");
    // reprsBuilder.put("ccedilla", "ç");
    // reprsBuilder.put("asciicircum", "^");
    // reprsBuilder.put("Ccedilla", "Ç");
    // reprsBuilder.put("agrave", "à");
    // reprsBuilder.put("at", "@");
    // reprsBuilder.put("Agrave", "À");
    // reprsBuilder.put("parenright", ")");
    // reprsBuilder.put("degree", "°");
    // reprsBuilder.put("bracketright", "]");
    // reprsBuilder.put("notequal", "≠");
    // reprsBuilder.put("equal", "=");
    // reprsBuilder.put("plus", "+");
    // reprsBuilder.put("braceright", "}");
    // reprsBuilder.put("plusminus", "±");
    reprsBuilder.put("BackSpace", "⌫");
    reprsBuilder.put("Tab", "⇥");
    reprsBuilder.put("ISO_Left_Tab", "⇤");
    // reprsBuilder.put("ae", "æ");
    // reprsBuilder.put("AE", "Æ");
    // reprsBuilder.put("acircumflex", "â");
    // reprsBuilder.put("Acircumflex", "Â");
    // reprsBuilder.put("EuroSign", "€");
    // reprsBuilder.put("cent", "¢");
    // reprsBuilder.put("ecircumflex", "ê");
    // reprsBuilder.put("Ecircumflex", "Ê");
    // reprsBuilder.put("thorn", "þ");
    // reprsBuilder.put("THORN", "Þ");
    // reprsBuilder.put("ydiaeresis", "ÿ");
    // reprsBuilder.put("Ydiaeresis", "Ÿ");
    // reprsBuilder.put("ucircumflex", "û");
    // reprsBuilder.put("Ucircumflex", "Û");
    // reprsBuilder.put("icircumflex", "î");
    // reprsBuilder.put("Icircumflex", "Î");
    // reprsBuilder.put("oe", "œ");
    // reprsBuilder.put("OE", "Œ");
    // reprsBuilder.put("ocircumflex", "ô");
    // reprsBuilder.put("Ocircumflex", "Ô");
    reprsBuilder.put("dead_circumflex", "^");
    reprsBuilder.put("dead_diaeresis", "̈");
    reprsBuilder.put("dead_tilde", "̃");
    reprsBuilder.put("dead_abovering", "˚");
    // reprsBuilder.put("dollar", "$");
    // reprsBuilder.put("sterling", "£");
    // reprsBuilder.put("oslash", "ø");
    // reprsBuilder.put("Ooblique", "Ø");
    reprsBuilder.put("Return", "⏎");
    reprsBuilder.put("Caps_Lock", "⇪");
    // reprsBuilder.put("adiaeresis", "ä");
    // reprsBuilder.put("Adiaeresis", "Ä");
    // reprsBuilder.put("ssharp", "ß");
    // reprsBuilder.put("doublelowquotemark", "„");
    // reprsBuilder.put("ediaeresis", "ë");
    // reprsBuilder.put("Ediaeresis", "Ë");
    // reprsBuilder.put("leftsinglequotemark", "‘");
    // reprsBuilder.put("singlelowquotemark", "‚");
    // reprsBuilder.put("rightsinglequotemark", "’");
    // reprsBuilder.put("yen", "¥");
    // reprsBuilder.put("eth", "ð");
    // reprsBuilder.put("ETH", "Ð");
    // reprsBuilder.put("udiaeresis", "ü");
    // reprsBuilder.put("Udiaeresis", "Ü");
    // reprsBuilder.put("idiaeresis", "ï");
    // reprsBuilder.put("Idiaeresis", "Ï");
    // reprsBuilder.put("odiaeresis", "ö");
    // reprsBuilder.put("Odiaeresis", "Ö");
    // reprsBuilder.put("ugrave", "ù");
    // reprsBuilder.put("percent", "%");
    reprsBuilder.put("dead_acute", "´");
    reprsBuilder.put("dead_cedilla", "¸");
    reprsBuilder.put("dead_ogonek", "˛");
    reprsBuilder.put("dead_caron", "ˇ");
    reprsBuilder.put("dead_breve", "˘");
    // reprsBuilder.put("Ugrave", "Ù");
    // reprsBuilder.put("asterisk", "*");
    // reprsBuilder.put("mu", "μ");
    reprsBuilder.put("dead_grave", "̀");
    reprsBuilder.put("dead_macron", "̄");
    reprsBuilder.put("Shift_L", "⇧");
    reprsBuilder.put("Shift_R", "⇧");
    // reprsBuilder.put("less", "<");
    // reprsBuilder.put("greater", ">");
    // reprsBuilder.put("lessthanequal", "≤");
    // reprsBuilder.put("greaterthanequal", "≥");
    // reprsBuilder.put("guillemotleft", "«");
    // reprsBuilder.put("leftdoublequotemark", "“");
    // reprsBuilder.put("guillemotright", "»");
    // reprsBuilder.put("rightdoublequotemark", "”");
    // reprsBuilder.put("copyright", "©");
    // reprsBuilder.put("registered", "®");
    // reprsBuilder.put("0x100202F", "⍽");
    reprsBuilder.put("leftarrow", "←");
    reprsBuilder.put("downarrow", "↓");
    reprsBuilder.put("uparrow", "↑");
    // reprsBuilder.put("notsign", "¬");
    reprsBuilder.put("rightarrow", "→");
    // reprsBuilder.put("comma", ",");
    // reprsBuilder.put("question", "?");
    // reprsBuilder.put("questiondown", "¿");
    // reprsBuilder.put("semicolon", ";");
    // reprsBuilder.put("period", ".");
    // reprsBuilder.put("multiply", "×");
    // reprsBuilder.put("colon", ":");
    // reprsBuilder.put("slash", "/");
    // reprsBuilder.put("division", "÷");
    // reprsBuilder.put("exclam", "!");
    // reprsBuilder.put("section", "§");
    // reprsBuilder.put("exclamdown", "¡");

    reprsBuilder.put("space", "␣");
    reprsBuilder.put("nobreakspace", "⍽");
    reprsBuilder.put("Control_L", "⎈");
    reprsBuilder.put("Super_L", "⊞");
    reprsBuilder.put("Super_R", "⊞");
    reprsBuilder.put("Alt_L", "⎇");
    reprsBuilder.put("Meta_L", "◆");
    reprsBuilder.put("Alt_R", "⎇");
    reprsBuilder.put("Meta_R", "◆");
    reprsBuilder.put("Control_R", "⎈");
    reprsBuilder.put("Up", "🠉");
    reprsBuilder.put("Left", "🠈");
    reprsBuilder.put("Down", "🠋");
    reprsBuilder.put("Right", "🠊");
    return reprsBuilder.build();
  }

  private static ImmutableMap<Integer, String> ucpToStr() {
    final ImmutableMap.Builder<Integer, String> reprsBuilder = new ImmutableMap.Builder<>();
    reprsBuilder.put(0xA0, MN_TO_STR.get("nobreakspace"));
    reprsBuilder.put(0x202F, "╫");
    return reprsBuilder.build();
  }

  public static Representation represent(KeysymEntry e) {
    if (e instanceof Mnemonic m) {
      String mnemonic = m.mnemonic();
      if (MN_TO_STR.containsKey(mnemonic)) {
        return Representation.fromString(MN_TO_STR.get(mnemonic));
      }
      return Representation.fromString(mnemonic);
    }
    if (e instanceof KeysymEntry.Ucp u) {
      int ucp = u.ucp();
      return represent(ucp);
    }
    verify(e instanceof KeysymEntry.Code);
    return Representation.fromString(e.asString());
  }

  private static Representation represent(int ucp) {
    if (UCP_TO_STR.containsKey(ucp)) {
      return Representation.fromString(UCP_TO_STR.get(ucp));
    }
    return Representation.fromString(new String(Character.toChars(ucp)));
  }

  public static Representation represent(CanonicalKeysymEntry e) {
    if (e instanceof CanonicalMnemonic c) {
      String mnemonic = c.mnemonic();
      if (MN_TO_STR.containsKey(mnemonic)) {
        return Representation.fromString(MN_TO_STR.get(mnemonic));
      }
      Optional<Integer> ucpOpt = c.ucp();
      if (ucpOpt.isPresent()) {
        int ucp = ucpOpt.orElseThrow();
        verify(!UCP_TO_STR.containsKey(ucp));
        return represent(ucp);
      }
      return Representation.fromString(c.mnemonic());
    }

    verify(e instanceof ImplicitUcp);
    ImplicitUcp imp = (ImplicitUcp) e;
    int ucp = imp.ucp();
    return represent(ucp);
  }
}
