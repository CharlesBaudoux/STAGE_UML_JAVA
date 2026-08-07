package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Les règles que le projet applique à tous ses diagrammes.
//
// POURQUOI UN FICHIER, ET NON UNE ANNOTATION
//
// Les annotations disent une chose sur un élément : cette classe-ci est une boîte, cet
// attribut-là est un lien. Elles sont le bon outil pour ce qui se décide au cas par cas, et le
// mauvais pour ce qui vaut partout — écrire « affiche ImmutableList comme List » sur chacun des
// deux cents attributs d'un projet est un travail que personne ne fait, et que personne ne tient
// à jour. Une règle générale se déclare une fois, à un endroit, et vaut pour tout le projet.
//
// OÙ LE FICHIER SE TROUVE
//
// À la racine du répertoire analysé, sous le nom « umlg.rules ». Pour un projet Maven ordinaire,
// cela veut dire src/main/resources/umlg.rules, que la construction recopie dans target/classes.
// Le fichier est facultatif : son absence donne les mêmes règles que son absence de contenu,
// c'est-à-dire aucune.
//
// CE QU'ON Y ÉCRIT AUJOURD'HUI
//
//   # La stratégie de tout le projet : @UMLClass seule suffit désormais.
//   strategy public
//
//   # Le bruit : rien n'est masqué tant qu'on ne l'a pas demandé.
//   hide accessors
//   hide object-methods
//   hide method with*
//   hide field log, LOGGER
//
//   # Les types de Guava n'apprennent rien au lecteur du diagramme.
//   type ImmutableList -> List
//   type com.google.common.collect.ImmutableSet -> Set
//
//   # Un retour void ne s'écrit pas, comme dans les diagrammes tenus à la main.
//   hide return void
//
//   # Un paquetage entier dans un rectangle, sans annoter chaque classe.
//   group package com.exemple.modele -> "Modèle"
//
//   # Les réglages du dessin d'ensemble.
//   title Architecture du module Facturation
//   direction left-to-right
//   show empty members
//   hide visibility
//
//   # Une boîte qu'aucune classe Java ne porte, dessinée en pointillés.
//   declare class StringRepresentation extends Representation
//   declare field StringRepresentation string : String
//   declare association Animal -> Chien 0..*
//
// Une ligne « type » remplace un nom de type par un autre, partout où ce type paraît dans une
// boîte : attributs, types de retour, paramètres. Le nom de gauche s'écrit au choix simple
// (ImmutableList) ou complet (com.google.common.collect.ImmutableList) ; le second lève
// l'ambiguïté quand deux paquetages ont une classe de même nom.
//
// Une ligne « hide return » efface un type de retour : la méthode s'écrit alors « valider() »
// et non « valider(): void ». UML tient ce type pour facultatif, et l'omettre veut dire « ne rend
// rien » — c'est la forme qu'emploient la plupart des diagrammes écrits à la main. La règle vaut
// pour le seul retour : le type reste écrit partout où il dit quelque chose, sur un attribut
// comme sur un paramètre. Par défaut, rien n'est masqué et le void est écrit.
//
// Le fichier est fait pour grandir : d'autres mots-clés viendront s'ajouter à « type », et c'est
// LecteurDeRegles qui les accueillera. Un mot-clé inconnu est refusé, avec le numéro de ligne —
// mieux vaut arrêter que d'appliquer en silence des règles dont une partie est ignorée.
//
// CE QUE LES RÈGLES NE TOUCHENT PAS
//
// Le nom d'une boîte, et la classe visée par une @UMLAssociation. Ces deux-là ne sont pas lus
// dans le code mais écrits dans une annotation : renommer une boîte, c'est @UMLClass(name = ...).
// Une règle qui les atteindrait ferait diverger le type écrit dans une boîte du nom de la boîte
// d'à côté, sans que rien ne le signale.
public final class Regles {

    // Le nom du fichier, à la racine du répertoire analysé.
    public static final String NOM_DU_FICHIER = "umlg.rules";

