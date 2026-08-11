package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Lit le fichier de règles, qui est un objet JSON.
//
// UNE CLÉ PAR NATURE DE RÈGLE
//
// L'objet de tête porte onze clés possibles — de « strategy » à « views » — et chacune dit une
// nature de règle. C'est la clé qui décide comment lire sa valeur, si bien qu'ajouter une nature
// se fait ici, et à un seul endroit : une branche de plus dans le switch, et la méthode qui va
// avec. Une clé inconnue est refusée : mieux vaut arrêter que d'appliquer en silence des règles
// dont une partie est ignorée.
//
// POURQUOI JSON, ET NON UNE GRAMMAIRE À NOUS
//
// Le fichier s'écrivait ligne à ligne, avec sa propre grammaire : un mot-clé, puis un reste que
// chaque mot-clé découpait à sa façon — une flèche pour « type », des virgules pour « hide », des
// guillemets et un tiret entouré d'espaces pour « view ». Cela se lisait bien et s'analysait
// sans dépendance, mais c'était un format que personne d'autre ne connaissait : aucun éditeur ne
// le colorait, aucun schéma ne le validait, et une virgule oubliée ne se voyait qu'à
// l'exécution. JSON coûte quelques accolades et rend tout cela.
//
// Il en rend d'ailleurs plus qu'il n'y paraît. Les listes n'ont plus à être découpées : « hide »
// et « omit » reçoivent des tableaux, et la virgule égarée est refusée par l'analyseur, non par
// nous. Une vue ne se déclare plus sur deux lignes — son contenu, puis sa direction — mais dans
// un objet qui porte les deux. Et une clé écrite deux fois est une faute que Jackson relève
// (STRICT_DUPLICATE_DETECTION), là où il fallait s'en souvenir soi-même.
//
// CE QUE JSON NE DONNE PAS
//
// Des commentaires. Le fichier est écrit à la main, et un fichier de règles a besoin de dire
// pourquoi il masque telle famille de noms. D'où la seule convention que ce lecteur ajoute au
// format : une clé dont le nom commence par « _ » n'est pas une règle et n'est pas lue. C'est du
// JSON valide, et c'est l'usage établi partout où le format sert de fichier de configuration.
//
// LES REFUS
//
// Le fichier étant écrit à la main, chaque refus nomme le fichier et le chemin fautif — « views[1]
// .direction » —, puis rappelle la forme attendue. C'est le seul retour que l'auteur du fichier
// aura : il n'y a pas de compilateur pour relire ce qu'il écrit. Les fautes de syntaxe, elles,
// gardent le numéro de ligne, que l'analyseur connaît.
final class LecteurDeRegles {

    // Les clés de l'objet de tête.
    private static final String TYPES = "types";
    private static final String HIDE = "hide";
    private static final String OMIT = "omit";
    private static final String STRATEGY = "strategy";
    private static final String VIEWS = "views";
    private static final String VIEWS_BY_PACKAGE = "viewsByPackage";
    private static final String TITLE = "title";
    private static final String SHOW_EMPTY_MEMBERS = "showEmptyMembers";
    private static final String ORDER = "order";
    private static final String MULTIPLICITY = "multiplicity";
    private static final String DIRECTION = "direction";

    // Les clés d'une vue, et celles d'un sélecteur qui n'est pas un simple nom de classe.
    private static final String NAME = "name";
    private static final String RETAIN = "retain";
    private static final String REMOVE = "remove";
    private static final String PACKAGE = "package";
    private static final String AROUND = "around";

    // Ce qu'un « omit » peut demander, et le mot qui l'écrit dans le fichier.
    private enum Omission {
        VISIBILITE("visibility"),
        NOMS_DE_PARAMETRES("parameter names"),
        RETOUR_VOID("return void"),
        ABSTRAIT("abstract");

        private final String ecrit;

        Omission(String ecrit) {
            this.ecrit = ecrit;
        }
    }

