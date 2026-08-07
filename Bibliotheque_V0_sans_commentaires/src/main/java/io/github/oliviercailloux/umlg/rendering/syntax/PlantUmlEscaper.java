package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkNotNull;

// Rend un texte utilisateur inoffensif dans le fichier PlantUML.
//
// Le modèle laisse passer n'importe quel caractère dans un nom, un rôle ou un label : c'est ici
// qu'on s'assure qu'un texte ne peut pas casser la syntaxe engendrée, ni s'y faire passer pour
// du balisage. Une méthode par position syntaxique, car les contraintes n'y sont pas les mêmes.
public final class PlantUmlEscaper {

    private static final String ESPERLUETTE = "&#38;";
    private static final String CHEVRON_OUVRANT = "&#60;";
    private static final String CHEVRON_FERMANT = "&#62;";
    private static final String GUILLEMET = "&#34;";

    private PlantUmlEscaper() {}

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
                default -> appendSiImprimable(sortie, c);
            }
        }
        return sortie.toString();
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
                default -> appendSiImprimable(sortie, c);
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
