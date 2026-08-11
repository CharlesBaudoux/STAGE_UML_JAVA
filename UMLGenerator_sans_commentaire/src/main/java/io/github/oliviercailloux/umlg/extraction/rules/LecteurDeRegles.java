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

final class LecteurDeRegles {

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
    private static final String ABSTRAIT = "abstract";
    private static final String DIRECTION = "direction";
    private static final String AROUND = "around";
    private static final String PACKAGE = "package";
    private static final String FLECHE = "->";

    private LecteurDeRegles() {}

    static Regles lire(ImmutableList<String> lignes, String origine) {
        Map<String, String> alias = new LinkedHashMap<>();
        List<Motif> membres = new ArrayList<>();
        Map<String, DefinitionDeVue> vues = new LinkedHashMap<>();
        boolean[] parPaquetage = {false};
        String[] titre = {""};
        UMLFlow[] direction = {UMLFlow.TOP_TO_BOTTOM};
        boolean[] masquerMembresVides = {true};
        boolean[] omettreVisibilite = {false};
        boolean[] omettreNomsDeParametres = {false};
        boolean[] omettreRetourVoid = {false};
        boolean[] omettreAbstrait = {false};
        Boolean[] ordreDeclaration = {null};
        boolean[] multiplicitesDeduites = {false};
        UMLVisibility[] strategie = {null};

        for (int i = 0; i < lignes.size(); i++) {
            String contexte = origine + ", line " + (i + 1);
            String ligne = lignes.get(i).strip();

            if (ligne.isEmpty() || ligne.startsWith("#")) {
                continue;
            }

            int fin = ligne.indexOf(' ');
            String motCle = fin < 0 ? ligne : ligne.substring(0, fin);
            String reste = fin < 0 ? "" : ligne.substring(fin + 1).strip();

            switch (motCle) {
                case TYPE -> lireType(alias, reste, contexte);
                case HIDE -> lireHide(membres, reste, contexte);
                case OMIT -> lireOmit(omettreVisibilite, omettreNomsDeParametres,
                    omettreRetourVoid, omettreAbstrait, reste, contexte);
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
            multiplicitesDeduites[0], omettreAbstrait[0]);
    }

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

    private static void lireMultiplicity(boolean[] deduites, String reste, String contexte) {
        checkArgument("brackets".equals(reste),
            "%s: expected \"multiplicity brackets\", got \"multiplicity %s\".", contexte, reste);
        checkArgument(!deduites[0],
            "%s: \"multiplicity brackets\" is already asked for.", contexte);

        deduites[0] = true;
    }

    private static void lireTitle(String[] titre, String reste, String contexte) {
        checkArgument(!reste.isBlank(),
            "%s: the title is missing, after \"title\".", contexte);
        checkArgument(titre[0].isEmpty(),
            "%s: the title is already set to \"%s\". Keep only one \"title\" line.",
            contexte, titre[0]);

        titre[0] = reste;
    }

    private static void lireShow(boolean[] masquerMembresVides, String reste, String contexte) {
        checkArgument(MEMBRES_VIDES.equals(reste),
            "%s: expected \"show empty members\", got \"show %s\".", contexte, reste);
        checkArgument(masquerMembresVides[0],
            "%s: \"show empty members\" is already asked for.", contexte);

        masquerMembresVides[0] = false;
    }

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

    private static void lireViews(boolean[] parPaquetage, String reste, String contexte) {
        checkArgument("by package".equals(reste),
            "%s: expected \"views by package\", got \"views %s\".", contexte, reste);
        checkArgument(!parPaquetage[0],
            "%s: \"views by package\" is already asked for.", contexte);

        parPaquetage[0] = true;
    }

    private static UMLFlow lireFlux(String reste, String contexte) {
        return switch (reste) {
            case "top-to-bottom" -> UMLFlow.TOP_TO_BOTTOM;
            case "left-to-right" -> UMLFlow.LEFT_TO_RIGHT;
            default -> throw new IllegalArgumentException(contexte + ": expected \"top-to-bottom\""
                + " or \"left-to-right\", got \"" + reste + "\".");
        };
    }

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
        checkArgument(lue != UMLVisibility.PROJECT,
            "%s: \"project\" is what a class writes to defer to this line; the project itself "
                + "must name a real strategy.", contexte);

        strategie[0] = lue;
    }

    private static void lireHide(List<Motif> membres, String reste, String contexte) {
        checkArgument(!reste.isEmpty(),
            "%s: expected \"hide <patterns>\", for example \"hide get*, log\"; got \"hide\".",
            contexte);

        lireMotifs(membres, reste, contexte);
    }

    private static void lireMotifs(List<Motif> motifs, String reste, String contexte) {
        for (String morceau : reste.split(",", -1)) {
            String motif = morceau.strip();
            checkArgument(!motif.isEmpty(),
                "%s: an empty pattern was read in \"hide %s\"; a stray comma, most likely.",
                contexte, reste);
            checkArgument(!motif.contains(" "),
                "%s: a name pattern cannot contain a space (read \"%s\"). Separate several "
                    + "patterns with commas, or did you mean \"omit\"?",
                contexte, motif);
            checkArgument(!motif.contains("."),
                "%s: \"%s\" names a member of one class, and a rule never designates an element "
                    + "by name. Put @UMLExclude on that member instead, or write a pattern that "
                    + "describes a family of names.",
                contexte, motif);
            checkArgument(!motif.equals("*"),
                "%s: the pattern \"*\" would hide every member of every class.", contexte);

            motifs.add(Motif.de(motif));
        }
    }

    private static void lireOmit(boolean[] omettreVisibilite, boolean[] omettreNomsDeParametres,
        boolean[] omettreRetourVoid, boolean[] omettreAbstrait, String reste, String contexte) {

        boolean[] demande = switch (reste) {
            case VISIBILITE -> omettreVisibilite;
            case NOMS_DE_PARAMETRES -> omettreNomsDeParametres;
            case RETOUR_VOID -> omettreRetourVoid;
            case ABSTRAIT -> omettreAbstrait;
            default -> throw new IllegalArgumentException(contexte + ": expected \"omit "
                + VISIBILITE + "\", \"omit " + NOMS_DE_PARAMETRES + "\", \"omit " + RETOUR_VOID
                + "\" or \"omit " + ABSTRAIT + "\", got \"omit " + reste + "\".");
        };
        checkArgument(!demande[0], "%s: \"omit %s\" is already asked for.", contexte, reste);

        demande[0] = true;
    }

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
        checkArgument(!source.contains(" ") && !affiche.contains(" "),
            "%s: a type name cannot contain a space (read \"%s\" -> \"%s\").",
            contexte, source, affiche);

        checkArgument(!alias.containsKey(source),
            "%s: \"%s\" already has a rule, which said \"%s\". Keep only one of the two.",
            contexte, source, alias.get(source));

        alias.put(source, affiche);
    }
}