    private static final Regles AUCUNE = new Regles(ImmutableMap.of(), ImmutableSet.of(),
        UMLVisibility.NONE, ImmutableSet.of(), ImmutableList.of(), ImmutableList.of(),
        ImmutableMap.of(), ImmutableList.of(), false, Sortie.REPLACE, "",
        UMLFlow.TOP_TO_BOTTOM, true, false, ImmutableList.of(), ImmutableList.of());

    // Clé : le nom de type écrit à gauche de la flèche, simple ou complet. Valeur : ce qui
    // s'affiche à sa place.
    private final ImmutableMap<String, String> alias;

    // Les types que l'on tait quand ils sont rendus par une méthode, écrits simples ou complets.
    private final ImmutableSet<String> retoursMasques;

    // La stratégie que prennent les classes qui n'en déclarent pas. NONE quand le projet se tait,
    // ce qui redonne exactement le comportement d'un projet sans fichier de règles.
    private final UMLVisibility strategie;

    // Les familles de membres que le projet tient pour du bruit. Vide par défaut : la
    // bibliothèque ne décide plus à la place de l'utilisateur de ce qui mérite d'être dessiné.
    private final ImmutableSet<Bruit> bruits;

    // Les motifs de noms, façon « get* » : méthodes d'un côté, attributs de l'autre.
    private final ImmutableList<Motif> methodesMasquees;
    private final ImmutableList<Motif> champsMasques;

    // Paquetage Java -> nom du groupe visuel qui l'entoure.
    private final ImmutableMap<String, String> groupesParPaquetage;

    // Les vues déclarées, dans l'ordre d'écriture : c'est celui des fichiers produits.
    private final ImmutableList<DefinitionDeVue> vues;

    // Vrai quand le projet demande une vue par paquetage, calculée sans rien déclarer.
    private final boolean vuesParPaquetage;

    // Ce qu'on fait des diagrammes de la génération précédente.
    private final Sortie sortie;

    // Le titre de la vue intégrale. Vide = aucun titre écrit en tête du dessin.
    private final String titre;

    // Le sens dans lequel la vue intégrale se déplie ; chaque autre vue déclare le sien.
    private final UMLFlow direction;

    // Faut-il masquer les compartiments d'attributs et de méthodes restés vides ?
    //
    // Vrai par défaut, et ce défaut n'est pas neutre : sans stratégie déclarée, une boîte n'a
    // aucun membre, si bien que ne pas masquer dessinerait deux rectangles vides sous chaque nom.
    private final boolean masquerMembresVides;

    // Faut-il taire les symboles de visibilité — « + », « - », « # », « ~ » — devant les membres ?
    //
    // Faux par défaut : UML les demande, et ils disent quelque chose qu'aucune autre marque ne
    // dit. Mais beaucoup de diagrammes tenus à la main les omettent, parce qu'ils exposent une
    // structure plutôt qu'une API, et le projet doit pouvoir en faire autant.
    private final boolean masquerVisibilite;

    // Les boîtes que le fichier déclare, et qu'aucune classe Java ne porte.
    private final ImmutableList<ClasseDeclaree> classesDeclarees;

    // Les liens déclarés entre deux boîtes, réelles ou déclarées : « source -> cible ».
    private final ImmutableList<LienDeclare> liensDeclares;

    // Ce que devient le dossier des vues d'une génération à la suivante.
    public enum Sortie {
        // Les diagrammes précédents sont effacés : le dossier décrit toujours l'état courant du
        // code, et une vue supprimée du fichier ne laisse pas derrière elle une image qui ment.
        REPLACE,
        // Rien n'est effacé : la génération précédente est conservée à côté, sous un nom
        // numéroté, ce qui permet de comparer avant et après une modification du code.
        KEEP
    }

