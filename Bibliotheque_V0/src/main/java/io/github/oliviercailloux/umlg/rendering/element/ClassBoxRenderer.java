package io.github.oliviercailloux.umlg.rendering.element;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Écrit la déclaration PlantUML d'une boîte.
//
// Forme produite :
//   class "Compte" as Compte <<entity>> #LightBlue {
//     - solde: double
//     --
//     + retirer(montant: double): void
//   }
//
// LA NOTATION « nom: Type »
//
// C'est celle d'UML, et elle vaut pour les trois endroits où un type paraît : un attribut, un
// paramètre, le retour d'une méthode. Le nom vient toujours en premier, le type après les
// deux-points — l'inverse de l'ordre de déclaration de Java, mais l'ordre que lit un diagramme :
// on cherche une propriété par son nom, pas par son type.
//
// Le mot-clé initial dépend de la nature de l'entité, et une énumération ouvre la boîte sur un
// compartiment supplémentaire, celui de ses littéraux :
//   enum "Devise" as Devise {
//     EURO
//     DOLLAR
//   }
//
// Tout texte venu du modèle passe par PlantUmlEscaper : c'est le seul rempart contre un nom
// contenant un guillemet, un chevron ou un retour à la ligne.
public final class ClassBoxRenderer {

    private ClassBoxRenderer() {}

    // L'alias est fourni par l'appelant, qui le tient d'AliasRegistry.
    public static String boite(UmlEntity entity, String alias) {
        StringBuilder boite = new StringBuilder();

        // Le mot-clé dit la nature de l'entité : class, abstract class, interface, enum,
        // annotation. Le nom affiché est mis entre guillemets, ce qui permet espaces et
        // accents ; l'alias, lui, reste un identifiant nu.
        boite.append(entity.kind().motCle())
             .append(" \"").append(PlantUmlEscaper.label(entity.name()))
             .append("\" as ").append(alias);

        // Le modèle garantit déjà le non-nul ; le test sur null est une ceinture de sécurité,
        // celui sur isBlank distingue l'absence de stéréotype.
        if (entity.stereotype() != null && !entity.stereotype().isBlank()) {
            boite.append(" <<").append(PlantUmlEscaper.stereotype(entity.stereotype()))
                 .append(">>");
        }

        // La couleur n'est pas échappée mais validée : son vocabulaire est fermé, et ColorCodec
        // rejette tout ce que PlantUML ne connaît pas. Le nom complet sert au message d'erreur.
        if (entity.color() != null && !entity.color().isBlank()) {
            boite.append(" ").append(ColorCodec.code(entity.color(), entity.qualifiedName()));
        }

        boite.append(" {\n");

        // Trois compartiments, dans l'ordre où UML les lit : les littéraux, les attributs, les
        // méthodes. Le filet séparateur n'a de sens qu'entre deux compartiments réellement
        // peuplés — d'où le filtrage des blocs vides avant le join, qui évite d'avoir à traiter
        // séparément chacune des combinaisons.
        boite.append(Stream.of(literaux(entity), attributs(entity), methodes(entity))
                           .filter(compartiment -> !compartiment.isEmpty())
                           .collect(Collectors.joining("  --\n")));

        // La ligne vide finale sépare les boîtes dans le fichier engendré : sans effet sur le
        // dessin, mais le texte reste lisible si on l'ouvre.
        return boite.append("}\n\n").toString();
    }

    // Les règles formelles, chacune entre accolades, à la suite de l'élément qu'elles
    // contraignent. Les accolades sont posées ici : l'utilisateur écrit la règle nue.
    private static String contraintes(List<String> regles) {
        StringBuilder sortie = new StringBuilder();
        for (String regle : regles) {
            sortie.append(" {").append(PlantUmlEscaper.label(regle)).append("}");
        }
        return sortie.toString();
    }

