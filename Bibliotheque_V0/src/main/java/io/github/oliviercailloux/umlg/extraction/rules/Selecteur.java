package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Une façon de désigner des classes dans la déclaration d'une vue.
//
// Trois formes, et trois seulement, parce qu'elles couvrent trois besoins distincts :
//
//   Facture                        une classe, nommée simplement ou complètement
//   package com.exemple.modele     tout un paquetage
//   around SvgKeyboard             une classe et ce qui la touche directement
//
// La première est celle qui reproduit un diagramme tenu à la main : on choisit, une par une. La
// deuxième évite d'énumérer vingt classes qui vont ensemble. La troisième répond à la question
// « que dois-je regarder pour comprendre cette classe-ci ? », et c'est la seule qui se calcule.
public record Selecteur(Sorte sorte, String valeur) {

    public enum Sorte {
        // Une classe désignée par son nom, simple ou complet.
        CLASSE,
        // Toutes les classes d'un paquetage, correspondance exacte.
        PAQUETAGE,
        // Une classe et ses voisines immédiates : celles qu'elle touche, celles qui la touchent.
        VOISINAGE
    }

    public Selecteur {
        checkNotNull(sorte);
        checkNotNull(valeur);
        checkArgument(!valeur.isBlank(), "A selector cannot be empty or made of whitespace only.");
        checkArgument(!valeur.contains(" "),
            "A selector cannot contain a space (read \"%s\").", valeur);
    }

    public static Selecteur classe(String nom) {
        return new Selecteur(Sorte.CLASSE, nom);
    }

    public static Selecteur paquetage(String nom) {
        return new Selecteur(Sorte.PAQUETAGE, nom);
    }

    public static Selecteur voisinage(String nom) {
        return new Selecteur(Sorte.VOISINAGE, nom);
    }

    // Tel que l'utilisateur l'a écrit, pour les messages qui le lui rappellent.
    public String ecrit() {
        return switch (sorte) {
            case CLASSE -> valeur;
            case PAQUETAGE -> "package " + valeur;
            case VOISINAGE -> "around " + valeur;
        };
    }
}
