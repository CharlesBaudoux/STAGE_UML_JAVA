package io.github.oliviercailloux.umlg.extraction.rules;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Lit le fichier de règles, ligne à ligne.
//
// La grammaire tient en trois cas : une ligne vide, un commentaire ouvert par « # », ou une
// règle. Une règle commence par un mot-clé qui dit sa nature — « type » et « hide » aujourd'hui —
// et c'est ce mot-clé qui décide comment lire le reste de la ligne. Ajouter une nature de règle
// se fait donc ici, et à un seul endroit : une branche de plus dans le switch, et la méthode qui
// va avec.
//
// Le fichier étant écrit à la main, chaque refus nomme le fichier et le numéro de ligne, puis
// rappelle la forme attendue. C'est le seul retour que l'auteur du fichier aura : il n'y a pas de
// compilateur pour relire ce qu'il écrit.
final class LecteurDeRegles {

    // Les mots-clés connus, et ce qui sépare les deux moitiés d'une règle « type ».
    private static final String TYPE = "type";
    private static final String HIDE = "hide";
    private static final String STRATEGY = "strategy";
    private static final String GROUP = "group";
    private static final String VIEW = "view";
    private static final String VIEWS = "views";
    private static final String OUTPUT = "output";
    private static final String TITLE = "title";
    private static final String SHOW = "show";
    private static final String MEMBRES_VIDES = "empty members";
    private static final String VISIBILITE = "visibility";
    private static final String DECLARE = "declare";
    private static final String CLASS = "class";
    private static final String EXTENDS = "extends";
    private static final String ASSOCIATION = "association";
    private static final String DIRECTION = "direction";
    private static final String AROUND = "around";
    private static final String RETURN = "return";
    private static final String METHOD = "method";
    private static final String FIELD = "field";
    private static final String PACKAGE = "package";
    private static final String FLECHE = "->";

    private LecteurDeRegles() {}

