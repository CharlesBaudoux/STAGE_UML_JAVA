package io.github.oliviercailloux.umlg.extraction.member;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.ConstraintExtractor;
import io.github.oliviercailloux.umlg.extraction.MembresOrdonnes;
import io.github.oliviercailloux.umlg.extraction.relation.TargetResolver;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlParameter;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

// Remplit les deux compartiments d'une boîte : les attributs et les méthodes.
//
// Tout l'enjeu est de trier. Une classe réelle a beaucoup plus de membres qu'un diagramme
// lisible n'en supporte ; trois filtres se succèdent donc, dans cet ordre :
//   1. le technique — ce que le compilateur a fabriqué (isSynthetic), et que l'utilisateur n'a
//      jamais écrit ; c'est le seul filtre que la bibliothèque applique de son propre chef ;
//   2. ce que le projet a déclaré sans intérêt — les familles et les motifs du fichier de règles
//      (NoiseFilter), qui ne masquent rien tant que rien n'est écrit ;
//   3. le choix de l'utilisateur — la stratégie de visibilité, et les vetos @UMLInclude /
//      @UMLExclude (VisibilityPolicy).
public final class MemberExtractor {

    private MemberExtractor() {}

    // Les littéraux d'une énumération : EURO, DOLLAR…
    //
    // Ils sont bien des attributs pour la réflexion — publics, statiques et finaux —, mais pas
    // pour UML, où un EnumerationLiteral ne porte que son nom. Les extraire à part est ce qui
    // permet de les écrire « EURO » plutôt que « + {static} Devise EURO », et laisse une
    // énumération avoir en plus de vrais attributs, qui eux restent dans extractFields.
    //
    // La stratégie s'applique quand même : un littéral est implicitement public, donc il paraît
    // pour toute stratégie sauf NONE. @UMLExclude garde le dernier mot, comme partout.
    public static ImmutableList<String> extractLiterals(Class<?> clazz, UMLVisibility strategy) {
        ImmutableList.Builder<String> result = ImmutableList.builder();

        Object[] constantes = clazz.getEnumConstants();
        if (constantes == null) {
            return result.build();
        }

        // getEnumConstants, et non getDeclaredFields : il rend les littéraux dans l'ordre des
        // ordinaux, c'est-à-dire celui de la déclaration. C'est le seul ordre qui ait un sens ici
        // — EN_COURS avant TERMINEE dit quelque chose que l'alphabet effacerait — et c'est aussi
        // le seul que la réflexion sache encore restituer après compilation.
        for (Object constante : constantes) {
            Field field;
            try {
                field = clazz.getDeclaredField(((Enum<?>) constante).name());
            } catch (NoSuchFieldException e) {
                // Inatteignable : le littéral vient de la classe elle-même.
                throw new IllegalStateException(e);
            }
            boolean visible = VisibilityPolicy.estVisible(field.getModifiers(), strategy,
                field.isAnnotationPresent(UMLInclude.class),
                field.isAnnotationPresent(UMLExclude.class));

            if (visible) {
                result.add(field.getName());
            }
        }
        return result.build();
    }

    // Sans règles de projet : le cas d'une classe extraite isolément, et celui des tests.
    public static ImmutableList<UmlField> extractFields(Class<?> clazz, UMLVisibility strategy) {
        return extractFields(clazz, strategy, Regles.aucune());
    }

    public static ImmutableList<UmlField> extractFields(Class<?> clazz, UMLVisibility strategy,
        Regles regles) {
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        // Les attributs déclarés par la classe, sans les hérités, dans un ordre stable.
        for (Field field : MembresOrdonnes.champs(clazz)) {

            // Filtre technique : this$0 des classes internes, $VALUES des enums… du code que
            // l'utilisateur n'a jamais écrit.
            if (field.isSynthetic()) {
                continue;
            }

            // Les littéraux d'une énumération partent dans leur propre compartiment
            // (extractLiterals) : les laisser ici les afficherait deux fois, et sous une forme
            // qu'UML ne reconnaît pas.
            if (field.isEnumConstant()) {
                continue;
            }

            UMLAssociation association = field.getAnnotation(UMLAssociation.class);

            // Deux multiplicités sur la même propriété : l'une des deux serait fausse, et rien ne
            // dirait laquelle. Sur un attribut porteur de @UMLAssociation, c'est
            // targetCardinality qui compte — @UMLMultiplicity n'a plus rien à qualifier.
            checkArgument(association == null || !field.isAnnotationPresent(UMLMultiplicity.class),
                "%s carries both @UMLAssociation and @UMLMultiplicity: the multiplicity of this "
                    + "property is already given by targetCardinality. Keep only one of the two.",
                emplacement(field));

            // Un attribut promu en trait quitte le compartiment des attributs : en UML, une
            // propriété se note soit dans la boîte, soit comme un lien entre boîtes, jamais les
            // deux à la fois — la répéter dessinerait deux fois la même information.
            //
            // Sous l'autre notation, c'est l'inverse : le trait n'existe pas, et l'attribut
            // reste ici. Il n'y a donc jamais de doublon, quelle que soit la notation choisie.
            if (association != null && association.notation() != UMLNotation.ATTRIBUTE) {
                continue;
            }

            // Un attribut que le projet tient pour du bruit — un logger, un compteur interne —
            // sort ici. Le veto individuel @UMLInclude le ramène, comme pour une méthode.
            if (NoiseFilter.estDuBruit(field, regles)
                && !field.isAnnotationPresent(UMLInclude.class)) {
                continue;
            }

            if (!estVisible(field, association, strategy)) {
                continue;
            }
            result.add(association == null
                ? champ(field, regles)
                : champAssocie(field, association));
        }
        return result.build();
    }

