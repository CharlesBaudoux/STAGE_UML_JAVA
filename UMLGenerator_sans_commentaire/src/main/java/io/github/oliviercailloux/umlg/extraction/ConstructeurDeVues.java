package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.extraction.rules.DefinitionDeVue;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.extraction.rules.Selecteur;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.model.UmlView;
import io.github.oliviercailloux.umlg.warning.Avertisseur;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

public final class ConstructeurDeVues {

    public static final String VUE_INTEGRALE = "Projet";

    static final String PAQUETAGE_PAR_DEFAUT = "(default)";

    private static final int BLOCS_ENCHAINABLES = 3;

    private ConstructeurDeVues() {}

    public static ImmutableList<UmlView> construire(ImmutableSet<UmlEntity> entites,
        UmlDiagram diagramme, Regles regles) {
        checkNotNull(entites);
        checkNotNull(diagramme);
        checkNotNull(regles);

        if (entites.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<UmlView> nommees = ImmutableList.builder();
        for (DefinitionDeVue definition : regles.vues()) {
            nommees.add(declaree(definition, entites));
        }
        if (regles.vuesParPaquetage()) {
            nommees.addAll(parPaquetage(entites));
        }
        ImmutableList<UmlView> vues = nommees.build();
        verifierLesNomsDeFichier(vues);

        String titre = diagramme.hasTitle() ? diagramme.title() : VUE_INTEGRALE;
        return ImmutableList.<UmlView>builder()
            .add(new UmlView(titre, ImmutableList.copyOf(entites), diagramme.direction()))
            .addAll(vues)
            .build();
    }

    private static void verifierLesNomsDeFichier(ImmutableList<UmlView> vues) {
        Map<String, String> parFichier = new LinkedHashMap<>();
        for (UmlView vue : vues) {
            String fichier = vue.fileName();
            String premiere = parFichier.putIfAbsent(fichier, vue.name());
            checkArgument(premiere == null,
                "Views \"%s\" and \"%s\" both come down to the file name \"%s\", so the second "
                    + "would overwrite the first. A file name is the view name without its "
                    + "accents, with everything that is neither a letter nor a digit turned into "
                    + "a dash, in lower case: give one of the two a name that differs by more "
                    + "than punctuation or case.",
                premiere, vue.name(), fichier);
        }
    }

    private static UmlView declaree(DefinitionDeVue definition, ImmutableSet<UmlEntity> entites) {
        Set<UmlEntity> retenues = new LinkedHashSet<>();
        for (Selecteur selecteur : definition.retenus()) {
            retenues.addAll(resoudre(selecteur, entites, definition.nom()));
        }
        for (Selecteur selecteur : definition.retires()) {
            retenues.removeAll(resoudre(selecteur, entites, definition.nom()));
        }
        checkArgument(!retenues.isEmpty(),
            "View \"%s\" retains no class; everything it named was also removed.",
            definition.nom());

        return new UmlView(definition.nom(), ordonnees(retenues), definition.direction());
    }

    private static ImmutableList<UmlView> parPaquetage(ImmutableSet<UmlEntity> entites) {
        Map<String, List<UmlEntity>> parNom = new LinkedHashMap<>();
        for (UmlEntity entite : entites) {
            parNom.computeIfAbsent(paquetageDe(entite), nom -> new ArrayList<>()).add(entite);
        }
        ImmutableList.Builder<UmlView> vues = ImmutableList.builder();
        for (String paquetage : parNom.keySet().stream().sorted().toList()) {
            vues.add(new UmlView(paquetage, ordonnees(parNom.get(paquetage)),
                UMLFlow.TOP_TO_BOTTOM));
        }
        return vues.build();
    }

    private static String paquetageDe(UmlEntity entite) {
        String complet = entite.qualifiedName();
        int point = complet.lastIndexOf('.');
        return point < 0 ? PAQUETAGE_PAR_DEFAUT : complet.substring(0, point);
    }

    private static ImmutableList<UmlEntity> resoudre(Selecteur selecteur,
        ImmutableSet<UmlEntity> entites, String vue) {

        ImmutableList<UmlEntity> trouvees = switch (selecteur.sorte()) {
            case CLASSE -> parNom(selecteur.valeur(), entites);
            case PAQUETAGE -> entites.stream()
                .filter(e -> paquetageDe(e).equals(selecteur.valeur()))
                .collect(ImmutableList.toImmutableList());
            case VOISINAGE -> voisinage(selecteur.valeur(), entites, vue);
        };

        checkArgument(!trouvees.isEmpty(),
            "View \"%s\": \"%s\" matches no class of the diagram. A class must carry @UMLClass to "
                + "be named here, and its name must be spelled as in the code.",
            vue, selecteur.ecrit());

        return trouvees;
    }

    private static ImmutableList<UmlEntity> parNom(String nom, ImmutableSet<UmlEntity> entites) {
        return entites.stream()
            .filter(e -> e.qualifiedName().equals(nom) || nomSimple(e).equals(nom))
            .collect(ImmutableList.toImmutableList());
    }

    private static String nomSimple(UmlEntity entite) {
        String complet = entite.qualifiedName();
        int separateur = Math.max(complet.lastIndexOf('.'), complet.lastIndexOf('$'));
        return separateur < 0 ? complet : complet.substring(separateur + 1);
    }

    private static ImmutableList<UmlEntity> voisinage(String nom, ImmutableSet<UmlEntity> entites,
        String vue) {
        ImmutableList<UmlEntity> centres = parNom(nom, entites);
        checkArgument(!centres.isEmpty(),
            "View \"%s\": \"around %s\" matches no class of the diagram.", vue, nom);

        Set<String> voulus = new LinkedHashSet<>();
        for (UmlEntity centre : centres) {
            voulus.add(centre.qualifiedName());
            for (UmlRelation relation : centre.relations()) {
                voulus.addAll(extremites(relation));
            }
        }
        for (UmlEntity candidate : entites) {
            for (UmlRelation relation : candidate.relations()) {
                if (centres.stream()
                    .anyMatch(c -> extremites(relation).contains(c.qualifiedName()))) {
                    voulus.add(candidate.qualifiedName());
                }
            }
        }
        signalerVoisinageVide(voulus, centres, nom, vue);

        return entites.stream().filter(e -> voulus.contains(e.qualifiedName()))
            .collect(ImmutableList.toImmutableList());
    }

    static boolean signalerVoisinageVide(Set<String> voulus, ImmutableList<UmlEntity> centres,
        String nom, String vue) {
        if (voulus.size() > centres.size()) {
            return false;
        }
        Avertisseur.dire("view \"" + vue + "\": \"around " + nom + "\" found no "
            + "neighbour, so the view shows the class alone. Neighbours come from declared "
            + "relations — @UMLAssociation, @UMLDependency, inheritance between annotated "
            + "classes — not from the types used by fields or methods. Declare a relation, or "
            + "name the classes of this view explicitly.");
        return true;
    }

    private static ImmutableSet<String> extremites(UmlRelation relation) {
        if (relation instanceof UmlAssociationClass classe) {
            return ImmutableSet.of(classe.sourceQualifiedName(), classe.targetQualifiedName());
        }
        return ImmutableSet.of(relation.targetQualifiedName());
    }

    private static ImmutableList<UmlEntity> ordonnees(Iterable<UmlEntity> retenues) {
        ImmutableSet<String> presentes = ImmutableSet.copyOf(
            StreamSupport.stream(retenues.spliterator(), false)
                .map(UmlEntity::qualifiedName).toList());

        List<UmlEntity> elaguees = new ArrayList<>();
        for (UmlEntity entite : retenues) {
            elaguees.add(elaguer(entite, presentes));
        }
        return enchainer(elaguees);
    }

    private static UmlEntity elaguer(UmlEntity entite, ImmutableSet<String> presentes) {
        return entite.withRelations(entite.relations().stream()
            .filter(relation -> presentes.containsAll(extremites(relation)))
            .collect(ImmutableList.toImmutableList()));
    }

    private static ImmutableList<UmlEntity> enchainer(List<UmlEntity> entites) {
        if (entites.size() < 2) {
            return ImmutableList.copyOf(entites);
        }
        List<List<UmlEntity>> blocs = blocs(entites);
        if (blocs.size() < 2 || blocs.size() > BLOCS_ENCHAINABLES) {
            return ImmutableList.copyOf(entites);
        }

        Map<String, String> versLeBlocSuivant = new LinkedHashMap<>();
        for (int i = 0; i + 1 < blocs.size(); i++) {
            versLeBlocSuivant.put(blocs.get(i).get(0).qualifiedName(),
                blocs.get(i + 1).get(0).qualifiedName());
        }

        ImmutableList.Builder<UmlEntity> resultat = ImmutableList.builder();
        for (UmlEntity entite : entites) {
            String suivant = versLeBlocSuivant.get(entite.qualifiedName());
            resultat.add(suivant == null ? entite : avecOrdre(entite, suivant));
        }
        return resultat.build();
    }

    private static UmlEntity avecOrdre(UmlEntity entite, String suivant) {
        return entite.withRelations(ImmutableList.<UmlRelation>builder()
            .addAll(entite.relations())
            .add(new UmlLayout(suivant, UMLDirection.NONE))
            .build());
    }

    private static List<List<UmlEntity>> blocs(List<UmlEntity> entites) {
        Map<String, Set<String>> voisins = adjacence(entites);
        Map<String, UmlEntity> parNom = new LinkedHashMap<>();
        for (UmlEntity entite : entites) {
            parNom.put(entite.qualifiedName(), entite);
        }

        Set<String> vues = new LinkedHashSet<>();
        List<List<UmlEntity>> blocs = new ArrayList<>();

        for (UmlEntity depart : entites) {
            if (!vues.add(depart.qualifiedName())) {
                continue;
            }
            Set<String> bloc = new LinkedHashSet<>();
            bloc.add(depart.qualifiedName());
            List<String> aVoir = new ArrayList<>(List.of(depart.qualifiedName()));

            while (!aVoir.isEmpty()) {
                String courant = aVoir.remove(0);
                for (String voisin : voisins.getOrDefault(courant, Set.of())) {
                    if (bloc.add(voisin)) {
                        vues.add(voisin);
                        aVoir.add(voisin);
                    }
                }
            }
            blocs.add(entites.stream().filter(e -> bloc.contains(e.qualifiedName())).toList());
        }
        return blocs;
    }

    private static Map<String, Set<String>> adjacence(List<UmlEntity> entites) {
        Map<String, Set<String>> voisins = new LinkedHashMap<>();
        for (UmlEntity entite : entites) {
            for (UmlRelation relation : entite.relations()) {
                for (String bout : extremites(relation)) {
                    voisins.computeIfAbsent(entite.qualifiedName(), n -> new LinkedHashSet<>())
                        .add(bout);
                    voisins.computeIfAbsent(bout, n -> new LinkedHashSet<>())
                        .add(entite.qualifiedName());
                }
            }
        }
        return voisins;
    }
}
