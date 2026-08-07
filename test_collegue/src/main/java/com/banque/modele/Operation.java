package com.banque.modele;

import io.github.oliviercailloux.umlg.annotation.*;

/**
 * Couvre : strategy = PROTECTED, @UMLInclude sur une méthode, @UMLConstraint répétée sur un
 * même attribut, et @UMLNote accrochée à une méthode — la forme en bloc, la seule que PlantUML
 * accepte pour un membre.
 */
@UMLClass(strategy = UMLVisibility.PROTECTED)
public class Operation {

    // PROTECTED affiche le public et le protégé...
    public String reference;

    /** Deux règles sur le même attribut : @UMLConstraint est répétable. */
    @UMLConstraint(rule = "montant != 0")
    @UMLConstraint(rule = "arrondi à deux décimales")
    protected double montant;

    // ... et laisse de côté le reste.
    private String signature;
    String commentaire;

    public void valider() {}

    protected void annuler() {}

    /** Dérogation positive : cet algorithme mérite d'être lu, même privé. */
    @UMLInclude
    @UMLNote(text = "SHA-256 sur la référence et le montant.", direction = UMLDirection.RIGHT)
    private void calculerEmpreinte() {}
}
