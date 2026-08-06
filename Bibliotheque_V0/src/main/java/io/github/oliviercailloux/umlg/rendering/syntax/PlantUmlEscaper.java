package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkNotNull;

// Rend un texte utilisateur inoffensif dans le fichier PlantUML.
//
// Le modèle laisse passer n'importe quel caractère dans un nom, un rôle ou un label : c'est ici
// qu'on s'assure qu'un texte ne peut pas casser la syntaxe engendrée, ni s'y faire passer pour
// du balisage. Une méthode par position syntaxique, car les contraintes n'y sont pas les mêmes.
public final class PlantUmlEscaper {

    // Entités numériques HTML : PlantUML les rend telles quelles à l'écran, sans les interpréter
    // comme du balisage. On préfère la forme numérique aux formes nommées (&amp;), reconnue
    // partout de la même façon.
    private static final String ESPERLUETTE = "&#38;";
    private static final String CHEVRON_OUVRANT = "&#60;";
    private static final String CHEVRON_FERMANT = "&#62;";
    private static final String GUILLEMET = "&#34;";

    private PlantUmlEscaper() {}

    // Pour les textes posés sur les traits et dans les boîtes, où les retours à la ligne sont
    // permis (PlantUML les note "\n" dans le fichier).
    public static String label(String texte) {
        checkNotNull(texte);

        // La marge de 16 évite quelques réallocations quand il y a des caractères à encoder.
        StringBuilder sortie = new StringBuilder(texte.length() + 16);
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            switch (c) {
                // L'esperluette d'abord, sinon les entités produites ci-dessous seraient
                // elles-mêmes réencodées... mais ici l'ordre des cas d'un switch ne joue pas :
                // chaque caractère d'entrée n'est examiné qu'une fois, et ce qu'on ajoute à la
                // sortie n'est jamais relu.
                case '&' -> sortie.append(ESPERLUETTE);
                case '<' -> sortie.append(CHEVRON_OUVRANT);
                case '>' -> sortie.append(CHEVRON_FERMANT);
                case '"' -> sortie.append(GUILLEMET);
                // Une barre oblique inverse littérale se double, faute de quoi elle formerait
                // une séquence d'échappement avec le caractère suivant.
                case '\\' -> sortie.append("\\\\");
                case '\n' -> sortie.append("\\n");
                case '\r' -> {
                    // Un « \r\n » ne doit produire qu'un seul saut de ligne.
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

    // Pour le texte d'une note écrite en bloc, que PlantUML referme par une ligne « end note ».
    //
    // Un bloc est la seule position du document où le texte de l'utilisateur occupe des lignes
    // entières. Deux conséquences, opposées à celles de label().
    //
    // D'abord, les retours à la ligne s'y écrivent pour de bon. La séquence « \n » qu'attend
    // PlantUML partout ailleurs n'est pas interprétée ici : elle s'afficherait telle quelle, en
    // toutes lettres au milieu de la phrase. Le texte est donc découpé, chaque ligne échappée
    // séparément — label() ne voit alors jamais de retour à la ligne — puis rassemblée par de
    // vrais sauts.
    //
    // Ensuite, chaque ligne peut imiter le terminateur. Un texte valant exactement « end note »
    // refermerait la bulle par surprise, et tout ce qui suit se lirait comme de la syntaxe — le
    // fait est vérifié, PlantUML rejette alors le document. Le premier caractère de chaque ligne
    // est donc écrit sous forme d'entité numérique : la ligne commence dès lors par « & », et
    // aucun mot-clé ne peut plus y être reconnu. L'affichage est inchangé, PlantUML décodant
    // l'entité.
    //
    // La protection porte sur toutes les lignes, et pas seulement la première : c'est le
    // découpage qui l'exige, puisqu'une ligne quelconque peut désormais devenir la ligne d'un
    // bloc. Neutraliser le premier caractère plutôt que de chercher le mot « end » évite par
    // ailleurs d'avoir à deviner la grammaire exacte du terminateur — « endnote » la refermait
    // aussi.
    public static String noteBloc(String texte) {
        checkNotNull(texte);

        // -1 conserve les lignes vides finales, que le découpage supprimerait sinon ; une ligne
        // vide est du texte, et PlantUML l'accepte au milieu d'un bloc.
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

    // Écrit le premier caractère d'une ligne en entité numérique, pour qu'elle ne puisse plus
    // être lue comme un mot-clé.
    private static String neutraliserPremierCaractere(String ligne) {
        // Vide, ou déjà commencée par une entité ou une séquence d'échappement : la ligne ne
        // peut pas former un mot-clé, il n'y a rien à ajouter.
        if (ligne.isEmpty() || ligne.charAt(0) == '&' || ligne.charAt(0) == '\\') {
            return ligne;
        }

        // codePointAt et charCount plutôt que charAt : un caractère hors du plan de base — une
        // émoticône — s'écrit sur deux char, et n'en encoder que la moitié produirait une entité
        // invalide.
        int premier = ligne.codePointAt(0);
        return "&#" + premier + ";" + ligne.substring(Character.charCount(premier));
    }

    // Pour un stéréotype, qui s'écrit sur une seule ligne entre guillemets doubles : un retour
    // à la ligne y est impossible, on le remplace donc par une espace.
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

    // Les caractères de contrôle (en dessous de l'espace) et le caractère de suppression (0x7F)
    // sont simplement supprimés : invisibles à l'écran, ils ne feraient que perturber le
    // fichier engendré. Les accents et les caractères non latins, eux, passent intacts.
    private static void appendSiImprimable(StringBuilder sortie, char c) {
        if (c >= ' ' && c != 0x7F) {
            sortie.append(c);
        }
    }
}
