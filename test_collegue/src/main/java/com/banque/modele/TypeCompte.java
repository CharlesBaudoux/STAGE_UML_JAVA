package com.banque.modele;

import io.github.oliviercailloux.umlg.annotation.*;

/**
 * Couvre : la nature ENUM d'une boîte, son compartiment de littéraux, et le filtre anti-bruit
 * sur une {@code enum}.
 *
 * <p>{@code values()} et {@code valueOf(String)} sont ajoutées par javac sans être marquées
 * synthétiques : elles échappent au filtre habituel et doivent être écartées nommément. Les
 * constantes, elles, partent dans leur propre compartiment — un EnumerationLiteral ne porte
 * que son nom, ni visibilité ni type.
 */
@UMLClass(strategy = UMLVisibility.PUBLIC)
public enum TypeCompte {

    COURANT,
    EPARGNE,
    TITRES;

    public boolean estRemunere() {
        return this != COURANT;
    }
}
