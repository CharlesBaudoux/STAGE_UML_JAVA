package com.banque.utils;

import com.banque.modele.Devise;

/**
 * Aucune annotation : le scanner doit l'ignorer totalement.
 *
 * <p>Le périmètre du diagramme est choisi, pas subi — c'est tout l'objet de @UMLClass. Cette
 * classe utilitaire est bien référencée par le reste du code, et n'en doit pas moins rester
 * absente du rendu.
 */
public class Calculateur {

    public static double tva() {
        return 1.2;
    }

    public static double convertir(double montant, Devise source, Devise cible) {
        return montant;
    }
}
