package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;

/**
 * Une boîte du diagramme : la traduction d'une classe annotée {@code @UMLClass}.
 *
 * <p>Record, donc immuable : une fois construite, l'entité ne peut plus violer ses invariants.
 * Les listes sont recopiées en {@code ImmutableList} à la construction, pour qu'une modification
 * ultérieure de la liste passée par l'appelant ne puisse pas atteindre l'entité.
 *
 * <p>Douze de ses quatorze composantes sont optionnelles ; le {@link Builder} évite d'avoir à
 * passer partout des chaînes et des listes vides.
 *
 * @param qualifiedName nom complet (paquetage inclus) : l'identité de l'entité, ce par quoi les
 *     relations la désignent. Deux classes de même nom simple dans deux paquetages restent
 *     distinctes. Ni nul ni vide.
 * @param name nom affiché dans la boîte, éventuellement redéfini par
 *     {@code @UMLClass(name = ...)}.
 * @param kind classe, interface, énumération… : ce que dit le mot-clé Java, déduit et non
 *     déclaré.
 * @param stereotype le mot entre guillemets français affiché au-dessus du nom, ou la chaîne vide.
 * @param color la couleur de remplissage de la boîte, ou la chaîne vide.
 * @param strategy la stratégie de visibilité retenue pour cette classe. Conservée jusqu'ici bien
 *     qu'elle ait déjà servi au filtrage des membres : le modèle garde ainsi trace de ce qui a
 *     été demandé.
 * @param literals les littéraux d'une énumération. Ils ne sont pas des attributs : en UML un
 *     {@code EnumerationLiteral} n'a ni visibilité ni type, il ne porte que son nom. D'où une
 *     liste de chaînes, et non de {@link UmlField} — une énumération peut d'ailleurs avoir les
 *     deux à la fois. Vide pour toute entité qui n'est pas une énumération.
 * @param fields le compartiment des attributs.
 * @param methods le compartiment des méthodes.
 * @param relations les liens qui partent de cette entité : associations, dépendances, héritages,
 *     classes d'association et placements mélangés dans une seule liste, filtrés à la demande par
 *     {@link #relations(Class)}.
 * @param notes les bulles de commentaire, celles de la classe comme celles de ses membres : une
 *     note n'est pas un morceau de la boîte, mais un objet posé à côté qui la désigne.
 * @param constraints les règles formelles portées par la classe entière, sans leurs accolades.
 * @param group le rectangle nommé dans lequel la boîte se range, ou {@link UmlPackage#aucun()} si
 *     elle se dessine au premier niveau. Vient de {@code @UMLPackage}, et ne dit rien du modèle :
 *     deux diagrammes qui ne différeraient que par leurs groupes disent la même chose.
 * @param typeParameters les paramètres de type de la classe, chevrons compris — {@code <T>},
 *     {@code <K, V extends Number>} — ou la chaîne vide. À part du nom, et non collés dedans,
 *     parce que les deux ne servent pas à la même chose : le nom désigne la boîte — c'est par lui
 *     qu'une vue la nomme, et l'alias s'en déduit —, tandis que ceci ne fait que s'afficher. Les
 *     coller aurait rendu {@code view "X" = Boite} introuvable dès que {@code Boite} devient
 *     générique.
 */
