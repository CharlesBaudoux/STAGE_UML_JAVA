package com.banque.modele;
import io.github.oliviercailloux.umlg.annotation.*;

@UMLClass(stereotype = "AggregateRoot", strategy = UMLVisibility.PACKAGE)
public class Compte {
    String numeroIban; // package-private (sera affiché grâce à la stratégie)
    private double soldeSecret; // Sera ignoré car private
    public void deposer(double montant) {}
}
