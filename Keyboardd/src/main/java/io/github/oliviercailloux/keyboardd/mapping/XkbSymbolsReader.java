package io.github.oliviercailloux.keyboardd.mapping;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;

import static com.google.common.base.Verify.verify;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import com.google.common.primitives.Chars;
import io.github.oliviercailloux.keyboardd.utils.ParseUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A reader of XKB symbols files, which produces a {@link KeyboardMap}. The reader is quite crude;
 * it will read correctly only the simplest files.
 */
@UMLClass
public class XkbSymbolsReader {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(XkbSymbolsReader.class);

  private static final Pattern P_COMMENT = Pattern.compile("^( *//.*)| *$");
  private static final Pattern P_OTHER = Pattern
      .compile("^(default )?(partial.*)|(xkb_symbols .*)|( *key.type.*)|( *name.*)|( *include .+)|"
          + "( *modifier_map.*)|(\\};)$");
  private static final Pattern P_KEY = Pattern.compile(
      "^ *key[ \\t]+<(?<name>.+)>" + "[ \\t]*\\{[ \\t]*\\[[ \\t]*" + "(?<entries>.*[^ \\t])"
          + "[ \\t]*\\][ \\t]*" + "(,[ \\t]*type=\".+\"[ \\t]*)?" + "\\}[ \\t]*;[ \\t]*(//.*)?$");
  private static final ImmutableSet<Pattern> PATTERNS = ImmutableSet.of(P_COMMENT, P_OTHER, P_KEY);
  private static final Pattern P_UNICODE = Pattern.compile("U(?<unicode>[0-9a-fA-F]+)");
  private static final Pattern P_CODE = Pattern.compile("0x(?<code>[0-9a-fA-F]+)");
  private static final ImmutableSet<Pattern> PATTERNS_VALUES = ImmutableSet.of(P_UNICODE, P_CODE);

  /**
   * From
   * https://gitlab.freedesktop.org/xkeyboard-config/xkeyboard-config/-/blob/aa709f2f45e7b6164dd583389489043cf92c5b1c/symbols/pc
   */
  static CharSource commonSource() {
    return embeddedSource("pc - aa709f");
  }

  static CharSource embeddedSource(String name) {
    CharSource source =
        Resources.asCharSource(XkbSymbolsReader.class.getResource(name), StandardCharsets.UTF_8);
    return source;
  }

  static KeyboardMap embeddedMap(String name, String key) throws IOException {
    CharSource source = embeddedSource(name);
    String symbols = XkbKeymapDecomposer.bySymbolsMap(source).get(key);
    return read(CharSource.wrap(symbols));
  }

  /**
   * From
   * https://gitlab.freedesktop.org/xkeyboard-config/xkeyboard-config/-/blob/f7eb40592a5c4a24d6313ec94153d7e15567eeb3/symbols/us
   */
  static CharSource usSource() {
    return embeddedSource("us - f7eb40");
  }

  /**
   * Reads a keyboard map from the given source.
   *
   * @param source the XKB symbols file to read from
   * @return a (possibly empty) keyboard map
   * @throws IOException if an I/O error occurs
   */
  // La lecture generale d'abord, les trois cartes livrees ensuite : c'est l'ordre du diagramme
  // de reference, et « order declaration » le lit dans le code.
  @UMLInclude
  public static KeyboardMap read(CharSource source) throws IOException {
    ImmutableList<String> lines = source.readLines();

    final ImmutableListMultimap.Builder<String, KeysymEntry> keys =
        new ImmutableListMultimap.Builder<>();
    for (String line : lines) {
      Matcher matcher = ParseUtils.matcher(line, PATTERNS);
      if (matcher.pattern().equals(P_COMMENT)) {
        continue;
      } else if (matcher.pattern().equals(P_OTHER)) {
        continue;
      } else {
        verify(matcher.pattern().equals(P_KEY));
        String name = matcher.group("name");
        String entries = matcher.group("entries");
        final ImmutableList<KeysymEntry> values = parseEntries(entries);
        keys.putAll(name, values);
      }
    }
    ImmutableListMultimap<String, KeysymEntry> map = keys.build();
    return KeyboardMap.from(map);
  }

  @UMLInclude
  public static KeyboardMap common() {
    KeyboardMap first;
    ImmutableSet<KeyboardMap> maps;
    try {
      first = read(commonSource());
      final ImmutableSet.Builder<KeyboardMap> mapsBuilder = new ImmutableSet.Builder<>();
      mapsBuilder.add(read(embeddedSource("simplified srvr_ctrl")));
      mapsBuilder.add(embeddedMap("keypad - a1813cc5", "x11"));
      maps = mapsBuilder.build();
    } catch (IOException e) {
      throw new VerifyException(e);
    }
    KeyboardMap total = first;
    for (KeyboardMap map : maps) {
      total = total.overwrite(map);
    }
    return total;
  }

  @UMLInclude
  public static KeyboardMap us() {
    CharSource source = usSource();
    try {
      String symbolsMap = XkbKeymapDecomposer.bySymbolsMap(source).get("basic");
      return read(CharSource.wrap(symbolsMap));
    } catch (IOException e) {
      throw new VerifyException(e);
    }
  }

  @UMLInclude
  public static KeyboardMap usIntl() {
    CharSource source = usSource();
    try {
      String symbolsMap = XkbKeymapDecomposer.bySymbolsMap(source).get("intl");
      return read(CharSource.wrap(symbolsMap));
    } catch (IOException e) {
      throw new VerifyException(e);
    }
  }

  private static ImmutableList<KeysymEntry> parseEntries(String entriesOneStr) {
    String[] split = entriesOneStr.split(", *");
    ImmutableList<String> entriesMultStr = ImmutableList.copyOf(split);

    final ImmutableList.Builder<KeysymEntry> entries = new ImmutableList.Builder<>();
    for (String entryStr : entriesMultStr) {
      LOGGER.debug("Parsing entry: {}.", entryStr);
      Optional<Matcher> matcherOpt = ParseUtils.matcherOpt(entryStr, PATTERNS_VALUES);
      if (matcherOpt.isEmpty()) {
        String m = entryStr.strip();
        if (m.isEmpty()) {
          throw new IllegalArgumentException("Unsupported empty entry in " + entriesOneStr);
        }
        entries.add(new KeysymEntry.Mnemonic(m));
      } else {
        Matcher matcher = matcherOpt.orElseThrow(VerifyException::new);
        verify(matcher.matches());
        if (matcher.pattern().equals(P_UNICODE)) {
          String uStr = matcher.group("unicode");
          int u = Integer.parseInt(uStr, 16);
          entries.add(new KeysymEntry.Ucp(u));
        } else {
          verify(matcher.pattern().equals(P_CODE));
          String cStr = matcher.group("code");
          int c = Integer.parseInt(cStr, 16);
          entries.add(new KeysymEntry.Code(c));
        }
      }
    }
    return entries.build();
  }

  private XkbSymbolsReader() {}
}
