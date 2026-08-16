package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The grammar of UML multiplicities: "1", "0..1", "*", "1..*", "2..5". */
public final class Multiplicities {
    private Multiplicities() {}

    private static final Pattern Cardinality =
        Pattern.compile("\\*|(\\d{1,9})(\\.\\.(\\d{1,9}|\\*))?");

    /**
     * Refuses a multiplicity UML would not write.
     *
     * @param multiplicity what was written; empty passes, and means "nothing to write".
     * @param parameter the name to quote in the refusal.
     */
    public static void validate(String multiplicity, String parameter) {
        String problem = problem(multiplicity, parameter);
        checkArgument(problem == null, problem);
    }

    /**
     * Says whether a multiplicity is written the way UML writes it.
     *
     * @param multiplicity what was written.
     * @return true if UML would write it that way.
     */
    public static boolean isValid(String multiplicity) {
        return problem(multiplicity, "multiplicity") == null;
    }

    private static String problem(String multiplicity, String parameter) {
        if (multiplicity.isEmpty()) {
            return null;
        }

        Matcher m = Cardinality.matcher(multiplicity);
        if (!m.matches()) {
            return parameter + " = \"" + multiplicity + "\" is not a valid UML multiplicity "
                + "(expected: an integer, \"*\", or a range such as \"0..1\" or \"1..*\").";
        }

        String highBound = m.group(3);

        if (highBound == null || highBound.equals("*")) {
            return null;
        }
        long min = Long.parseLong(m.group(1));
        long max = Long.parseLong(highBound);

        if (min > max) {
            return parameter + " = \"" + multiplicity
                + "\": the lower bound exceeds the upper bound.";
        }
        return null;
    }
}
