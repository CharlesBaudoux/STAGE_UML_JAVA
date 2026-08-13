package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Le diagramme entier : l'ensemble des boîtes, chacune portant ses propres relations, et les
 * réglages qui valent pour le dessin dans son ensemble.
 *
 * @param entities les boîtes. Un {@code Set} et non une {@code List} : deux fois la même entité
 *     n'aurait aucun sens, et l'ordre du rendu est décidé plus tard. {@code ImmutableSet}
 *     conserve tout de même l'ordre d'insertion, ce qui rend la sortie reproductible d'une
 *     exécution à l'autre. Jamais vide, et sans deux entités de même
 *     {@link UmlEntity#qualifiedName()}.
 * @param diagram les réglages d'ensemble, venus du fichier de règles — titre, sens de lecture,
 *     sort des compartiments vides. Jamais nul : un projet qui n'a rien déclaré porte
 *     {@link UmlDiagram#defaults()}, ce qui évite à tout le rendu d'avoir à distinguer « aucun
 *     réglage » de « les réglages ordinaires ».
 * @param views les diagrammes partiels à produire en plus de la vue intégrale ; vide si le projet
 *     n'en déclare aucun.
 */
public record UmlProject(ImmutableSet<UmlEntity> entities, UmlDiagram diagram,
    ImmutableList<UmlView> views) {

    /**
     * Point d'entrée commode depuis n'importe quel {@code Set} : la copie immuable est faite ici.
     *
     * <p>Les réglages d'ensemble sont ceux par défaut, ce qui décrit exactement un projet dont le
     * fichier de règles ne dit rien du dessin — le cas de loin le plus fréquent.
     *
     * @param entities les boîtes du diagramme, au moins une.
     * @return le projet.
     */
    public static UmlProject given(Set<UmlEntity> entities) {
        return given(entities, UmlDiagram.defaults());
    }

    /**
     * La même chose pour un projet qui déclare ses réglages d'ensemble.
     *
     * @param entities les boîtes du diagramme, au moins une.
     * @param diagram les réglages d'ensemble.
     * @return le projet, sans vue.
     */
    public static UmlProject given(Set<UmlEntity> entities, UmlDiagram diagram) {
        return given(entities, diagram, List.of());
    }

    /**
     * La forme complète, celle qu'emploie le scan : les vues sont dérivées des entités et du
     * fichier de règles, et non déclarées à part.
     *
     * @param entities les boîtes du diagramme, au moins une.
     * @param diagram les réglages d'ensemble.
     * @param views les vues à produire. Une liste vide est acceptée et veut dire « ce projet ne
     *     connaît que son diagramme d'ensemble ».
     * @return le projet.
     */
    public static UmlProject given(Set<UmlEntity> entities, UmlDiagram diagram,
        List<UmlView> views) {
        checkNotNull(entities, "The set of entities cannot be null.");
        return new UmlProject(ImmutableSet.copyOf(entities), diagram,
            ImmutableList.copyOf(checkNotNull(views, "The list of views cannot be null.")));
    }

    /**
     * Vérifie les invariants du projet.
     *
     * @throws NullPointerException si l'une des composantes est nulle ; l'absence de vue se dit
     *     avec une liste vide, et l'absence de réglages avec {@link UmlDiagram#defaults()}.
     * @throws IllegalArgumentException si le projet ne contient aucune entité, ou si deux entités
     *     portent le même nom qualifié. Un projet vide signale presque toujours une erreur de
     *     l'utilisateur — mauvais paquetage scanné, {@code @UMLClass} oubliée — et échouer ici
     *     donne un message plus clair qu'un diagramme vide livré sans un mot.
     */
    public UmlProject {
        checkNotNull(entities, "The set of entities cannot be null.");
        checkNotNull(diagram, "The diagram settings cannot be null; use UmlDiagram.defaults().");
        checkNotNull(views, "The list of views cannot be null (use an empty list).");
        // Un projet vide signale presque toujours une erreur de l'utilisateur (mauvais
        // paquetage scanné, @UMLClass oubliée) ; échouer ici donne un message plus clair qu'un
        // diagramme vide livré sans un mot.
        checkArgument(!entities.isEmpty(),
            "The project must contain at least one annotated class to generate a diagram.");
        verifierLIdentite(entities);
    }

    // L'IDENTITÉ, TENUE ICI PLUTÔT QUE DÉCOUVERTE AU RENDU
    //
    // UmlEntity dit de qualifiedName qu'il est « l'identité de l'entité ». Mais un ImmutableSet
    // dédoublonne sur les quatorze composantes du record : deux entités de même nom qualifié qui
    // ne diffèrent que par une couleur y tenaient toutes les deux, et la faute ne se voyait qu'au
    // rendu — deux couches plus loin, sous la forme d'un alias en double —, ou pas du tout en
    // usage purement programmatique.
    //
    // La règle est donc écrite là où l'identité est définie. Le message nomme le doublon : c'est
    // la seule chose que l'appelant ait à corriger.
    private static void verifierLIdentite(ImmutableSet<UmlEntity> entities) {
        Set<String> vus = new HashSet<>();
        for (UmlEntity entite : entities) {
            checkArgument(vus.add(entite.qualifiedName()),
                "Two entities share the qualified name \"%s\", which is what identifies a box; "
                    + "the diagram would draw one of them and silently drop the other.",
                entite.qualifiedName());
        }
    }
}
