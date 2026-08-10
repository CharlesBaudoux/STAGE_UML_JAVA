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

// Le projet cobaye du test de référence : un petit domaine de facturation qui exerce, en une
// fois, les neuf annotations de la bibliothèque et leurs vingt et un paramètres.
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

    // Une interface : cible d'un « implements », donc d'une réalisation. Sans stratégie, sa boîte
    // reste vide — le cas nominal de @UMLClass, qui doit suffire seule.
    @UMLClass
    public interface Identifiable {

        String identifiant();
    }

    // Une classe abstraite : cible d'un « extends », donc d'une généralisation. PROTECTED montre
    // le public et le protégé.
    @UMLClass(strategy = UMLVisibility.PROTECTED)
    public abstract static class Personne implements Identifiable {

        protected String nom;

        public abstract void valider();
    }

    // Une énumération : ses littéraux occupent leur propre compartiment, sans type ni visibilité.
    // values() et valueOf(), que le compilateur ajoute, sont écartés par un motif du fichier de
    // règles — elles ne sont pas synthétiques, rien ne les retirerait sans qu'on le demande.
    @UMLClass(strategy = UMLVisibility.PUBLIC)
    public enum Statut {
        ACTIF, SUSPENDU
    }

    // La cible de l'association.
    @UMLClass
    public static class Commande {}

    // La cible de la dépendance. Volontairement distincte de Commande : une dépendance vers une
    // classe déjà atteinte par une association serait signalée comme un doublon.
    @UMLClass
    public static class ServiceBancaire {}

    // La classe qui porte presque tout.
    //
    // @UMLClass y montre ses quatre paramètres, @UMLNote ses deux, @UMLDependency ses deux,
    // @UMLConstraint le sien. La stratégie PACKAGE affiche le public et le package-private, mais
    // ni le protégé ni le privé — ce sont les dérogations qui rattrapent ce dernier.
    @UMLClass(name = "Client", color = "LightBlue", stereotype = "Entity",
        strategy = UMLVisibility.PACKAGE)
    @UMLNote(text = "Coeur du domaine, ne pas exposer hors du module",
        direction = UMLDirection.UP)
    @UMLConstraint(rule = "titulaire majeur")
    @UMLDependency(target = ServiceBancaire.class, label = "facture via")
    public static class ClientFacture extends Personne {

        // Privé, donc masqué par la stratégie : @UMLInclude le fait revenir.
        @UMLInclude
        private int compteur;

        // Public, donc affiché par la stratégie : @UMLExclude l'en retire.
        @UMLExclude
        public String journal;

        // Calculé et non stocké : UML le note d'une barre oblique devant son nom.
        @UMLDerived
        int age;

        // La contrainte s'écrit à la suite de l'attribut, entre accolades posées par le rendu.
        // La note, elle, s'accroche au membre : c'est le seul endroit du diagramme où PlantUML
        // impose la forme en bloc, refermée par « end note ».
        @UMLNote(text = "Jamais negatif")
        @UMLConstraint(rule = "solde positif")
        double solde;

        // Un attribut dont le type est lui-même une classe du diagramme, et qui reste pourtant un
        // attribut : la bibliothèque ne déduit jamais un lien d'un type. Seule @UMLAssociation en
        // crée un.
        Statut statut;

        // Une propriété multiple qui n'est pas un lien : il n'y a aucune classe à l'autre bout
        // d'un ensemble de chaînes. @UMLMultiplicity dit le nombre, et rien d'autre.
        @UMLMultiplicity("0..*")
        String etiquettes;

        // La seconde notation de la même annotation : l'association reste écrite dans la boîte,
        // sous la forme « type nom [multiplicité] ». Aucun trait n'en part, et la ligne paraît
        // bien que l'attribut soit privé — une association demandée nommément l'emporte sur la
        // stratégie, exactement comme le trait ci-dessous.
        @UMLAssociation(
            target = Commande.class,
            targetCardinality = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Commande> archivees;

        // L'association, avec ses neuf paramètres. Les contraintes posées sur le même attribut
        // partent sur le lien et non dans la boîte : l'attribut n'y figure plus. « ordered » en
        // est une comme une autre, depuis que le paramètre isOrdered — qui produisait le même
        // {ordered} par un second chemin — a été retiré.
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

        // Une méthode à paramètre : le diagramme le nomme, « montant : double ». Ce projet-ci est
        // compilé avec l'option « -parameters » (voir le pom) ; sans elle, la signature se
        // réduirait à « crediter(double) : void ».
        public void crediter(double montant) {}

        // Redéfinit la méthode abstraite de Personne.
        @Override
        public void valider() {}

        // La même annotation sur une méthode : c'est alors la valeur rendue qui est comptée.
        @UMLMultiplicity("0..*")
        public String historique() {
            return "";
        }

        // Une méthode dérivée : elle rend une valeur calculée.
        @UMLDerived
        public String resumer() {
            return "";
        }

        // Le contrat de l'interface, honoré par la classe mère.
        @Override
        public String identifiant() {
            return "";
        }

        // Un accesseur : le motif « hide get* » l'écarte, il n'apparaît pas sur le diagramme.
        public String getNom() {
            return nom;
        }
    }
}
