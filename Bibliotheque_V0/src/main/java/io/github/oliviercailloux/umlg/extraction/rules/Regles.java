package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
//   # Les types de Guava n'apprennent rien au lecteur du diagramme.
//   type ImmutableList -> List
//   type com.google.common.collect.ImmutableSet -> Set
//
//   # Un retour void ne s'écrit pas, comme dans les diagrammes tenus à la main.
//   hide return void
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

    private static final Regles AUCUNE = new Regles(ImmutableMap.of(), ImmutableSet.of());

    // Clé : le nom de type écrit à gauche de la flèche, simple ou complet. Valeur : ce qui
    // s'affiche à sa place.
    private final ImmutableMap<String, String> alias;

    // Les types que l'on tait quand ils sont rendus par une méthode, écrits simples ou complets.
    private final ImmutableSet<String> retoursMasques;

    Regles(ImmutableMap<String, String> alias, ImmutableSet<String> retoursMasques) {
        this.alias = checkNotNull(alias);
        this.retoursMasques = checkNotNull(retoursMasques);
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
        return alias.isEmpty() && retoursMasques.isEmpty();
    }
}
