package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Lit le fichier de règles, ligne à ligne.
//
// La grammaire tient en trois cas : une ligne vide, un commentaire ouvert par « # », ou une
// règle. Une règle commence par un mot-clé qui dit sa nature — « type » et « hide » aujourd'hui —
// et c'est ce mot-clé qui décide comment lire le reste de la ligne. Ajouter une nature de règle
// se fait donc ici, et à un seul endroit : une branche de plus dans le switch, et la méthode qui
// va avec.
//
// Le fichier étant écrit à la main, chaque refus nomme le fichier et le numéro de ligne, puis
// rappelle la forme attendue. C'est le seul retour que l'auteur du fichier aura : il n'y a pas de
// compilateur pour relire ce qu'il écrit.
final class LecteurDeRegles {

    // Les mots-clés connus, et ce qui sépare les deux moitiés d'une règle « type ».
    private static final String TYPE = "type";
    private static final String HIDE = "hide";
    private static final String RETURN = "return";
    private static final String FLECHE = "->";

    private LecteurDeRegles() {}

    static Regles lire(ImmutableList<String> lignes, String origine) {
        // LinkedHashMap et non ImmutableMap.Builder : il faut pouvoir demander « cette clé
        // est-elle déjà là ? » avant d'insérer, pour refuser deux règles contradictoires en
        // nommant la ligne fautive. Le builder de Guava, lui, ne se plaindrait qu'au build, sans
        // savoir où.
        Map<String, String> alias = new LinkedHashMap<>();
        // LinkedHashSet pour la même raison, et pour que l'ordre de lecture soit conservé.
        Set<String> retoursMasques = new LinkedHashSet<>();

        for (int i = 0; i < lignes.size(); i++) {
            // Le numéro affiché est celui que montre un éditeur de texte : les lignes s'y
            // comptent à partir de 1.
            String contexte = origine + ", line " + (i + 1);
            String ligne = lignes.get(i).strip();

            if (ligne.isEmpty() || ligne.startsWith("#")) {
                continue;
            }

            // Le mot-clé est ce qui précède la première espace ; le reste appartient à la règle.
            int fin = ligne.indexOf(' ');
            String motCle = fin < 0 ? ligne : ligne.substring(0, fin);
            String reste = fin < 0 ? "" : ligne.substring(fin + 1).strip();

            switch (motCle) {
                case TYPE -> lireType(alias, reste, contexte);
                case HIDE -> lireHide(retoursMasques, reste, contexte);
                default -> throw new IllegalArgumentException(contexte + ": unknown rule \""
                    + motCle + "\". Known rules: " + TYPE + ", " + HIDE + ".");
            }
        }
        return new Regles(ImmutableMap.copyOf(alias), ImmutableSet.copyOf(retoursMasques));
    }

    // « hide return void » : le type rendu par une méthode n'est pas écrit.
    //
    // Le mot « return » n'est pas une décoration : il dit où la règle s'applique, et laisse la
    // place à d'autres endroits si l'usage en révèle. Ne rien masquer ailleurs est délibéré —
    // un attribut sans type n'apprendrait plus rien, alors qu'une méthode sans type de retour
    // dit, en UML, qu'elle ne rend rien.
    private static void lireHide(Set<String> retoursMasques, String reste, String contexte) {
        checkArgument(reste.startsWith(RETURN + " "),
            "%s: expected \"hide return <type>\", got \"hide %s\". Only a return type can be "
                + "hidden: elsewhere, a type carries what the line has to say.",
            contexte, reste);

        String type = reste.substring(RETURN.length()).strip();

        checkArgument(!type.isEmpty(), "%s: the type to hide is missing, after \"return\".",
            contexte);
        checkArgument(!type.contains(" "),
            "%s: a type name cannot contain a space (read \"%s\").", contexte, type);
        checkArgument(retoursMasques.add(type),
            "%s: the return type \"%s\" is already hidden. Keep only one of the two lines.",
            contexte, type);
    }

    // « type ImmutableList -> List » : le nom lu dans le code, puis celui qui s'affiche.
    private static void lireType(Map<String, String> alias, String reste, String contexte) {
        int fleche = reste.indexOf(FLECHE);
        checkArgument(fleche >= 0,
            "%s: expected \"type <type of the code> -> <displayed type>\", got \"type %s\".",
            contexte, reste);

        String source = reste.substring(0, fleche).strip();
        String affiche = reste.substring(fleche + FLECHE.length()).strip();

        checkArgument(!source.isEmpty(), "%s: the type to replace is missing, before the arrow.",
            contexte);
        checkArgument(!affiche.isEmpty(),
            "%s: the displayed type is missing, after the arrow. To hide a type, there is nothing "
                + "to write: leave the line out.",
            contexte);
        // Une espace au milieu d'un nom de type trahit une flèche oubliée ou un mot en trop ; le
        // laisser passer écrirait dans la boîte un texte que personne ne relirait.
        checkArgument(!source.contains(" ") && !affiche.contains(" "),
            "%s: a type name cannot contain a space (read \"%s\" -> \"%s\").",
            contexte, source, affiche);

        // Deux règles sur le même type se contrediraient, et la seconde l'emporterait en silence.
        checkArgument(!alias.containsKey(source),
            "%s: \"%s\" already has a rule, which said \"%s\". Keep only one of the two.",
            contexte, source, alias.get(source));

        alias.put(source, affiche);
    }
}
