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

    private static final Regles AUCUNE = new Regles(ImmutableMap.of(), false,
        UMLVisibility.NONE, false, ImmutableList.of(),
        ImmutableList.of(), false, "",
        UMLFlow.TOP_TO_BOTTOM, true, false, false, false, false, false);

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
        return alias.isEmpty() && !omettreRetourVoid && strategie == UMLVisibility.NONE
            && !strategieDeclaree
            && membresMasques.isEmpty()
            && vues.isEmpty() && !vuesParPaquetage && titre.isEmpty()
            && direction == UMLFlow.TOP_TO_BOTTOM && masquerMembresVides && !omettreVisibilite
            && !omettreNomsDeParametres && !ordreDeclaration && !multiplicitesDeduites
            && !omettreAbstrait;
    }
}