    Regles(ImmutableMap<String, String> alias, ImmutableSet<String> retoursMasques,
        UMLVisibility strategie, ImmutableSet<Bruit> bruits,
        ImmutableList<Motif> methodesMasquees, ImmutableList<Motif> champsMasques,
        ImmutableMap<String, String> groupesParPaquetage, ImmutableList<DefinitionDeVue> vues,
        boolean vuesParPaquetage, Sortie sortie, String titre, UMLFlow direction,
        boolean masquerMembresVides, boolean masquerVisibilite,
        ImmutableList<ClasseDeclaree> classesDeclarees,
        ImmutableList<LienDeclare> liensDeclares) {
        this.alias = checkNotNull(alias);
        this.retoursMasques = checkNotNull(retoursMasques);
        this.strategie = checkNotNull(strategie);
        this.bruits = checkNotNull(bruits);
        this.methodesMasquees = checkNotNull(methodesMasquees);
        this.champsMasques = checkNotNull(champsMasques);
        this.groupesParPaquetage = checkNotNull(groupesParPaquetage);
        this.vues = checkNotNull(vues);
        this.vuesParPaquetage = vuesParPaquetage;
        this.sortie = checkNotNull(sortie);
        this.titre = checkNotNull(titre);
        this.direction = checkNotNull(direction);
        this.masquerMembresVides = masquerMembresVides;
        this.masquerVisibilite = masquerVisibilite;
        this.classesDeclarees = checkNotNull(classesDeclarees);
        this.liensDeclares = checkNotNull(liensDeclares);
    }

    public ImmutableList<ClasseDeclaree> classesDeclarees() {
        return classesDeclarees;
    }

    public ImmutableList<LienDeclare> liensDeclares() {
        return liensDeclares;
    }

    // Un lien déclaré dans le fichier, entre deux boîtes désignées par leur nom.
    public record LienDeclare(String source, String cible, String cardinalite) {}

    public boolean masquerVisibilite() {
        return masquerVisibilite;
    }

    // Le titre écrit en tête de la vue intégrale, ou la chaîne vide.
    public String titre() {
        return titre;
    }

    // Le sens de lecture de la vue intégrale.
    public UMLFlow direction() {
        return direction;
    }

    public boolean masquerMembresVides() {
        return masquerMembresVides;
    }

    // Les vues que le projet déclare, dans l'ordre d'écriture.
    public ImmutableList<DefinitionDeVue> vues() {
        return vues;
    }

    // Le projet demande-t-il une vue par paquetage, en plus de ce qu'il déclare ?
    public boolean vuesParPaquetage() {
        return vuesParPaquetage;
    }

    public Sortie sortie() {
        return sortie;
    }

    // Ce que la stratégie du projet vaut pour une classe qui ne déclare rien.
    public UMLVisibility strategie() {
        return strategie;
    }

    // La stratégie effective d'une classe : la sienne, ou celle du projet si elle s'en remet à
    // lui. C'est le seul endroit où UMLVisibility.PROJECT est résolu.
    public UMLVisibility strategieDe(UMLVisibility declaree) {
        checkNotNull(declaree);
        return declaree == UMLVisibility.PROJECT ? strategie : declaree;
    }

    // Cette famille de bruit est-elle masquée par le projet ?
    public boolean masque(Bruit famille) {
        checkNotNull(famille);
        return bruits.contains(famille);
    }

    // Ce nom de méthode correspond-il à l'un des motifs masqués ?
    public boolean methodeMasquee(String nom) {
        return correspond(methodesMasquees, nom);
    }

    public boolean champMasque(String nom) {
        return correspond(champsMasques, nom);
    }

    private static boolean correspond(ImmutableList<Motif> motifs, String nom) {
        checkNotNull(nom);
        for (Motif motif : motifs) {
            if (motif.correspond(nom)) {
                return true;
            }
        }
        return false;
    }

    // Le groupe visuel d'un paquetage, ou la chaîne vide s'il n'en a pas.
    //
    // La correspondance est exacte : « com.exemple.modele » ne range pas les classes de
    // « com.exemple.modele.detail ». Deux règles qui se recouvriraient demanderaient une règle de
    // priorité, et un sous-paquetage ajouté entrerait en silence dans un rectangle que personne
    // n'a désigné.
    public String groupeDuPaquetage(String paquetage) {
        checkNotNull(paquetage);
        return groupesParPaquetage.getOrDefault(paquetage, "");
    }

