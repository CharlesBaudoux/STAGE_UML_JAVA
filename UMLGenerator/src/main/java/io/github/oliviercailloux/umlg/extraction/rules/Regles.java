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
// À la racine du répertoire analysé, sous le nom « umlg.json ». Pour un projet Maven ordinaire,
// cela veut dire src/main/resources/umlg.json, que la construction recopie dans target/classes.
// Le fichier est facultatif : son absence donne les mêmes règles que son absence de contenu,
// c'est-à-dire aucune.
//
// CE QU'ON Y ÉCRIT AUJOURD'HUI
//
//   {
//     "_": "La stratégie de tout le projet : @UMLClass seule suffit désormais.",
//     "strategy": "public",
//
//     "_bruit": "Rien n'est masqué tant qu'on ne l'a pas demandé.",
//     "hide": ["get*", "set*", "is*", "with*", "log", "LOGGER"],
//
//     "_types": "Les types de Guava n'apprennent rien au lecteur du diagramme.",
//     "types": {
//       "ImmutableList": "List",
//       "com.google.common.collect.ImmutableSet": "Set"
//     },
//
//     "_dessin": "Les réglages du dessin d'ensemble.",
//     "title": "Architecture du module Facturation",
//     "direction": "left-to-right",
//     "showEmptyMembers": true,
//
//     "_omit": "Ce qui n'est pas écrit sur la ligne d'un membre qui, lui, reste affiché.",
//     "omit": ["visibility", "parameter names", "return void"],
//
//     "_ordre": "Les membres dans l'ordre du code, et non par ordre alphabétique.",
//     "order": "declaration"
//   }
//
// JSON n'a pas de commentaires ; une clé dont le nom commence par « _ » n'est pas une règle et
// n'est pas lue, et c'est là que va une note. C'est la seule convention que la bibliothèque
// ajoute au format, et elle existe parce qu'un fichier écrit à la main a besoin de dire pourquoi.
//
// Une entrée de « types » remplace un nom de type par un autre, partout où ce type paraît dans
// une boîte : attributs, types de retour, paramètres. La clé s'écrit au choix simple
// (ImmutableList) ou complète (com.google.common.collect.ImmutableList) ; la seconde lève
// l'ambiguïté quand deux paquetages ont une classe de même nom.
//
// DEUX MOTS, ET CE QUI LES SÉPARE
//
// « hide » fait disparaître un membre de la boîte ; « omit » retire un morceau de la ligne d'un
// membre qui, lui, reste affiché. La distinction n'est pas cosmétique : @UMLInclude ramène tout
// ce qu'un « hide » écarte, tandis qu'un « omit » n'a rien à ramener — le membre est déjà là. Une
// seule clé pour les deux obligerait le lecteur à se demander, à chaque entrée, si la règle de
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
// « "hide": ["Representation.string"] » n'existe pas, et @UMLExclude est le seul moyen de taire
// ce champ-là. De même, aucune règle ne range une classe nommée (@UMLPackage seul), et aucune ne
// fabrique une boîte ni un lien : ce qui n'est pas dans le code n'est pas sur le diagramme.
//
// Le sort de la génération précédente ne s'écrit pas ici non plus : il ne dit rien du modèle, et
// se règle à l'appel du générateur (UMLGenerator.Builder.keepPrevious).
//
// Le fichier est fait pour grandir : d'autres clés viendront s'ajouter à « types », et c'est
// LecteurDeRegles qui les accueillera. Une clé inconnue est refusée, en nommant son chemin dans
// le fichier — mieux vaut arrêter que d'appliquer en silence des règles dont une partie est
// ignorée.
//
// CE QUE LES RÈGLES NE TOUCHENT PAS
//
// Le nom d'une boîte, et la classe visée par une @UMLAssociation. Ces deux-là ne sont pas lus
// dans le code mais écrits dans une annotation : renommer une boîte, c'est @UMLClass(name = ...).
// Une règle qui les atteindrait ferait diverger le type écrit dans une boîte du nom de la boîte
// d'à côté, sans que rien ne le signale.
public final class Regles {

