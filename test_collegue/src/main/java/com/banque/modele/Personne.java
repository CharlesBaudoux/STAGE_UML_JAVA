package com.banque.modele;

import io.github.oliviercailloux.umlg.annotation.*;

/**
 * Couvre : la nature ABSTRACT_CLASS — nom en italique —, {@code color} donnée par son nom
 * PlantUML plutôt qu'en hexadécimal, et {@code @UMLDerived} sur un attribut.
 *
 * <p>{@code ClientPremium} l'étend : c'est la troisième forme d'héritage du diagramme, la
 * généralisation entre deux classes.
 */
@UMLClass(stereotype = "Abstraction", color = "LightYellow",
    strategy = UMLVisibility.PROTECTED)
@UMLNote(text = "Classe abstraite : PlantUML met son nom en italique.")
public abstract class Personne {

    protected String nomComplet;

    /**
     * Attribut dérivé : UML le note d'une barre oblique — « /ageEnAnnees » —, que Java ne
     * permet pas d'écrire dans un identifiant. L'âge ne se range pas, il se recalcule à chaque
     * lecture depuis la date de naissance.
     */
    @UMLDerived
    protected int ageEnAnnees;

    // private : hors stratégie PROTECTED, donc absent de la boîte.
    private String dateNaissance;

    public abstract String identifiant();

    // Un accesseur n'apprend rien de plus que l'attribut qu'il rend : c'est ce qu'un
    // « hide get* » écarterait, si ce projet écrivait un fichier de règles.
    public String getNomComplet() {
        return nomComplet;
    }
}
