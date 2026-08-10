package io.github.oliviercailloux.umlg.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Force l'affichage d'un membre, quelle que soit la stratégie de la classe.
 *
 * <p>Sert dans deux cas : montrer un membre trop peu visible pour la stratégie choisie (un private
 * avec strategy = PUBLIC), et rattraper un membre écarté par un motif « hide » du fichier de
 * règles — un getter que l'on juge, exceptionnellement, digne du diagramme.
 *
 * <p>Les deux dérogations sur le même membre sont une erreur de compilation : l'intention est
 * contradictoire, et le processeur refuse de choisir à la place de l'auteur (VerificateurMembre).
 * La règle de départage existe pourtant — @UMLExclude l'emporte, comme partout (VisibilityPolicy)
 * — mais elle n'est là que pour le code déjà compilé sans le processeur : rien de ce que
 * l'extraction rencontre ne doit rester sans réponse.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface UMLInclude {

    /**
     * Le nom sous lequel le membre paraît dans la boîte, à la place du sien.
     *
     * <p>Sert quand le diagramme nomme les choses plus longuement que le code : un attribut
     * « codeByName » qu'un diagramme de référence appelle « xKeycodeByXKeyName ». Le renommage
     * est porté par la dérogation d'affichage, et non par une règle du projet, pour la même
     * raison que @UMLClass(name = ...) : c'est une décision sur un élément, écrite là où
     * l'élément est écrit.
     *
     * <p>La chaîne vide — le défaut — veut dire « le nom du code ». Un nom réduit à des espaces est
     * refusé, et un nom identique à celui du code ne change rien, ce que le processeur signale.
     */
    String name() default "";

    /**
     * Écrit la méthode en notation attribut : « xKeycodeByXKeyName : Map&lt;String, Short&gt; », sans
     * parenthèses, dans le compartiment des attributs.
     *
     * <p>C'est la notation des diagrammes qui montrent une structure : un accesseur sans paramètre
     * expose une propriété de l'objet, et le dessiner comme une méthode dit « comment on
     * l'obtient » là où le diagramme veut dire « ce que c'est ». La décision est individuelle,
     * et non une règle du projet, parce que le même diagramme mélange les deux formes : le
     * diagramme de référence de Keyboardd écrit « xKeycodeByXKeyName : Map&lt;String, Short&gt; » mais
     * garde « latest(): Xkeys » — une fabrique n'est pas une propriété, et seul l'auteur sait
     * laquelle est laquelle.
     *
     * <p>Réservé aux méthodes sans paramètre qui rendent quelque chose : un paramètre ou un retour
     * void n'ont pas de forme attribut, et le processeur le refuse à la compilation.
     */
    boolean asAttribute() default false;
}
