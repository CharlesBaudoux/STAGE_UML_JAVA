package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Lit le fichier de règles, ligne à ligne.
//
// La grammaire tient en trois cas : une ligne vide, un commentaire ouvert par « # », ou une
// règle. Une règle commence par un mot-clé qui dit sa nature — onze aujourd'hui, de « type » à
// « views » — et c'est ce mot-clé qui décide comment lire le reste de la ligne. Ajouter une
// nature de règle se fait donc ici, et à un seul endroit : une branche de plus dans le switch, et
// la méthode qui va avec.
//
// Le vocabulaire s'est raccourci plus souvent qu'il ne s'est allongé. Trois mots-clés en sont
// sortis — « group », « output » et « declare » —, et « hide method »/« hide field » ont fondu en
// un seul motif : une règle ne désigne jamais un élément par son nom (c'est une annotation), et
// ce qui ne dit rien du modèle se règle à l'appel du générateur.
//
// Le fichier étant écrit à la main, chaque refus nomme le fichier et le numéro de ligne, puis
// rappelle la forme attendue. C'est le seul retour que l'auteur du fichier aura : il n'y a pas de
// compilateur pour relire ce qu'il écrit.
final class LecteurDeRegles {

    // Les mots-clés connus, et ce qui sépare les deux moitiés d'une règle « type ».
    private static final String TYPE = "type";
    private static final String HIDE = "hide";
    private static final String OMIT = "omit";
    private static final String STRATEGY = "strategy";
    private static final String VIEW = "view";
    private static final String VIEWS = "views";
    private static final String TITLE = "title";
    private static final String SHOW = "show";
    private static final String MEMBRES_VIDES = "empty members";
    private static final String VISIBILITE = "visibility";
    private static final String ORDER = "order";
    private static final String MULTIPLICITY = "multiplicity";
    private static final String NOMS_DE_PARAMETRES = "parameter names";
    private static final String RETOUR_VOID = "return void";
    private static final String DIRECTION = "direction";
    private static final String AROUND = "around";
    private static final String PACKAGE = "package";
    private static final String FLECHE = "->";

    private LecteurDeRegles() {}

