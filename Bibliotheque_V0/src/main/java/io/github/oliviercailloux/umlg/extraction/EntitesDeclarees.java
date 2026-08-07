package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.extraction.rules.ClasseDeclaree;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.model.UmlInheritanceType;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Fabrique les boîtes que le fichier de règles déclare, et pose les liens qu'il décrit.
//
// CE QUE CETTE CLASSE PERMET, ET CE QU'ELLE COÛTE
//
// Elle ouvre le diagramme à ce que le code ne dit pas : une classe encore à écrire, une
// abstraction que le code réalise autrement. C'est un besoin réel — les diagrammes tenus à la
// main le font couramment — et le refuser reviendrait à interdire de dessiner ce qu'on a en tête.
//
// C'est aussi la seule partie de la bibliothèque qui puisse mentir. Une boîte déclarée ici
// n'est vérifiée par aucun compilateur, ne suit aucun renommage, et rien ne signalera le jour où
// le code aura pris un autre chemin. La contrepartie est qu'elle se voit : stéréotype
// « declared » et contour pointillé, décidés au rendu et non négociables.
//
// LES NOMS
//
// Une boîte déclarée n'a pas de paquetage : son nom qualifié est son nom simple. Les liens
// désignent leurs extrémités par le nom qu'on lit sur le dessin, réelle ou déclarée — c'est le
// seul nom que l'auteur du fichier connaisse.
public final class EntitesDeclarees {

    private EntitesDeclarees() {}

    // Les boîtes déclarées, prêtes à rejoindre celles du code.
    //
    // Les entités réelles sont passées pour résoudre les liens : « declare association Animal ->
    // Chien » peut viser une classe du code aussi bien qu'une autre boîte déclarée.
    public static ImmutableList<UmlEntity> de(ImmutableSet<UmlEntity> reelles, Regles regles) {
        checkNotNull(reelles);
        checkNotNull(regles);

        if (regles.classesDeclarees().isEmpty() && regles.liensDeclares().isEmpty()) {
            return ImmutableList.of();
        }

        // Nom affiché -> nom complet, pour toutes les boîtes que les liens peuvent viser.
        Map<String, String> parNom = new LinkedHashMap<>();
        for (UmlEntity reelle : reelles) {
            parNom.put(reelle.name(), reelle.qualifiedName());
        }
        for (ClasseDeclaree declaree : regles.classesDeclarees()) {
            parNom.put(declaree.nom(), declaree.nom());
        }

        // Les relations que chaque boîte déclarée portera, accumulées avant de la construire :
        // un record est immuable, et il vaut mieux le bâtir une fois complet.
        Map<String, List<UmlRelation>> relations = new LinkedHashMap<>();
        for (ClasseDeclaree declaree : regles.classesDeclarees()) {
            relations.put(declaree.nom(), new ArrayList<>());
            if (declaree.aUnParent()) {
                relations.get(declaree.nom()).add(new UmlInheritance(
                    resoudre(parNom, declaree.parent(), "declare class " + declaree.nom()),
                    UmlInheritanceType.GENERALIZATION));
            }
        }

        for (Regles.LienDeclare lien : regles.liensDeclares()) {
            String contexte = "declare association " + lien.source() + " -> " + lien.cible();
            String source = resoudre(parNom, lien.source(), contexte);
            String cible = resoudre(parNom, lien.cible(), contexte);

            // Le lien part de la boîte source, qu'elle soit déclarée ou réelle. Une source réelle
            // ne peut pas recevoir de relation ici — son entité est déjà construite —, ce que le
            // message explique plutôt que de le laisser deviner.
            List<UmlRelation> portees = relations.get(lien.source());
            checkArgument(portees != null,
                "%s: the source must be a declared class; %s comes from the code, and a rule "
                    + "cannot add a link to it. Declare the link on the other side, or use "
                    + "@UMLAssociation in the code.",
                contexte, lien.source());

            portees.add(UmlAssociation.builder(lien.source(), cible)
                .targetCardinality(lien.cardinalite()).build());
            // La variable « source » ne sert qu'à vérifier que la source existe : le lien est
            // porté par la boîte, qui connaît déjà son propre nom.
            assert source != null;
        }

        ImmutableList.Builder<UmlEntity> boites = ImmutableList.builder();
        for (ClasseDeclaree declaree : regles.classesDeclarees()) {
            boites.add(UmlEntity.builder(declaree.nom(), declaree.nom())
                .declared(true)
                .fields(champs(declaree))
                .relations(relations.get(declaree.nom()))
                .build());
        }
        return boites.build();
    }

    // Les attributs d'une boîte déclarée : un nom, un type, et rien d'autre.
    //
    // Ni visibilité ni multiplicité : ce qui n'existe pas dans le code n'a pas d'encapsulation à
    // montrer, et une visibilité inventée serait une affirmation de plus que rien ne garantit.
    private static ImmutableList<UmlField> champs(ClasseDeclaree declaree) {
        ImmutableList.Builder<UmlField> champs = ImmutableList.builder();
        for (ClasseDeclaree.ChampDeclare champ : declaree.champs()) {
            champs.add(new UmlField(champ.nom(), champ.type(), "", "", false, false,
                ImmutableList.of()));
        }
        return champs.build();
    }

    // Le nom complet de la boîte portant ce nom affiché.
    private static String resoudre(Map<String, String> parNom, String nom, String contexte) {
        String complet = parNom.get(nom);
        checkArgument(complet != null,
            "%s: \"%s\" is neither a class of the diagram nor a declared one. Check the spelling, "
                + "or declare it with \"declare class %s\".",
            contexte, nom, nom);

        return complet;
    }
}