    // L'analyseur, réglé deux fois plus strictement que par défaut.
    //
    // STRICT_DUPLICATE_DETECTION : deux clés de même nom se contrediraient, et la seconde
    // l'emporterait en silence — c'est exactement le refus que le format ligne à ligne écrivait à
    // la main, rendu ici par l'analyseur, et pour toutes les clés d'un coup.
    //
    // FAIL_ON_TRAILING_TOKENS : ce qui suit l'accolade fermante est du texte que personne ne
    // relit. Une accolade fermée trop tôt laisse le reste du fichier sans effet, et le diagramme
    // n'est alors pas celui qu'on a demandé.
    private static final ObjectMapper ANALYSEUR = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    private LecteurDeRegles() {}

    // Où l'on se trouve dans le fichier : son nom, et le chemin JSON depuis la racine.
    //
    // Le chemin s'allonge à mesure qu'on descend — « views », puis « views[1] », puis
    // « views[1].direction » —, si bien que le refus le plus profond nomme quand même l'endroit
    // exact. Vide à la racine, où le nom du fichier suffit.
    private record Lieu(String origine, String chemin) {

        Lieu dans(String cle) {
            return new Lieu(origine, chemin.isEmpty() ? cle : chemin + "." + cle);
        }

        Lieu indice(int rang) {
            return new Lieu(origine, chemin + "[" + rang + "]");
        }

        // Ce qui paraît en tête d'un message de refus.
        @Override
        public String toString() {
            return chemin.isEmpty() ? origine : origine + ", " + chemin;
        }
    }

    static Regles lire(String contenu, String origine) {
        Lieu racine = new Lieu(origine, "");
        JsonNode objet = arbre(contenu, racine);

        // Les valeurs par défaut sont celles d'un projet sans fichier : une clé absente ne dit
        // rien, et ne rien dire c'est laisser le code se décrire tel qu'il est écrit.
        ImmutableMap<String, String> alias = ImmutableMap.of();
        ImmutableList<Motif> membresMasques = ImmutableList.of();
        ImmutableList<DefinitionDeVue> vues = ImmutableList.of();
        Set<Omission> omissions = EnumSet.noneOf(Omission.class);
        boolean vuesParPaquetage = false;
        String titre = "";
        UMLFlow direction = UMLFlow.TOP_TO_BOTTOM;
        boolean montrerMembresVides = false;
        boolean ordreDeclaration = false;
        boolean multiplicitesDeduites = false;
        // Null veut dire « aucune clé strategy », ce qui n'est pas la même chose que
        // « "strategy": "none" » : le premier cas est un silence, le second un choix, et la
        // génération ne les signale pas de la même façon (voir Regles.strategieDeclaree).
        UMLVisibility strategie = null;

        for (Map.Entry<String, JsonNode> champ : objet.properties()) {
            String cle = champ.getKey();
            JsonNode valeur = champ.getValue();
            Lieu lieu = racine.dans(cle);

            // Le commentaire du pauvre, et le seul que JSON autorise : une clé qui commence par
            // « _ » n'est pas une règle. Voir l'en-tête de cette classe.
            if (cle.startsWith("_")) {
                continue;
            }

            switch (cle) {
                case TYPES -> alias = lireTypes(valeur, lieu);
                case HIDE -> membresMasques = lireHide(valeur, lieu);
                case OMIT -> omissions = lireOmit(valeur, lieu);
                case STRATEGY -> strategie = lireStrategy(valeur, lieu);
                case VIEWS -> vues = lireViews(valeur, lieu);
                case VIEWS_BY_PACKAGE -> vuesParPaquetage = booleen(valeur, lieu);
                case TITLE -> titre = lireTitle(valeur, lieu);
                case SHOW_EMPTY_MEMBERS -> montrerMembresVides = booleen(valeur, lieu);
                case ORDER -> ordreDeclaration = lireOrder(valeur, lieu);
                case MULTIPLICITY -> multiplicitesDeduites = lireMultiplicity(valeur, lieu);
                case DIRECTION -> direction = lireFlux(valeur, lieu);
                default -> throw new IllegalArgumentException(racine + ": unknown rule \"" + cle
                    + "\". Known rules: " + STRATEGY + ", " + TYPES + ", " + HIDE + ", " + OMIT
                    + ", " + TITLE + ", " + DIRECTION + ", " + ORDER + ", " + MULTIPLICITY + ", "
                    + SHOW_EMPTY_MEMBERS + ", " + VIEWS + ", " + VIEWS_BY_PACKAGE + ". A key whose"
                    + " name starts with \"_\" is ignored, and that is where a note goes.");
            }
        }
        return new Regles(alias, omissions.contains(Omission.RETOUR_VOID),
            strategie == null ? UMLVisibility.NONE : strategie, strategie != null,
            membresMasques, vues, vuesParPaquetage, titre, direction,
            !montrerMembresVides, omissions.contains(Omission.VISIBILITE),
            omissions.contains(Omission.NOMS_DE_PARAMETRES), ordreDeclaration,
            multiplicitesDeduites, omissions.contains(Omission.ABSTRAIT));
    }

