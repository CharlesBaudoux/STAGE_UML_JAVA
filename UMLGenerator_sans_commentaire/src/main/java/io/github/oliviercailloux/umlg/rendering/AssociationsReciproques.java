package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.Avertissements;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AssociationsReciproques {

    private AssociationsReciproques() {}

    /**
     * Les relations de chaque entité, une fois les paires réciproques fusionnées.
     *
     * @param entites les boîtes du diagramme en cours de dessin
     * @return pour chaque nom complet d'entité, ses relations à écrire
     */
    public static ImmutableMap<String, ImmutableList<UmlRelation>> de(
        Iterable<UmlEntity> entites) {
        checkNotNull(entites);

        Map<String, UmlEntity> parNom = new LinkedHashMap<>();
        for (UmlEntity entite : entites) {
            parNom.put(entite.qualifiedName(), entite);
        }

        Map<String, UmlAssociation> absorbees = new LinkedHashMap<>();
        Map<String, UmlAssociation> fusionnees = new LinkedHashMap<>();

        for (UmlEntity entite : parNom.values()) {
            for (UmlRelation relation : entite.relations()) {
                if (!(relation instanceof UmlAssociation association)) {
                    continue;
                }
                String clef = clef(entite.qualifiedName(), association);
                if (absorbees.containsKey(clef) || fusionnees.containsKey(clef)) {
                    continue;
                }
                UmlEntity enFace = parNom.get(association.targetQualifiedName());
                if (enFace == null || enFace == entite) {
                    continue;
                }
                UmlAssociation partenaire = reciproque(enFace, entite.qualifiedName(),
                    absorbees.keySet(), fusionnees.keySet());
                if (partenaire == null) {
                    continue;
                }
                String clefPartenaire = clef(enFace.qualifiedName(), partenaire);

                if (!fusionnable(association, partenaire)) {
                    Avertissements.dire(entite.qualifiedName() + "." + association.fieldName()
                        + " and " + enFace.qualifiedName() + "." + partenaire.fieldName()
                        + " declare the same association from both sides, and both carry more "
                        + "than a role and a multiplicity (a type, a label, a constraint or a "
                        + "slash). UML would draw one line with two "
                        + "ends; two are drawn, because merging them would silently drop what "
                        + "the second one says. Keep the decorations on one side only to get a "
                        + "single line.");
                    fusionnees.put(clef, association);
                    fusionnees.put(clefPartenaire, partenaire);
                    continue;
                }
                fusionnees.put(clef, fusionner(association, partenaire));
                absorbees.put(clefPartenaire, partenaire);
            }
        }

        ImmutableMap.Builder<String, ImmutableList<UmlRelation>> resultat = ImmutableMap.builder();
        for (UmlEntity entite : parNom.values()) {
            List<UmlRelation> gardees = new ArrayList<>();
            for (UmlRelation relation : entite.relations()) {
                if (!(relation instanceof UmlAssociation association)) {
                    gardees.add(relation);
                    continue;
                }
                String clef = clef(entite.qualifiedName(), association);
                if (absorbees.containsKey(clef)) {
                    continue;
                }
                gardees.add(fusionnees.getOrDefault(clef, association));
            }
            resultat.put(entite.qualifiedName(), ImmutableList.copyOf(gardees));
        }
        return resultat.buildOrThrow();
    }

    private static UmlAssociation reciproque(UmlEntity enFace, String nomSource,
        Set<String> absorbees, Set<String> fusionnees) {
        for (UmlRelation relation : enFace.relations()) {
            if (!(relation instanceof UmlAssociation candidate)) {
                continue;
            }
            if (!candidate.targetQualifiedName().equals(nomSource)) {
                continue;
            }
            String clef = clef(enFace.qualifiedName(), candidate);
            if (absorbees.contains(clef) || fusionnees.contains(clef)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean fusionnable(UmlAssociation gardee, UmlAssociation partenaire) {
        return partenaire.type() == UMLRelationType.ASSOCIATION
            && partenaire.label().isEmpty()
            && partenaire.constraints().isEmpty()
            && !partenaire.derived()
            && gardee.sourceRole().isEmpty()
            && gardee.sourceCardinality().isEmpty();
    }

    private static UmlAssociation fusionner(UmlAssociation gardee, UmlAssociation partenaire) {
        return new UmlAssociation(gardee.fieldName(), gardee.targetQualifiedName(), gardee.type(),
            partenaire.targetCardinality(), gardee.targetCardinality(),
            partenaire.targetRole(), gardee.targetRole(),
            gardee.label(), gardee.direction(), gardee.derived(), gardee.constraints());
    }

    private static String clef(String nomSource, UmlAssociation association) {
        return nomSource + "#" + association.fieldName() + "->"
            + association.targetQualifiedName();
    }
}
