package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.regex.Pattern;

public final class NamePattern {
    private final String written;

    private final Pattern regex;

    private volatile boolean matched;

    private NamePattern(String written, Pattern regex) {
        this.written = written;
        this.regex = regex;
    }

    public static NamePattern of(String written) {
        checkNotNull(written);
        checkArgument(!written.isBlank(), "A pattern cannot be empty or made of whitespace only.");

        return new NamePattern(written, Pattern.compile(toRegex(written)));
    }

    private static String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        int start = 0;

        while (true) {
            int star = pattern.indexOf('*', start);
            if (star < 0) {
                break;
            }

            if (star > start) {
                regex.append(Pattern.quote(pattern.substring(start, star)));
            }
            regex.append(".*");
            start = star + 1;
        }
        if (start < pattern.length()) {
            regex.append(Pattern.quote(pattern.substring(start)));
        }
        return regex.toString();
    }

    public boolean matches(String name) {
        checkNotNull(name);
        boolean result = regex.matcher(name).matches();
        if (result) {
            matched = true;
        }
        return result;
    }

    public boolean withoutEffect() {
        return !matched;
    }

    public String written() {
        return written;
    }

    @Override
    public String toString() {
        return written;
    }
}
