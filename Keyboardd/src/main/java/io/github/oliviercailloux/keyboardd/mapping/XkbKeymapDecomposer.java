package io.github.oliviercailloux.keyboardd.mapping;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XkbKeymapDecomposer {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(XkbKeymapDecomposer.class);

  private static final String INTRO = "^xkb_symbols \"(?<name>[^\"]++)\" \\{";
  private static final String SKIP = "[\\n\\r]*+";
  private static final String INSIDE_BRACKETS =
      "([^\\{\\}]*+\\{[^\\}]*+\\}([^/\\n]*//[^\\n]*$)?)*+";
  private static final Pattern SYMBOLS = Pattern.compile(
      INTRO + SKIP + "(?<contents>" + INSIDE_BRACKETS + "[^\\{\\}]++)" + SKIP + "\\};$",
      Pattern.MULTILINE | Pattern.DOTALL);

  public static ImmutableMap<String, String> bySymbolsMap(CharSource source) throws IOException {
    Matcher matcher = SYMBOLS.matcher(source.read());
    final ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();

    while (matcher.find()) {
      String name = matcher.group("name");
      String symbols = matcher.group("contents");
      builder.put(name, symbols);
    }
    return builder.build();
  }
}