    // Le jeu vide : ce que rend un projet sans fichier de règles, et ce que passent les appelants
    // qui n'en ont pas — les tests, l'extraction d'une classe isolée.
    public static Regles aucune() {
        return AUCUNE;
    }

    // Les règles d'un projet, lues à la racine du répertoire analysé.
    //
    // L'absence de fichier n'est pas une faute : la grande majorité des projets n'en ont pas
    // besoin. Un fichier présent mais illisible en est une, en revanche — il a été écrit pour
    // être appliqué, et l'ignorer donnerait un diagramme qui n'est pas celui qu'on a demandé.
    public static Regles duProjet(Path racine) throws IOException {
        checkNotNull(racine);
        Path fichier = racine.resolve(NOM_DU_FICHIER);

        if (!Files.isRegularFile(fichier)) {
            return aucune();
        }
        ImmutableList<String> lignes =
            ImmutableList.copyOf(Files.readAllLines(fichier, StandardCharsets.UTF_8));

        return LecteurDeRegles.lire(lignes, fichier.toString());
    }

    // Les règles d'un fichier désigné nommément, et non cherché à la racine d'un répertoire.
    //
    // Sert au processeur d'annotations : javac ne connaît pas le répertoire des classes
    // compilées, et se voit donc donner le chemin du fichier par l'option -Aumlg.rules. Le
    // fichier doit exister — l'avoir nommé et ne pas l'avoir écrit est une faute de
    // configuration, que l'appelant est seul à pouvoir signaler utilement.
    public static Regles duFichier(Path fichier) throws IOException {
        checkNotNull(fichier);
        return LecteurDeRegles.lire(
            ImmutableList.copyOf(Files.readAllLines(fichier, StandardCharsets.UTF_8)),
            fichier.toString());
    }

    // Les règles d'un contenu déjà lu. Sert aux tests, et à qui tiendrait ses règles ailleurs que
    // dans un fichier ; « origine » n'est là que pour les messages d'erreur.
    public static Regles lues(ImmutableList<String> lignes, String origine) {
        return LecteurDeRegles.lire(lignes, origine);
    }

    // Le nom sous lequel un type paraît dans une boîte.
    //
    // Le nom complet est consulté avant le nom simple : une règle qui désigne précisément
    // com.google.common.collect.ImmutableList doit l'emporter sur une règle homonyme visant
    // toutes les ImmutableList, sans quoi la précision demandée serait sans effet.
    public String nomAffiche(Class<?> type) {
        checkNotNull(type);

        String complet = alias.get(type.getName());
        if (complet != null) {
            return complet;
        }
        String simple = alias.get(type.getSimpleName());

        return simple != null ? simple : type.getSimpleName();
    }

    // Le nom sous lequel un type rendu par une méthode paraît, ou la chaîne vide s'il est tu.
    //
    // Une méthode est le seul endroit où un type peut n'être pas écrit du tout : « valider() »
    // dit déjà qu'il n'y a rien à rendre. Ailleurs — un attribut, un paramètre — le type porte
    // l'information principale, et le taire laisserait une ligne incompréhensible ; c'est
    // pourquoi la règle est réservée au retour, et non offerte partout.
    public String nomAfficheRetour(Class<?> type) {
        checkNotNull(type);

        if (retoursMasques.contains(type.getName())
            || retoursMasques.contains(type.getSimpleName())) {
            return "";
        }
        return nomAffiche(type);
    }

    // Vrai quand aucune règle n'a été écrite. Lu par les tests, et par qui veut savoir si un
    // fichier a été trouvé.
    public boolean vides() {
        return alias.isEmpty() && retoursMasques.isEmpty() && strategie == UMLVisibility.NONE
            && bruits.isEmpty() && methodesMasquees.isEmpty() && champsMasques.isEmpty()
            && groupesParPaquetage.isEmpty() && vues.isEmpty() && !vuesParPaquetage
            && sortie == Sortie.REPLACE && titre.isEmpty()
            && direction == UMLFlow.TOP_TO_BOTTOM && masquerMembresVides && !masquerVisibilite
            && classesDeclarees.isEmpty() && liensDeclares.isEmpty();
    }
}
