package io.github.oliviercailloux.umlg.rendering.element;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
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
// LA NOTATION « nom : Type »
//
// C'est celle d'UML, et elle vaut pour les trois endroits où un type paraît : un attribut, un
// paramètre, le retour d'une méthode. Le nom vient toujours en premier, le type après les
// deux-points — l'inverse de l'ordre de déclaration de Java, mais l'ordre que lit un diagramme :
// on cherche une propriété par son nom, pas par son type.
//
// Les deux-points sont entourés d'espaces, comme les écrit la norme UML : « solde : double », et
// non « solde: double », qui est la ponctuation de Java et de PlantUML. La bibliothèque produit
// des diagrammes UML ; elle en suit la notation jusque dans ce détail, qui est celui qu'un
// lecteur habitué remarque en premier.
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

    // L'alias est fourni par l'appelant, qui le tient d'AliasRegistry ; le lien mène à la vue où
    // cette boîte est détaillée, et vaut "" quand il n'y en a pas.
    //
    // Un dossier de onze diagrammes est une collection d'images sans rapport tant qu'on ne peut
    // pas passer de l'une à l'autre. Le lien en fait une documentation qui se parcourt : depuis
    // la vue d'ensemble, cliquer sur une classe ouvre la vue qui la détaille.
    public static String boite(UmlEntity entity, String alias, String lien) {
        StringBuilder boite = new StringBuilder();

        // Le mot-clé dit la nature de l'entité : class, abstract class, interface, enum,
        // annotation. Le nom affiché est mis entre guillemets, ce qui permet espaces et
        // accents ; l'alias, lui, reste un identifiant nu.
        // Les paramètres de type suivent le nom, comme en Java : « Boite<T> ». UML les dessine
        // dans un cartouche au coin de la boîte, ce que PlantUML ne sait pas faire ; les écrire
        // à la suite dit la même chose, et vaut mieux que de les taire — un attribut de type T
        // dans une boîte nommée « Boite » ne renvoyait à rien.
        //
        // Le nom occupe à lui seul la première ligne dessinée de la boîte : c'est donc
        // debutDeLigne et non label, sans quoi un nom valant « = titre » perdrait son signe égal,
        // lu comme un titre Creole.
        //
        // La vacuité du nom est éprouvée ici comme celle du stéréotype plus bas, et pour la même
        // raison : le modèle refuse un nom vide ou fait d'espaces, mais il ne connaît pas
        // PlantUML et n'a donc rien à dire des caractères de contrôle, que l'échappement
        // supprime. Un nom qui n'en contient que — construit directement par l'API, sans passer
        // par ClassHeaderValidator — écrivait « class "" as C », que le moteur refuse : la garde
        // du writer levait alors une IllegalStateException accusant « un défaut de la
        // bibliothèque », message que l'appelant ne pouvait pas suivre.
        String nomEcrit = PlantUmlEscaper.debutDeLigne(entity.name() + entity.typeParameters());
        checkArgument(!nomEcrit.isBlank(),
            "The name of %s draws nothing: it is made of invisible characters only.",
            entity.qualifiedName());

        boite.append(entity.kind().motCle())
             .append(" \"")
             .append(nomEcrit)
             .append("\" as ").append(alias);

        // Le modèle garantit déjà le non-nul ; le test sur null est une ceinture de sécurité.
        //
        // La vacuité est éprouvée sur le texte échappé, et non sur celui du modèle : l'échappement
        // supprime les caractères de contrôle, qu'isBlank() ne tient pas pour des blancs, et un
        // stéréotype fait de ces seuls caractères engendrait « <<>> » — que le moteur refuse.
        String stereotype =
            entity.stereotype() == null ? "" : PlantUmlEscaper.stereotype(entity.stereotype());
        if (!stereotype.isBlank()) {
            boite.append(" <<").append(stereotype).append(">>");
        }

        // L'ORDRE DE CES DEUX MORCEAUX N'EST PAS LIBRE
        //
        // Le moteur accepte « [[lien]] #couleur » et refuse « #couleur [[lien]] » — devant la
        // seconde forme, il ne lève rien et écrit une image d'erreur, que seule la garde du
        // writer distingue d'un dessin réussi. Rien ne le documente ; mesuré.
        //
        // Le lien vient donc en premier. Le cas n'apparaît que sur une boîte qui porte à la fois
        // une couleur et un lien de navigation, c'est-à-dire dès qu'un projet à plusieurs vues
        // écrit @UMLClass(color = ...) : tous les tests qui éprouvaient l'un sans l'autre
        // passaient.
        //
        // Le chemin du lien n'est pas échappé : il est calculé par la bibliothèque à partir des
        // noms de fichiers qu'elle a elle-même formés, jamais écrit par l'utilisateur.
        if (!lien.isEmpty()) {
            boite.append(" [[").append(lien).append("]]");
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

    // Le symbole de visibilité et l'espace qui le suit, ou rien du tout.
    //
    // Un projet peut demander à taire les visibilités (« "omit": ["visibility"] ») : le symbole est
    // alors vide, et l'espace doit disparaître avec lui — sans quoi chaque ligne commencerait
    // par un blanc que rien ne justifie.
    private static String prefixe(String visibilite) {
        return visibilite.isEmpty() ? "" : PlantUmlEscaper.label(visibilite) + " ";
    }

    // Le nom d'un membre, précédé de la barre oblique s'il est dérivé.
    //
    // La barre se pose devant le nom, et devant lui seul : c'est la propriété qui est calculée,
    // non son type ni sa visibilité. Elle est écrite après l'échappement, sans passer par lui —
    // c'est une notation UML que nous produisons, pas un texte venu de l'utilisateur.
    //
    // ouvreLaLigne dit si rien ne précède le nom sur la ligne dessinée. C'est le cas quand le
    // projet a demandé à taire les visibilités et que le membre n'est ni statique ni abstrait :
    // le nom devient alors le premier caractère de la ligne, et un membre nommé « #total » se
    // dessinait « total », son dièse lu comme une puce de liste.
    private static String derive(boolean derived, String nom, boolean ouvreLaLigne) {
        if (derived) {
            // La barre oblique ouvre la ligne à la place du nom, et n'est pas un marqueur.
            return "/" + PlantUmlEscaper.label(nom);
        }
        return ouvreLaLigne ? PlantUmlEscaper.debutDeLigne(nom) : PlantUmlEscaper.label(nom);
    }

    // Les littéraux d'une énumération, un par ligne. Ni visibilité, ni type, ni {static} : en
    // UML un EnumerationLiteral ne porte que son nom.
    //
    // Le littéral occupe donc à lui seul sa ligne dessinée, et rien ne le précède jamais : c'est
    // la sixième position atteinte par le balisage de début de ligne.
    private static String literaux(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (String literal : entity.literals()) {
            compartiment.append("  ").append(PlantUmlEscaper.debutDeLigne(literal)).append("\n");
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
            String visibilite = prefixe(field.visibility());
            compartiment.append("  ").append(visibilite);

            // {static} demande à PlantUML de souligner le membre, comme le veut UML.
            if (field.isStatic()) {
                compartiment.append("{static} ");
            }

            compartiment
                .append(derive(field.derived(), field.name(),
                    visibilite.isEmpty() && !field.isStatic()))
                .append(" : ").append(PlantUmlEscaper.label(field.type()))
                .append(multiplicite(field.cardinality()))
                .append(NotationContrainte.aLaSuite(field.constraints())).append("\n");
        }
        return compartiment.toString();
    }

    // Format « visibilité [{static}] nom(paramètres): type [multiplicité] ».
    private static String methodes(UmlEntity entity) {
        StringBuilder compartiment = new StringBuilder();
        for (UmlMethod method : entity.methods()) {
            String visibilite = prefixe(method.visibility());
            compartiment.append("  ").append(visibilite);

            if (method.isStatic()) {
                compartiment.append("{static} ");
            }
            // {abstract} demande à PlantUML l'italique, la notation qu'UML réserve à une
            // opération sans implémentation.
            if (method.isAbstract()) {
                compartiment.append("{abstract} ");
            }

            compartiment
                .append(derive(method.derived(), method.name(),
                    visibilite.isEmpty() && !method.isStatic() && !method.isAbstract()))
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
        return " : " + PlantUmlEscaper.label(method.returnType())
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
                         ? PlantUmlEscaper.label(parametre.name()) + " : "
                             + PlantUmlEscaper.label(parametre.type())
                         : PlantUmlEscaper.label(parametre.type()))
                     .collect(Collectors.joining(", "));
    }
}
