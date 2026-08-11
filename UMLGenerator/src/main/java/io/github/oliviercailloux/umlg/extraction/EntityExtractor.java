package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationClassExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.AssociationExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.DependencyExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.InheritanceExtractor;
import io.github.oliviercailloux.umlg.extraction.relation.RelationExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlEntityKind;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.StringJoiner;

// Traduit une classe annotée en une UmlEntity : c'est le chef d'orchestre de l'extraction.
//
// Il ne fait presque rien lui-même. Il lit l'en-tête (@UMLClass), puis délègue : les membres à
// MemberExtractor, les relations aux RelationExtractor, la validation à ClassHeaderValidator.
public final class EntityExtractor {

    private static final ImmutableList<RelationExtractor> EXTRACTEURS = ImmutableList.of(
        new AssociationExtractor(),
        new DependencyExtractor(),
        new InheritanceExtractor(),
        new AssociationClassExtractor());

    private EntityExtractor() {}

    // Sans règles de projet : une classe extraite pour elle seule, telle que son code la décrit.
    public static UmlEntity extract(Class<?> clazz) {
        return extract(clazz, Regles.aucune());
    }

    // Avec les règles du projet, celles que ProjectScanner a lues une fois pour toutes. Elles ne
    // traversent l'extracteur que pour atteindre MemberExtractor : c'est le nom des types
    // affichés dans les boîtes qu'elles gouvernent, et rien d'autre.
    public static UmlEntity extract(Class<?> clazz, Regles regles) {
        checkNotNull(clazz);
        checkNotNull(regles);

        checkArgument(clazz.isAnnotationPresent(UMLClass.class),
            "Class %s does not carry the @UMLClass annotation.", clazz.getName());

        UMLClass annotation = clazz.getAnnotation(UMLClass.class);

        String finalName = annotation.name().isEmpty() ? clazz.getSimpleName() : annotation.name();
        String finalStereotype = annotation.stereotype();
        String finalColor = annotation.color();
        // La seule résolution de UMLVisibility.PROJECT de toute la bibliothèque : la classe dit
        // la sienne, ou s'en remet à celle du projet. Tout ce qui suit ne voit qu'une visibilité
        // concrète, et n'a donc pas à connaître l'existence du renvoi.
        UMLVisibility finalStrategy = regles.strategieDe(annotation.strategy());

        ClassHeaderValidator.validateName(finalName, clazz);
        ClassHeaderValidator.validateStereotype(finalStereotype, clazz);
        ClassHeaderValidator.validateColor(finalColor, clazz);

        // Les membres sont extraits avant la construction du builder : les notes ont besoin de
        // savoir lesquels seront dessinés, car une bulle ne s'accroche qu'à ce qui existe.
        //
        // Les méthodes écrites en notation attribut — @UMLInclude(asAttribute = true) —
        // rejoignent le compartiment des attributs, après les attributs du code : ce que la
        // classe stocke d'abord, ce qu'elle expose ensuite.
        ImmutableList<UmlField> fields = ImmutableList.<UmlField>builder()
            .addAll(MemberExtractor.extractFields(clazz, finalStrategy, regles))
            .addAll(MemberExtractor.methodesEnAttributs(clazz, finalStrategy, regles))
            .build();
        ImmutableList<UmlMethod> methods =
            MemberExtractor.extractMethods(clazz, finalStrategy, regles);

        UmlEntity.Builder builder = UmlEntity.builder(clazz.getName(), finalName)
            .kind(kind(clazz))
            .stereotype(finalStereotype)
            .color(finalColor)
            .strategy(finalStrategy)

            .literals(MemberExtractor.extractLiterals(clazz, finalStrategy, regles))
            .fields(fields)
            .methods(methods)
            .notes(NoteExtractor.extraire(clazz, fields, methods))
            .constraints(ConstraintExtractor.de(clazz, clazz.getName()))
            .group(PackageExtractor.extraire(clazz, regles))
            .typeParameters(parametresDeType(clazz, regles));

        for (RelationExtractor extracteur : EXTRACTEURS) {
            builder.relations(extracteur.extraire(clazz));
        }
        return builder.build();
    }

    // Ce que la classe est, lu par réflexion.
    //
    // L'ordre des tests n'est pas indifférent, car les catégories de Java se recouvrent. Un type
    // annotation est aussi une interface ; une interface porte toujours le modificateur
    // abstract ; une énumération dont une constante a un corps est abstract elle aussi. Chaque
    // cas particulier doit donc être écarté avant le cas général qui le contient.
    //
    // Un record n'apparaît pas dans ce test : c'est une classe finale, et il tombe donc dans
    // CLASS — ce qui le décrit exactement.
    private static UmlEntityKind kind(Class<?> clazz) {
        if (clazz.isAnnotation()) {
            return UmlEntityKind.ANNOTATION;
        }
        if (clazz.isInterface()) {
            return UmlEntityKind.INTERFACE;
        }
        if (clazz.isEnum()) {
            return UmlEntityKind.ENUM;
        }
        if (Modifier.isAbstract(clazz.getModifiers())) {
            return UmlEntityKind.ABSTRACT_CLASS;
        }
        return UmlEntityKind.CLASS;
    }

    // Les paramètres de type de la classe, chevrons compris : « <T> », « <K, V extends Number> ».
    //
    // UML les dessine dans un cartouche en pointillés au coin de la boîte ; PlantUML les écrit
    // simplement à la suite du nom, ce qui dit la même chose. Ils manquaient tout à fait : une
    // « Boite<T> » s'affichait « Boite », avec un attribut de type T que plus rien dans le
    // diagramme ne définissait.
    //
    // Les bornes suivent les règles de nommage du projet, comme partout ailleurs : un
    // « T extends ImmutableList » s'écrit « T extends List » si le projet le demande.
    private static String parametresDeType(Class<?> clazz, Regles regles) {
        TypeVariable<?>[] variables = clazz.getTypeParameters();
        if (variables.length == 0) {
            return "";
        }
        StringJoiner parametres = new StringJoiner(", ", "<", ">");
        for (TypeVariable<?> variable : variables) {
            StringBuilder ecrit = new StringBuilder(variable.getName());
            // « extends Object » est la borne par défaut : l'écrire n'apprendrait rien, et
            // allongerait l'en-tête de toutes les classes génériques du projet.
            StringJoiner bornes = new StringJoiner(" & ");
            for (Type borne : variable.getBounds()) {
                if (borne != Object.class) {
                    bornes.add(TypeAffiche.de(borne, regles));
                }
            }
            if (bornes.length() > 0) {
                ecrit.append(" extends ").append(bornes);
            }
            parametres.add(ecrit);
        }
        return parametres.toString();
    }
}
