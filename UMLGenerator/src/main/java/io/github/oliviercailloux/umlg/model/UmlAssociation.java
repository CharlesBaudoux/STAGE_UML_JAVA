package io.github.oliviercailloux.umlg.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import java.util.List;

/**
 * Un trait entre deux boîtes : la traduction d'un attribut annoté {@code @UMLAssociation}.
 *
 * <p>L'entité qui porte cette association en est la source ; la cible est désignée par son nom
 * complet, et non par une référence à l'objet {@link UmlEntity} — le modèle reste ainsi
 * constructible dans n'importe quel ordre, sans cycle de dépendance entre entités.
 *
 * @param fieldName le nom de l'attribut qui porte le lien. Ni nul ni vide.
 * @param targetQualifiedName le nom complet de la boîte d'arrivée. Ni nul ni vide.
 * @param type la nature du lien : association simple, agrégation ou composition.
 * @param sourceMultiplicity la multiplicité écrite du côté de la source, ou la chaîne vide.
 * @param targetMultiplicity la multiplicité écrite du côté de la cible, ou la chaîne vide.
 * @param sourceRole le rôle écrit du côté de la source, ou la chaîne vide.
 * @param targetRole le rôle écrit du côté de la cible, ou la chaîne vide.
 * @param label le texte posé au milieu du trait, ou la chaîne vide.
 * @param direction le sens de lecture imposé au trait, ou {@code NONE}.
 * @param derived un lien dérivé : la propriété qu'il porte se calcule au lieu d'être stockée. La
 *     barre oblique se place devant le rôle cible, qui est le nom de cette propriété sur le
 *     dessin.
 * @param constraints les règles formelles portées par le lien, sans leurs accolades —
 *     {@code subset}, {@code ordered}. Une contrainte écrite sur l'attribut atterrit ici dès lors
 *     qu'il porte {@code @UMLAssociation} : c'est le lien qu'elle vise, et non la ligne dans la
 *     boîte, qui n'existe plus. {@code ordered} n'a pas de champ à lui : c'est une contrainte UML
 *     comme une autre.
 */
