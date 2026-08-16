package io.github.oliviercailloux.umlg.rendering;

import io.github.oliviercailloux.umlg.warning.Warner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Proportions {
    private static final double REPORT_MAXIMUM = 4.0;

    private static final int SIZE_SIGNIFICANT = 1500;

    private static final Pattern WIDTH_SVG = Pattern.compile("width=\"(\\d+)px\"");

    private static final Pattern HEIGHT_SVG = Pattern.compile("height=\"(\\d+)px\"");

    private Proportions() {}

    static boolean check(Path file, String view) throws IOException {
        int[] dimensions = dimensions(file);
        if (dimensions == null) {
            return false;
        }
        int width = dimensions[0];
        int height = dimensions[1];
        if (width == 0 || height == 0) {
            return false;
        }
        double report = Math.max((double) width / height, (double) height / width);

        if (report <= REPORT_MAXIMUM
            || Math.max(width, height) < SIZE_SIGNIFICANT) {
            return false;
        }
        Warner.say("view \"" + view + "\" is " + width + "×" + height
            + " pixels, a ratio of " + Math.round(report) + ":1. Such a diagram is hard to read"
            + " once printed. Try another reading direction for this view, split it into smaller"
            + " ones, or show fewer members.");
        return true;
    }

    private static int[] dimensions(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".svg")) {
            String start = header(file, 2048);
            Matcher width = WIDTH_SVG.matcher(start);
            Matcher height = HEIGHT_SVG.matcher(start);
            return width.find() && height.find()
                ? new int[] {Integer.parseInt(width.group(1)),
                    Integer.parseInt(height.group(1))}
                : null;
        }
        if (name.endsWith(".png")) {
            byte[] bytes = Files.readAllBytes(file);

            if (bytes.length < 24) {
                return null;
            }
            return new int[] {integer(bytes, 16), integer(bytes, 20)};
        }
        return null;
    }

    private static String header(Path file, int bytes) throws IOException {
        byte[] start = new byte[bytes];
        int read;
        try (InputStream stream = Files.newInputStream(file)) {
            read = stream.readNBytes(start, 0, bytes);
        }
        return new String(start, 0, read, StandardCharsets.UTF_8);
    }

    private static int integer(byte[] bytes, int start) {
        return ((bytes[start] & 0xFF) << 24) | ((bytes[start + 1] & 0xFF) << 16)
            | ((bytes[start + 2] & 0xFF) << 8) | (bytes[start + 3] & 0xFF);
    }
}
