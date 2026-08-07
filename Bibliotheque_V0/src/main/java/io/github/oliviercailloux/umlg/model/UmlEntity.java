package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;

// Une boîte du diagramme : la traduction d'une classe annotée @UMLClass.
//
// Record, donc immuable : une fois construite, l'entité ne peut plus violer ses invariants. Les
// listes sont recopiées en ImmutableList à la construction, pour qu'une modification ultérieure
// de la liste passée par l'appelant ne puisse pas atteindre l'entité.
public record UmlEntity(
    // Nom complet (paquetage inclus) : l'identité de l'entité, ce par quoi les relations la
    // désignent. Deux classes de même nom simple dans deux paquetages restent distinctes.
    String qualifiedName,
    // Nom affiché dans la boîte, éventuellement redéfini par @UMLClass(name = ...).
    String name,
    // Classe, interface, énumération… : ce que dit le mot-clé Java, déduit et non déclaré.
    UmlEntityKind kind,
    String stereotype,
    String color,
    // Conservée jusqu'ici bien qu'elle ait déjà servi au filtrage des membres : le modèle garde
    // ainsi trace de ce qui a été demandé.
    UMLVisibility strategy,
    // Les littéraux d'une énumération. Ils ne sont pas des attributs : en UML un
    // EnumerationLiteral n'a ni visibilité ni type, il ne porte que son nom. D'où une liste de
    // chaînes, et non de UmlField — une énumération peut d'ailleurs avoir les deux à la fois.
    // Vide pour toute entité qui n'est pas une énumération.
    ImmutableList<String> literals,
    ImmutableList<UmlField> fields,
    ImmutableList<UmlMethod> methods,
    // Associations et dépendances mélangées dans une seule liste, filtrées à la demande par
    // relations(Class).
    ImmutableList<UmlRelation> relations,
    // Les bulles de commentaire, celles de la classe comme celles de ses membres : une note
    // n'est pas un morceau de la boîte, mais un objet posé à côté qui la désigne.
    ImmutableList<UmlNote> notes,
    // Les règles formelles portées par la classe entière, sans leurs accolades.
    ImmutableList<String> constraints,
    // Le rectangle nommé dans lequel la boîte se range, ou UmlPackage.aucun() si elle se dessine
    // au premier niveau. Vient de @UMLPackage, et ne dit rien du modèle : deux diagrammes qui ne
    // différeraient que par leurs groupes disent la même chose.
    UmlPackage group,
    // Vrai pour une boîte que le fichier de règles déclare, et qu'aucune classe Java ne porte.
    //
    // Le rendu la dessine en pointillés : elle échappe à tout ce qui fait la valeur du reste —
    // aucun compilateur ne la vérifie, aucun renommage ne la suit —, et le lecteur doit pouvoir
    // distinguer d'un coup d'œil ce qui a du code derrière de ce qui n'en a pas.
    boolean declared
) {
    // Constructeur compact : s'exécute avant l'affectation des champs, quel que soit le chemin
    // de construction (builder, appel direct, test). C'est ici, et nulle part ailleurs, que les
    // invariants sont tenus.
    public UmlEntity {
        checkNotNull(name, "The entity name cannot be null.");
        checkNotNull(qualifiedName, "The entity qualified name cannot be null.");
        checkNotNull(kind, "The entity kind cannot be null (use UmlEntityKind.CLASS).");
        // Les textes optionnels sont exigés non nuls : l'absence se dit avec la chaîne vide, ce
        // qui évite d'avoir à tester null partout en aval.
        checkNotNull(stereotype, "The stereotype cannot be null (use an empty string).");
        checkNotNull(color, "The color cannot be null (use an empty string).");
        checkNotNull(strategy, "The visibility strategy cannot be null.");
        checkArgument(!qualifiedName.isBlank(), "The entity qualified name cannot be empty.");

        // La recopie défensive : le paramètre est réaffecté, donc c'est bien la copie immuable
        // qui finit dans le champ.
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
    }

    // Raccourci pour les cas sans relation, où le nom simple sert aussi de nom complet. La
    // nature n'est pas demandée : ce raccourci sert aux cas ordinaires, et une classe est le
    // cas ordinaire.
    public UmlEntity(String name, String stereotype, String color, UMLVisibility strategy,
        List<UmlField> fields, List<UmlMethod> methods) {
        this(name, name, UmlEntityKind.CLASS, stereotype, color, strategy,
            ImmutableList.of(),
            ImmutableList.copyOf(
                checkNotNull(fields, "The list of fields cannot be null.")),
            ImmutableList.copyOf(
                checkNotNull(methods, "The list of methods cannot be null.")),
            ImmutableList.of(), ImmutableList.of(), ImmutableList.of(), UmlPackage.aucun(),
            false);
    }

    // Rend les relations d'une nature donnée. Le paramètre de type T suit celui du Class<T>
    // passé en argument, si bien que l'appelant récupère une liste déjà typée, sans transtypage.
    public <T extends UmlRelation> ImmutableList<T> relations(Class<T> nature) {
        checkNotNull(nature);
        return relations.stream()
                        .filter(nature::isInstance)
                        .map(nature::cast)
                        .collect(ImmutableList.toImmutableList());
    }

    public ImmutableList<UmlAssociation> associations() {
        return relations(UmlAssociation.class);
    }

    public ImmutableList<UmlDependency> dependencies() {
        return relations(UmlDependency.class);
    }

    public ImmutableList<UmlInheritance> inheritances() {
        return relations(UmlInheritance.class);
    }

    public ImmutableList<UmlLayout> layouts() {
        return relations(UmlLayout.class);
    }

    // Au plus une : @UMLAssociationClass n'est pas répétable, une classe ne qualifiant qu'un
    // lien. La liste reste néanmoins le type rendu, pour que tous ces accesseurs se lisent
    // pareil.
    public ImmutableList<UmlAssociationClass> associationClasses() {
        return relations(UmlAssociationClass.class);
    }

    public static Builder builder(String qualifiedName, String name) {
        return new Builder(qualifiedName, name);
    }

    // Le builder existe parce que l'entité a huit composantes dont six optionnelles : appeler le
    // constructeur directement obligerait à passer partout des chaînes vides et des listes vides.
    public static final class Builder {

        // Les deux seules données obligatoires : demandées à la création du builder, jamais
        // modifiables ensuite.
        private final String qualifiedName;
        private final String name;
        // Valeurs par défaut = « rien de particulier », cohérentes avec celles de @UMLClass.
        // Pour la nature, « rien de particulier » veut dire une classe ordinaire.
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
        // Un builder de Guava, et non une liste mutable : les relations s'ajoutent en plusieurs
        // fois — une passe par extracteur — et lui seul accumule sans jamais exposer d'état
        // modifiable, ni exiger une recopie au moment de figer.
        private final ImmutableList.Builder<UmlRelation> relations = ImmutableList.builder();

        private Builder(String qualifiedName, String name) {
            this.qualifiedName =
                checkNotNull(qualifiedName, "The entity qualified name cannot be null.");
            this.name = checkNotNull(name, "The entity name cannot be null.");
        }

        // Chaque setter rend le builder : c'est ce qui permet d'enchaîner les appels.
        public Builder kind(UmlEntityKind kind) {
            this.kind = checkNotNull(kind);
            return this;
        }

        public Builder stereotype(String stereotype) {
            this.stereotype = checkNotNull(stereotype);
            return this;
        }

        public Builder color(String color) {
            this.color = checkNotNull(color);
            return this;
        }

        public Builder strategy(UMLVisibility strategy) {
            this.strategy = checkNotNull(strategy);
            return this;
        }

        public Builder literals(List<String> literals) {
            this.literals = ImmutableList.copyOf(checkNotNull(literals));
            return this;
        }

        public Builder fields(List<UmlField> fields) {
            this.fields = ImmutableList.copyOf(checkNotNull(fields));
            return this;
        }

        public Builder methods(List<UmlMethod> methods) {
            this.methods = ImmutableList.copyOf(checkNotNull(methods));
            return this;
        }

        public Builder notes(List<UmlNote> notes) {
            this.notes = ImmutableList.copyOf(checkNotNull(notes));
            return this;
        }

        public Builder constraints(List<String> constraints) {
            this.constraints = ImmutableList.copyOf(checkNotNull(constraints));
            return this;
        }

        public Builder group(UmlPackage group) {
            this.group = checkNotNull(group);
            return this;
        }

        public Builder declared(boolean declared) {
            this.declared = declared;
            return this;
        }

        // Contrairement aux autres, cette méthode ajoute au lieu de remplacer : deux appels
        // successifs cumulent leurs relations.
        public Builder relations(List<? extends UmlRelation> relations) {
            // addAll refuse les éléments nuls comme la collection finale les refuserait : la
            // faute est signalée à l'ajout, où l'appelant sait encore ce qu'il ajoutait.
            this.relations.addAll(checkNotNull(relations, "The list of relations cannot be null."));
            return this;
        }

        // Deux noms plus parlants côté appelant ; techniquement le même ajout.
        public Builder associations(List<UmlAssociation> associations) {
            return relations(associations);
        }

        public Builder dependencies(List<UmlDependency> dependencies) {
            return relations(dependencies);
        }

        // Le figeage : la liste mutable devient immuable, et le constructeur compact revalide
        // l'ensemble.
        public UmlEntity build() {
            return new UmlEntity(qualifiedName, name, kind, stereotype, color, strategy, literals,
                fields, methods, relations.build(), notes, constraints, group, declared);
        }
    }
}