    // Le contenu du fichier, analysé, et dont on sait qu'il porte un objet.
    private static JsonNode arbre(String contenu, Lieu racine) {
        // Un fichier vide donne les mêmes règles qu'un fichier absent : aucune. La propriété
        // valait avant JSON, et rien ne gagnerait à ce qu'elle cesse — un fichier créé mais pas
        // encore écrit n'est pas une faute.
        if (contenu.isBlank()) {
            return ANALYSEUR.createObjectNode();
        }

        JsonNode lu;
        try {
            lu = ANALYSEUR.readTree(contenu);
        } catch (JsonProcessingException e) {
            // La faute de syntaxe est la seule que l'analyseur situe mieux que nous : il connaît
            // la ligne et la colonne, que le chemin JSON ne donnerait pas — il n'y a pas encore
            // d'arbre où se situer.
            JsonLocation ou = e.getLocation();
            String ligne = ou == null ? "" : ", line " + ou.getLineNr();
            throw new IllegalArgumentException(racine + ligne + ": this is not valid JSON — "
                + e.getOriginalMessage() + ". Note that JSON has no comments: a key whose name "
                + "starts with \"_\" is ignored, and that is where a note goes.", e);
        }
        checkArgument(lu.isObject(),
            "%s: expected a JSON object, for example { \"strategy\": \"public\" }; got %s.",
            racine, sorte(lu));

        return lu;
    }

