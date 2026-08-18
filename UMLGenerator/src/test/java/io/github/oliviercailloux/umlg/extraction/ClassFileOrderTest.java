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

class ClassFileOrderTest {

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
        ImmutableList<String> fields = ClassFileOrder.fields(Tank.class);

        assertEquals(List.of("Large", "Half", "Third", "Account", "Name"), fields);
    }

    @Test
    void methodsOfAFileWithLongConstants_areReadInOrder() {
        ImmutableList<String> methods = ClassFileOrder.methods(Tank.class);

        assertEquals("<init>()V", methods.get(0));
        assertEquals("namer()Ljava/util/function/Supplier;", methods.get(1));
        assertTrue(methods.stream().anyMatch(m -> m.startsWith("lambda$")), methods::toString);
    }

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
        assertEquals("nothing()V", key("nothing"));
        assertEquals("all(BCSIJFD)Z", key("all"));
    }

    @Test
    void arrays_keepTheirBrackets() {
        assertEquals("arrays([I)[Ljava/lang/String;", key("arrays"));
    }

    @Test
    void theComputedKey_isTheOneReadInTheFile() {
        ImmutableList<String> read = ClassFileOrder.methods(Signatures.class);

        for (Method method : Signatures.class.getDeclaredMethods()) {
            if (method.isSynthetic()) {
                continue;
            }
            assertTrue(read.contains(ClassFileOrder.key(method)),
                () -> ClassFileOrder.key(method) + " missing from " + read);
        }
    }

    @Test
    void aClassWithoutFile_isReturnedNull() {
        Runnable lambda = () -> {};

        assertNull(ClassFileOrder.fields(lambda.getClass()));
        assertNull(ClassFileOrder.methods(lambda.getClass()));
    }

    @Test
    void lostOrder_isSaidOnlyOncePerClass() {
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