public record UmlAssociation(

    String fieldName,
    String targetQualifiedName,
    UMLRelationType type,
    String sourceMultiplicity,
    String targetMultiplicity,
    String sourceRole,
    String targetRole,
    String label,
    UMLDirection direction,
    boolean derived,
    ImmutableList<String> constraints
) implements UmlRelation {

    /**
     * Toutes les vérifications sont ici : quel que soit le chemin de construction, une
     * {@code UmlAssociation} qui existe est une association valide.
     *
     * @throws NullPointerException si l'une des composantes est nulle ; l'absence d'un texte
     *     facultatif se dit avec la chaîne vide.
     * @throws IllegalArgumentException si le nom de l'attribut ou celui de la cible est vide, si
     *     une multiplicité n'est pas une multiplicité UML — un entier, {@code *}, ou un
     *     intervalle tel que {@code 0..1} dont la borne basse ne dépasse pas la haute —, ou si un
     *     texte libre n'est fait que d'espaces.
     */
    public UmlAssociation {
        checkNotNull(fieldName, "The name of the carrying field cannot be null.");
        checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        checkNotNull(type, "The relation type cannot be null.");
        checkNotNull(sourceMultiplicity,
            "The source multiplicity cannot be null (use an empty string).");
        checkNotNull(targetMultiplicity,
            "The target multiplicity cannot be null (use an empty string).");
        checkNotNull(sourceRole, "The source role cannot be null (use an empty string).");
        checkNotNull(targetRole, "The target role cannot be null (use an empty string).");
        checkNotNull(label, "The label cannot be null (use an empty string).");
        checkNotNull(direction, "The direction cannot be null (use UMLDirection.NONE).");

        checkArgument(!fieldName.isBlank(), "The name of the carrying field cannot be empty.");
        checkArgument(!targetQualifiedName.isBlank(), "The target qualified name cannot be empty.");

        Multiplicites.valider(sourceMultiplicity, "sourceMultiplicity");
        Multiplicites.valider(targetMultiplicity, "targetMultiplicity");

        validateTexteLibre(sourceRole, "sourceRole");
        validateTexteLibre(targetRole, "targetRole");
        validateTexteLibre(label, "label");

        constraints = Contraintes.validees(constraints);
    }

    /**
     * Ouvre la construction d'un lien, en donnant les deux seules valeurs obligatoires.
     *
     * @param fieldName le nom de l'attribut qui porte le lien.
     * @param targetQualifiedName le nom complet de la boîte d'arrivée.
     * @return un builder neuf, dont tout le reste vaut « rien de particulier ».
     */
    public static Builder builder(String fieldName, String targetQualifiedName) {
        return new Builder(fieldName, targetQualifiedName);
    }

    private static void validateTexteLibre(String valeur, String parametre) {
        checkArgument(valeur.isEmpty() || !valeur.isBlank(),
            "%s cannot be made of whitespace only.", parametre);
    }

    /**
     * De quoi construire un lien sans nommer ce qu'il n'a pas.
     *
     * <p>Neuf des onze composantes sont optionnelles. Chaque setter rend le builder, ce qui
     * permet d'enchaîner les appels ; la validation, elle, n'a lieu qu'au {@link #build()}.
     */
    public static final class Builder {

        private final String fieldName;
        private final String targetQualifiedName;
        private UMLRelationType type = UMLRelationType.ASSOCIATION;
        private String sourceMultiplicity = "";
        private String targetMultiplicity = "";
        private String sourceRole = "";
        private String targetRole = "";
        private String label = "";
        private UMLDirection direction = UMLDirection.NONE;
        private boolean derived = false;
        private ImmutableList<String> constraints = ImmutableList.of();

        private Builder(String fieldName, String targetQualifiedName) {
            this.fieldName =
                checkNotNull(fieldName, "The name of the carrying field cannot be null.");
            this.targetQualifiedName =
                checkNotNull(targetQualifiedName, "The target qualified name cannot be null.");
        }

        /**
         * Fixe la nature du lien.
         *
         * @param type association simple, agrégation ou composition.
         * @return ce builder.
         */
        public Builder type(UMLRelationType type) {
            this.type = checkNotNull(type);
            return this;
        }

        /**
         * Fixe la multiplicité du côté de la source.
         *
         * @param multiplicity la multiplicité du côté de la source, par exemple {@code 1}.
         * @return ce builder.
         */
        public Builder sourceMultiplicity(String multiplicity) {
            this.sourceMultiplicity = checkNotNull(multiplicity);
            return this;
        }

        /**
         * Fixe la multiplicité du côté de la cible.
         *
         * @param multiplicity la multiplicité du côté de la cible, par exemple {@code 0..*}.
         * @return ce builder.
         */
        public Builder targetMultiplicity(String multiplicity) {
            this.targetMultiplicity = checkNotNull(multiplicity);
            return this;
        }

        /**
         * Fixe le rôle du côté de la source.
         *
         * @param role le rôle écrit du côté de la source.
         * @return ce builder.
         */
        public Builder sourceRole(String role) {
            this.sourceRole = checkNotNull(role);
            return this;
        }

        /**
         * Fixe le rôle du côté de la cible.
         *
         * @param role le rôle écrit du côté de la cible.
         * @return ce builder.
         */
        public Builder targetRole(String role) {
            this.targetRole = checkNotNull(role);
            return this;
        }

        /**
         * Fixe le texte posé sur le trait.
         *
         * @param label le texte posé au milieu du trait.
         * @return ce builder.
         */
        public Builder label(String label) {
            this.label = checkNotNull(label);
            return this;
        }

        /**
         * Fixe le sens de lecture du trait.
         *
         * @param direction le sens de lecture imposé au trait.
         * @return ce builder.
         */
        public Builder direction(UMLDirection direction) {
            this.direction = checkNotNull(direction);
            return this;
        }

        /**
         * Marque le lien comme dérivé.
         *
         * @param derived vrai si la propriété portée se calcule au lieu d'être stockée.
         * @return ce builder.
         */
        public Builder derived(boolean derived) {
            this.derived = derived;
            return this;
        }

        /**
         * Remplace les contraintes du lien.
         *
         * @param constraints les règles formelles portées par le lien, sans accolades.
         * @return ce builder.
         */
        public Builder constraints(List<String> constraints) {
            this.constraints = ImmutableList.copyOf(checkNotNull(constraints));
            return this;
        }

        /**
         * Le figeage : le constructeur compact valide l'ensemble.
         *
         * @return le lien, immuable et vérifié.
         */
        public UmlAssociation build() {
            return new UmlAssociation(fieldName, targetQualifiedName, type, sourceMultiplicity,
                targetMultiplicity, sourceRole, targetRole, label, direction, derived, constraints);
        }
    }
}
