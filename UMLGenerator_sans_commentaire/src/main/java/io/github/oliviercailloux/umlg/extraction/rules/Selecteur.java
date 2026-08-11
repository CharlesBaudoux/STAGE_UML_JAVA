package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public record Selecteur(Sorte sorte, String valeur) {

    public enum Sorte {
        CLASSE,
        PAQUETAGE,
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

    public String ecrit() {
        return switch (sorte) {
            case CLASSE -> valeur;
            case PAQUETAGE -> "package " + valeur;
            case VOISINAGE -> "around " + valeur;
        };
    }
}
