package com.banque.modele;

import io.github.oliviercailloux.umlg.annotation.*;

/**
 * Couvre : strategy = PUBLIC, le modificateur static sur un attribut comme sur une méthode, et
 * @UMLNote accrochée à un attribut.
 */
@UMLClass(strategy = UMLVisibility.PUBLIC)
public class CarteBancaire {

    public static final int DUREE_VALIDITE_ANNEES = 3;

    /** Note accrochée à un attribut : PlantUML la veut en bloc, ancrée sur « Alias::membre ». */
    @UMLNote(text = "Quatre derniers chiffres seulement :\nle reste ne quitte jamais la base.")
    public String numeroMasque;

    private String cryptogramme;

    public void bloquer() {}

    public static CarteBancaire emettre(String numero) {
        return new CarteBancaire();
    }
}
