package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkNotNull;

public final class PlantUmlEscaper {
    private static final String Ampersand = "&#38;";
    private static final String ANGLE_BRACKET_OPENING = "&#60;";
    private static final String ANGLE_BRACKET_CLOSING = "&#62;";
    private static final String Quote = "&#34;";

    private static final String MARKERS_DOUBLES = "*/~-_[";

    private static final String LINE_MARKERS = "#*=|";

    private PlantUmlEscaper() {}

    private static boolean opensAMarkup(String text, int i) {
        char c = text.charAt(i);
        return MARKERS_DOUBLES.indexOf(c) >= 0
            && i + 1 < text.length() && text.charAt(i + 1) == c;
    }

    private static void appendNeutralized(StringBuilder output, String text, int i) {
        if (opensAMarkup(text, i)) {
            output.append("&#").append((int) text.charAt(i)).append(';');
            return;
        }
        appendIfPrintable(output, text.charAt(i));
    }

    public static String label(String text) {
        checkNotNull(text);

        StringBuilder output = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> output.append(Ampersand);
                case '<' -> output.append(ANGLE_BRACKET_OPENING);
                case '>' -> output.append(ANGLE_BRACKET_CLOSING);
                case '"' -> output.append(Quote);

                case '\\' -> output.append("\\\\");
                case '\n' -> output.append("\\n");
                case '\r' -> {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    output.append("\\n");
                }
                default -> appendNeutralized(output, text, i);
            }
        }
        return output.toString();
    }

    public static String lineStart(String text) {
        checkNotNull(text);

        String escaped = label(text);
        if (escaped.isEmpty() || LINE_MARKERS.indexOf(escaped.charAt(0)) < 0) {
            return escaped;
        }
        return neutralizeFirstCharacter(escaped);
    }

    public static String noteBloc(String text) {
        checkNotNull(text);

        String[] lines = text.split("\r\n|\r|\n", -1);

        StringBuilder output = new StringBuilder(text.length() + 16);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                output.append('\n');
            }
            output.append(neutralizeFirstCharacter(label(lines[i])));
        }
        return output.toString();
    }

    private static String neutralizeFirstCharacter(String line) {
        if (line.isEmpty() || line.charAt(0) == '&' || line.charAt(0) == '\\') {
            return line;
        }

        int first = line.codePointAt(0);
        return "&#" + first + ";" + line.substring(Character.charCount(first));
    }

    public static String stereotype(String text) {
        checkNotNull(text);

        StringBuilder output = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> output.append(Ampersand);
                case '<' -> output.append(ANGLE_BRACKET_OPENING);
                case '>' -> output.append(ANGLE_BRACKET_CLOSING);
                case '"' -> output.append(Quote);
                case '\n', '\r' -> output.append(' ');
                default -> appendNeutralized(output, text, i);
            }
        }
        return output.toString();
    }

    private static void appendIfPrintable(StringBuilder output, char c) {
        if (c >= ' ' && c != 0x7F) {
            output.append(c);
        }
    }
}
