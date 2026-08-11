package io.github.oliviercailloux.keyboardd.utils;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseUtils {

  public static Matcher matcher(String line, Set<Pattern> patterns) {
    return matcherOpt(line, patterns).orElseThrow(
        () -> new IllegalArgumentException("Unrecognised pattern on line: ‘" + line + "’."));
  }

  public static Optional<Matcher> matcherOpt(String line, Set<Pattern> patterns) {
    final ImmutableSet.Builder<Matcher> matchersBuilder = new ImmutableSet.Builder<>();
    for (Pattern pattern : patterns) {
      Matcher matcher = pattern.matcher(line);
      if (matcher.matches()) {
        matchersBuilder.add(matcher);
      }
    }
    ImmutableSet<Matcher> matchers = matchersBuilder.build();
    verify(matchers.size() <= 1);

    return matchers.stream().collect(MoreCollectors.toOptional());
  }
}
