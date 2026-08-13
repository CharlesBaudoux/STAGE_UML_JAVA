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

public final class Regles {

    public static final String NOM_DU_FICHIER = "umlg.json";

    private static final Regles AUCUNE = constructeur().construire();

    private final ImmutableMap<String, String> alias;

    private final boolean omettreRetourVoid;

    private final UMLVisibility strategie;

    private final boolean strategieDeclaree;

    private final ImmutableList<Motif> membresMasques;

    private final ImmutableList<DefinitionDeVue> vues;

    private final boolean vuesParPaquetage;

    private final String titre;

    private final UMLFlow direction;

    private final boolean masquerMembresVides;

    private final boolean omettreVisibilite;

    private final boolean omettreNomsDeParametres;

    private final boolean ordreDeclaration;

    private final boolean multiplicitesDeduites;

    private final boolean omettreAbstrait;

    private final Set<String> aliasUtilises = ConcurrentHashMap.newKeySet();

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

    static Constructeur constructeur() {
        return new Constructeur();
    }

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

    public String titre() {
        return titre;
    }

    public UMLFlow direction() {
        return direction;
    }

    public boolean masquerMembresVides() {
        return masquerMembresVides;
    }

    public ImmutableList<DefinitionDeVue> vues() {
        return vues;
    }

    public boolean vuesParPaquetage() {
        return vuesParPaquetage;
    }

    public UMLVisibility strategie() {
        return strategie;
    }

    public boolean strategieDeclaree() {
        return strategieDeclaree;
    }

    public boolean omettreNomsDeParametres() {
        return omettreNomsDeParametres;
    }

    public boolean ordreDeclaration() {
        return ordreDeclaration;
    }

    public boolean multiplicitesDeduites() {
        return multiplicitesDeduites;
    }

    public boolean omettreAbstrait() {
        return omettreAbstrait;
    }

    public UMLVisibility strategieDe(UMLVisibility declaree) {
        checkNotNull(declaree);
        return declaree == UMLVisibility.PROJECT ? strategie : declaree;
    }

    public boolean membreMasque(String nom) {
        checkNotNull(nom);
        for (Motif motif : membresMasques) {
            if (motif.correspond(nom)) {
                return true;
            }
        }
        return false;
    }

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

    public static Regles aucune() {
        return AUCUNE;
    }

    public static Regles duProjet(Path racine) throws IOException {
        checkNotNull(racine);
        Path fichier = racine.resolve(NOM_DU_FICHIER);

        if (!Files.isRegularFile(fichier)) {
            return aucune();
        }
        return LecteurDeRegles.lire(contenuEnUtf8(fichier), fichier.toString());
    }

    public static Regles duFichier(Path fichier) throws IOException {
        checkNotNull(fichier);
        return LecteurDeRegles.lire(contenuEnUtf8(fichier), fichier.toString());
    }

    private static String contenuEnUtf8(Path fichier) throws IOException {
        try {
            return Files.readString(fichier, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(fichier + " is not encoded in UTF-8, and the "
                + "rules file must be. Re-save it as UTF-8 — an accented letter in a title is "
                + "enough to reveal another encoding.", e);
        }
    }

    public static Regles lues(String contenu, String origine) {
        return LecteurDeRegles.lire(contenu, origine);
    }

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

    public String nomAfficheRetour(Class<?> type) {
        checkNotNull(type);

        if (omettreRetourVoid && type == void.class) {
            return "";
        }
        return nomAffiche(type);
    }

    public boolean vides() {
        return !auMoinsUneRegle;
    }
}
