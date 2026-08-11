package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.regex.Pattern;

public final class Motif {

    private final String ecrit;

    private final Pattern regex;

    private volatile boolean aCorrespondu;

    private Motif(String ecrit, Pattern regex) {
        this.ecrit = ecrit;
        this.regex = regex;
    }

    public static Motif de(String ecrit) {
        checkNotNull(ecrit);
        checkArgument(!ecrit.isBlank(), "A pattern cannot be empty or made of whitespace only.");

        return new Motif(ecrit, Pattern.compile(enRegex(ecrit)));
    }

    private static String enRegex(String motif) {
        StringBuilder regex = new StringBuilder();
        int debut = 0;

        while (true) {
            int etoile = motif.indexOf('*', debut);
            if (etoile < 0) {
                break;
            }
            if (etoile > debut) {
                regex.append(Pattern.quote(motif.substring(debut, etoile)));
            }
            regex.append(".*");
            debut = etoile + 1;
        }
        if (debut < motif.length()) {
            regex.append(Pattern.quote(motif.substring(debut)));
        }
        return regex.toString();
    }

    public boolean correspond(String nom) {
        checkNotNull(nom);
        boolean correspond = regex.matcher(nom).matches();
        if (correspond) {
            aCorrespondu = true;
        }
        return correspond;
    }

    public boolean sansEffet() {
        return !aCorrespondu;
    }

    public String ecrit() {
        return ecrit;
    }

    @Override
    public String toString() {
        return ecrit;
    }
}