    // Le nom du fichier, à la racine du répertoire analysé.
    public static final String NOM_DU_FICHIER = "umlg.json";

    private static final Regles AUCUNE = constructeur().construire();

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

    // Vrai quand une clé « strategy » a été écrite, fût-ce « "strategy": "none" ».
    //
    // La distinction ne change rien au dessin — NONE dans les deux cas — mais elle change ce
    // qu'on peut dire à l'utilisateur : une boîte vide sous un « "strategy": "none" » écrit est un
    // choix, la même boîte sans aucune clé est presque toujours une surprise, et c'est elle
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
    // qu'une entrée du fichier n'a rien fait exige de se souvenir de ce qui a servi, et seule
    // Regles voit passer toutes les consultations.
    //
    // L'ensemble est concurrent. Il l'était « comme la liste des classes ignorées du scanner »,
    // qui ne l'est plus : ce parcours-là s'est révélé séquentiel, et sa synchronisation ne
    // protégeait rien. Reste que Regles est consultée depuis ce parcours sans rien savoir de
    // lui, et qu'un objet partagé qui ne connaît pas ses appelants a raison de se garder.
    private final Set<String> aliasUtilises = ConcurrentHashMap.newKeySet();

    // Le fichier a-t-il écrit au moins une règle ? Voir vides().
    private final boolean auMoinsUneRegle;

    private Regles(Constructeur pose) {
        this.alias = pose.alias;
        this.omettreRetourVoid = pose.omettreRetourVoid;
        this.strategie = pose.strategie;
        this.strategieDeclaree = pose.strategieDeclaree;
        this.membresMasques = pose.membresMasques;
        this.vues = pose.vues;
        this.vuesParPaquetage = pose.vuesParPaquetage;
        this.titre = pose.titre;
        this.direction = pose.direction;
        this.masquerMembresVides = pose.masquerMembresVides;
        this.omettreVisibilite = pose.omettreVisibilite;
        this.omettreNomsDeParametres = pose.omettreNomsDeParametres;
        this.ordreDeclaration = pose.ordreDeclaration;
        this.multiplicitesDeduites = pose.multiplicitesDeduites;
        this.omettreAbstrait = pose.omettreAbstrait;
        this.auMoinsUneRegle = pose.auMoinsUneRegle;
    }

    // De quoi poser un jeu de règles sans compter les arguments.
    static Constructeur constructeur() {
        return new Constructeur();
    }

    // Un réglage à la fois, chacun sous son nom.
    //
    // POURQUOI PAS UN CONSTRUCTEUR
    //
    // Il en prenait quinze, positionnels, dont neuf booléens de suite : le compilateur ne
    // distinguera jamais « ordreDeclaration » de « multiplicitesDeduites », et deux valeurs
    // interverties auraient donné un dessin faux sans une ligne rouge nulle part. Le seul appel
    // qui existait était juste, ce qui ne dit rien du prochain — c'est l'absence de filet qui
    // était le défaut, non une faute constatée.
    //
    // DEUX RÉGLAGES QUI ÉTAIENT ENCODÉS PLUTÔT QU'ÉCRITS
    //
    // Le lecteur passait « !montrerMembresVides » : le fichier dit « montre », le champ dit
    // « masque », et l'inversion se faisait sur la ligne d'appel, là où personne ne la relit.
    // Elle se fait maintenant dans le setter, qui porte le mot du fichier.
    //
    // Il passait de même « strategie == null ? NONE : strategie, strategie != null », c'est-à-dire
    // deux informations tirées d'un même null. Ici, appeler strategie(...) dit à lui seul qu'une
    // stratégie a été écrite, et ne pas l'appeler dit qu'il n'y en a pas.
    static final class Constructeur {