    static Regles lire(ImmutableList<String> lignes, String origine) {
        // LinkedHashMap et non ImmutableMap.Builder : il faut pouvoir demander « cette clé
        // est-elle déjà là ? » avant d'insérer, pour refuser deux règles contradictoires en
        // nommant la ligne fautive. Le builder de Guava, lui, ne se plaindrait qu'au build, sans
        // savoir où.
        Map<String, String> alias = new LinkedHashMap<>();
        // LinkedHashSet pour la même raison, et pour que l'ordre de lecture soit conservé.
        Set<String> retoursMasques = new LinkedHashSet<>();
        Set<Bruit> bruits = new LinkedHashSet<>();
        List<Motif> methodes = new ArrayList<>();
        List<Motif> champs = new ArrayList<>();
        Map<String, String> groupes = new LinkedHashMap<>();
        // LinkedHashMap : l'ordre d'écriture des vues est celui des fichiers produits.
        Map<String, DefinitionDeVue> vues = new LinkedHashMap<>();
        boolean[] parPaquetage = {false};
        Regles.Sortie[] sortie = {Regles.Sortie.REPLACE};
        String[] titre = {""};
        UMLFlow[] direction = {UMLFlow.TOP_TO_BOTTOM};
        boolean[] masquerMembresVides = {true};
        boolean[] masquerVisibilite = {false};
        // LinkedHashMap : les boîtes déclarées se complètent ligne à ligne — la classe d'abord,
        // ses attributs ensuite — et sortent dans l'ordre d'écriture.
        Map<String, ClasseDeclaree> declarees = new LinkedHashMap<>();
        List<Regles.LienDeclare> liens = new ArrayList<>();
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
                case HIDE -> lireHide(retoursMasques, bruits, methodes, champs, masquerVisibilite,
                    reste, contexte);
                case STRATEGY -> lireStrategy(strategie, reste, contexte);
                case GROUP -> lireGroup(groupes, reste, contexte);
                case VIEW -> lireView(vues, reste, contexte);
                case VIEWS -> lireViews(parPaquetage, reste, contexte);
                case OUTPUT -> lireOutput(sortie, reste, contexte);
                case TITLE -> lireTitle(titre, reste, contexte);
                case DIRECTION -> direction[0] = lireFlux(reste, contexte);
                case SHOW -> lireShow(masquerMembresVides, reste, contexte);
                case DECLARE -> lireDeclare(declarees, liens, reste, contexte);
                default -> throw new IllegalArgumentException(contexte + ": unknown rule \""
                    + motCle + "\". Known rules: " + TYPE + ", " + HIDE + ", " + STRATEGY + ", "
                    + GROUP + ", " + VIEW + ", " + VIEWS + ", " + OUTPUT + ", " + TITLE + ", "
                    + DIRECTION + ", " + SHOW + ", " + DECLARE + ".");
            }
        }
        return new Regles(ImmutableMap.copyOf(alias), ImmutableSet.copyOf(retoursMasques),
            strategie[0] == null ? UMLVisibility.NONE : strategie[0],
            ImmutableSet.copyOf(bruits), ImmutableList.copyOf(methodes),
            ImmutableList.copyOf(champs), ImmutableMap.copyOf(groupes),
            ImmutableList.copyOf(vues.values()), parPaquetage[0], sortie[0], titre[0],
            direction[0], masquerMembresVides[0], masquerVisibilite[0],
            ImmutableList.copyOf(declarees.values()), ImmutableList.copyOf(liens));
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

    // « output replace » ou « output keep ».
    private static void lireOutput(Regles.Sortie[] sortie, String reste, String contexte) {
        Regles.Sortie lue = switch (reste) {
            case "replace" -> Regles.Sortie.REPLACE;
            case "keep" -> Regles.Sortie.KEEP;
            default -> throw new IllegalArgumentException(contexte + ": expected \"output "
                + "replace\" or \"output keep\", got \"output " + reste + "\".");
        };
        sortie[0] = lue;
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

    // Les trois formes de « declare » : une boîte, un de ses attributs, un lien.
    //
    //   declare class StringRepresentation extends Representation
    //   declare field StringRepresentation string : String
    //   declare association Animal -> Chien 0..*
    //
    // Elles se lisent dans cet ordre : une boîte doit exister avant qu'on lui pose un attribut,
    // faute de quoi la ligne désignerait une boîte que rien ne dessine.
    private static void lireDeclare(Map<String, ClasseDeclaree> declarees,
        List<Regles.LienDeclare> liens, String reste, String contexte) {

        if (reste.startsWith(CLASS + " ")) {
            lireDeclareClass(declarees, reste.substring(CLASS.length()).strip(), contexte);
            return;
        }
        if (reste.startsWith(FIELD + " ")) {
            lireDeclareField(declarees, reste.substring(FIELD.length()).strip(), contexte);
            return;
        }
        if (reste.startsWith(ASSOCIATION + " ")) {
            lireDeclareAssociation(liens, reste.substring(ASSOCIATION.length()).strip(), contexte);
            return;
        }
        throw new IllegalArgumentException(contexte + ": expected \"declare class …\", \"declare "
            + "field …\" or \"declare association …\", got \"declare " + reste + "\".");
    }

    // « declare class Animal » ou « declare class Chien extends Animal ».
    private static void lireDeclareClass(Map<String, ClasseDeclaree> declarees, String reste,
        String contexte) {
        String nom = reste;
        String parent = "";

        int mot = reste.indexOf(" " + EXTENDS + " ");
        if (mot >= 0) {
            nom = reste.substring(0, mot).strip();
            parent = reste.substring(mot + EXTENDS.length() + 2).strip();
            checkArgument(!parent.isEmpty() && !parent.contains(" "),
                "%s: expected a single parent name after \"extends\", read \"%s\".",
                contexte, parent);
        }
        checkArgument(!nom.isEmpty() && !nom.contains(" "),
            "%s: expected \"declare class <Name> [extends <Name>]\", got \"declare class %s\".",
            contexte, reste);
        checkArgument(!declarees.containsKey(nom),
            "%s: class \"%s\" is already declared. Keep only one of the two.", contexte, nom);

        declarees.put(nom, ClasseDeclaree.de(nom, parent));
    }

    // « declare field StringRepresentation string : String ».
    private static void lireDeclareField(Map<String, ClasseDeclaree> declarees, String reste,
        String contexte) {
        int espace = reste.indexOf(' ');
        checkArgument(espace > 0,
            "%s: expected \"declare field <Class> <name> : <Type>\", got \"declare field %s\".",
            contexte, reste);

        String classe = reste.substring(0, espace);
        String champ = reste.substring(espace + 1).strip();
        int deuxPoints = champ.indexOf(':');
        checkArgument(deuxPoints > 0,
            "%s: expected \"<name> : <Type>\" after the class name, got \"%s\".",
            contexte, champ);

        String nom = champ.substring(0, deuxPoints).strip();
        String type = champ.substring(deuxPoints + 1).strip();
        ClasseDeclaree porteuse = declarees.get(classe);
        checkArgument(porteuse != null,
            "%s: class \"%s\" is not declared. Write \"declare class %s\" first.",
            contexte, classe, classe);
        checkArgument(!nom.isEmpty() && !type.isEmpty(),
            "%s: the field name or its type is missing, in \"%s\".", contexte, champ);

        declarees.put(classe, porteuse.avec(new ClasseDeclaree.ChampDeclare(nom, type)));
    }

    // « declare association Animal -> Chien 0..* » : la cardinalité est facultative.
    private static void lireDeclareAssociation(List<Regles.LienDeclare> liens, String reste,
        String contexte) {
        int fleche = reste.indexOf(FLECHE);
        checkArgument(fleche >= 0,
            "%s: expected \"declare association <Source> -> <Target> [cardinality]\", got "
                + "\"declare association %s\".",
            contexte, reste);

        String source = reste.substring(0, fleche).strip();
        String suite = reste.substring(fleche + FLECHE.length()).strip();
        int espace = suite.indexOf(' ');
        String cible = espace < 0 ? suite : suite.substring(0, espace);
        String cardinalite = espace < 0 ? "" : suite.substring(espace + 1).strip();

        checkArgument(!source.isEmpty() && !cible.isEmpty(),
            "%s: the source or the target is missing, in \"declare association %s\".",
            contexte, reste);
        checkArgument(!source.contains(" "),
            "%s: a class name cannot contain a space (read \"%s\").", contexte, source);

        liens.add(new Regles.LienDeclare(source, cible, cardinalite));
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

    // « group package com.exemple.modele -> \"Modèle\" » : tout un paquetage dans un rectangle.
    private static void lireGroup(Map<String, String> groupes, String reste, String contexte) {
        checkArgument(reste.startsWith(PACKAGE + " "),
            "%s: expected \"group package <package> -> <group name>\", got \"group %s\". Only a "
                + "package can be grouped by a rule; a single class is grouped by @UMLPackage.",
            contexte, reste);

        String apresPackage = reste.substring(PACKAGE.length()).strip();
        int fleche = apresPackage.indexOf(FLECHE);
        checkArgument(fleche >= 0,
            "%s: expected \"group package <package> -> <group name>\", got \"group %s\".",
            contexte, reste);

        String paquetage = apresPackage.substring(0, fleche).strip();
        String groupe = apresPackage.substring(fleche + FLECHE.length()).strip();
        // Les guillemets sont facultatifs : ils servent à écrire un nom qui contient des espaces.
        if (groupe.length() >= 2 && groupe.startsWith("\"") && groupe.endsWith("\"")) {
            groupe = groupe.substring(1, groupe.length() - 1);
        }

        checkArgument(!paquetage.isEmpty(),
            "%s: the package is missing, before the arrow.", contexte);
        checkArgument(!groupe.isBlank(),
            "%s: the group name is missing, after the arrow.", contexte);
        checkArgument(!paquetage.contains(" "),
            "%s: a package name cannot contain a space (read \"%s\").", contexte, paquetage);
        checkArgument(!groupes.containsKey(paquetage),
            "%s: package \"%s\" already belongs to group \"%s\". Keep only one of the two.",
            contexte, paquetage, groupes.get(paquetage));

        groupes.put(paquetage, groupe);
    }

    // « hide return void » : le type rendu par une méthode n'est pas écrit.
    //
    // Le mot « return » n'est pas une décoration : il dit où la règle s'applique, et laisse la
    // place à d'autres endroits si l'usage en révèle. Ne rien masquer ailleurs est délibéré —
    // un attribut sans type n'apprendrait plus rien, alors qu'une méthode sans type de retour
    // dit, en UML, qu'elle ne rend rien.
    private static void lireHide(Set<String> retoursMasques, Set<Bruit> bruits,
        List<Motif> methodes, List<Motif> champs, boolean[] masquerVisibilite, String reste,
        String contexte) {

        // « hide visibility » : les membres s'écrivent sans « + » ni « - ».
        if (VISIBILITE.equals(reste)) {
            checkArgument(!masquerVisibilite[0],
                "%s: \"hide visibility\" is already asked for.", contexte);
            masquerVisibilite[0] = true;
            return;
        }

        // Une famille de bruit s'écrit d'un seul mot : « hide accessors ».
        Bruit famille = Bruit.parMotCle(reste);
        if (famille != null) {
            checkArgument(bruits.add(famille),
                "%s: \"%s\" is already hidden. Keep only one of the two lines.", contexte, reste);
            return;
        }
        if (reste.startsWith(METHOD + " ")) {
            lireMotifs(methodes, reste.substring(METHOD.length()).strip(), contexte, METHOD);
            return;
        }
        if (reste.startsWith(FIELD + " ")) {
            lireMotifs(champs, reste.substring(FIELD.length()).strip(), contexte, FIELD);
            return;
        }
        checkArgument(reste.startsWith(RETURN + " "),
            "%s: expected \"hide return <type>\", \"hide method <pattern>\", \"hide field "
                + "<pattern>\", \"hide visibility\" or one of the noise families (%s); got "
                + "\"hide %s\".",
            contexte, Bruit.motsCles(), reste);

        String type = reste.substring(RETURN.length()).strip();

        checkArgument(!type.isEmpty(), "%s: the type to hide is missing, after \"return\".",
            contexte);
        checkArgument(!type.contains(" "),
            "%s: a type name cannot contain a space (read \"%s\").", contexte, type);
        checkArgument(retoursMasques.add(type),
            "%s: the return type \"%s\" is already hidden. Keep only one of the two lines.",
            contexte, type);
    }

    // « hide method get*, is* » : un ou plusieurs motifs, séparés par des virgules.
    //
    // La virgule évite de répéter « hide method » sur chaque ligne quand on écarte une famille de
    // noms d'un seul tenant. Chaque morceau est un motif à part entière, avec sa propre étoile.
    private static void lireMotifs(List<Motif> motifs, String reste, String contexte,
        String nature) {
        checkArgument(!reste.isEmpty(),
            "%s: the pattern is missing, after \"%s\".", contexte, nature);

        // La limite négative conserve les morceaux vides, y compris celui que laisse une virgule
        // finale : « hide method get*, » est une faute de frappe, et le fichier n'a pas de
        // compilateur pour la relever à notre place.
        for (String morceau : reste.split(",", -1)) {
            String motif = morceau.strip();
            checkArgument(!motif.isEmpty(),
                "%s: an empty pattern was read in \"hide %s %s\"; a stray comma, most likely.",
                contexte, nature, reste);
            checkArgument(!motif.contains(" "),
                "%s: a name pattern cannot contain a space (read \"%s\").", contexte, motif);
            // « * » seul masquerait tout le compartiment : c'est licite, mais assez inattendu
            // pour mériter d'être écrit autrement le jour où la règle existera.
            checkArgument(!motif.equals("*"),
                "%s: the pattern \"*\" would hide every %s of every class.", contexte, nature);

            motifs.add(Motif.de(motif));
        }
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
