package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

// Le diagramme entier : l'ensemble des boîtes, chacune portant ses propres relations, et les
// réglages qui valent pour le dessin dans son ensemble.
//
// Un Set et non une List : deux fois la même entité n'aurait aucun sens, et l'ordre du rendu
// est décidé plus tard par PlantUmlSyntaxBuilder. ImmutableSet conserve tout de même l'ordre
// d'insertion, ce qui rend la sortie reproductible d'une exécution à l'autre.
//
// Le second composant vient de @UMLDiagram, déclarée sur un package-info. Il n'est jamais nul :
// un projet qui n'a rien déclaré porte UmlDiagram.defaut(), ce qui évite à tout le rendu d'avoir
// à distinguer « aucun réglage » de « les réglages ordinaires ».
public record UmlProject(ImmutableSet<UmlEntity> entities, UmlDiagram diagram) {

    // Point d'entrée commode depuis n'importe quel Set : la copie immuable est faite ici. Les
    // réglages d'ensemble sont ceux par défaut, ce qui décrit exactement un projet sans
    // @UMLDiagram — le cas de loin le plus fréquent.
    public static UmlProject given(Set<UmlEntity> entities) {
        return given(entities, UmlDiagram.defaut());
    }

    // La même chose pour un projet dont un paquetage déclare ses réglages d'ensemble.
    public static UmlProject given(Set<UmlEntity> entities, UmlDiagram diagram) {
        checkNotNull(entities, "The set of entities cannot be null.");
        return new UmlProject(ImmutableSet.copyOf(entities), diagram);
    }

    public UmlProject {
        checkNotNull(entities, "The set of entities cannot be null.");
        checkNotNull(diagram, "The diagram settings cannot be null; use UmlDiagram.defaut().");
        // Un projet vide signale presque toujours une erreur de l'utilisateur (mauvais
        // paquetage scanné, @UMLClass oubliée) ; échouer ici donne un message plus clair qu'un
        // diagramme vide livré sans un mot.
        checkArgument(!entities.isEmpty(),
            "The project must contain at least one annotated class to generate a diagram.");
    }

}
