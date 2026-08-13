package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkNotNull;

public final class PlantUmlEscaper {

    private static final String ESPERLUETTE = "&#38;";
    private static final String CHEVRON_OUVRANT = "&#60;";
    private static final String CHEVRON_FERMANT = "&#62;";
    private static final String GUILLEMET = "&#34;";

    private static final String MARQUEURS_DOUBLES = "*/~-_[";

    private static final String MARQUEURS_DE_LIGNE = "#*=|";

    private PlantUmlEscaper() {}

    private static boolean ouvreUnBalisage(String texte, int i) {
        char c = texte.charAt(i);
        return MARQUEURS_DOUBLES.indexOf(c) >= 0
            && i + 1 < texte.length() && texte.charAt(i + 1) == c;
    }

    private static void appendNeutralise(StringBuilder sortie, String texte, int i) {
        if (ouvreUnBalisage(texte, i)) {
            sortie.append("&#").append((int) texte.charAt(i)).append(';');
            return;
        }
        appendSiImprimable(sortie, texte.charAt(i));
    }

    public static String label(String texte) {
        checkNotNull(texte);

        StringBuilder sortie = new StringBuilder(texte.length() + 16);
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            switch (c) {
                case '&' -> sortie.append(ESPERLUETTE);
                case '<' -> sortie.append(CHEVRON_OUVRANT);
                case '>' -> sortie.append(CHEVRON_FERMANT);
                case '"' -> sortie.append(GUILLEMET);
                case '\\' -> sortie.append("\\\\");
                case '\n' -> sortie.append("\\n");
                case '\r' -> {
                    if (i + 1 < texte.length() && texte.charAt(i + 1) == '\n') {
                        i++;
                    }
                    sortie.append("\\n");
                }
                default -> appendNeutralise(sortie, texte, i);
            }
        }
        return sortie.toString();
    }

    public static String debutDeLigne(String texte) {
        checkNotNull(texte);

        String echappe = label(texte);
        if (echappe.isEmpty() || MARQUEURS_DE_LIGNE.indexOf(echappe.charAt(0)) < 0) {
            return echappe;
        }
        return neutraliserPremierCaractere(echappe);
    }

    public static String noteBloc(String texte) {
        checkNotNull(texte);

        String[] lignes = texte.split("\r\n|\r|\n", -1);

        StringBuilder sortie = new StringBuilder(texte.length() + 16);
        for (int i = 0; i < lignes.length; i++) {
            if (i > 0) {
                sortie.append('\n');
            }
            sortie.append(neutraliserPremierCaractere(label(lignes[i])));
        }
        return sortie.toString();
    }

    private static String neutraliserPremierCaractere(String ligne) {
        if (ligne.isEmpty() || ligne.charAt(0) == '&' || ligne.charAt(0) == '\\') {
            return ligne;
        }

        int premier = ligne.codePointAt(0);
        return "&#" + premier + ";" + ligne.substring(Character.charCount(premier));
    }

    public static String stereotype(String texte) {
        checkNotNull(texte);

        StringBuilder sortie = new StringBuilder(texte.length() + 16);
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            switch (c) {
                case '&' -> sortie.append(ESPERLUETTE);
                case '<' -> sortie.append(CHEVRON_OUVRANT);
                case '>' -> sortie.append(CHEVRON_FERMANT);
                case '"' -> sortie.append(GUILLEMET);
                case '\n', '\r' -> sortie.append(' ');
                default -> appendNeutralise(sortie, texte, i);
            }
        }
        return sortie.toString();
    }

    private static void appendSiImprimable(StringBuilder sortie, char c) {
        if (c >= ' ' && c != 0x7F) {
            sortie.append(c);
        }
    }
}
