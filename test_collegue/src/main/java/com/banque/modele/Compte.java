package com.banque.modele;

import com.banque.contrat.Identifiable;
import io.github.oliviercailloux.umlg.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * Couvre : strategy = PACKAGE, @UMLInclude sur un attribut et sur une méthode que le filtre
 * anti-bruit aurait écartée, @UMLConstraint répétée sur une classe et posée sur un attribut,
 * @UMLNote orientée vers le bas, l'association qualifiée, et la cible portée par une Map comme
 * par un attribut simple.
 */
@UMLClass(stereotype = "AggregateRoot", strategy = UMLVisibility.PACKAGE)
@UMLDependency(target = Confidentiel.class, label = "marque ses champs")
@UMLNote(text = "Racine d'agrégat : les opérations\nn'existent qu'au travers d'elle.",
    direction = UMLDirection.DOWN)
@UMLConstraint(rule = "un compte clôturé n'accepte plus d'opération")
@UMLConstraint(rule = "devise immuable après ouverture")
public class Compte implements Identifiable {

    // package-private : affiché par la stratégie PACKAGE.
    String numeroIban;

    // private : hors stratégie, donc ignoré.
    @Confidentiel
    private double soldeSecret;

    // ... à moins d'une dérogation explicite. La contrainte a dès lors un attribut où
    // s'accrocher, et s'écrit entre accolades à sa suite.
    @UMLInclude
    @UMLConstraint(rule = "plafondAutorise > 0")
    private double plafondAutorise;

    /**
     * Cible portée par une Map : rien dans le type ne dirait laquelle des deux moitiés est
     * visée, d'où le target explicite, ici le type de la <em>valeur</em>.
     */
    @UMLAssociation(
        target = Operation.class,
        type = UMLRelationType.COMPOSITION,
        targetCardinality = "0..*",
        targetRole = "historique",
        label = "enregistre")
    @UMLConstraint(rule = "ordered")
    private Map<String, Operation> operations;

    /** Cible portée par un attribut simple ; cardinalité côté source non triviale. */
    @UMLAssociation(
        target = Devise.class,
        sourceCardinality = "*",
        targetCardinality = "1",
        targetRole = "libellé en",
        direction = UMLDirection.LEFT)
    private Devise devise;

    /** La cible est une énumération : elle aussi doit porter @UMLClass pour être visée. */
    @UMLAssociation(
        target = TypeCompte.class,
        targetCardinality = "1",
        label = "de type",
        direction = UMLDirection.UP)
    private TypeCompte type;

    /** Non annoté : reste un attribut ordinaire, aucun lien n'en est tiré. */
    List<String> etiquettes;

    public void deposer(double montant) {}

    public void cloturer() {}

    /**
     * Dérogation positive sur une méthode que le filtre anti-bruit aurait écartée : c'est un
     * getter en bonne et due forme, et il est pourtant affiché.
     */
    @UMLInclude
    public double getSoldeSecret() {
        return soldeSecret;
    }

    // Bruit : un setter, et une méthode héritée d'Object.
    public void setSoldeSecret(double solde) {
        this.soldeSecret = solde;
    }

    @Override
    public int hashCode() {
        return numeroIban.hashCode();
    }

    @Override
    public String identifiant() {
        return numeroIban;
    }
}
