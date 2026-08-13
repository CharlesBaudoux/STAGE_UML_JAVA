/**
 * La bibliothèque comme unité d'encapsulation.
 *
 * <p>Ce que ce fichier protège. Le code interne est découpé en sous-paquetages
 * (extraction.relation, rendering.syntax…) pour rester lisible. Java n'accordant aucun privilège
 * d'accès entre un paquetage et ses sous-paquetages, ce découpage oblige à déclarer public des
 * classes qui collaborent — AliasRegistry, MemberExtractor, PlantUmlEscaper… — et qui n'ont
 * pourtant aucune vocation à être appelées de l'extérieur.
 *
 * <p>La liste des exports ci-dessous rétablit la frontière au bon endroit : l'unité
 * d'encapsulation devient la bibliothèque entière, et non plus le paquetage. Un utilisateur qui
 * écrirait « import …umlg.rendering.syntax.AliasRegistry; » obtient une erreur de compilation.
 * Le mot-clé public redevient ce qu'il devrait être ici — « visible de mes collaborateurs » — et
 * exports porte seul le sens « visible de tous ».
 *
 * <p>La règle à tenir. Trois paquetages sont exportés, et ce sont exactement les trois que la
 * façade met en jeu : le point d'entrée, le vocabulaire que l'utilisateur écrit dans son code,
 * et le modèle qu'il peut inspecter. Ajouter un exports est un engagement de stabilité, jamais
 * une commodité pour débloquer un accès.
 */
module io.github.oliviercailloux.umlg {

    // Guava, pour les collections immuables et les Preconditions.
    //
    // « transitive », et c'est obligatoire : les trois paquetages exportés rendent des types
    // Guava dans leurs signatures publiques — RapportDeGeneration.classesIgnorees() une
    // ImmutableList, UmlProject une ImmutableSet, UMLGenerator.knownColors() une
    // ImmutableSortedSet. Sans le mot-clé, un module client lit ces méthodes sans pouvoir lire
    // leur type de retour, et échoue à la compilation sur « List.size() is defined in an
    // inaccessible class ». Exporter un type, c'est s'engager à ce qu'il soit utilisable.
    requires transitive com.google.common;
    // PlantUML, pour le dessin proprement dit.
    requires net.sourceforge.plantuml;
    // Jackson, pour lire le fichier de règles umlg.json.
    //
    // Pas « transitive », et la différence compte : aucun type Jackson ne paraît dans une
    // signature exportée — le fichier est lu à l'intérieur du paquetage extraction.rules, qui
    // n'en fait sortir que des Regles. Un module client n'a donc rien à savoir de Jackson.
    requires com.fasterxml.jackson.databind;
    // java.compiler : l'API des processeurs d'annotations (JSR 269), dont UmlgProcessor est une
    // implémentation. C'est aujourd'hui une dépendance de plein droit de la bibliothèque, et non
    // plus une simple commodité pour les tests d'intégration — lesquels s'en servent par
    // ailleurs pour compiler à la volée les projets cobayes qu'ils scannent ensuite.
    requires java.compiler;

    // UMLGenerator, la façade.
    exports io.github.oliviercailloux.umlg;

    // @UMLClass, @UMLAssociation… : ce que l'utilisateur écrit.
    exports io.github.oliviercailloux.umlg.annotation;

    // UmlProject, UmlEntity… : le diagramme tel qu'il est dit.
    exports io.github.oliviercailloux.umlg.model;

    // Le processeur d'annotations, offert au compilateur et à lui seul.
    //
    // « provides » suffit à le rendre trouvable sur le module path : il n'exige pas que le
    // paquetage soit exporté, et processing reste donc fermé comme les autres. Le doublon
    // META-INF/services couvre l'usage sur le classpath, où le module-info est ignoré.
    provides javax.annotation.processing.Processor
        with io.github.oliviercailloux.umlg.processing.UmlgProcessor;

    /*
     * Non exportés, et c'est le sujet : extraction, extraction.relation, extraction.member,
     * extraction.rules, processing, rendering, rendering.element, rendering.syntax, warning.
     *
     * Le dernier n'existe que pour cette ligne. Avertisseur.dire est le geste de l'extraction et
     * du rendu, jamais celui de l'utilisateur ; tant qu'il vivait dans la classe Avertissements,
     * du paquetage exporté, rien n'empêchait un appelant d'injecter ses propres messages dans le
     * compte rendu de la bibliothèque. Le déclarer dans une classe non exportée dont
     * Avertissements hériterait ne fermait rien — mesuré : un membre public hérité par une classe
     * publique d'un paquetage exporté reste accessible hors du module, l'appel se résolvant sur la
     * classe qui hérite et non sur celle qui déclare. Seul le déplacement ferme la porte.
     */
}
