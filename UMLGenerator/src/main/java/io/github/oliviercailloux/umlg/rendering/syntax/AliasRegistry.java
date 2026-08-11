package io.github.oliviercailloux.umlg.rendering.syntax;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import io.github.oliviercailloux.umlg.warning.Avertisseur;
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

    // Les alias déjà attribués, pour garantir l'unicité.
    private final Set<String> aliasUtilises = new HashSet<>();
    // Nom complet de la classe -> son alias. Le nom complet est l'identité ; c'est par lui que
    // les relations retrouvent leurs extrémités.
    private final Map<String, String> aliasParNomQualifie = new HashMap<>();
    // Nom affiché -> la première classe complète à l'avoir porté, pour dénoncer les homonymes.
    private final Map<String, String> nomsAffichesParNom = new HashMap<>();

    // Appelée une fois par entité, au moment de déclarer sa boîte.
    public String enregistrer(String nomQualifie, String nomAffiche) {
        checkNotNull(nomQualifie);
        checkNotNull(nomAffiche);
        // Vérifié avant de fabriquer l'alias : une inscription refusée ne doit rien laisser
        // derrière elle, ni dans la table, ni dans la réserve des alias distribués.
        checkArgument(!aliasParNomQualifie.containsKey(nomQualifie),
            "Class \"%s\" is declared twice in the same diagram: the associations targeting it "
                + "could not tell which of the two boxes to point at.", nomQualifie);

        // Deux classes de paquetages différents peuvent porter le même nom simple. Les alias, eux,
        // ne se marchent pas dessus — le suffixe « _2 » y pourvoit —, mais l'alias ne se lit pas
        // sur le dessin : le lecteur, lui, voit deux boîtes rigoureusement identiques, et les
        // traits qui y aboutissent ne lui disent pas laquelle est laquelle. Le diagramme est
        // exact et illisible, ce qui est la pire des combinaisons.
        //
        // Rien n'est renommé d'autorité : le nom affiché appartient à l'utilisateur, et
        // @UMLClass(name = ...) est là pour qu'il tranche. On le lui dit, et on nomme les deux
        // classes complètes pour qu'il sache lesquelles.
        String homonyme = nomsAffichesParNom.put(nomAffiche, nomQualifie);
        if (homonyme != null) {
            Avertisseur.dire("\"" + nomAffiche + "\" is the "
                + "name of two boxes of the same diagram (" + homonyme + " and " + nomQualifie
                + "). They are distinct classes, but nothing on the drawing tells them apart, "
                + "nor which one a link reaches. Rename one with @UMLClass(name = ...), or split "
                + "them into two views.");
        }

        String alias = alias(nomAffiche);
        aliasParNomQualifie.put(nomQualifie, alias);
        return alias;
    }

    // Alias de la classe dont on est en train d'écrire les relations. Une absence ici est une
    // faute de la bibliothèque — les deux passes de PlantUmlWriter ont été mal ordonnées —,
    // d'où checkState plutôt que checkArgument.
    public String aliasSource(String nomQualifie) {
        String alias = aliasParNomQualifie.get(checkNotNull(nomQualifie));
        checkState(alias != null,
            "Class \"%s\" has not been declared: call addClass on every entity before adding "
                + "their relations.", nomQualifie);
        return alias;
    }

    // Alias de la classe visée par une relation. Une absence ici est en revanche une faute de
    // l'utilisateur : il vise une classe hors du diagramme. Le paramètre « quoi » décrit la
    // relation fautive, pour que le message dise laquelle.
    public String aliasCible(String nomQualifie, String quoi) {
        String alias = aliasParNomQualifie.get(checkNotNull(nomQualifie));
        checkArgument(alias != null,
            "%s targets \"%s\", which is not part of the diagram. Check that this class carries "
                + "@UMLClass and that it sits in the scanned directory.", quoi, nomQualifie);
        return alias;
    }

    // Fabrique un alias à partir du nom affiché.
    private String alias(String nomAffiche) {
        // Ne garder que lettres et chiffres : « Compte bancaire » devient « Comptebancaire ».
        String base = nomAffiche.replaceAll("[^a-zA-Z0-9]", "");
        // Un identifiant doit commencer par une lettre. Le préfixe couvre aussi le cas d'un nom
        // entièrement fait de caractères écartés, où base serait vide.
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "C" + base;
        }

        // Deux noms différents peuvent donner la même base (« Compte-bancaire » et « Compte
        // bancaire ») : on suffixe alors _2, _3… jusqu'à trouver libre.
        // Set.add rend false si l'alias existait déjà : la boucle teste et réserve d'un coup.
        String candidat = base;
        for (int suffixe = 2; !aliasUtilises.add(candidat); suffixe++) {
            candidat = base + "_" + suffixe;
        }
        return candidat;
    }
}