    // « "order": "declaration" » ou « "order": "alphabetical" » : l'ordre des membres dans les
    // boîtes. Rend vrai pour l'ordre du code.
    //
    // L'alphabet reste le défaut, mais il s'écrit aussi : un fichier qui le dit explicitement se
    // relit mieux qu'un fichier qui compte sur la mémoire du lecteur.
    private static boolean lireOrder(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);
        return switch (ecrit) {
            case "declaration" -> true;
            case "alphabetical" -> false;
            default -> throw new IllegalArgumentException(lieu + ": expected \"declaration\" or "
                + "\"alphabetical\", got \"" + ecrit + "\".");
        };
    }

    // « "multiplicity": "brackets" » : la forme des conteneurs se traduit en multiplicités.
    //
    // « Set<String> aliases » s'écrit alors « aliases : String [0..*] », « Optional<Integer> »
    // s'écrit « int [0..1] » : le conteneur est la façon dont Java écrit ces multiplicités, et la
    // règle ne fait que les transcrire en notation UML. Le mot « brackets » nomme ce qu'on
    // obtient — les crochets — et laisse la place à d'autres formes si l'usage en révèle. C'est
    // pourquoi la clé porte un mot et non un booléen.
    private static boolean lireMultiplicity(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);
        checkArgument("brackets".equals(ecrit),
            "%s: expected \"brackets\", got \"%s\".", lieu, ecrit);

        return true;
    }

    // « "title": "Architecture du module Facturation" » : le titre de la vue intégrale.
    //
    // La chaîne est prise telle quelle, espaces compris : un titre est une phrase, pas un
    // identifiant, et rien n'y est à interpréter.
    private static String lireTitle(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);
        checkArgument(!ecrit.isBlank(),
            "%s: the title cannot be empty or made of whitespace only. To have no title, leave "
                + "the key out.", lieu);

        return ecrit;
    }

    // « "views": [ { "name": "Mapping", "retain": […], "remove": […],
    //               "direction": "left-to-right" } ]
    //
    // Une vue tient dans un objet, et non plus dans deux lignes qu'un nom entre guillemets
    // rattachait l'une à l'autre : son contenu et son sens de lecture sont deux champs du même
    // objet. C'est le gain le plus net du format.
    private static ImmutableList<DefinitionDeVue> lireViews(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isArray(),
            "%s: expected an array of views, for example [ { \"name\": \"Mapping\", "
                + "\"retain\": [\"KeysymEntry\"] } ]; got %s.",
            lieu, sorte(valeur));

        // LinkedHashMap et non un builder : il faut pouvoir demander « ce nom est-il déjà là ? »
        // avant d'insérer, pour refuser deux vues homonymes en nommant celle qui est en trop.
        // L'ordre d'écriture est par ailleurs celui des fichiers produits.
        Map<String, DefinitionDeVue> vues = new LinkedHashMap<>();

        for (int rang = 0; rang < valeur.size(); rang++) {
            Lieu ou = lieu.indice(rang);
            DefinitionDeVue vue = lireVue(valeur.get(rang), ou);
            checkArgument(!vues.containsKey(vue.nom()),
                "%s: view \"%s\" is already defined. Keep only one of the two.", ou, vue.nom());

            vues.put(vue.nom(), vue);
        }
        return ImmutableList.copyOf(vues.values());
    }

    // Une vue : son nom, ce qu'elle retient, ce qu'elle retire, son sens de lecture.
    private static DefinitionDeVue lireVue(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isObject(),
            "%s: expected a view object, with a \"%s\" and a \"%s\"; got %s.",
            lieu, NAME, RETAIN, sorte(valeur));

        String nom = null;
        ImmutableList<Selecteur> retenus = null;
        ImmutableList<Selecteur> retires = ImmutableList.of();
        UMLFlow direction = UMLFlow.TOP_TO_BOTTOM;

        for (Map.Entry<String, JsonNode> champ : valeur.properties()) {
            String cle = champ.getKey();
            Lieu ou = lieu.dans(cle);

            if (cle.startsWith("_")) {
                continue;
            }

            switch (cle) {
                case NAME -> nom = lireNomDeVue(champ.getValue(), ou);
                case RETAIN -> retenus = selecteurs(champ.getValue(), ou);
                case REMOVE -> retires = selecteurs(champ.getValue(), ou);
                case DIRECTION -> direction = lireFlux(champ.getValue(), ou);
                default -> throw new IllegalArgumentException(lieu + ": unknown key \"" + cle
                    + "\" in a view. Known keys: " + NAME + ", " + RETAIN + ", " + REMOVE + ", "
                    + DIRECTION + ".");
            }
        }
        checkArgument(nom != null, "%s: this view has no \"%s\".", lieu, NAME);
        checkArgument(retenus != null,
            "%s: view \"%s\" has no \"%s\"; write at least one class or package.",
            lieu, nom, RETAIN);
        checkArgument(!retenus.isEmpty(),
            "%s: view \"%s\" retains nothing; write at least one class or package.", lieu, nom);

        return new DefinitionDeVue(nom, retenus, retires, direction);
    }

    // Le nom d'une vue, qui sert aussi de titre et de nom de fichier.
    private static String lireNomDeVue(JsonNode valeur, Lieu lieu) {
        String nom = texte(valeur, lieu);
        checkArgument(!nom.isBlank(),
            "%s: the view name cannot be empty or made of whitespace only.", lieu);

        return nom;
    }

    // « "retain": ["Xkeys", { "package": "io.exemple.modele" }, { "around": "SvgKeyboard" }] »
    //
    // Une chaîne désigne une classe : c'est la forme la plus fréquente, et lui donner la forme la
    // plus courte est ce qui rend le fichier relisible. Les deux autres sélecteurs sont des
    // objets d'une seule clé, qui nomme la sorte.
    //
    // Un seul tableau, et non trois — un par sorte —, parce que l'ordre d'écriture est celui du
    // dessin (voir DefinitionDeVue) : le séparer par sorte perdrait l'ordre entre les sortes.
    private static ImmutableList<Selecteur> selecteurs(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isArray(),
            "%s: expected an array of selectors, for example [\"Facture\", { \"%s\": "
                + "\"io.exemple.modele\" }]; got %s.",
            lieu, PACKAGE, sorte(valeur));

        ImmutableList.Builder<Selecteur> lus = ImmutableList.builder();
        for (int rang = 0; rang < valeur.size(); rang++) {
            lus.add(selecteur(valeur.get(rang), lieu.indice(rang)));
        }
        return lus.build();
    }

    // Un sélecteur : une chaîne pour une classe, un objet d'une clé pour les deux autres sortes.
    private static Selecteur selecteur(JsonNode valeur, Lieu lieu) {
        if (valeur.isTextual()) {
            return Selecteur.classe(nomDesigne(valeur, lieu, "class"));
        }
        checkArgument(valeur.isObject(),
            "%s: expected a class name, or { \"%s\": … }, or { \"%s\": … }; got %s.",
            lieu, PACKAGE, AROUND, sorte(valeur));
        // Deux clés dans le même objet demanderaient deux choses d'un seul sélecteur, et il
        // faudrait inventer laquelle l'emporte. Les écrire côte à côte dans le tableau dit la
        // même chose, sans inventer.
        checkArgument(valeur.size() == 1,
            "%s: a selector object carries exactly one key, \"%s\" or \"%s\"; this one carries %s."
                + " Write one selector per entry of the array.",
            lieu, PACKAGE, AROUND, valeur.size());

        Map.Entry<String, JsonNode> seule = valeur.properties().iterator().next();
        Lieu ou = lieu.dans(seule.getKey());
        return switch (seule.getKey()) {
            case PACKAGE -> Selecteur.paquetage(nomDesigne(seule.getValue(), ou, PACKAGE));
            case AROUND -> Selecteur.voisinage(nomDesigne(seule.getValue(), ou, AROUND));
            default -> throw new IllegalArgumentException(lieu + ": unknown selector \""
                + seule.getKey() + "\". A string names a class; \"" + PACKAGE + "\" takes a whole "
                + "package, and \"" + AROUND + "\" a class and its immediate neighbours.");
        };
    }

    // Le nom que porte un sélecteur, quelle que soit sa sorte.
    private static String nomDesigne(JsonNode valeur, Lieu lieu, String sorte) {
        String nom = texte(valeur, lieu);
        checkArgument(!nom.isBlank(),
            "%s: a %s selector cannot be empty or made of whitespace only.", lieu, sorte);
        // Une espace au milieu d'un nom trahit deux noms écrits dans la même chaîne, là où le
        // tableau JSON attend une entrée par sélecteur.
        checkArgument(!nom.contains(" "),
            "%s: \"%s\" contains a space, so it is not a name. Write one selector per entry of "
                + "the array.", lieu, nom);

        return nom;
    }

    // Le sens de lecture, écrit comme on le lit : « left-to-right ».
    private static UMLFlow lireFlux(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);
        return switch (ecrit) {
            case "top-to-bottom" -> UMLFlow.TOP_TO_BOTTOM;
            case "left-to-right" -> UMLFlow.LEFT_TO_RIGHT;
            default -> throw new IllegalArgumentException(lieu + ": expected \"top-to-bottom\" or "
                + "\"left-to-right\", got \"" + ecrit + "\".");
        };
    }

    // « "strategy": "public" » : ce que prennent les classes qui ne déclarent pas la leur.
    private static UMLVisibility lireStrategy(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);

        UMLVisibility lue;
        try {
            lue = UMLVisibility.valueOf(ecrit.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(lieu + ": unknown strategy \"" + ecrit
                + "\". Known strategies: none, public, protected, package, private.", e);
        }
        // PROJECT est le renvoi qu'écrit @UMLClass vers cette clé même : l'accepter ici
        // demanderait au projet de se renvoyer à lui-même.
        checkArgument(lue != UMLVisibility.PROJECT,
            "%s: \"project\" is what a class writes to defer to this key; the project itself "
                + "must name a real strategy.", lieu);

        return lue;
    }

    // « "hide": ["get*", "log", "*Cache"] » : ce qui disparaît de la boîte.
    //
    // Une seule liste de motifs : un motif vise les méthodes et les attributs indifféremment. Il
    // n'y a pas de « hideMethods » ni de « hideFields » — un nom appartient presque toujours à
    // l'un ou à l'autre, et le rare membre pris à tort par un motif se ramène par @UMLInclude,
    // qui est fait pour cela. Deux clés pour distinguer ce que le lecteur ne distingue pas
    // auraient coûté plus que le cas qu'elles réglaient.
    private static ImmutableList<Motif> lireHide(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isArray(),
            "%s: expected an array of name patterns, for example [\"get*\", \"log\"]; got %s.",
            lieu, sorte(valeur));

        ImmutableList.Builder<Motif> motifs = ImmutableList.builder();
        for (int rang = 0; rang < valeur.size(); rang++) {
            Lieu ou = lieu.indice(rang);
            String motif = texte(valeur.get(rang), ou);

            checkArgument(!motif.isBlank(),
                "%s: a name pattern cannot be empty or made of whitespace only.", ou);
            checkArgument(!motif.contains(" "),
                "%s: a name pattern cannot contain a space (read \"%s\"). Write one pattern per "
                    + "entry of the array, or did you mean \"" + OMIT + "\"?",
                ou, motif);
            // Le point est ce qui distingue un nom qualifié d'un nom de membre, qui n'en contient
            // jamais. Une règle ne désigne pas un élément par son nom : c'est le partage même
            // entre le fichier et les annotations, et @UMLExclude est fait pour ce cas-là.
            checkArgument(!motif.contains("."),
                "%s: \"%s\" names a member of one class, and a rule never designates an element "
                    + "by name. Put @UMLExclude on that member instead, or write a pattern that "
                    + "describes a family of names.",
                ou, motif);
            // « * » seul masquerait tout le compartiment : c'est licite, mais assez inattendu
            // pour mériter d'être écrit autrement le jour où la règle existera.
            checkArgument(!motif.equals("*"),
                "%s: the pattern \"*\" would hide every member of every class.", ou);

            motifs.add(Motif.de(motif));
        }
        return motifs.build();
    }

    // « "omit": ["visibility", "parameter names", "return void", "abstract"] »
    //
    // Un mot distinct de « hide », et c'est délibéré : ces quatre-là ne font disparaître aucun
    // membre, ils retirent un morceau de la ligne d'un membre qui reste affiché. Le mot dit à lui
    // seul qu'aucun @UMLInclude ne les contredira, puisqu'il n'y a rien à ramener.
    //
    // « return void » ne vaut que pour le retour, et que pour void : UML tient ce type pour
    // facultatif, et l'omettre veut dire « ne rend rien ». Un attribut ou un paramètre privé de
    // son type laisserait au contraire une ligne incompréhensible, et effacer un type de retour
    // utile ferait perdre au dessin ce que la boîte existe pour montrer.
    //
    // « abstract » ne vaut que pour l'italique, et non pour le mot-clé d'une classe : ce qui
    // disparaît est la marque que PlantUML pose sur une opération sans implémentation, pas le
    // « abstract class » écrit en tête de la boîte. Un diagramme dont les boîtes abstraites sont
    // presque toutes des interfaces le dit déjà par le mot « interface », et les diagrammes tenus
    // à la main s'en passent le plus souvent.
    private static Set<Omission> lireOmit(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isArray(),
            "%s: expected an array, for example [\"%s\", \"%s\"]; got %s.",
            lieu, Omission.VISIBILITE.ecrit, Omission.RETOUR_VOID.ecrit, sorte(valeur));

        Set<Omission> demandees = EnumSet.noneOf(Omission.class);
        for (int rang = 0; rang < valeur.size(); rang++) {
            Lieu ou = lieu.indice(rang);
            String ecrit = texte(valeur.get(rang), ou);

            Omission demandee = null;
            for (Omission candidate : Omission.values()) {
                if (candidate.ecrit.equals(ecrit)) {
                    demandee = candidate;
                }
            }
            checkArgument(demandee != null, "%s: expected \"%s\", \"%s\", \"%s\" or \"%s\"; got "
                + "\"%s\".", ou, Omission.VISIBILITE.ecrit, Omission.NOMS_DE_PARAMETRES.ecrit,
                Omission.RETOUR_VOID.ecrit, Omission.ABSTRAIT.ecrit, ecrit);
            // La seconde mention ne demanderait rien de plus ; un fichier qui se répète est un
            // fichier que personne ne relit.
            checkArgument(!demandees.contains(demandee),
                "%s: \"%s\" is already asked for.", ou, ecrit);

            demandees.add(demandee);
        }
        return demandees;
    }

    // « "types": { "ImmutableList": "List" } » : le nom lu dans le code, puis celui qui s'affiche.
    //
    // Un objet, et non un tableau de paires : le nom du code est une clé — on ne le remplace que
    // par une chose —, et JSON refuse alors tout seul les deux règles contradictoires que le
    // format ligne à ligne devait guetter.
    private static ImmutableMap<String, String> lireTypes(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isObject(),
            "%s: expected an object, for example { \"ImmutableList\": \"List\" }; got %s.",
            lieu, sorte(valeur));

        ImmutableMap.Builder<String, String> alias = ImmutableMap.builder();
        for (Map.Entry<String, JsonNode> champ : valeur.properties()) {
            String source = champ.getKey();
            Lieu ou = lieu.dans(source);
            String affiche = texte(champ.getValue(), ou);

            checkArgument(!source.isBlank(),
                "%s: the type to replace cannot be empty or made of whitespace only.", ou);
            checkArgument(!affiche.isBlank(),
                "%s: the displayed type cannot be empty. To hide a type, there is nothing to "
                    + "write: leave the entry out.", ou);
            // Une espace au milieu d'un nom de type trahit un mot en trop ; le laisser passer
            // écrirait dans la boîte un texte que personne ne relirait.
            checkArgument(!source.contains(" ") && !affiche.contains(" "),
                "%s: a type name cannot contain a space (read \"%s\" -> \"%s\").",
                ou, source, affiche);

            alias.put(source, affiche);
        }
        return alias.build();
    }

    // La valeur d'une clé qui attend vrai ou faux.
    //
    // Écrire le défaut est permis, là où le format ligne à ligne ne savait que le demander : une
    // clé qui redit le défaut ne change rien au dessin, et un fichier qui dit tout haut ce qu'il
    // fait se relit mieux.
    private static boolean booleen(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isBoolean(),
            "%s: expected true or false, got %s.", lieu, sorte(valeur));

        return valeur.booleanValue();
    }

    // La valeur d'une clé qui attend une chaîne.
    private static String texte(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isTextual(), "%s: expected a string, got %s.", lieu, sorte(valeur));

        return valeur.textValue();
    }

    // Ce qu'on a lu là où l'on attendait autre chose, nommé comme le nomme JSON : un refus qui
    // dit « got an array » se recoupe sans effort avec le fichier ouvert sous les yeux.
    private static String sorte(JsonNode valeur) {
        return switch (valeur.getNodeType()) {
            case ARRAY -> "an array";
            case BOOLEAN -> "a boolean";
            case NULL -> "null";
            case NUMBER -> "a number";
            case OBJECT -> "an object";
            case STRING -> "a string";
            // Ni MISSING, ni BINARY, ni POJO ne peuvent venir d'un fichier analysé : ils
            // désignent des nœuds qu'on fabrique en mémoire.
            default -> "\"" + valeur + "\"";
        };
    }
}
