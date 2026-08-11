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

final class LecteurDeRegles {

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

    private static final String NAME = "name";
    private static final String RETAIN = "retain";
    private static final String REMOVE = "remove";
    private static final String PACKAGE = "package";
    private static final String AROUND = "around";

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

    private static final ObjectMapper ANALYSEUR = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .build();

    private LecteurDeRegles() {}

    private record Lieu(String origine, String chemin) {

        Lieu dans(String cle) {
            return new Lieu(origine, chemin.isEmpty() ? cle : chemin + "." + cle);
        }

        Lieu indice(int rang) {
            return new Lieu(origine, chemin + "[" + rang + "]");
        }

        @Override
        public String toString() {
            return chemin.isEmpty() ? origine : origine + ", " + chemin;
        }
    }

    static Regles lire(String contenu, String origine) {
        Lieu racine = new Lieu(origine, "");
        JsonNode objet = arbre(contenu, racine);

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
        UMLVisibility strategie = null;

        for (Map.Entry<String, JsonNode> champ : objet.properties()) {
            String cle = champ.getKey();
            JsonNode valeur = champ.getValue();
            Lieu lieu = racine.dans(cle);

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

    private static JsonNode arbre(String contenu, Lieu racine) {
        if (contenu.isBlank()) {
            return ANALYSEUR.createObjectNode();
        }

        JsonNode lu;
        try {
            lu = ANALYSEUR.readTree(contenu);
        } catch (JsonProcessingException e) {
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

    private static boolean lireOrder(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);
        return switch (ecrit) {
            case "declaration" -> true;
            case "alphabetical" -> false;
            default -> throw new IllegalArgumentException(lieu + ": expected \"declaration\" or "
                + "\"alphabetical\", got \"" + ecrit + "\".");
        };
    }

    private static boolean lireMultiplicity(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);
        checkArgument("brackets".equals(ecrit),
            "%s: expected \"brackets\", got \"%s\".", lieu, ecrit);

        return true;
    }

    private static String lireTitle(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);
        checkArgument(!ecrit.isBlank(),
            "%s: the title cannot be empty or made of whitespace only. To have no title, leave "
                + "the key out.", lieu);

        return ecrit;
    }

    private static ImmutableList<DefinitionDeVue> lireViews(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isArray(),
            "%s: expected an array of views, for example [ { \"name\": \"Mapping\", "
                + "\"retain\": [\"KeysymEntry\"] } ]; got %s.",
            lieu, sorte(valeur));

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

    private static String lireNomDeVue(JsonNode valeur, Lieu lieu) {
        String nom = texte(valeur, lieu);
        checkArgument(!nom.isBlank(),
            "%s: the view name cannot be empty or made of whitespace only.", lieu);

        return nom;
    }

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

    private static Selecteur selecteur(JsonNode valeur, Lieu lieu) {
        if (valeur.isTextual()) {
            return Selecteur.classe(nomDesigne(valeur, lieu, "class"));
        }
        checkArgument(valeur.isObject(),
            "%s: expected a class name, or { \"%s\": … }, or { \"%s\": … }; got %s.",
            lieu, PACKAGE, AROUND, sorte(valeur));
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

    private static String nomDesigne(JsonNode valeur, Lieu lieu, String sorte) {
        String nom = texte(valeur, lieu);
        checkArgument(!nom.isBlank(),
            "%s: a %s selector cannot be empty or made of whitespace only.", lieu, sorte);
        checkArgument(!nom.contains(" "),
            "%s: \"%s\" contains a space, so it is not a name. Write one selector per entry of "
                + "the array.", lieu, nom);

        return nom;
    }

    private static UMLFlow lireFlux(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);
        return switch (ecrit) {
            case "top-to-bottom" -> UMLFlow.TOP_TO_BOTTOM;
            case "left-to-right" -> UMLFlow.LEFT_TO_RIGHT;
            default -> throw new IllegalArgumentException(lieu + ": expected \"top-to-bottom\" or "
                + "\"left-to-right\", got \"" + ecrit + "\".");
        };
    }

    private static UMLVisibility lireStrategy(JsonNode valeur, Lieu lieu) {
        String ecrit = texte(valeur, lieu);

        UMLVisibility lue;
        try {
            lue = UMLVisibility.valueOf(ecrit.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(lieu + ": unknown strategy \"" + ecrit
                + "\". Known strategies: none, public, protected, package, private.", e);
        }
        checkArgument(lue != UMLVisibility.PROJECT,
            "%s: \"project\" is what a class writes to defer to this key; the project itself "
                + "must name a real strategy.", lieu);

        return lue;
    }

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
            checkArgument(!motif.contains("."),
                "%s: \"%s\" names a member of one class, and a rule never designates an element "
                    + "by name. Put @UMLExclude on that member instead, or write a pattern that "
                    + "describes a family of names.",
                ou, motif);
            checkArgument(!motif.equals("*"),
                "%s: the pattern \"*\" would hide every member of every class.", ou);

            motifs.add(Motif.de(motif));
        }
        return motifs.build();
    }

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
            checkArgument(!demandees.contains(demandee),
                "%s: \"%s\" is already asked for.", ou, ecrit);

            demandees.add(demandee);
        }
        return demandees;
    }

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
            checkArgument(!source.contains(" ") && !affiche.contains(" "),
                "%s: a type name cannot contain a space (read \"%s\" -> \"%s\").",
                ou, source, affiche);

            alias.put(source, affiche);
        }
        return alias.build();
    }

    private static boolean booleen(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isBoolean(),
            "%s: expected true or false, got %s.", lieu, sorte(valeur));

        return valeur.booleanValue();
    }

    private static String texte(JsonNode valeur, Lieu lieu) {
        checkArgument(valeur.isTextual(), "%s: expected a string, got %s.", lieu, sorte(valeur));

        return valeur.textValue();
    }

    private static String sorte(JsonNode valeur) {
        return switch (valeur.getNodeType()) {
            case ARRAY -> "an array";
            case BOOLEAN -> "a boolean";
            case NULL -> "null";
            case NUMBER -> "a number";
            case OBJECT -> "an object";
            case STRING -> "a string";
            default -> "\"" + valeur + "\"";
        };
    }
}