    static Regles lire(ImmutableList<String> lignes, String origine) {
        // LinkedHashMap et non ImmutableMap.Builder : il faut pouvoir demander « cette clé
        // est-elle déjà là ? » avant d'insérer, pour refuser deux règles contradictoires en
        // nommant la ligne fautive. Le builder de Guava, lui, ne se plaindrait qu'au build, sans
        // savoir où.
        Map<String, String> alias = new LinkedHashMap<>();
        List<Motif> membres = new ArrayList<>();
        // LinkedHashMap : l'ordre d'écriture des vues est celui des fichiers produits.
        Map<String, DefinitionDeVue> vues = new LinkedHashMap<>();
        boolean[] parPaquetage = {false};
        String[] titre = {""};
        UMLFlow[] direction = {UMLFlow.TOP_TO_BOTTOM};
        boolean[] masquerMembresVides = {true};
        boolean[] omettreVisibilite = {false};
        boolean[] omettreNomsDeParametres = {false};
        boolean[] omettreRetourVoid = {false};
        // Null tant qu'aucune ligne « order » n'est lue : la distinction permet de refuser une
        // seconde ligne, même quand la première redisait le défaut.
        Boolean[] ordreDeclaration = {null};
        boolean[] multiplicitesDeduites = {false};
        // Un tableau d'une case : une variable locale ne peut pas être réaffectée depuis une
        // méthode appelée, et la stratégie est lue par lireStrategy. Un objet mutable d'une seule
        // case est le moyen le plus court de lui laisser rendre sa valeur sans inventer un type.
        //
        // Null veut dire « aucune ligne strategy lue », ce qui n'est pas la même chose que
        // « strategy none » : sans cette distinction, une seconde ligne écrite après un
        // « strategy none » explicite passerait pour la première.
        UMLVisibility[] strategie = {null};

        for (int i = 0; i < lignes.size(); i++) {
            // Le numéro affiché est celui que montre un éditeur de texte : les lignes s'y
            // comptent à partir de 1.
            String contexte = origine + ", line " + (i + 1);
            String ligne = lignes.get(i).strip();

            if (ligne.isEmpty() || ligne.startsWith("#")) {
                continue;
            }

            // Le mot-clé est ce qui précède la première espace ; le reste appartient à la règle.
            int fin = ligne.indexOf(' ');
            String motCle = fin < 0 ? ligne : ligne.substring(0, fin);
            String reste = fin < 0 ? "" : ligne.substring(fin + 1).strip();

            switch (motCle) {
                case TYPE -> lireType(alias, reste, contexte);
                case HIDE -> lireHide(membres, reste, contexte);
                case OMIT -> lireOmit(omettreVisibilite, omettreNomsDeParametres,
                    omettreRetourVoid, reste, contexte);
                case STRATEGY -> lireStrategy(strategie, reste, contexte);
                case VIEW -> lireView(vues, reste, contexte);
                case VIEWS -> lireViews(parPaquetage, reste, contexte);
                case TITLE -> lireTitle(titre, reste, contexte);
                case DIRECTION -> direction[0] = lireFlux(reste, contexte);
                case SHOW -> lireShow(masquerMembresVides, reste, contexte);
                case ORDER -> lireOrder(ordreDeclaration, reste, contexte);
                case MULTIPLICITY -> lireMultiplicity(multiplicitesDeduites, reste, contexte);
                default -> throw new IllegalArgumentException(contexte + ": unknown rule \""
                    + motCle + "\". Known rules: " + TYPE + ", " + HIDE + ", " + OMIT + ", "
                    + STRATEGY + ", " + VIEW + ", " + VIEWS + ", " + TITLE + ", "
                    + DIRECTION + ", " + SHOW + ", " + ORDER + ", " + MULTIPLICITY + ".");
            }
        }
        return new Regles(ImmutableMap.copyOf(alias), omettreRetourVoid[0],
            strategie[0] == null ? UMLVisibility.NONE : strategie[0], strategie[0] != null,
            ImmutableList.copyOf(membres),
            ImmutableList.copyOf(vues.values()), parPaquetage[0], titre[0],
            direction[0], masquerMembresVides[0], omettreVisibilite[0],
            omettreNomsDeParametres[0],
            ordreDeclaration[0] != null && ordreDeclaration[0],
            multiplicitesDeduites[0]);
    }

    // « order declaration » ou « order alphabetical » : l'ordre des membres dans les boîtes.
    //
    // L'alphabet reste le défaut, mais il s'écrit aussi : un fichier qui le dit explicitement se
    // relit mieux qu'un fichier qui compte sur la mémoire du lecteur.
    private static void lireOrder(Boolean[] ordreDeclaration, String reste, String contexte) {
        checkArgument(ordreDeclaration[0] == null,
            "%s: the member order is already set. Keep only one \"order\" line.", contexte);

        ordreDeclaration[0] = switch (reste) {
            case "declaration" -> true;
            case "alphabetical" -> false;
            default -> throw new IllegalArgumentException(contexte + ": expected \"order "
                + "declaration\" or \"order alphabetical\", got \"order " + reste + "\".");
        };
    }

    // « multiplicity brackets » : la forme des conteneurs se traduit en multiplicités.
    //
    // « Set<String> aliases » s'écrit alors « aliases : String [0..*] », « Optional<Integer> »
    // s'écrit « int [0..1] » : le conteneur est la façon dont Java écrit ces multiplicités, et la
    // règle ne fait que les transcrire en notation UML. Le mot « brackets » nomme ce qu'on
    // obtient — les crochets — et laisse la place à d'autres formes si l'usage en révèle.
    private static void lireMultiplicity(boolean[] deduites, String reste, String contexte) {
        checkArgument("brackets".equals(reste),
            "%s: expected \"multiplicity brackets\", got \"multiplicity %s\".", contexte, reste);
        checkArgument(!deduites[0],
            "%s: \"multiplicity brackets\" is already asked for.", contexte);

        deduites[0] = true;
    }

