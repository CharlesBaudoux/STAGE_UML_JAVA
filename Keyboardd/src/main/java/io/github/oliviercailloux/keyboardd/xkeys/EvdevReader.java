package io.github.oliviercailloux.keyboardd.xkeys;

import static com.google.common.base.Verify.verify;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import io.github.oliviercailloux.keyboardd.utils.ParseUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class EvdevReader {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(EvdevReader.class);

  private static final Pattern P_NOTHING = Pattern.compile("^[ \\t]*$");
  private static final Pattern P_COMMENT = Pattern.compile("^[ \\t]*((//)|#).*");
  private static final Pattern P_OTHER = Pattern.compile(
      "^[ \\t]*((default .*)|(xkb_keycodes.*)|(};$)|(minimum.*)|(maximum.*)|(indicator .*))");
  private static final Pattern P_NAME_CODE =
      Pattern.compile("^[ \\t]*<(?<name>[^>]+)>[ \\t]*=[ \\t]*(?<code>[0-9]+);.*");
  private static final Pattern P_ALIAS_NAME_CODE = Pattern
      .compile("^[ \\t]*alias[ \\t]+<(?<newName>[^>]+)>[ \\t]*=[ \\t]*<(?<previousName>[^>]+)>;.*");
  private static final ImmutableSet<Pattern> PATTERNS =
      ImmutableSet.of(P_NOTHING, P_COMMENT, P_OTHER, P_NAME_CODE, P_ALIAS_NAME_CODE);

  public static Xkeys latest() {
    CharSource evdev = Resources.asCharSource(EvdevReader.class.getResource("evdev - 733b90"),
        StandardCharsets.UTF_8);
    try {
      return parse(evdev);
    } catch (IOException e) {
      throw new VerifyException(e);
    }
  }

  public static Xkeys parse(CharSource evdev) throws IOException {
    ImmutableList<String> lines = evdev.readLines();

    final ImmutableBiMap.Builder<String, Short> builder = new ImmutableBiMap.Builder<>();
    final ImmutableMap.Builder<String, String> builderAliases = new ImmutableMap.Builder<>();

    for (String line : lines) {
      Matcher matcher = ParseUtils.matcher(line, PATTERNS);
      if (matcher.pattern().equals(P_NOTHING)) {
        continue;
      } else if (matcher.pattern().equals(P_COMMENT)) {
        continue;
      } else if (matcher.pattern().equals(P_OTHER)) {
        continue;
      } else if (matcher.pattern().equals(P_NAME_CODE)) {
        String name = matcher.group("name");
        String codeStr = matcher.group("code");
        short code = Short.parseShort(codeStr);
        builder.put(name, code);
      } else {
        verify(matcher.pattern().equals(P_ALIAS_NAME_CODE));
        String newName = matcher.group("newName");
        String previousName = matcher.group("previousName");
        builderAliases.put(newName, previousName);
      }
    }

    return Xkeys.fromMaps(builder.build(), builderAliases.build());
  }
}
