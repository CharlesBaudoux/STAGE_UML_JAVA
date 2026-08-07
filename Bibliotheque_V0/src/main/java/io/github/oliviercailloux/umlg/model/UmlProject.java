package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;

// Le diagramme entier : l'ensemble des boîtes, chacune portant ses propres relations, et les
// réglages qui valent pour le dessin dans son ensemble.
//
// Un Set et non une List : deux fois la même entité n'aurait aucun sens, et l'ordre du rendu
// est décidé plus tard par PlantUmlSyntaxBuilder. ImmutableSet conserve tout de même l'ordre
// d'insertion, ce qui rend la sortie reproductible d'une exécution à l'autre.
//
// Le second composant vient du fichier de règles — titre, sens de lecture, sort des
// compartiments vides. Il n'est jamais nul :
// un projet qui n'a rien déclaré porte UmlDiagram.defaut(), ce qui évite à tout le rendu d'avoir
// à distinguer « aucun réglage » de « les réglages ordinaires ».
public record UmlProject(ImmutableSet<UmlEntity> entities, UmlDiagram diagram,
    ImmutableList<UmlView> views) {

    // Point d'entrée commode depuis n'importe quel Set : la copie immuable est faite ici. Les
    // réglages d'ensemble sont ceux par défaut, ce qui décrit exactement un projet dont le
    // fichier de règles ne dit rien du dessin — le cas de loin le plus fréquent.
    public static UmlProject given(Set<UmlEntity> entities) {
        return given(entities, UmlDiagram.defaut());
    }

    // La même chose pour un projet dont un paquetage déclare ses réglages d'ensemble.
    public static UmlProject given(Set<UmlEntity> entities, UmlDiagram diagram) {
        return given(entities, diagram, List.of());
    }

    // La forme complète, celle qu'emploie le scan : les vues sont dérivées des entités et du
    // fichier de règles, et non déclarées à part.
    //
    // Une liste vide est acceptée et veut dire « ce projet ne connaît que son diagramme
    // d'ensemble » : c'est ce que rendent les deux fabriques ci-dessus, et ce qu'attendent les
    // appelants qui ne se soucient pas des vues.
    public static UmlProject given(Set<UmlEntity> entities, UmlDiagram diagram,
        List<UmlView> views) {
        checkNotNull(entities, "The set of entities cannot be null.");
        return new UmlProject(ImmutableSet.copyOf(entities), diagram,
            ImmutableList.copyOf(checkNotNull(views, "The list of views cannot be null.")));
    }

    public UmlProject {
        checkNotNull(entities, "The set of entities cannot be null.");
        checkNotNull(diagram, "The diagram settings cannot be null; use UmlDiagram.defaut().");
        checkNotNull(views, "The list of views cannot be null (use an empty list).");
        // Un projet vide signale presque toujours une erreur de l'utilisateur (mauvais
        // paquetage scanné, @UMLClass oubliée) ; échouer ici donne un message plus clair qu'un
        // diagramme vide livré sans un mot.
        checkArgument(!entities.isEmpty(),
            "The project must contain at least one annotated class to generate a diagram.");
    }

}
