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

// Where the warnings go, and what becomes of the channel once set.
//
// THE GAP THESE TESTS CLOSE
//
// toChannel and its counterpart were called by no test: the only two places that
// read a warning back diverted System.err instead. So nobody checked that
// the channel is undone — and a channel that is not undone captures the next generation, on a thread
// that a pool reuses. It is the worst of symptoms: the warnings of one Maven module
// land in the log of the next module.
//
// TWO CLASSES FOR A SINGLE MECHANISM
//
// Warnings is the exported facade: set a channel, close it. Warner carries the
// mechanism, in a package the module does not export — "saying" is the gesture of
// extraction and rendering, and the user need not reach it. The tests, for their part, live in
// the module and see both.
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
        // That is what generate() needs: it sets its own over the Maven plugin's, and
        // closing it must restore the plugin's, not standard error.
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
        // The form generate() uses: the warnings enter the returned report and
        // go on reaching the real-time relay. Plainly replacing would make
        // silence Maven's log for the whole generation, that is, for the only moment
        // where it has something to write.
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
        // The default, that of a program launched by hand.
        String written = capturingStandardError(() -> Warner.say("nothing set"));

        assertEquals("Warning: nothing set", written.strip());
    }

    @Test
    void anAddedReaderWithoutChannel_stillWritesToStandardError() {
        // The case of a program launched by hand: generate() adds its reader to the default channel,
        // and the user must go on seeing their warnings go by.
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

    // What standard error receives while the action runs. The original stream is
    // restored in every case: losing it would silence every error of the suite.
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
