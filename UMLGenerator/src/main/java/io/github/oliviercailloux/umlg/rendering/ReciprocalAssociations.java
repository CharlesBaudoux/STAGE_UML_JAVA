package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.warning.Warner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReciprocalAssociations {
    private ReciprocalAssociations() {}

    public static ImmutableMap<String, ImmutableList<UmlRelation>> of(
        Iterable<UmlEntity> entities) {
        checkNotNull(entities);

        Map<String, UmlEntity> byName = new LinkedHashMap<>();
        for (UmlEntity entity : entities) {
            byName.put(entity.qualifiedName(), entity);
        }

        Map<String, UmlAssociation> absorbed = new LinkedHashMap<>();
        Map<String, UmlAssociation> merged = new LinkedHashMap<>();

        for (UmlEntity entity : byName.values()) {
            for (UmlRelation relation : entity.relations()) {
                if (!(relation instanceof UmlAssociation association)) {
                    continue;
                }
                String key = key(entity.qualifiedName(), association);
                if (absorbed.containsKey(key) || merged.containsKey(key)) {
                    continue;
                }
                UmlEntity enFace = byName.get(association.targetQualifiedName());
                if (enFace == null || enFace == entity) {
                    continue;
                }
                UmlAssociation partner = reciprocal(enFace, entity.qualifiedName(),
                    absorbed.keySet(), merged.keySet());
                if (partner == null) {
                    continue;
                }
                String keyPartner = key(enFace.qualifiedName(), partner);

                if (!mergeable(association, partner)) {
                    Warner.say(
                        whyNotMerged(entity, association, enFace, partner));

                    merged.put(key, association);
                    merged.put(keyPartner, partner);
                    continue;
                }
                merged.put(key, merge(association, partner));
                absorbed.put(keyPartner, partner);
            }
        }

        ImmutableMap.Builder<String, ImmutableList<UmlRelation>> result = ImmutableMap.builder();
        for (UmlEntity entity : byName.values()) {
            List<UmlRelation> kept = new ArrayList<>();
            for (UmlRelation relation : entity.relations()) {
                if (!(relation instanceof UmlAssociation association)) {
                    kept.add(relation);
                    continue;
                }
                String key = key(entity.qualifiedName(), association);
                if (absorbed.containsKey(key)) {
                    continue;
                }
                kept.add(merged.getOrDefault(key, association));
            }
            result.put(entity.qualifiedName(), ImmutableList.copyOf(kept));
        }
        return result.buildOrThrow();
    }

    private static UmlAssociation reciprocal(UmlEntity enFace, String nameSource,
        Set<String> absorbed, Set<String> merged) {
        for (UmlRelation relation : enFace.relations()) {
            if (!(relation instanceof UmlAssociation candidate)) {
                continue;
            }
            if (!candidate.targetQualifiedName().equals(nameSource)) {
                continue;
            }
            String key = key(enFace.qualifiedName(), candidate);
            if (absorbed.contains(key) || merged.contains(key)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private static boolean mergeable(UmlAssociation kept, UmlAssociation partner) {
        return !partnerDecorated(partner) && !sourceSideAlreadyWritten(kept);
    }

    private static boolean partnerDecorated(UmlAssociation partner) {
        return partner.type() != UMLRelationType.ASSOCIATION
            || !partner.label().isEmpty()
            || !partner.constraints().isEmpty()
            || partner.derived();
    }

    private static boolean sourceSideAlreadyWritten(UmlAssociation kept) {
        return !kept.sourceRole().isEmpty() || !kept.sourceMultiplicity().isEmpty();
    }

    private static String whyNotMerged(UmlEntity entity, UmlAssociation association,
        UmlEntity enFace, UmlAssociation partner) {
        String here = entity.qualifiedName() + "." + association.fieldName();
        String below = enFace.qualifiedName() + "." + partner.fieldName();

        StringBuilder message = new StringBuilder(here + " and " + below
            + " declare the same association from both sides. UML would draw one line with two "
            + "ends; two are drawn, because merging them would silently drop what the second one "
            + "says.");

        if (partnerDecorated(partner)) {
            message.append(" ").append(below).append(" carries more than a role and a "
                + "multiplicity (a type, a label, a constraint or a slash), and the end of a line "
                + "cannot hold any of those. Keep the decorations on one side only.");
        }
        if (sourceSideAlreadyWritten(association)) {
            message.append(" ").append(here).append(" already names its own source end "
                + "(sourceRole or sourceMultiplicity), which is exactly where ").append(below)
                .append(" would have been written. Drop that source end, and let the class "
                    + "opposite say what it is called.");
        }
        return message.toString();
    }

    private static UmlAssociation merge(UmlAssociation kept, UmlAssociation partner) {
        return new UmlAssociation(kept.fieldName(), kept.targetQualifiedName(), kept.type(),
            partner.targetMultiplicity(), kept.targetMultiplicity(),
            partner.targetRole(), kept.targetRole(),
            kept.label(), kept.direction(), kept.derived(), kept.constraints());
    }

    private static String key(String nameSource, UmlAssociation association) {
        return nameSource + "#" + association.fieldName() + "->"
            + association.targetQualifiedName();
    }
}