    // « title Architecture du module Facturation » : le titre de la vue intégrale.
    //
    // Le reste de la ligne est pris tel quel, espaces compris : un titre est une phrase, pas un
    // identifiant, et rien n'y est à interpréter.
    private static void lireTitle(String[] titre, String reste, String contexte) {
        checkArgument(!reste.isBlank(),
            "%s: the title is missing, after \"title\".", contexte);
        checkArgument(titre[0].isEmpty(),
            "%s: the title is already set to \"%s\". Keep only one \"title\" line.",
            contexte, titre[0]);

        titre[0] = reste;
    }

    // « show empty members » : rétablit les compartiments vides, masqués par défaut.
    private static void lireShow(boolean[] masquerMembresVides, String reste, String contexte) {
        checkArgument(MEMBRES_VIDES.equals(reste),
            "%s: expected \"show empty members\", got \"show %s\".", contexte, reste);
        checkArgument(masquerMembresVides[0],
            "%s: \"show empty members\" is already asked for.", contexte);

        masquerMembresVides[0] = false;
    }

    // « view "Mapping" = KeysymEntry, KeyboardMap - XkbKeymapDecomposer »
    // « view "Mapping" direction left-to-right »
    //
    // Deux formes pour un même mot-clé, parce qu'une vue a deux choses à dire et qu'une ligne
    // n'en porte qu'une : ce qu'elle contient, et comment elle se lit. Le nom entre guillemets
    // les rattache l'une à l'autre, et sert aussi de titre et de nom de fichier.
    private static void lireView(Map<String, DefinitionDeVue> vues, String reste,
        String contexte) {
        checkArgument(reste.startsWith("\""),
            "%s: expected \"view <name in quotes> = <classes>\", got \"view %s\".",
            contexte, reste);
        int fin = reste.indexOf('"', 1);
        checkArgument(fin > 0, "%s: the view name is not closed by a quote.", contexte);

        String nom = reste.substring(1, fin);
        String suite = reste.substring(fin + 1).strip();
        checkArgument(!nom.isBlank(),
            "%s: the view name cannot be empty or made of whitespace only.", contexte);

        if (suite.startsWith(DIRECTION + " ")) {
            DefinitionDeVue definie = vues.get(nom);
            checkArgument(definie != null,
                "%s: view \"%s\" has no content yet. Write \"view \"%s\" = …\" first.",
                contexte, nom, nom);
            vues.put(nom,
                definie.avec(lireFlux(suite.substring(DIRECTION.length()).strip(), contexte)));
            return;
        }

        checkArgument(suite.startsWith("="),
            "%s: expected \"=\" or \"direction\" after the view name, got \"%s\".",
            contexte, suite);
        checkArgument(!vues.containsKey(nom),
            "%s: view \"%s\" is already defined. Keep only one of the two.", contexte, nom);

        vues.put(nom, contenu(nom, suite.substring(1).strip(), contexte));
    }

    // Le contenu d'une vue : ce qu'elle retient, moins ce qu'elle retire.
    //
    // Le tiret sépare les deux moitiés. Il ne peut pas être confondu avec un nom de classe : un
    // identifiant Java n'en contient jamais, et le fichier exige des espaces autour.
    private static DefinitionDeVue contenu(String nom, String reste, String contexte) {
        checkArgument(!reste.isEmpty(),
            "%s: view \"%s\" retains nothing; write at least one class or package.",
            contexte, nom);

        String garde = reste;
        String retire = "";
        int tiret = reste.indexOf(" - ");
        if (tiret >= 0) {
            garde = reste.substring(0, tiret).strip();
            retire = reste.substring(tiret + 3).strip();
        }
        return new DefinitionDeVue(nom, selecteurs(garde, contexte, nom),
            selecteurs(retire, contexte, nom), UMLFlow.TOP_TO_BOTTOM);
    }