public record UmlEntity(
    String qualifiedName,
    String name,
    UmlEntityKind kind,
    String stereotype,
    String color,
    UMLVisibility strategy,
    ImmutableList<String> literals,
    ImmutableList<UmlField> fields,
    ImmutableList<UmlMethod> methods,
    ImmutableList<UmlRelation> relations,
    ImmutableList<UmlNote> notes,
    ImmutableList<String> constraints,
    UmlPackage group,
    String typeParameters
) {
    /**
     * Constructeur compact : s'exécute avant l'affectation des champs, quel que soit le chemin de
     * construction (builder, appel direct, test). C'est ici, et nulle part ailleurs, que les
     * invariants sont tenus.
     *
     * @throws NullPointerException si l'une des composantes est nulle ; l'absence d'un texte
     *     facultatif se dit avec la chaîne vide, jamais avec {@code null}.
     * @throws IllegalArgumentException si le nom complet est vide, ou si une contrainte est mal
     *     formée.
     */
    public UmlEntity {
        checkNotNull(name, "The entity name cannot be null.");
        checkNotNull(qualifiedName, "The entity qualified name cannot be null.");
        checkNotNull(kind, "The entity kind cannot be null (use UmlEntityKind.CLASS).");
        checkNotNull(stereotype, "The stereotype cannot be null (use an empty string).");
        checkNotNull(color, "The color cannot be null (use an empty string).");
        checkNotNull(strategy, "The visibility strategy cannot be null.");
        checkArgument(!qualifiedName.isBlank(), "The entity qualified name cannot be empty.");

        literals = ImmutableList.copyOf(
            checkNotNull(literals, "The list of literals cannot be null."));
        fields = ImmutableList.copyOf(
            checkNotNull(fields, "The list of fields cannot be null."));
        methods = ImmutableList.copyOf(
            checkNotNull(methods, "The list of methods cannot be null."));
        relations = ImmutableList.copyOf(
            checkNotNull(relations, "The list of relations cannot be null."));
        notes = ImmutableList.copyOf(
            checkNotNull(notes, "The list of notes cannot be null."));
        constraints = Contraintes.validees(constraints);
        checkNotNull(group, "The group cannot be null (use UmlPackage.aucun()).");
        checkNotNull(typeParameters, "The type parameters cannot be null (use \"\").");
    }

    /**
     * Raccourci pour les cas sans relation, où le nom simple sert aussi de nom complet.
     *
     * <p>La nature n'est pas demandée : ce raccourci sert aux cas ordinaires, et une classe est
     * le cas ordinaire.
     *
     * @param name le nom, qui sert aussi de nom complet.
     * @param stereotype le stéréotype, ou la chaîne vide.
     * @param color la couleur de remplissage, ou la chaîne vide.
     * @param strategy la stratégie de visibilité retenue.
     * @param fields le compartiment des attributs.
     * @param methods le compartiment des méthodes.
     */
    public UmlEntity(String name, String stereotype, String color, UMLVisibility strategy,
        List<UmlField> fields, List<UmlMethod> methods) {
        this(name, name, UmlEntityKind.CLASS, stereotype, color, strategy,
            ImmutableList.of(),
            ImmutableList.copyOf(
                checkNotNull(fields, "The list of fields cannot be null.")),
            ImmutableList.copyOf(
                checkNotNull(methods, "The list of methods cannot be null.")),
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), UmlPackage.aucun(),
            "");
    }

    /**
     * Rend les relations d'une nature donnée.
     *
     * <p>Le paramètre de type {@code T} suit celui du {@code Class<T>} passé en argument, si bien
     * que l'appelant récupère une liste déjà typée, sans transtypage.
     *
     * @param <T> la nature de relation cherchée.
     * @param nature la classe de cette nature, par exemple {@code UmlAssociation.class}.
     * @return les relations de cette nature, dans l'ordre où elles ont été ajoutées ; vide si
     *     l'entité n'en porte aucune.
     */
    public <T extends UmlRelation> ImmutableList<T> relations(Class<T> nature) {
        checkNotNull(nature);
        return relations.stream()
                        .filter(nature::isInstance)
                        .map(nature::cast)
                        .collect(ImmutableList.toImmutableList());
    }

    /**
     * Les traits pleins ou losangés qui partent de cette entité.
     *
     * @return ses associations, vide si elle n'en porte aucune.
     */
    public ImmutableList<UmlAssociation> associations() {
        return relations(UmlAssociation.class);
    }

    /**
     * Les flèches pointillées qui partent de cette entité.
     *
     * @return ses dépendances, vide si elle n'en porte aucune.
     */
    public ImmutableList<UmlDependency> dependencies() {
        return relations(UmlDependency.class);
    }

    /**
     * Les {@code extends} et {@code implements} de cette entité.
     *
     * @return ses liens d'héritage, vide si elle n'en porte aucun.
     */
    public ImmutableList<UmlInheritance> inheritances() {
        return relations(UmlInheritance.class);
    }

    /**
     * Les contraintes de placement, qui ne dessinent aucun trait.
     *
     * @return ses placements, vide si elle n'en porte aucun.
     */
    public ImmutableList<UmlLayout> layouts() {
        return relations(UmlLayout.class);
    }

    /**
     * La classe qui qualifie un lien partant de cette entité.
     *
     * @return au plus un élément : {@code @UMLAssociationClass} n'est pas répétable, une classe
     *     ne qualifiant qu'un lien. La liste reste néanmoins le type rendu, pour que tous ces
     *     accesseurs se lisent pareil.
     */
    public ImmutableList<UmlAssociationClass> associationClasses() {
        return relations(UmlAssociationClass.class);
    }

    /**
     * Ouvre la construction d'une entité, en donnant les deux seules valeurs obligatoires.
     *
     * @param qualifiedName le nom complet, paquetage inclus.
     * @param name le nom affiché dans la boîte.
     * @return un builder neuf, dont tout le reste vaut « rien de particulier ».
     */
    public static Builder builder(String qualifiedName, String name) {
        return new Builder(qualifiedName, name);
    }

    /**
     * De quoi construire une entité sans nommer ce qu'elle n'a pas.
     *
     * <p>Il existe parce que l'entité a quatorze composantes dont douze optionnelles : appeler le
     * constructeur directement obligerait à passer partout des chaînes vides et des listes vides.
     * Chaque setter rend le builder, ce qui permet d'enchaîner les appels.
     */
    public static final class Builder {

        private final String qualifiedName;
        private final String name;
        private UmlEntityKind kind = UmlEntityKind.CLASS;
        private String stereotype = "";
        private String color = "";
        private UMLVisibility strategy = UMLVisibility.NONE;
        private ImmutableList<String> literals = ImmutableList.of();
        private ImmutableList<UmlField> fields = ImmutableList.of();
        private ImmutableList<UmlMethod> methods = ImmutableList.of();
        private ImmutableList<UmlNote> notes = ImmutableList.of();
        private ImmutableList<String> constraints = ImmutableList.of();
        private UmlPackage group = UmlPackage.aucun();
        private boolean declared;
        private String typeParameters = "";
        private final ImmutableList.Builder<UmlRelation> relations = ImmutableList.builder();

        private Builder(String qualifiedName, String name) {
            this.qualifiedName =
                checkNotNull(qualifiedName, "The entity qualified name cannot be null.");
            this.name = checkNotNull(name, "The entity name cannot be null.");
        }

        /**
         * Fixe la nature de la boîte.
         *
         * @param kind classe, interface, énumération… Par défaut, une classe ordinaire.
         * @return ce builder.
         */
        public Builder kind(UmlEntityKind kind) {
            this.kind = checkNotNull(kind);
            return this;
        }

        /**
         * Fixe le stéréotype.
         *
         * @param stereotype le mot affiché entre guillemets français au-dessus du nom.
         * @return ce builder.
         */
        public Builder stereotype(String stereotype) {
            this.stereotype = checkNotNull(stereotype);
            return this;
        }

        /**
         * Fixe la couleur de remplissage.
         *
         * @param color la couleur de remplissage de la boîte.
         * @return ce builder.
         */
        public Builder color(String color) {
            this.color = checkNotNull(color);
            return this;
        }

        /**
         * Fixe la stratégie de visibilité.
         *
         * @param strategy la stratégie de visibilité retenue pour cette classe.
         * @return ce builder.
         */
        public Builder strategy(UMLVisibility strategy) {
            this.strategy = checkNotNull(strategy);
            return this;
        }

        /**
         * Remplace les littéraux d'énumération.
         *
         * @param literals les littéraux, si l'entité est une énumération.
         * @return ce builder.
         */
        public Builder literals(List<String> literals) {
            this.literals = ImmutableList.copyOf(checkNotNull(literals));
            return this;
        }

        /**
         * Remplace le compartiment des attributs.
         *
         * @param fields le compartiment des attributs, qui remplace le précédent.
         * @return ce builder.
         */
        public Builder fields(List<UmlField> fields) {
            this.fields = ImmutableList.copyOf(checkNotNull(fields));
            return this;
        }

        /**
         * Remplace le compartiment des méthodes.
         *
         * @param methods le compartiment des méthodes, qui remplace le précédent.
         * @return ce builder.
         */
        public Builder methods(List<UmlMethod> methods) {
            this.methods = ImmutableList.copyOf(checkNotNull(methods));
            return this;
        }

        /**
         * Remplace les notes.
         *
         * @param notes les bulles de commentaire attachées à la classe ou à ses membres.
         * @return ce builder.
         */
        public Builder notes(List<UmlNote> notes) {
            this.notes = ImmutableList.copyOf(checkNotNull(notes));
            return this;
        }

        /**
         * Remplace les contraintes de la classe.
         *
         * @param constraints les règles formelles portées par la classe entière, sans accolades.
         * @return ce builder.
         */
        public Builder constraints(List<String> constraints) {
            this.constraints = ImmutableList.copyOf(checkNotNull(constraints));
            return this;
        }

        /**
         * Range la boîte dans un rectangle nommé.
         *
         * @param group le rectangle nommé où ranger la boîte.
         * @return ce builder.
         */
        public Builder group(UmlPackage group) {
            this.group = checkNotNull(group);
            return this;
        }

        /**
         * Ajoute des relations à celles déjà accumulées.
         *
         * <p>Contrairement aux autres, cette méthode ajoute au lieu de remplacer : deux appels
         * successifs cumulent leurs relations.
         *
         * @param relations les liens à ajouter, de n'importe quelle nature.
         * @return ce builder.
         */
        public Builder relations(List<? extends UmlRelation> relations) {
            this.relations.addAll(checkNotNull(relations, "The list of relations cannot be null."));
            return this;
        }

        /**
         * Un nom plus parlant côté appelant ; techniquement le même ajout que
         * {@link #relations(List)}.
         *
         * @param associations les associations à ajouter.
         * @return ce builder.
         */
        public Builder associations(List<UmlAssociation> associations) {
            return relations(associations);
        }

        /**
         * Un nom plus parlant côté appelant ; techniquement le même ajout que
         * {@link #relations(List)}.
         *
         * @param dependencies les dépendances à ajouter.
         * @return ce builder.
         */
        public Builder dependencies(List<UmlDependency> dependencies) {
            return relations(dependencies);
        }

        /**
         * Fixe les paramètres de type.
         *
         * @param typeParameters les paramètres de type de la classe générique, chevrons compris.
         * @return ce builder.
         */
        public Builder typeParameters(String typeParameters) {
            this.typeParameters = checkNotNull(typeParameters);
            return this;
        }

        /**
         * Le figeage : la liste mutable devient immuable, et le constructeur compact revalide
         * l'ensemble.
         *
         * @return l'entité, immuable et vérifiée.
         */
        public UmlEntity build() {
            return new UmlEntity(qualifiedName, name, kind, stereotype, color, strategy, literals,
                fields, methods, relations.build(), notes, constraints, group,
                typeParameters);
        }
    }
}
