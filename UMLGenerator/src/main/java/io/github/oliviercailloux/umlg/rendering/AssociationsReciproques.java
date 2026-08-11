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

// Deux attributs qui se font face ne sont qu'une association.
//
// LE DÉFAUT QUE CETTE CLASSE CORRIGE
//
// Chaque attribut annoté était traduit isolément, et le dessin recevait donc deux traits :
//
//     A --> "bs 0..*" B
//     B --> "a 1" A
//
// UML n'en veut qu'un, portant un rôle et une multiplicité à chaque bout — c'est même la
// définition d'une association binaire, dont chaque extrémité est nommée par la classe d'en
// face. Le cas est loin d'être rare : un modèle métier est plein d'attributs réciproques, et le
// diagramme se retrouvait couvert de paires de traits parallèles.
//
// CE QUI EST FUSIONNÉ, ET CE QUI NE L'EST PAS
//
// Le modèle dit fidèlement ce que chaque classe déclare ; la fusion a donc lieu au rendu, et
// non à l'extraction. Elle est de surcroît prudente : une extrémité source ne sait porter qu'un
// rôle et une multiplicité. Tout ce que le second lien dirait de plus — un libellé, un losange,
// une contrainte, une barre de dérivation — se perdrait en route. On ne fusionne donc que
// lorsque le second lien n'ajoute rien d'autre qu'un rôle et une multiplicité ; sinon les deux
// traits restent, et l'utilisateur en est averti.
//
// Perdre une information sans le dire serait le seul vrai défaut ; en dessiner deux traits est
// simplement moins joli.
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

        // Les associations dont un partenaire a déjà pris la charge : elles ne seront pas
        // écrites une seconde fois. La clef désigne l'exemplaire précis, pas la paire de classes
        // — deux attributs distincts vers la même cible restent deux traits.
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
                    // Une cible hors du diagramme n'a rien à dire de son côté ; une association
                    // d'une classe vers elle-même n'a pas de « face » distincte.
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
                    // Marquées comme traitées, pour que la paire ne soit pas réexaminée depuis
                    // l'autre bout et l'avertissement dit deux fois.
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

    // L'association de « enFace » qui vise « nomSource », et qu'aucune paire n'a déjà prise.
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

    // Le second lien n'ajoute-t-il rien qu'une extrémité source sache porter ?
    //
    // Il faut que le partenaire soit une association nue — ni losange, ni libellé, ni contrainte,
    // ni dérivation — et que le lien conservé n'ait pas déjà écrit son propre côté source, qui
    // l'emporterait alors sur ce que dit l'autre.
    private static boolean fusionnable(UmlAssociation gardee, UmlAssociation partenaire) {
        return partenaire.type() == UMLRelationType.ASSOCIATION
            && partenaire.label().isEmpty()
            && partenaire.constraints().isEmpty()
            && !partenaire.derived()
            && gardee.sourceRole().isEmpty()
            && gardee.sourceCardinality().isEmpty();
    }

    // Le lien conservé, dont l'extrémité source reçoit ce que la classe d'en face dit d'elle.
    //
    // C'est exactement la lecture UML : le rôle d'une extrémité est le nom sous lequel l'autre
    // bout la désigne. « B.a » nomme donc l'extrémité A du trait.
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