    // Le nom d'un membre, précédé de la barre oblique s'il est dérivé.
    //
    // La barre se pose devant le nom, et devant lui seul : c'est la propriété qui est calculée,
    // non son type ni sa visibilité. Elle est écrite après l'échappement, sans passer par lui —
    // c'est une notation UML que nous produisons, pas un texte venu de l'utilisateur.
    private static String derive(boolean derived, String nom) {
        return (derived ? "/" : "") + PlantUmlEscaper.label(nom);
    }

    // Les littéraux d'une énumération, un par ligne. Ni visibilité, ni type, ni {static} : en
    // UML un EnumerationLiteral ne porte que son nom.
    private static String literaux(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (String literal : entity.literals()) {
            compartiment.append("  ").append(PlantUmlEscaper.label(literal)).append("\n");
        }
        return compartiment.toString();
    }

    // La multiplicité d'un attribut, entre crochets à la suite du nom : « commandes [0..*] ».
    //
    // Écrite sans échappement, et c'est sans risque : le modèle n'accepte qu'une multiplicité
    // conforme à la grammaire UML, faite de chiffres, de points et d'étoiles. Les crochets, eux,
    // sont une notation que nous produisons — les échapper les afficherait tels quels.
    private static String multiplicite(String cardinality) {
        return cardinality.isEmpty() ? "" : " [" + cardinality + "]";
    }

    // Format « visibilité [{static}] nom: type [multiplicité] {contraintes} ».
    private static String attributs(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlField field : entity.fields()) {
            compartiment.append("  ").append(PlantUmlEscaper.label(field.visibility())).append(" ");

            // {static} demande à PlantUML de souligner le membre, comme le veut UML.
            if (field.isStatic()) {
                compartiment.append("{static} ");
            }

            compartiment.append(derive(field.derived(), field.name()))
                        .append(": ").append(PlantUmlEscaper.label(field.type()))
                        .append(multiplicite(field.cardinality()))
                        .append(contraintes(field.constraints())).append("\n");
        }
        return compartiment.toString();
    }

    // Format « visibilité [{static}] nom(paramètres): type [multiplicité] ».
    private static String methodes(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlMethod method : entity.methods()) {
            compartiment.append("  ").append(PlantUmlEscaper.label(method.visibility()))
                        .append(" ");

            if (method.isStatic()) {
                compartiment.append("{static} ");
            }

            compartiment.append(derive(method.derived(), method.name()))
                        .append("(").append(parametres(method)).append(")")
                        .append(retour(method))
                        .append("\n");
        }
        return compartiment.toString();
    }

    // Ce qui suit les parenthèses : « : String [0..*] », ou rien du tout.
    //
    // Un type de retour vide n'écrit pas même les deux-points : c'est la façon dont UML dit
    // « cette méthode ne rend rien », et ce que demande une règle de projet qui masque le void.
    // La multiplicité le suit, et disparaît avec lui — elle ne qualifierait plus rien.
    private static String retour(UmlMethod method) {
        if (method.returnType().isEmpty()) {
            return "";
        }
        return ": " + PlantUmlEscaper.label(method.returnType())
            + multiplicite(method.cardinality());
    }

    // Les paramètres entre parenthèses, séparés par des virgules.
    //
    // Chacun s'écrit « nom: type » quand la compilation a conservé son nom, et « type » seul
    // sinon : mieux vaut une signature moins bavarde qu'une signature peuplée d'« arg0 ». Les
    // deux-points ne sont posés qu'autour d'un nom réel, faute de quoi la parenthèse afficherait
    // un « : double » orphelin.
    private static String parametres(UmlMethod method) {
        return method.parameters().stream()
                     .map(parametre -> parametre.nomme()
                         ? PlantUmlEscaper.label(parametre.name()) + ": "
                             + PlantUmlEscaper.label(parametre.type())
                         : PlantUmlEscaper.label(parametre.type()))
                     .collect(Collectors.joining(", "));
    }
}