    // Ce membre entre-t-il dans la boîte ?
    //
    // Pour un attribut ordinaire, la stratégie de la classe et les deux dérogations décident.
    // Pour une association dessinée dans la boîte, la question ne se pose pas : l'utilisateur a
    // demandé ce lien nommément, et une stratégie — qui n'est qu'un défaut — n'a pas à défaire
    // une demande explicite. C'est déjà la règle sous la notation ARROW, où le trait se dessine
    // quelle que soit la visibilité de l'attribut porteur. Seul @UMLExclude, veto individuel,
    // garde le dernier mot.
    private static boolean estVisible(Field field, UMLAssociation association,
        UMLVisibility strategy) {
        if (association != null) {
            return !field.isAnnotationPresent(UMLExclude.class);
        }
        // getModifiers rend les mots-clés de déclaration sous forme d'entier de bits :
        // public, private, static… C'est ce que VisibilityPolicy sait lire.
        return VisibilityPolicy.estVisible(field.getModifiers(), strategy,
            field.isAnnotationPresent(UMLInclude.class),
            field.isAnnotationPresent(UMLExclude.class));
    }

    // Un attribut ordinaire : son type est celui que le code déclare, sauf si une règle du projet
    // dit sous quel nom l'afficher — « ImmutableList » écrit « List ». Sa multiplicité, elle, ne
    // se lit nulle part dans le code : seule @UMLMultiplicity la donne.
    private static UmlField champ(Field field, Regles regles) {
        try {
            return new UmlField(
                field.getName(),
                regles.nomAffiche(field.getType()),
                multiplicite(field),
                VisibilityPolicy.symbole(field.getModifiers()),
                Modifier.isStatic(field.getModifiers()),
                field.isAnnotationPresent(UMLDerived.class),
                ConstraintExtractor.de(field, emplacement(field)));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "@UMLMultiplicity sur " + emplacement(field) + " : " + e.getMessage(), e);
        }
    }

    // La multiplicité déclarée par @UMLMultiplicity, ou rien.
    //
    // Sur un attribut porteur de @UMLAssociation, la question est déjà tranchée : c'est
    // targetCardinality qui dit le nombre, et deux multiplicités sur la même propriété se
    // contrediraient tôt ou tard. La bibliothèque saurait en choisir une, mais le faire en
    // silence reviendrait à décider à la place de l'auteur, dans un code qui se contredit.
    private static String multiplicite(AnnotatedElement membre) {
        UMLMultiplicity annotation = membre.getAnnotation(UMLMultiplicity.class);
        return annotation == null ? "" : annotation.value();
    }

    // Le nom sous lequel un attribut paraît dans la boîte.
    //
    // C'est celui de l'attribut Java, sauf pour une association écrite dans la boîte qui nomme
    // son rôle cible : en notation attribut, ce rôle est le nom de la propriété — « physicalKeys:
    // RectangularKey [0..*] » là où le code déclare un champ « keys ». Le rôle ne se perd donc
    // pas en changeant de notation, il change seulement de place.
    //
    // Public parce que NoteExtractor doit poser la même question : une bulle s'accroche au nom
    // affiché, et à lui seul.
    public static String nomAffiche(Field field) {
        UMLAssociation association = field.getAnnotation(UMLAssociation.class);

        if (association == null || association.notation() != UMLNotation.ATTRIBUTE
            || association.targetRole().isEmpty()) {
            return field.getName();
        }
        return association.targetRole();
    }

    // Une association écrite dans la boîte, par exemple « - Commande commandes [0..*] ».
    //
    // Trois valeurs viennent de l'annotation et non du code : le nom est le rôle cible s'il est
    // écrit, le type affiché est la classe visée — et non « List », qui ne dit rien du contenu —,
    // et la multiplicité est celle déclarée côté cible. C'est exactement ce que porterait le
    // trait ; seule change la place où on l'écrit.
    //
    // Le reste de la ligne se lit sur l'attribut lui-même, comme pour n'importe quel autre :
    // visibilité, {static}, barre oblique de @UMLDerived, contraintes de @UMLConstraint.
    private static UmlField champAssocie(Field field, UMLAssociation association) {
        String contexte = "@UMLAssociation sur " + emplacement(field);
        // La même exigence que sous la notation ARROW : la cible est une classe du diagramme.
        TargetResolver.validerCible(association.target(), contexte, "target()");

        try {
            // La multiplicité et le rôle sont validés en construisant l'objet du modèle avec ces
            // mêmes valeurs — le procédé qu'emploie déjà le processeur d'annotations. Aucune
            // règle n'est donc réécrite ici : la grammaire des multiplicités reste écrite une
            // seule fois, dans UmlAssociation, et le refus est le même sous les deux notations.
            UmlAssociation lien =
                UmlAssociation.builder(field.getName(), association.target().getName())
                    .targetRole(association.targetRole())
                    .targetCardinality(association.targetCardinality())
                    .ordered(association.isOrdered())
                    .constraints(ConstraintExtractor.de(field, emplacement(field)))
                    .build();

            // {ordered} d'abord, les règles de l'utilisateur ensuite : le même ordre que sur un
            // trait (voir Extremite), pour que déplacer un lien d'une notation à l'autre ne
            // réarrange rien.
            ImmutableList.Builder<String> regles = ImmutableList.builder();
            if (lien.ordered()) {
                regles.add("ordered");
            }
            regles.addAll(lien.constraints());

            return new UmlField(
                nomAffiche(field),
                association.target().getSimpleName(),
                lien.targetCardinality(),
                VisibilityPolicy.symbole(field.getModifiers()),
                Modifier.isStatic(field.getModifiers()),
                field.isAnnotationPresent(UMLDerived.class),
                regles.build());
        } catch (IllegalArgumentException | NullPointerException e) {
            // Le modèle a détecté la faute — une multiplicité « 3..1 », par exemple — mais ignore
            // d'où elle vient : on rejoue son message en le préfixant de l'emplacement.
            throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
        }
    }

    // Désigne un attribut dans un message d'erreur, sous la forme « com.banque.Client.solde ».
    private static String emplacement(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    // Les paramètres d'une méthode, avec leur nom lorsqu'il a survécu à la compilation.
    //
    // isNamePresent est la seule façon de poser la question. Un .class ne porte les noms des
    // paramètres que si le projet a été compilé avec l'option « -parameters » ; sans elle, la
    // réflexion invente « arg0 », « arg1 »… et getName() les rend sans prévenir. Les afficher
    // serait pire que de les taire : le lecteur croirait lire le code de l'auteur.
    private static ImmutableList<UmlParameter> parametres(Method method, Regles regles) {
        ImmutableList.Builder<UmlParameter> parametres = ImmutableList.builder();

        for (Parameter parametre : method.getParameters()) {
            String type = regles.nomAffiche(parametre.getType());

            parametres.add(parametre.isNamePresent()
                ? new UmlParameter(parametre.getName(), type)
                : UmlParameter.anonyme(type));
        }
        return parametres.build();
    }

    public static ImmutableList<UmlMethod> extractMethods(Class<?> clazz, UMLVisibility strategy) {
        return extractMethods(clazz, strategy, Regles.aucune());
    }

    public static ImmutableList<UmlMethod> extractMethods(Class<?> clazz, UMLVisibility strategy,
        Regles regles) {
        ImmutableList.Builder<UmlMethod> result = ImmutableList.builder();

        for (Method method : MembresOrdonnes.methodes(clazz)) {

            if (method.isSynthetic()) {
                continue;
            }

            boolean hasInclude = method.isAnnotationPresent(UMLInclude.class);
            boolean hasExclude = method.isAnnotationPresent(UMLExclude.class);

            // Le bruit ne se juge plus tout seul : seules les familles et les motifs écrits dans
            // le fichier de règles écartent quoi que ce soit. @UMLInclude garde le dernier mot,
            // comme toujours — la décision individuelle bat la règle générale.
            if (NoiseFilter.estDuBruit(method, clazz, regles) && !hasInclude) {
                continue;
            }

            if (VisibilityPolicy.estVisible(method.getModifiers(), strategy, hasInclude,
                hasExclude)) {

                try {
                    result.add(new UmlMethod(
                        method.getName(),
                        // nomAfficheRetour, et non nomAffiche : c'est le seul endroit où une
                        // règle du projet peut faire disparaître le type — « valider() » plutôt
                        // que « valider(): void ».
                        regles.nomAfficheRetour(method.getReturnType()),
                        multiplicite(method),
                        VisibilityPolicy.symbole(method.getModifiers()),
                        Modifier.isStatic(method.getModifiers()),
                        parametres(method, regles),
                        method.isAnnotationPresent(UMLDerived.class)
                    ));
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw new IllegalArgumentException("@UMLMultiplicity sur "
                        + method.getDeclaringClass().getName() + "." + method.getName() + " : "
                        + e.getMessage(), e);
                }
            }
        }
        return result.build();
    }
}
