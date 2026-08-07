package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Donne à chaque boîte un identifiant utilisable dans le texte PlantUML.
//
// Pourquoi un alias ? Le nom affiché peut contenir des espaces, des accents, des ponctuations —
// que PlantUML ne sait pas lire comme identifiant dans une ligne de relation. On écrit donc
// « class "Compte bancaire" as Comptebancaire », puis on ne désigne plus la boîte que par
// Comptebancaire.
//
// Ce registre a un état : il se souvient des alias déjà distribués. Une instance par diagramme,
// créée par PlantUmlSyntaxBuilder.
public final class AliasRegistry {

    private final Set<String> aliasUtilises = new HashSet<>();
    private final Map<String, String> aliasParNomQualifie = new HashMap<>();

    public String enregistrer(String nomQualifie, String nomAffiche) {
        checkNotNull(nomQualifie);
        checkNotNull(nomAffiche);
        checkArgument(!aliasParNomQualifie.containsKey(nomQualifie),
            "Class \"%s\" is declared twice in the same diagram: the associations targeting it "
                + "could not tell which of the two boxes to point at.", nomQualifie);

        String alias = alias(nomAffiche);
        aliasParNomQualifie.put(nomQualifie, alias);
        return alias;
    }

    public String aliasSource(String nomQualifie) {
        String alias = aliasParNomQualifie.get(checkNotNull(nomQualifie));
        checkState(alias != null,
            "Class \"%s\" has not been declared: call addClass on every entity before adding "
                + "their relations.", nomQualifie);
        return alias;
    }

    public String aliasCible(String nomQualifie, String quoi) {
        String alias = aliasParNomQualifie.get(checkNotNull(nomQualifie));
        checkArgument(alias != null,
            "%s targets \"%s\", which is not part of the diagram. Check that this class carries "
                + "@UMLClass and that it sits in the scanned directory.", quoi, nomQualifie);
        return alias;
    }

    private String alias(String nomAffiche) {
        String base = nomAffiche.replaceAll("[^a-zA-Z0-9]", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "C" + base;
        }

        String candidat = base;
        for (int suffixe = 2; !aliasUtilises.add(candidat); suffixe++) {
            candidat = base + "_" + suffixe;
        }
        return candidat;
    }
}