        private ImmutableMap<String, String> alias = ImmutableMap.of();
        private boolean omettreRetourVoid;
        private UMLVisibility strategie = UMLVisibility.NONE;
        private boolean strategieDeclaree;
        private ImmutableList<Motif> membresMasques = ImmutableList.of();
        private ImmutableList<DefinitionDeVue> vues = ImmutableList.of();
        private boolean vuesParPaquetage;
        private String titre = "";
        private UMLFlow direction = UMLFlow.TOP_TO_BOTTOM;
        private boolean masquerMembresVides = true;
        private boolean omettreVisibilite;
        private boolean omettreNomsDeParametres;
        private boolean ordreDeclaration;
        private boolean multiplicitesDeduites;
        private boolean omettreAbstrait;
        private boolean auMoinsUneRegle;

        private Constructeur() {}

        // Toute clé de règle lue passe par ici, y compris celle qui redit le défaut : c'est ce
        // qui permet à vides() de répondre à la question posée — « le fichier écrit-il quelque
        // chose ? » — et non à une autre.
        private Constructeur ecrite() {
            auMoinsUneRegle = true;
            return this;
        }

        Constructeur types(ImmutableMap<String, String> alias) {
            this.alias = checkNotNull(alias);
            return ecrite();
        }

        Constructeur motifsMasques(ImmutableList<Motif> membresMasques) {
            this.membresMasques = checkNotNull(membresMasques);
            return ecrite();
        }

        // La stratégie que le fichier écrit. L'appeler dit qu'une stratégie a été écrite ; ne pas
        // l'appeler dit que le fichier se tait, et le silence vaut NONE.
        Constructeur strategie(UMLVisibility strategie) {
            this.strategie = checkNotNull(strategie);
            this.strategieDeclaree = true;
            return ecrite();
        }

        Constructeur vues(ImmutableList<DefinitionDeVue> vues) {
            this.vues = checkNotNull(vues);
            return ecrite();
        }

        Constructeur vuesParPaquetage(boolean vuesParPaquetage) {
            this.vuesParPaquetage = vuesParPaquetage;
            return ecrite();
        }

        Constructeur titre(String titre) {
            this.titre = checkNotNull(titre);
            return ecrite();
        }

        Constructeur direction(UMLFlow direction) {
            this.direction = checkNotNull(direction);
            return ecrite();
        }

        // Le mot du fichier — « showEmptyMembers » —, et non celui du champ : l'inversion vit ici,
        // à un seul endroit, et non sur la ligne d'appel.
        Constructeur montrerMembresVides(boolean montrer) {
            this.masquerMembresVides = !montrer;
            return ecrite();
        }

        Constructeur omettreVisibilite(boolean omettreVisibilite) {
            this.omettreVisibilite = omettreVisibilite;
            return ecrite();
        }

        Constructeur omettreNomsDeParametres(boolean omettreNomsDeParametres) {
            this.omettreNomsDeParametres = omettreNomsDeParametres;
            return ecrite();
        }

        Constructeur omettreRetourVoid(boolean omettreRetourVoid) {
            this.omettreRetourVoid = omettreRetourVoid;
            return ecrite();
        }

        Constructeur omettreAbstrait(boolean omettreAbstrait) {
            this.omettreAbstrait = omettreAbstrait;
            return ecrite();
        }

        Constructeur ordreDeclaration(boolean ordreDeclaration) {
            this.ordreDeclaration = ordreDeclaration;
            return ecrite();
        }

        Constructeur multiplicitesDeduites(boolean multiplicitesDeduites) {
            this.multiplicitesDeduites = multiplicitesDeduites;
            return ecrite();
        }

        Regles construire() {
            return new Regles(this);
        }
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

    // Une clé « strategy » a-t-elle été écrite ? Sert au signalement des boîtes vides : sans
    // clé, une boîte vide est une surprise à expliquer ; avec, c'est un choix à respecter.
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
    // « "hide": ["log"] » tait le champ log comme la méthode log, et c'est ce que veut dire
    // l'entrée.
    public boolean membreMasque(String nom) {
        checkNotNull(nom);
        for (Motif motif : membresMasques) {
            if (motif.correspond(nom)) {
                return true;
            }
        }
        return false;
    }

