package io.github.oliviercailloux.umlg.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.Warnings;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// Reading the .class file: the constant tank, the descriptors, and failure.
//
// What these tests protect fits in one sentence: the constant tank is crossed at
// blindly, and a badly sized entry shifts everything that follows. The shift does not show —
// it raises nothing, it simply returns members filed in an absurd order, or a file
// declared unreadable. Hence fixtures chosen for the shape of their tank rather than for what
// qu'ils font.
//
// The fixtures carry no @UMLClass: they have nothing to do on a diagram, and the tests
// integration tests sweep the whole of target/test-classes.
class ClassFileOrderTest {

    // One fixture per tricky constant nature.
    //
    // A long and a double take TWO entries of the tank: the second does not exist, and the
    // the reader must skip the index. A lambda, for its part, is written as invokedynamic, which forces a
    // CONSTANT_MethodHandle — three bytes, neither two nor four. These three natures appear
    // in no other fixture of the suite: without this one, the reader was never exercised there
    // where it can shift.
    static class Tank {
        static final long Large = 4_294_967_296L;
        static final double Half = 0.5d;
        static final float Third = 0.33f;
        static final int Account = 70_000;
        static final String Name = "reservoir";

        static Supplier<String> namer() {
            return () -> Name + Large;
        }
    }

    @Test
    void aLongAndALambda_doNotShiftTheReading() {
        // If the long or the double had been counted as a single entry, or the
        // MethodHandle for two bytes, the rest of the file would be read askew: the read
        // would fail (null) or return names other than those.
        ImmutableList<String> fields = ClassFileOrder.fields(Tank.class);

        assertEquals(List.of("Large", "Half", "Third", "Account", "Name"), fields);
    }

    @Test
    void methodsOfAFileWithLongConstants_areReadInOrder() {
        // The method table comes after the tank: it is reached correctly only
        // whether each entry was skipped by the right length. The lambda adds here a
        // synthetic, which reading returns like the others — it is the caller that drops it.
        ImmutableList<String> methods = ClassFileOrder.methods(Tank.class);

        // The constructor opens the table, before the written methods; the lambda body
        // closed, under the name javac gives it. Reflection drops those two later —
        // here, everything the file holds really is read.
        assertEquals("<init>()V", methods.get(0));
        assertEquals("namer()Ljava/util/function/Supplier;", methods.get(1));
        assertTrue(methods.stream().anyMatch(m -> m.startsWith("lambda$")), methods::toString);
    }

    // --- The descriptors, the other half of the match ---

    static class Signatures {
        public void nothing() {}

        public boolean all(byte b, char c, short s, int i, long l, float f, double d) {
            return true;
        }

        public String[] arrays(int[] integers) {
            return integers.length == 0 ? null : new String[0];
        }
    }

    private static String key(String name) {
        Method method = Arrays.stream(Signatures.class.getDeclaredMethods())
            .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        return ClassFileOrder.key(method);
    }

    @Test
    void primitives_serveTheirJvmLetter() {
        // The key computed from reflection must be written exactly like the one read in the
        // file, otherwise no member is found again and the whole box goes to the end of the list.
        assertEquals("nothing()V", key("nothing"));
        assertEquals("all(BCSIJFD)Z", key("all"));
    }

    @Test
    void arrays_keepTheirBrackets() {
        assertEquals("arrays([I)[Ljava/lang/String;", key("arrays"));
    }

    @Test
    void theComputedKey_isTheOneReadInTheFile() {
        // The real contract: the two halves meet. Comparing it on the real methods
        // is better than freezing a string written by hand on both sides.
        ImmutableList<String> read = ClassFileOrder.methods(Signatures.class);

        // isSynthetic drops what coverage instrumentation adds to the loaded class
        // ($jacocoInit): what reflection sees then is not what the file contains.
        for (Method method : Signatures.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            assertTrue(read.contains(ClassFileOrder.key(method)),
                () -> ClassFileOrder.key(method) + " missing from " + read);
        }
    }

    // --- The missing file ---

    @Test
    void aClassWithoutFile_isReturnedNull() {
        // The class of a lambda is built at run time: it has no .class file to
        // read back. It is the only read failure a test can produce without faking a
        // file, and that is the one that counts — the generation goes on, in alphabetical order.
        Runnable lambda = () -> {};

        assertNull(ClassFileOrder.fields(lambda.getClass()));
        assertNull(ClassFileOrder.methods(lambda.getClass()));
    }

    @Test
    void lostOrder_isSaidOnlyOncePerClass() {
        // The file is asked for the fields, for the methods, for the links and for
        // the bubbles. The same finding repeated four times teaches nothing more the second time,
        // and drowns the other warnings.
        Runnable lambda = () -> {};
        Rules rules = Rules.read("{ \"order\": \"declaration\" }", "umlg.json");
        List<String> said = new ArrayList<>();

        try (Warnings.Channel channel = Warnings.redirectTo(said::add)) {
            OrderedMembers.fields(lambda.getClass(), rules);
            OrderedMembers.methods(lambda.getClass(), rules);
            OrderedMembers.fields(lambda.getClass(), rules);
        }

        assertEquals(1, said.size(), said::toString);
        assertTrue(said.get(0).contains("\"order\": \"declaration\""), said.get(0));
        assertTrue(said.get(0).contains("alphabetical order"), said.get(0));
    }
}