    // Les sélecteurs d'une moitié, séparés par des virgules, dans l'ordre d'écriture.
    private static ImmutableList<Selecteur> selecteurs(String reste, String contexte, String nom) {
        if (reste.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<Selecteur> lus = ImmutableList.builder();

        for (String morceau : reste.split(",", -1)) {
            String ecrit = morceau.strip();
            checkArgument(!ecrit.isEmpty(),
                "%s: an empty selector was read in view \"%s\"; a stray comma, most likely.",
                contexte, nom);

            if (ecrit.startsWith(PACKAGE + " ")) {
                lus.add(Selecteur.paquetage(ecrit.substring(PACKAGE.length()).strip()));
            } else if (ecrit.startsWith(AROUND + " ")) {
                lus.add(Selecteur.voisinage(ecrit.substring(AROUND.length()).strip()));
            } else {
                checkArgument(!ecrit.contains(" "),
                    "%s: \"%s\" is neither a class name, a \"package …\" nor an \"around …\".",
                    contexte, ecrit);
                lus.add(Selecteur.classe(ecrit));
            }
        }
        return lus.build();
    }

    // « views by package » : une vue par paquetage, sans rien déclarer.
    private static void lireViews(boolean[] parPaquetage, String reste, String contexte) {
        checkArgument("by package".equals(reste),
            "%s: expected \"views by package\", got \"views %s\".", contexte, reste);
        checkArgument(!parPaquetage[0],
            "%s: \"views by package\" is already asked for.", contexte);

        parPaquetage[0] = true;
    }

    // Le sens de lecture, écrit comme on le lit : « left-to-right ».
    private static UMLFlow lireFlux(String reste, String contexte) {
        return switch (reste) {
            case "top-to-bottom" -> UMLFlow.TOP_TO_BOTTOM;
            case "left-to-right" -> UMLFlow.LEFT_TO_RIGHT;
            default -> throw new IllegalArgumentException(contexte + ": expected \"top-to-bottom\""
                + " or \"left-to-right\", got \"" + reste + "\".");
        };
    }

    // « strategy public » : ce que prennent les classes qui ne déclarent pas la leur.
    private static void lireStrategy(UMLVisibility[] strategie, String reste, String contexte) {
        checkArgument(!reste.isEmpty(),
            "%s: expected \"strategy <none|public|protected|package|private>\", got \"strategy\".",
            contexte);
        checkArgument(strategie[0] == null,
            "%s: the project strategy is already set to \"%s\". Keep only one \"strategy\" line.",
            contexte, strategie[0] == null ? "" : strategie[0].name().toLowerCase(Locale.ROOT));

        UMLVisibility lue;
        try {
            lue = UMLVisibility.valueOf(reste.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(contexte + ": unknown strategy \"" + reste
                + "\". Known strategies: none, public, protected, package, private.", e);
        }
        // PROJECT est le renvoi qu'écrit @UMLClass vers cette ligne même : l'accepter ici
        // demanderait au projet de se renvoyer à lui-même.
        checkArgument(lue != UMLVisibility.PROJECT,
            "%s: \"project\" is what a class writes to defer to this line; the project itself "
                + "must name a real strategy.", contexte);

        strategie[0] = lue;
    }

    // « hide get*, log, *Cache » : ce qui disparaît de la boîte.
    //
    // Une seule forme, et une seule liste de motifs : un motif vise les méthodes et les attributs
    // indifféremment. Il n'y a pas de « hide method » ni de « hide field » — un nom appartient
    // presque toujours à l'un ou à l'autre, et le rare membre pris à tort par un motif se ramène
    // par @UMLInclude, qui est fait pour cela. Deux mots-clés pour distinguer ce que le lecteur
    // ne distingue pas auraient coûté plus que le cas qu'ils réglaient.
    private static void lireHide(List<Motif> membres, String reste, String contexte) {
        checkArgument(!reste.isEmpty(),
            "%s: expected \"hide <patterns>\", for example \"hide get*, log\"; got \"hide\".",
            contexte);

        lireMotifs(membres, reste, contexte);
    }

    // « hide get*, is*, LOGGER » : un ou plusieurs motifs, séparés par des virgules.
    //
    // La virgule évite de répéter « hide » sur chaque ligne quand on écarte une famille de noms
    // d'un seul tenant. Chaque morceau est un motif à part entière, avec sa propre étoile.
    private static void lireMotifs(List<Motif> motifs, String reste, String contexte) {
        // La limite négative conserve les morceaux vides, y compris celui que laisse une virgule
        // finale : « hide get*, » est une faute de frappe, et le fichier n'a pas de compilateur
        // pour la relever à notre place.
        for (String morceau : reste.split(",", -1)) {
            String motif = morceau.strip();
            checkArgument(!motif.isEmpty(),
                "%s: an empty pattern was read in \"hide %s\"; a stray comma, most likely.",
                contexte, reste);
            checkArgument(!motif.contains(" "),
                "%s: a name pattern cannot contain a space (read \"%s\"). Separate several "
                    + "patterns with commas, or did you mean \"omit\"?",
                contexte, motif);
            // Le point est ce qui distingue un nom qualifié d'un nom de membre, qui n'en contient
            // jamais. Une règle ne désigne pas un élément par son nom : c'est le partage même
            // entre le fichier et les annotations, et @UMLExclude est fait pour ce cas-là.
            checkArgument(!motif.contains("."),
                "%s: \"%s\" names a member of one class, and a rule never designates an element "
                    + "by name. Put @UMLExclude on that member instead, or write a pattern that "
                    + "describes a family of names.",
                contexte, motif);
            // « * » seul masquerait tout le compartiment : c'est licite, mais assez inattendu
            // pour mériter d'être écrit autrement le jour où la règle existera.
            checkArgument(!motif.equals("*"),
                "%s: the pattern \"*\" would hide every member of every class.", contexte);

            motifs.add(Motif.de(motif));
        }
    }

    // « omit visibility », « omit parameter names », « omit return void ».
    //
    // Un verbe distinct de « hide », et c'est délibéré : ces trois-là ne font disparaître aucun
    // membre, ils retirent un morceau de la ligne d'un membre qui reste affiché. Le mot dit à lui
    // seul qu'aucun @UMLInclude ne les contredira, puisqu'il n'y a rien à ramener.
    //
    // « return void » ne vaut que pour le retour, et que pour void : UML tient ce type pour
    // facultatif, et l'omettre veut dire « ne rend rien ». Un attribut ou un paramètre privé de
    // son type laisserait au contraire une ligne incompréhensible, et effacer un type de retour
    // utile ferait perdre au dessin ce que la boîte existe pour montrer.
    private static void lireOmit(boolean[] omettreVisibilite, boolean[] omettreNomsDeParametres,
        boolean[] omettreRetourVoid, String reste, String contexte) {

        boolean[] demande = switch (reste) {
            case VISIBILITE -> omettreVisibilite;
            case NOMS_DE_PARAMETRES -> omettreNomsDeParametres;
            case RETOUR_VOID -> omettreRetourVoid;
            default -> throw new IllegalArgumentException(contexte + ": expected \"omit "
                + VISIBILITE + "\", \"omit " + NOMS_DE_PARAMETRES + "\" or \"omit " + RETOUR_VOID
                + "\", got \"omit " + reste + "\".");
        };
        // La seconde ligne ne demanderait rien de plus ; un fichier qui se répète est un fichier
        // que personne ne relit.
        checkArgument(!demande[0], "%s: \"omit %s\" is already asked for.", contexte, reste);

        demande[0] = true;
    }

    // « type ImmutableList -> List » : le nom lu dans le code, puis celui qui s'affiche.
    private static void lireType(Map<String, String> alias, String reste, String contexte) {
        int fleche = reste.indexOf(FLECHE);
        checkArgument(fleche >= 0,
            "%s: expected \"type <type of the code> -> <displayed type>\", got \"type %s\".",
            contexte, reste);

        String source = reste.substring(0, fleche).strip();
        String affiche = reste.substring(fleche + FLECHE.length()).strip();

        checkArgument(!source.isEmpty(), "%s: the type to replace is missing, before the arrow.",
            contexte);
        checkArgument(!affiche.isEmpty(),
            "%s: the displayed type is missing, after the arrow. To hide a type, there is nothing "
                + "to write: leave the line out.",
            contexte);
        // Une espace au milieu d'un nom de type trahit une flèche oubliée ou un mot en trop ; le
        // laisser passer écrirait dans la boîte un texte que personne ne relirait.
        checkArgument(!source.contains(" ") && !affiche.contains(" "),
            "%s: a type name cannot contain a space (read \"%s\" -> \"%s\").",
            contexte, source, affiche);

        // Deux règles sur le même type se contrediraient, et la seconde l'emporterait en silence.
        checkArgument(!alias.containsKey(source),
            "%s: \"%s\" already has a rule, which said \"%s\". Keep only one of the two.",
            contexte, source, alias.get(source));

        alias.put(source, affiche);
    }
}
