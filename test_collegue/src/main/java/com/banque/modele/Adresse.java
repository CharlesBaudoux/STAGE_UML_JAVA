package com.banque.modele;

import io.github.oliviercailloux.umlg.annotation.*;

/**
 * Couvre : @UMLClass nu, sans strategy, et @UMLNote orientée vers la droite.
 *
 * <p>La classe figure sur le diagramme, mais comme une coquille vide : aucun de ses membres
 * n'est comptabilisé tant qu'aucune stratégie n'est demandée. C'est le comportement par
 * défaut, et il est délibéré — apparaître sur le diagramme et y déverser son contenu sont deux
 * décisions distinctes.
 */
@UMLClass
@UMLNote(text = "Coquille vide : strategy vaut NONE par défaut,\nmalgré trois attributs publics.",
    direction = UMLDirection.RIGHT)
public class Adresse {

    public String rue;
    public String ville;
    public String codePostal;

    public void normaliser() {}
}
