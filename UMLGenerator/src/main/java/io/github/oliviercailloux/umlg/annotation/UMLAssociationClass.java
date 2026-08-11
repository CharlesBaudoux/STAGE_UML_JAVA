package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Désigne une classe comme portant les attributs d'un lien entre deux autres.
 *
 * <p>UML permet à une association de porter elle-même des propriétés : la note qu'un étudiant
 * obtient dans un cours n'appartient ni à l'étudiant ni au cours, mais à leur inscription. Le
 * dessin le montre par un trait en pointillés reliant le milieu de l'association à une boîte.
 *
 * <p>Java ne sait pas attacher un objet à un lien : il faut une classe intermédiaire, qui ressemble
 * alors à une classe ordinaire. C'est cette annotation qui dit qu'elle n'en est pas une, et
 * quelles deux classes elle relie.
 *
 * <p>LE LIEN EST DESSINÉ PAR ELLE
 *
 * <p>Déclarer la classe d'association suffit : le trait entre les deux extrémités est tracé du même
 * coup. Il n'y a donc pas à écrire en plus une @UMLAssociation entre elles — ce serait un second
 * trait, et le dessin dirait deux fois la même chose.
 *
 * <p>&#64;UMLAssociationClass(source = Etudiant.class, target = Cours.class)
 * &#64;UMLClass(strategy = UMLVisibility.PRIVATE)
 * public class Inscription {
 * private LocalDate date;
 * private double note;
 * }
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLAssociationClass {

    /**
     * Les deux extrémités du lien que cette classe qualifie. Obligatoires toutes les deux :
     * aucune ne se devine, et une classe d'association qui n'en connaîtrait qu'une ne
     * qualifierait rien.
     *
     * <p>« source » et « target » sont les noms qu'emploient toutes les annotations désignant les
     * bouts d'un lien : une convention uniforme évite d'avoir à se rappeler laquelle dit quoi.
     *
     * @return l'extrémité de départ du lien qualifié. Obligatoire.
     */
    Class<?> source();

    /**
     * L'autre extrémité du lien que cette classe qualifie.
     *
     * @return l'extrémité d'arrivée du lien qualifié. Obligatoire.
     */
    Class<?> target();
}
