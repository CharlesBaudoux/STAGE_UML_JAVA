package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Désigne une classe comme portant les attributs d'un lien entre deux autres.
//
// UML permet à une association de porter elle-même des propriétés : la note qu'un étudiant
// obtient dans un cours n'appartient ni à l'étudiant ni au cours, mais à leur inscription. Le
// dessin le montre par un trait en pointillés reliant le milieu de l'association à une boîte.
//
// Java ne sait pas attacher un objet à un lien : il faut une classe intermédiaire, qui ressemble
// alors à une classe ordinaire. C'est cette annotation qui dit qu'elle n'en est pas une, et
// quelles deux classes elle relie.
//
// LE LIEN EST DESSINÉ PAR ELLE
//
// Déclarer la classe d'association suffit : le trait entre les deux extrémités est tracé du même
// coup. Il n'y a donc pas à écrire en plus une @UMLAssociation entre elles — ce serait un second
// trait, et le dessin dirait deux fois la même chose.
//
//   @UMLAssociationClass(source = Etudiant.class, target = Cours.class)
//   @UMLClass(strategy = UMLVisibility.PRIVATE)
//   public class Inscription {
//       private LocalDate date;
//       private double note;
//   }
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface UMLAssociationClass {

    // Les deux extrémités du lien que cette classe qualifie. Obligatoires toutes les deux :
    // aucune ne se devine, et une classe d'association qui n'en connaîtrait qu'une ne
    // qualifierait rien.
    //
    // « source » et « target » sont les noms qu'emploient toutes les annotations désignant les
    // bouts d'un lien : une convention uniforme évite d'avoir à se rappeler laquelle dit quoi.
    Class<?> source();

    Class<?> target();
}
