package io.github.oliviercailloux.umlg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.warning.Warner;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarningsTest {

    @Test
    void aSetChannel_receivesTheWarningsOfTheThread() {
        List<String> received = new ArrayList<>();

        try (Warnings.Channel channel = Warnings.redirectTo(received::add)) {
            Warner.say("the view is unreadable");
        }

        assertEquals(List.of("the view is unreadable"), received);
    }

    @Test
    void aClosedChannel_receivesNothingMore() {
        List<String> received = new ArrayList<>();

        try (Warnings.Channel channel = Warnings.redirectTo(received::add)) {
            Warner.say("during");
        }
        String onStandardError = capturingStandardError(() -> Warner.say("after"));

        assertEquals(List.of("during"), received, "The closed channel must capture nothing more.");
        assertTrue(onStandardError.contains("after"),
            "The thread must have found standard error again: " + onStandardError);
    }

    @Test
    void twoNestedChannels_undoInReverseOrder() {
        List<String> outside = new ArrayList<>();
        List<String> inside = new ArrayList<>();

        try (Warnings.Channel external = Warnings.redirectTo(outside::add)) {
            try (Warnings.Channel internal = Warnings.redirectTo(inside::add)) {
                Warner.say("inside");
            }
            Warner.say("outside");
        }

        assertEquals(List.of("inside"), inside);
        assertEquals(List.of("outside"), outside, "The outer channel must be restored, not lost.");
    }

    @Test
    void anAddedReader_doesNotSilenceTheChannelInPlace() {
        List<String> relay = new ArrayList<>();
        List<String> read = new ArrayList<>();

        try (Warnings.Channel external = Warnings.redirectTo(relay::add)) {
            try (Warnings.Channel addition = Warner.onTopOfTheCurrentChannel(read::add)) {
                Warner.say("both");
            }
        }

        assertEquals(List.of("both"), relay);
        assertEquals(List.of("both"), read);
    }

    @Test
    void withoutAnyChannel_theWarningGoesToPrefixedStandardError() {
        String written = capturingStandardError(() -> Warner.say("nothing set"));

        assertEquals("Warning: nothing set", written.strip());
    }

    @Test
    void anAddedReaderWithoutChannel_stillWritesToStandardError() {
        List<String> read = new ArrayList<>();

        String written = capturingStandardError(() -> {
            try (Warnings.Channel addition = Warner.onTopOfTheCurrentChannel(read::add)) {
                Warner.say("seen twice");
            }
        });

        assertEquals(List.of("seen twice"), read);
        assertTrue(written.contains("seen twice"), written);
    }

    @Test
    void aNullChannel_isRefused() {
        assertThrows(NullPointerException.class, () -> Warnings.redirectTo(null));
    }

    @Test
    void aNullMessage_isRefused() {
        assertThrows(NullPointerException.class, () -> Warner.say(null));
    }

    private static String capturingStandardError(Runnable action) {
        PrintStream origin = System.err;
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        System.setErr(new PrintStream(capture, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(origin);
        }
        return capture.toString(StandardCharsets.UTF_8);
    }
}
