package com.banque.modele;

import com.banque.contrat.Auditable;
import io.github.oliviercailloux.umlg.annotation.*;
import java.util.List;
import java.util.Optional;

/**
 * Couvre : @UMLClass(name, stereotype, color sans '#', strategy = PRIVATE), @UMLExclude sur un
 * attribut, @UMLDerived sur une méthode et sur un lien, @UMLNote répétée sur une classe,
 * @UMLConstraint sur une classe et sur un lien, le filtre anti-bruit sur les accesseurs, et
 * trois formes d'associations.
 *
 * <p>Hérite de {@code Personne} — généralisation, trait plein — et réalise {@code Auditable} —
 * trait pointillé. Ces deux liens ne sont portés par aucune annotation : la signature Java
 * suffit.
 */
@UMLClass(name = "ClientPremium", stereotype = "Entity", color = "ADD8E6",
    strategy = UMLVisibility.PRIVATE)
@UMLNote(text = "Le « Premium » du nom vient de @UMLClass(name),\npas de la classe Java.",
    direction = UMLDirection.LEFT)
@UMLNote(text = "Seconde bulle sur la même classe : @UMLNote est répétable.")
@UMLConstraint(rule = "email unique dans la banque")
public class Client extends Personne implements Auditable {

    // strategy = PRIVATE : tout est affiché, y compris ces attributs privés.
    private String nom;
    private String email;
    public int age;

    // ... sauf ce qui est explicitement écarté. Une trace technique n'a aucune valeur
    // architecturale : c'est le cas d'usage même de @UMLExclude.
    @UMLExclude
    private String traceInterne;

    /**
     * Tous les paramètres de @UMLAssociation sauf qualifier, avec une cible explicite : le cas
     * de la spécification.
     *
     * <p>La contrainte, posée sur un attribut promu en lien, s'applique au lien : elle s'écrit
     * à l'extrémité cible, à côté de « {ordered} », et non dans la boîte.
     */
    @UMLAssociation(
        target = Compte.class,
        type = UMLRelationType.COMPOSITION,
        sourceCardinality = "1",
        targetCardinality = "1..*",
        sourceRole = "titulaire",
        targetRole = "comptes",
        isOrdered = true,
        label = "détient",
        direction = UMLDirection.DOWN)
    @UMLConstraint(rule = "un compte courant au moins")
    private List<Compte> comptes;

    /** Cible portée par un Optional ; agrégation, cycles de vie indépendants. */
    @UMLAssociation(
        target = Adresse.class,
        type = UMLRelationType.AGGREGATION,
        targetCardinality = "0..1",
        targetRole = "domicile",
        direction = UMLDirection.RIGHT)
    private Optional<Adresse> adresse;

    /**
     * Lien dérivé : la barre oblique se place devant le rôle cible, « /cartesActives ». Rien
     * n'est stocké de ce côté — la liste se recalcule en filtrant les cartes non bloquées.
     *
     * <p>Tableau porteur, association simple, direction laissée à NONE : c'est PlantUML qui
     * place cette extrémité.
     */
    @UMLDerived
    @UMLAssociation(
        target = CarteBancaire.class,
        targetCardinality = "0..*",
        targetRole = "cartesActives")
    private CarteBancaire[] cartes;

    // Filtre anti-bruit : ni le constructeur, ni les accesseurs, ni toString n'apparaissent.
    public Client() {}

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public boolean isMajeur() {
        return age >= 18;
    }

    @Override
    public String toString() {
        return nom;
    }

    // Une vraie méthode métier, elle, est affichée.
    public void fusionnerAvec(Client autre) {}

    /**
     * Méthode dérivée : le total ne se range nulle part, il se somme à chaque appel.
     *
     * <p>La note est orientée à gauche, et non vers le bas : PlantUML n'accepte « top » et
     * « bottom » que pour une note de classe. La bibliothèque se rabat désormais sur la droite
     * plutôt que d'écrire une syntaxe refusée, et le processeur le signale à la compilation —
     * autant écrire d'emblée une direction qui existe.
     */
    @UMLDerived
    @UMLNote(text = "Recalculé à chaque appel :\nla barre oblique le dit.",
        direction = UMLDirection.LEFT)
    public double soldeTotal() {
        return 0;
    }

    // « getaway » commence par « get » sans être un accesseur : le filtre s'appuie sur la
    // convention JavaBean complète, pas sur le seul préfixe. Cette méthode reste affichée.
    public void getaway() {}

    @Override
    public String identifiant() {
        return email;
    }

    @Override
    public String empreinteAudit() {
        return nom;
    }
}
