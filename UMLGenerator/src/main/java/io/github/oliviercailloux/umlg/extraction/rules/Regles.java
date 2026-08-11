package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
//   hide get*, set*, is*
//   hide with*, log, LOGGER
//
//   # Les types de Guava n'apprennent rien au lecteur du diagramme.
//   type ImmutableList -> List
//   type com.google.common.collect.ImmutableSet -> Set
//
//   # Les réglages du dessin d'ensemble.
//   title Architecture du module Facturation
//   direction left-to-right
//   show empty members
//
//   # Ce qui n'est pas écrit sur la ligne d'un membre qui, lui, reste affiché.
//   omit visibility
//   omit parameter names
//   omit return void
//
//   # Les membres dans l'ordre du code, et non par ordre alphabétique.
//   order declaration
//
// Une ligne « type » remplace un nom de type par un autre, partout où ce type paraît dans une
// boîte : attributs, types de retour, paramètres. Le nom de gauche s'écrit au choix simple
// (ImmutableList) ou complet (com.google.common.collect.ImmutableList) ; le second lève
// l'ambiguïté quand deux paquetages ont une classe de même nom.
//
// DEUX VERBES, ET CE QUI LES SÉPARE
//
// « hide » fait disparaître un membre de la boîte ; « omit » retire un morceau de la ligne d'un
// membre qui, lui, reste affiché. La distinction n'est pas cosmétique : @UMLInclude ramène tout
// ce qu'un « hide » écarte, tandis qu'un « omit » n'a rien à ramener — le membre est déjà là. Un
// seul verbe pour les deux obligerait le lecteur à se demander, à chaque ligne, si la règle de
// priorité s'y applique.
//
// « omit return void » efface le type rendu : la méthode s'écrit alors « valider() » et non
// « valider(): void ». UML tient ce type pour facultatif, et l'omettre veut dire « ne rend rien »
// — c'est la forme qu'emploient la plupart des diagrammes écrits à la main. La règle vaut pour le
// seul retour, et pour le seul void : un attribut ou un paramètre privé de son type laisserait
// une ligne incompréhensible, et effacer un type de retour utile — « names(): String » devenant
// « names() » — ferait perdre au dessin ce que la boîte existe pour montrer.
//
// CE QUI NE S'ÉCRIT PAS ICI
//
// Une règle ne désigne jamais un élément par son nom : c'est le partage avec les annotations
// (voir le paquetage annotation). Un motif est une famille de noms, jamais un nom qualifié —
// « hide Representation.string » n'existe pas, et @UMLExclude est le seul moyen de taire ce
// champ-là. De même, aucune règle ne range une classe nommée (@UMLPackage seul), et aucune ne
// fabrique une boîte ni un lien : ce qui n'est pas dans le code n'est pas sur le diagramme.
//
// Le sort de la génération précédente ne s'écrit pas ici non plus : il ne dit rien du modèle, et
// se règle à l'appel du générateur (UMLGenerator.Builder.keepPrevious).
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

    private static final Regles AUCUNE = new Regles(ImmutableMap.of(), false,
        UMLVisibility.NONE, false, ImmutableList.of(),
        ImmutableList.of(), false, "",
        UMLFlow.TOP_TO_BOTTOM, true, false, false, false, false, false);

    // Clé : le nom de type écrit à gauche de la flèche, simple ou complet. Valeur : ce qui
    // s'affiche à sa place.
    private final ImmutableMap<String, String> alias;

    // Faut-il effacer le « void » d'une méthode qui ne rend rien ?
    //
    // Faux par défaut : le type de retour est écrit, void compris. La règle « omit return void »
    // le tait, et elle ne vaut que pour ce type-là — voir l'en-tête de cette classe.
    private final boolean omettreRetourVoid;

    // La stratégie que prennent les classes qui n'en déclarent pas. NONE quand le projet se tait,
    // ce qui redonne exactement le comportement d'un projet sans fichier de règles.
    private final UMLVisibility strategie;

    // Vrai quand une ligne « strategy » a été écrite, fût-ce « strategy none ».
    //
    // La distinction ne change rien au dessin — NONE dans les deux cas — mais elle change ce
    // qu'on peut dire à l'utilisateur : une boîte vide sous un « strategy none » écrit est un
    // choix, la même boîte sans aucune ligne est presque toujours une surprise, et c'est elle
    // que la génération signale (voir ProjectScanner).
    private final boolean strategieDeclaree;

    // Les motifs de noms, façon « get* ». Une seule liste : un motif vise les méthodes et les
    // attributs indifféremment, un nom appartenant presque toujours à l'un ou à l'autre. Le rare
    // membre pris à tort se ramène par @UMLInclude, qui est fait pour cela — deux mots-clés pour
    // distinguer ce que le lecteur ne distingue pas auraient coûté plus que le cas qu'ils
    // réglaient.
    private final ImmutableList<Motif> membresMasques;

    // Les vues déclarées, dans l'ordre d'écriture : c'est celui des fichiers produits.
    private final ImmutableList<DefinitionDeVue> vues;

    // Vrai quand le projet demande une vue par paquetage, calculée sans rien déclarer.
    private final boolean vuesParPaquetage;

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
    private final boolean omettreVisibilite;

    // Faut-il taire les noms des paramètres — « from(KeyboardMap) » et non
    // « from(keyboardMap : KeyboardMap) » ?
    //
    // Faux par défaut : un nom écrit dans le code dit quelque chose. Mais les signatures
    // complètes font des boîtes larges, et un diagramme d'ensemble y perd plus qu'il n'y gagne —
    // mesuré sur un projet réel, la vue passait de 2522 à quelque 1300 pixels de large.
    private final boolean omettreNomsDeParametres;

    // Faut-il ranger les membres dans l'ordre où le code les déclare, plutôt que par alphabet ?
    //
    // Faux par défaut : l'ordre alphabétique reste le choix le plus sûr (voir MembresOrdonnes).
    // Mais l'ordre d'écriture porte souvent une intention — les fabriques d'abord, ou les
    // attributs importants en tête — que l'alphabet efface, et un projet doit pouvoir le garder.
    private final boolean ordreDeclaration;

    // Faut-il traduire la forme des conteneurs en multiplicités — « Set<X> » écrit
    // « X [0..*] », « Optional<X> » écrit « X [0..1] » ?
    //
    // Faux par défaut : la traduction change la notation de toutes les lignes à collection, et
    // c'est au projet de choisir la sienne. Rien n'y est deviné — le conteneur est la façon dont
    // Java écrit ces multiplicités — mais deux notations qui cohabiteraient sans qu'on l'ait
    // demandé seraient pires que l'une ou l'autre partout (voir TypeAffiche.multipliciteDeduite).
    // @UMLMultiplicity, décision individuelle, l'emporte sur la traduction.
    private final boolean multiplicitesDeduites;

    // Les opérations sans implémentation s'écrivent-elles sans la marque {abstract} ?
    //
    // Faux par défaut : l'italique est la notation qu'UML réserve à une opération abstraite, et
    // la retirer perd une information. Elle en perd peu, cependant, dans un diagramme dont les
    // boîtes abstraites sont des interfaces — le mot « interface » écrit en tête dit déjà que
    // tout y est abstrait, sauf ce que {static} distingue et les méthodes default. C'est ce
    // calcul-là que le projet fait à sa place, comme il le fait pour la visibilité.
    private final boolean omettreAbstrait;

    // Les alias qui ont servi au moins une fois. Les motifs, eux, se marquent eux-mêmes.
    //
    // C'est la seule entorse à l'immuabilité de cette classe, et elle est délibérée : savoir
    // qu'une ligne du fichier n'a rien fait exige de se souvenir de ce qui a servi, et seule
    // Regles voit passer toutes les consultations.
    //
    // L'ensemble est concurrent. Il l'était « comme la liste des classes ignorées du scanner »,
    // qui ne l'est plus : ce parcours-là s'est révélé séquentiel, et sa synchronisation ne
    // protégeait rien. Reste que Regles est consultée depuis ce parcours sans rien savoir de
    // lui, et qu'un objet partagé qui ne connaît pas ses appelants a raison de se garder.
    private final Set<String> aliasUtilises = ConcurrentHashMap.newKeySet();

    Regles(ImmutableMap<String, String> alias, boolean omettreRetourVoid,
        UMLVisibility strategie, boolean strategieDeclaree,
        ImmutableList<Motif> membresMasques, ImmutableList<DefinitionDeVue> vues,
        boolean vuesParPaquetage, String titre, UMLFlow direction,
        boolean masquerMembresVides, boolean omettreVisibilite,
        boolean omettreNomsDeParametres, boolean ordreDeclaration,
        boolean multiplicitesDeduites, boolean omettreAbstrait) {
        this.alias = checkNotNull(alias);
        this.omettreRetourVoid = omettreRetourVoid;
        this.strategie = checkNotNull(strategie);
        this.strategieDeclaree = strategieDeclaree;
        this.membresMasques = checkNotNull(membresMasques);
        this.vues = checkNotNull(vues);
        this.vuesParPaquetage = vuesParPaquetage;
        this.titre = checkNotNull(titre);
        this.direction = checkNotNull(direction);
        this.masquerMembresVides = masquerMembresVides;
        this.omettreVisibilite = omettreVisibilite;
        this.omettreNomsDeParametres = omettreNomsDeParametres;
        this.ordreDeclaration = ordreDeclaration;
        this.multiplicitesDeduites = multiplicitesDeduites;
        this.omettreAbstrait = omettreAbstrait;
    }

    public boolean omettreVisibilite() {
        return omettreVisibilite;
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

    // Ce que la stratégie du projet vaut pour une classe qui ne déclare rien.
    public UMLVisibility strategie() {
        return strategie;
    }

    // Une ligne « strategy » a-t-elle été écrite ? Sert au signalement des boîtes vides : sans
    // ligne, une boîte vide est une surprise à expliquer ; avec, c'est un choix à respecter.
    public boolean strategieDeclaree() {
        return strategieDeclaree;
    }

    // Les signatures s'écrivent-elles sans les noms des paramètres ?
    public boolean omettreNomsDeParametres() {
        return omettreNomsDeParametres;
    }

    // Les membres se rangent-ils dans l'ordre du code plutôt que par alphabet ?
    public boolean ordreDeclaration() {
        return ordreDeclaration;
    }

    // La forme des conteneurs se traduit-elle en multiplicités — « Set<X> » en « X [0..*] » ?
    public boolean multiplicitesDeduites() {
        return multiplicitesDeduites;
    }

    // Les opérations abstraites s'écrivent-elles sans la marque {abstract} ?
    public boolean omettreAbstrait() {
        return omettreAbstrait;
    }

    // La stratégie effective d'une classe : la sienne, ou celle du projet si elle s'en remet à
    // lui. C'est le seul endroit où UMLVisibility.PROJECT est résolu.
    public UMLVisibility strategieDe(UMLVisibility declaree) {
        checkNotNull(declaree);
        return declaree == UMLVisibility.PROJECT ? strategie : declaree;
    }

    // Ce nom de membre — méthode ou attribut — correspond-il à l'un des motifs masqués ?
    //
    // Une seule question pour les deux, parce que le fichier n'écrit qu'une seule sorte de motif :
    // « hide log » tait le champ log comme la méthode log, et c'est ce que veut dire la ligne.
    public boolean membreMasque(String nom) {
        checkNotNull(nom);
        for (Motif motif : membresMasques) {
            if (motif.correspond(nom)) {
                return true;
            }
        }
        return false;
    }

    // Les lignes du fichier qui n'ont servi à rien pendant le scan, telles qu'on les a écrites.
    //
    // Une règle qui désigne un nom introuvable est un no-op parfait : le diagramme se dessine,
    // exact, et rien n'y montre qu'une ligne du fichier est morte — une classe renommée, une
    // faute de frappe. Les vues, elles, échouent bruyamment dans ce cas ; les règles de nommage
    // ne le peuvent pas, un motif ayant le droit de ne viser personne aujourd'hui. D'où ce
    // relevé, que la génération signale en avertissement (voir ProjectScanner) : assez pour
    // être vu, pas assez pour arrêter un dessin qui reste juste.
    public ImmutableList<String> reglesSansEffet() {
        ImmutableList.Builder<String> mortes = ImmutableList.builder();
        for (var regle : alias.entrySet()) {
            if (!aliasUtilises.contains(regle.getKey())) {
                mortes.add("type " + regle.getKey() + " -> " + regle.getValue());
            }
        }
        for (Motif motif : membresMasques) {
            if (motif.sansEffet()) {
                mortes.add("hide " + motif.ecrit());
            }
        }
        return mortes.build();
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
        return LecteurDeRegles.lire(lignesEnUtf8(fichier), fichier.toString());
    }

    // Les règles d'un fichier désigné nommément, et non cherché à la racine d'un répertoire.
    //
    // Sert au processeur d'annotations : javac ne connaît pas le répertoire des classes
    // compilées, et se voit donc donner le chemin du fichier par l'option -Aumlg.rules. Le
    // fichier doit exister — l'avoir nommé et ne pas l'avoir écrit est une faute de
    // configuration, que l'appelant est seul à pouvoir signaler utilement.
    public static Regles duFichier(Path fichier) throws IOException {
        checkNotNull(fichier);
        return LecteurDeRegles.lire(lignesEnUtf8(fichier), fichier.toString());
    }

    // Les lignes du fichier, qui doit être en UTF-8.
    //
    // Un fichier enregistré dans un autre encodage — le CP-1252 d'un éditeur Windows, dès le
    // premier accent d'un commentaire — levait une MalformedInputException que l'enveloppe
    // générique de la génération rendait indéchiffrable. C'est une faute du fichier, comme un
    // mot-clé inconnu : elle mérite le même traitement, un refus qui dit quoi corriger.
    private static ImmutableList<String> lignesEnUtf8(Path fichier) throws IOException {
        try {
            return ImmutableList.copyOf(Files.readAllLines(fichier, StandardCharsets.UTF_8));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(fichier + " is not encoded in UTF-8, and the "
                + "rules file must be. Re-save it as UTF-8 — an accented letter in a comment is "
                + "enough to reveal another encoding.", e);
        }
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
            aliasUtilises.add(type.getName());
            return complet;
        }
        String simple = alias.get(type.getSimpleName());
        if (simple != null) {
            aliasUtilises.add(type.getSimpleName());
            return simple;
        }
        return type.getSimpleName();
    }

    // Le nom sous lequel un type rendu par une méthode paraît, ou la chaîne vide s'il est tu.
    //
    // Une méthode est le seul endroit où un type peut n'être pas écrit du tout, et void le seul
    // type qui s'y prête : « valider() » dit déjà qu'il n'y a rien à rendre. Ailleurs — un
    // attribut, un paramètre — le type porte l'information principale, et le taire laisserait une
    // ligne incompréhensible ; ailleurs qu'à void, effacer le retour retirerait au dessin ce que
    // la boîte existe pour montrer.
    public String nomAfficheRetour(Class<?> type) {
        checkNotNull(type);

        if (omettreRetourVoid && type == void.class) {
            return "";
        }
        return nomAffiche(type);
    }

    // Vrai quand aucune règle n'a été écrite. Lu par les tests, et par qui veut savoir si un
    // fichier a été trouvé.
    public boolean vides() {
        return alias.isEmpty() && !omettreRetourVoid && strategie == UMLVisibility.NONE
            && !strategieDeclaree
            && membresMasques.isEmpty()
            && vues.isEmpty() && !vuesParPaquetage && titre.isEmpty()
            && direction == UMLFlow.TOP_TO_BOTTOM && masquerMembresVides && !omettreVisibilite
            && !omettreNomsDeParametres && !ordreDeclaration && !multiplicitesDeduites
            && !omettreAbstrait;
    }
}
