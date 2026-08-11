package io.github.oliviercailloux.umlg.extraction.member;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.lang.reflect.Modifier;

// Décide si un membre entre dans la boîte, et sous quel symbole.
//
// Le paramètre « modifiers » est l'entier de bits rendu par Field.getModifiers() ou
// Method.getModifiers() ; la classe Modifier sait y lire chaque drapeau. Travailler sur cet
// entier plutôt que sur Field ou Method rend la règle commune aux attributs et aux méthodes.
//
// C'est aussi ce qui la rend réutilisable par le processeur d'annotations, qui ne dispose ni de
// Field ni de Method mais sait fabriquer le même entier (voir VerificateurMembre). D'où le
// « public » : le paquetage n'est pas exporté, il ne s'agit donc pas d'une promesse faite à
// l'utilisateur, mais du seul moyen qu'a le processeur de poser sa question — « ce membre
// serait-il affiché ? » — sans réécrire la règle qui y répond.
public final class VisibilityPolicy {

    private VisibilityPolicy() {}

    // Trois niveaux de décision, du plus fort au plus faible : @UMLExclude, puis @UMLInclude,
    // puis la stratégie de la classe. Les deux annotations sont des vetos individuels ; la
    // stratégie n'est qu'un défaut.
    //
    // C'est la question que pose le processeur d'annotations, qui n'a pas de fichier de règles
    // sous la main. L'extraction, elle, en a un : elle compose elle-même les deux défauts
    // généraux — cette stratégie et les motifs « hide » — et passe donc par parLaStrategie.
    public static boolean estVisible(int modifiers, UMLVisibility strategy, boolean hasInclude,
        boolean hasExclude) {

        if (hasExclude) {
            return false;
        }
        if (hasInclude) {
            return true;
        }

        return parLaStrategie(modifiers, strategy);
    }

    // La seule stratégie, sans les vetos : ce que la classe montre par défaut.
    //
    // Séparer les deux permet à l'appelant qui a d'autres défauts généraux à composer — les
    // motifs du fichier de règles, dans MemberExtractor — de les mettre au même rang que
    // celui-ci, plutôt que de trancher avant ou après la hiérarchie complète.
    public static boolean parLaStrategie(int modifiers, UMLVisibility strategy) {
        return switch (strategy) {
            case NONE -> false;

            // PROJECT n'est pas une visibilité mais un renvoi vers la clé « strategy » du
            // fichier de règles, et EntityExtractor l'a résolu bien avant d'arriver ici. S'il
            // parvient tout de même jusqu'à cette méthode — un appelant qui construit le modèle
            // par programme, un test —, le traiter comme NONE est le repli le plus sûr : une
            // boîte vide se remarque, un membre affiché par erreur non.
            case PROJECT -> false;

            case PRIVATE -> true;
            case PUBLIC -> Modifier.isPublic(modifiers);
            case PROTECTED -> Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
            case PACKAGE -> Modifier.isPublic(modifiers) || estVisiblePaquetage(modifiers);
        };
    }

    private static boolean estVisiblePaquetage(int modifiers) {
        return !Modifier.isPublic(modifiers)
            && !Modifier.isProtected(modifiers)
            && !Modifier.isPrivate(modifiers);
    }

    // Traduction en symbole UML. L'ordre des tests est indifférent : les quatre visibilités
    // s'excluent mutuellement. Le "~" final couvre le cas package-private, celui sans mot-clé.
    static String symbole(int modifiers) {
        if (Modifier.isPublic(modifiers)) {
            return "+";
        }
        if (Modifier.isPrivate(modifiers)) {
            return "-";
        }
        if (Modifier.isProtected(modifiers)) {
            return "#";
        }
        return "~";
    }
}