    // Les entrées du fichier qui n'ont servi à rien pendant le scan, désignées par leur clé et ce
    // qu'on y a écrit.
    //
    // Une règle qui désigne un nom introuvable est un no-op parfait : le diagramme se dessine,
    // exact, et rien n'y montre qu'une entrée du fichier est morte — une classe renommée, une
    // faute de frappe. Les vues, elles, échouent bruyamment dans ce cas ; les règles de nommage
    // ne le peuvent pas, un motif ayant le droit de ne viser personne aujourd'hui. D'où ce
    // relevé, que la génération signale en avertissement (voir ProjectScanner) : assez pour
    // être vu, pas assez pour arrêter un dessin qui reste juste.
    public ImmutableList<String> reglesSansEffet() {
        ImmutableList.Builder<String> mortes = ImmutableList.builder();
        for (var regle : alias.entrySet()) {
            if (!aliasUtilises.contains(regle.getKey())) {
                mortes.add("types: " + regle.getKey() + " -> " + regle.getValue());
            }
        }
        for (Motif motif : membresMasques) {
            if (motif.sansEffet()) {
                mortes.add("hide: " + motif.ecrit());
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
        return LecteurDeRegles.lire(contenuEnUtf8(fichier), fichier.toString());
    }

    // Les règles d'un fichier désigné nommément, et non cherché à la racine d'un répertoire.
    //
    // Sert au processeur d'annotations : javac ne connaît pas le répertoire des classes
    // compilées, et se voit donc donner le chemin du fichier par l'option -Aumlg.rules. Le
    // fichier doit exister — l'avoir nommé et ne pas l'avoir écrit est une faute de
    // configuration, que l'appelant est seul à pouvoir signaler utilement.
    public static Regles duFichier(Path fichier) throws IOException {
        checkNotNull(fichier);
        return LecteurDeRegles.lire(contenuEnUtf8(fichier), fichier.toString());
    }

    // Le contenu du fichier, qui doit être en UTF-8.
    //
    // JSON exige UTF-8 — la norme ne laisse le choix qu'entre les trois encodages d'Unicode, et
    // l'usage a retenu celui-là. Un fichier enregistré autrement — le CP-1252 d'un éditeur
    // Windows, dès le premier accent d'un titre — levait une MalformedInputException que
    // l'enveloppe générique de la génération rendait indéchiffrable. C'est une faute du fichier,
    // comme une clé inconnue : elle mérite le même traitement, un refus qui dit quoi corriger.
    private static String contenuEnUtf8(Path fichier) throws IOException {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(fichier + " is not encoded in UTF-8, and the "
                + "rules file must be. Re-save it as UTF-8 — an accented letter in a title is "
                + "enough to reveal another encoding.", e);
        }
    }

    // Les règles d'un contenu déjà lu. Sert aux tests, et à qui tiendrait ses règles ailleurs que
    // dans un fichier ; « origine » n'est là que pour les messages d'erreur.
    public static Regles lues(String contenu, String origine) {
        return LecteurDeRegles.lire(contenu, origine);
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

    // Vrai quand le fichier n'a écrit aucune règle. Lu par les tests, et par qui veut savoir si un
    // fichier a été trouvé.
    //
    // La question est bien « le fichier écrit-il quelque chose ? », et non « le dessin
    // s'en trouve-t-il changé ? ». Ces deux questions se confondaient : la méthode comparait les
    // quinze réglages à leurs valeurs par défaut, si bien qu'un fichier écrivant
    // « "showEmptyMembers": false » — que le format autorise explicitement, un fichier qui dit
    // tout haut ce qu'il fait se relisant mieux — se voyait répondre « aucune règle ». La réponse
    // était fausse pour l'usage annoncé, et les quinze comparaisons étaient de surcroît une
    // quinzième occasion d'oublier un champ à chaque réglage ajouté.
    public boolean vides() {
        return !auMoinsUneRegle;
    }
}
