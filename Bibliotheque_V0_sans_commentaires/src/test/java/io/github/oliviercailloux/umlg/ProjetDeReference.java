package io.github.oliviercailloux.umlg;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;

// Le projet cobaye du test de référence : un petit domaine de facturation qui exerce, en une
// fois, les huit annotations de la bibliothèque et leurs dix-neuf paramètres.
//
// Ce fichier est la moitié d'une paire. L'autre est diagramme-de-reference.puml, écrit à la main
// lui aussi : l'un dit ce que le développeur écrit, l'autre ce que la bibliothèque doit en faire.
// Les lire côte à côte est la façon la plus courte de comprendre ce que fait l'outil.
//
// Deux règles à tenir en le modifiant :
//   - il doit compiler sans le moindre avertissement du processeur d'annotations. Un cobaye que
//     la bibliothèque désapprouve ferait un mauvais exemple ;
//   - toute modification change le diagramme, donc le fichier de référence. C'est voulu : c'est
//     la relecture de cet écart qui donne sa valeur au test.
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

        @UMLConstraint(rule = "non vide")
        @UMLAssociation(
            target = Commande.class,
            type = UMLRelationType.COMPOSITION,
            sourceCardinality = "1",
            targetCardinality = "0..*",
            sourceRole = "acheteur",
            targetRole = "achats",
            isOrdered = true,
            qualifier = "numero",
            label = "passe",
            direction = UMLDirection.DOWN)
        private List<Commande> commandes;

        @Override
        public void valider() {}

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
