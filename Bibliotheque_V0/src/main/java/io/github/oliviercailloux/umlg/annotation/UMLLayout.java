package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Force la position d'une boîte par rapport à une autre, sans rien dire du modèle.
//
// Le moteur de rendu place les boîtes lui-même, et le fait bien dans la grande majorité des cas.
// Il lui arrive pourtant de croiser deux traits sans nécessité, ou d'éloigner deux classes que
// le lecteur attend côte à côte. L'annotation pose alors un lien invisible — « -[hidden]-> » —
// que le moteur respecte comme un autre, mais que personne ne voit.
//
// C'EST LA SEULE ANNOTATION SANS AUCUN SENS UML
//
// Toutes les autres disent quelque chose du modèle : cette classe est une boîte, cet attribut
// est un lien. Celle-ci ne dit rien qu'un diagramme puisse signifier — elle corrige un
// placement. À manier avec parcimonie : quelques-unes redressent un dessin, une vingtaine le
// figent, et le moindre ajout de classe oblige alors à toutes les reprendre.
//
// @Repeatable : une classe peut avoir à être placée par rapport à plusieurs autres.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(UMLLayouts.class)
public @interface UMLLayout {

    // La classe à placer par rapport à celle qui porte l'annotation. Obligatoire, comme partout
    // où une extrémité est désignée : rien n'est jamais deviné.
    Class<?> target();

    // De quel côté la cible doit se trouver. NONE pose un lien invisible sans contrainte de
    // côté, ce qui rapproche seulement les deux boîtes ; c'est déjà utile, mais UP, DOWN, LEFT
    // ou RIGHT est ce qu'on écrit d'ordinaire — sans quoi l'annotation ne dit pas grand-chose.
    UMLDirection direction() default UMLDirection.NONE;
}
