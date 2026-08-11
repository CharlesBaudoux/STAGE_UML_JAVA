package io.github.oliviercailloux.umlg;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;

public final class ProjetDeReference {

    private ProjetDeReference() {}

    @UMLClass
    public interface Identifiable {

        String identifiant();
    }

    @UMLClass(strategy = UMLVisibility.PROTECTED)
    public abstract static class Personne implements Identifiable {

        protected String nom;

        public abstract void valider();
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    public enum Statut {
        ACTIF, SUSPENDU
    }

    @UMLClass
    public static class Commande {}

    @UMLClass
    public static class ServiceBancaire {}

    @UMLClass(name = "Client", color = "LightBlue", stereotype = "Entity",
        strategy = UMLVisibility.PACKAGE)
    @UMLNote(text = "Coeur du domaine, ne pas exposer hors du module",
        direction = UMLDirection.UP)
    @UMLConstraint(rule = "titulaire majeur")
    @UMLDependency(target = ServiceBancaire.class, label = "facture via")
    public static class ClientFacture extends Personne {

        @UMLInclude
        private int compteur;

        @UMLExclude
        public String journal;

        @UMLDerived
        int age;

        @UMLNote(text = "Jamais negatif")
        @UMLConstraint(rule = "solde positif")
        double solde;

        Statut statut;

        @UMLMultiplicity("0..*")
        String etiquettes;

        @UMLAssociation(
            target = Commande.class,
            targetCardinality = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> archivees;

        @UMLConstraint(rule = "ordered")
        @UMLConstraint(rule = "non vide")
        @UMLAssociation(
            target = Commande.class,
            notation = UMLNotation.ARROW,
            type = UMLRelationType.COMPOSITION,
            sourceCardinality = "1",
            targetCardinality = "0..*",
            sourceRole = "acheteur",
            targetRole = "achats",
            label = "passe",
            direction = UMLDirection.DOWN)
        private List<Commande> commandes;

        public void crediter(double montant) {}

        @Override
        public void valider() {}

        @UMLMultiplicity("0..*")
        public String historique() {
            return "";
        }

        @UMLDerived
        public String resumer() {
            return "";
        }

        @Override
        public String identifiant() {
            return "";
        }

        public String getNom() {
            return nom;
        }
    }
}
