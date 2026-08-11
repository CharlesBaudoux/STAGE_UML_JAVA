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
import io.github.oliviercailloux.umlg.extraction.TypeAffiche;
import io.github.oliviercailloux.umlg.extraction.relation.CibleDeduite;
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
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Remplit les deux compartiments d'une boîte : les attributs et les méthodes.
//
// Tout l'enjeu est de trier. Une classe réelle a beaucoup plus de membres qu'un diagramme
// lisible n'en supporte ; trois filtres se succèdent donc, dans cet ordre :
//   1. le technique — ce que le compilateur a fabriqué (isSynthetic), et que l'utilisateur n'a
//      jamais écrit ; c'est le seul filtre que la bibliothèque applique de son propre chef ;
//   2. ce que le projet a déclaré sans intérêt — les motifs de noms du fichier de règles
//      (Regles.membreMasque), qui ne masquent rien tant que rien n'est écrit ;
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
        for (Field field : MembresOrdonnes.champs(clazz, regles)) {

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
            if (regles.membreMasque(field.getName())
                && !field.isAnnotationPresent(UMLInclude.class)) {
                continue;
            }

            if (!estVisible(field, association, strategy)) {
                continue;
            }
            result.add(association == null
                ? champ(field, regles)
                : champAssocie(field, association, regles));
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
        // Un composant de record est sa définition même : « record Mnemonic(String
        // keysymMnemonic) » ne dit rien d'autre que ce composant, et une boîte qui le tairait
        // serait vide de ce qu'elle est. Il paraît donc quelle que soit la stratégie — y compris
        // NONE, faite pour choisir parmi des membres ordinaires, pas pour taire une définition.
        // Seul @UMLExclude, veto individuel, garde le dernier mot, comme pour une association.
        if (estComposantDeRecord(field)) {
            return !field.isAnnotationPresent(UMLExclude.class);
        }
        // getModifiers rend les mots-clés de déclaration sous forme d'entier de bits :
        // public, private, static… C'est ce que VisibilityPolicy sait lire.
        return VisibilityPolicy.estVisible(field.getModifiers(), strategy,
            field.isAnnotationPresent(UMLInclude.class),
            field.isAnnotationPresent(UMLExclude.class));
    }

    // Cet attribut est-il le champ d'un composant de record ?
    //
    // Le nom suffit : le compilateur fabrique pour chaque composant un champ de même nom, et un
    // record ne peut pas déclarer d'autre champ d'instance — un champ ajouté est forcément
    // statique, donc jamais homonyme d'un composant.
    private static boolean estComposantDeRecord(Field field) {
        Class<?> classe = field.getDeclaringClass();
        if (!classe.isRecord()) {
            return false;
        }
        for (RecordComponent composant : classe.getRecordComponents()) {
            if (composant.getName().equals(field.getName())) {
                return true;
            }
        }
        return false;
    }

    // Un attribut ordinaire : son type est celui que le code déclare, sauf si une règle du projet
    // dit sous quel nom l'afficher — « ImmutableList » écrit « List ». Sa multiplicité, elle, ne
    // se lit nulle part dans le code : seule @UMLMultiplicity la donne.
    private static UmlField champ(Field field, Regles regles) {
        try {
            String multiplicite = multiplicite(field, field.getGenericType(), regles);
            return new UmlField(
                nomAffiche(field),
                // Avec une multiplicité, UML veut le type de l'élément : « aliases: String
                // [0..*] », et non « Set [0..*] », qui se lirait « plusieurs ensembles ».
                multiplicite.isEmpty()
                    ? TypeAffiche.de(field.getGenericType(), regles)
                    : TypeAffiche.deLElement(field.getGenericType(), regles),
                multiplicite,
                symbole(field.getModifiers(), regles),
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

    // La multiplicité d'une propriété : celle que @UMLMultiplicity déclare, ou, si le projet a
    // demandé la traduction (« multiplicity brackets »), celle que la forme du type dit.
    //
    // L'annotation l'emporte : c'est la décision individuelle, et la règle n'est qu'un défaut —
    // le même ordre de préséance que partout ailleurs dans la bibliothèque.
    private static String multiplicite(AnnotatedElement membre, java.lang.reflect.Type type,
        Regles regles) {
        String declaree = multiplicite(membre);
        if (!declaree.isEmpty() || !regles.multiplicitesDeduites()) {
            return declaree;
        }
        return TypeAffiche.multipliciteDeduite(type);
    }

    // Le nom sous lequel un attribut paraît dans la boîte.
    //
    // C'est celui de l'attribut Java, sauf deux redéfinitions. Une association écrite dans la
    // boîte nomme son rôle cible : en notation attribut, ce rôle est le nom de la propriété —
    // « physicalKeys: RectangularKey [0..*] » là où le code déclare un champ « keys ». Et
    // @UMLInclude(name = ...) renomme un membre ordinaire, quand le diagramme nomme les choses
    // plus longuement que le code. Les deux à la fois se contrediraient : c'est refusé, ici et
    // par le processeur.
    //
    // Public parce que NoteExtractor doit poser la même question : une bulle s'accroche au nom
    // affiché, et à lui seul.
    public static String nomAffiche(Field field) {
        UMLAssociation association = field.getAnnotation(UMLAssociation.class);
        String renomme = renomme(field, emplacement(field));

        if (association != null && association.notation() == UMLNotation.ATTRIBUTE
            && !association.targetRole().isEmpty()) {
            checkArgument(renomme.isEmpty(),
                "%s carries both targetRole = \"%s\" and @UMLInclude(name = \"%s\"): two names "
                    + "for the same line. Keep only one of the two.",
                emplacement(field), association.targetRole(), renomme);
            return association.targetRole();
        }
        return renomme.isEmpty() ? field.getName() : renomme;
    }

    // Le nom sous lequel une méthode paraît, celui du code ou celui que @UMLInclude redonne.
    public static String nomAffiche(Method method) {
        String renomme = renomme(method,
            method.getDeclaringClass().getName() + "." + method.getName());
        return renomme.isEmpty() ? method.getName() : renomme;
    }

    // Le nom que @UMLInclude(name = ...) donne à ce membre, ou la chaîne vide.
    //
    // Un nom fait d'espaces est refusé dès la lecture : l'écrire n'a aucun sens, et le laisser
    // passer dessinerait une ligne au nom invisible.
    private static String renomme(AnnotatedElement membre, String emplacement) {
        UMLInclude inclusion = membre.getAnnotation(UMLInclude.class);
        if (inclusion == null || inclusion.name().isEmpty()) {
            return "";
        }
        checkArgument(!inclusion.name().isBlank(),
            "%s: @UMLInclude(name = ...) cannot be made of whitespace only.", emplacement);
        return inclusion.name();
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
    private static UmlField champAssocie(Field field, UMLAssociation association,
        Regles regles) {
        String contexte = "@UMLAssociation sur " + emplacement(field);
        // La même exigence que sous la notation ARROW : la cible est une classe du diagramme.
        Class<?> cible = CibleDeduite.de(field, association, contexte);
        TargetResolver.validerCible(cible, contexte, "target()");

        try {
            // La multiplicité et le rôle sont validés en construisant l'objet du modèle avec ces
            // mêmes valeurs — le procédé qu'emploie déjà le processeur d'annotations. Aucune
            // règle n'est donc réécrite ici : la grammaire des multiplicités reste écrite une
            // seule fois, dans UmlAssociation, et le refus est le même sous les deux notations.
            UmlAssociation lien =
                UmlAssociation.builder(field.getName(), cible.getName())
                    .targetRole(association.targetRole())
                    .targetCardinality(association.targetCardinality())
                    .constraints(ConstraintExtractor.de(field, emplacement(field)))
                    .build();

            // Les contraintes s'écrivent dans l'ordre où elles sont déclarées, le même que sur un
            // trait (voir Extremite), pour que déplacer un lien d'une notation à l'autre ne
            // réarrange rien. « ordered » est l'une d'elles, et n'a plus de traitement à part.
            ImmutableList<String> contraintes = lien.constraints();

            return new UmlField(
                nomAffiche(field),
                // getSimpleName et non les règles du projet : le document réserve le renommage
                // aux types lus dans le code, pour que le type écrit ici ne diverge jamais du nom
                // de la boîte voisine.
                cible.getSimpleName(),
                lien.targetCardinality(),
                symbole(field.getModifiers(), regles),
                Modifier.isStatic(field.getModifiers()),
                field.isAnnotationPresent(UMLDerived.class),
                contraintes);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Le modèle a détecté la faute — une multiplicité « 3..1 », par exemple — mais ignore
            // d'où elle vient : on rejoue son message en le préfixant de l'emplacement.
            throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
        }
    }

    // Le type rendu par une méthode, tel qu'il paraît après les deux-points.
    //
    // La règle « omit return void » peut l'effacer tout à fait — c'est le seul endroit où un
    // type peut ne pas être écrit du tout, « valider() » disant déjà qu'il n'y a rien à rendre.
    // Sinon,
    // il s'écrit avec ses paramètres de type, et se réduit à l'élément quand une multiplicité le
    // suit : « names(): String [0..*] » se lit « rend zéro ou plusieurs chaînes ».
    private static String retour(Method method, String multiplicite, Regles regles) {
        if (regles.nomAfficheRetour(method.getReturnType()).isEmpty()) {
            return "";
        }
        return multiplicite.isEmpty()
            ? TypeAffiche.de(method.getGenericReturnType(), regles)
            : TypeAffiche.deLElement(method.getGenericReturnType(), regles);
    }

    // Le symbole de visibilité, ou rien si le projet a demandé qu'on le taise.
    //
    // Le taire est un choix de lecture : UML le demande, mais beaucoup de diagrammes tenus à la
    // main l'omettent, parce qu'ils montrent une structure plutôt qu'une API.
    private static String symbole(int modifiers, Regles regles) {
        return regles.omettreVisibilite() ? "" : VisibilityPolicy.symbole(modifiers);
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
            // getParameterizedType, et non getType : « Map<String, Short> » plutôt que « Map ».
            String type = TypeAffiche.de(parametre.getParameterizedType(), regles);

            // « omit parameter names » : le projet a choisi les signatures courtes, celles des
            // diagrammes tenus à la main — le nom est tu même quand la compilation l'a gardé.
            parametres.add(parametre.isNamePresent() && !regles.omettreNomsDeParametres()
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

        for (Method method : MembresOrdonnes.methodes(clazz, regles)) {

            if (method.isSynthetic()) {
                continue;
            }

            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);

            // Une méthode écrite en notation attribut quitte ce compartiment : elle paraît parmi
            // les attributs (voir methodesEnAttributs), et la dessiner ici la répéterait.
            if (inclusion != null && inclusion.asAttribute()) {
                continue;
            }

            boolean hasInclude = inclusion != null;
            // Le veto se lit aussi sur la déclaration que cette méthode redéfinit : un
            // @UMLExclude posé sur l'interface vaut pour chacune de ses réalisations, sans qu'il
            // faille le répéter sur chaque override (voir excluParHeritage).
            boolean hasExclude = method.isAnnotationPresent(UMLExclude.class)
                || excluParHeritage(clazz, method);

            // Le bruit ne se juge plus tout seul : seuls les motifs écrits dans le fichier de
            // règles écartent quoi que ce soit. @UMLInclude garde le dernier mot, comme toujours
            // — la décision individuelle bat la règle générale.
            if (regles.membreMasque(method.getName()) && !hasInclude) {
                continue;
            }

            if (VisibilityPolicy.estVisible(method.getModifiers(), strategy, hasInclude,
                hasExclude)) {

                try {
                    String multiplicite =
                        multiplicite(method, method.getGenericReturnType(), regles);
                    result.add(new UmlMethod(
                        nomAffiche(method),
                        retour(method, multiplicite, regles),
                        multiplicite,
                        symbole(method.getModifiers(), regles),
                        Modifier.isStatic(method.getModifiers()),
                        parametres(method, regles),
                        method.isAnnotationPresent(UMLDerived.class),
                        // Une méthode d'interface est abstraite sans porter le mot-clé, sauf
                        // si elle est default ou static : le modificateur, lui, ne ment pas.
                        Modifier.isAbstract(method.getModifiers())
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

    // Les méthodes que @UMLInclude(asAttribute = true) écrit en notation attribut :
    // « xKeycodeByXKeyName : Map<String, Short> », sans parenthèses, parmi les attributs.
    //
    // Un accesseur sans paramètre expose une propriété de l'objet ; les diagrammes qui montrent
    // une structure l'écrivent comme telle, et c'est la forme des diagrammes de référence de
    // Keyboardd. La conversion exige une méthode sans paramètre qui rend quelque chose : les
    // parenthèses retirées, il ne reste que le nom et le type, et il faut que les deux existent.
    // Le processeur refuse le reste à la compilation ; la vérification est refaite ici pour le
    // code compilé sans lui.
    //
    // La dérogation étant du @UMLInclude, la visibilité est acquise ; ne l'écarte que le seul veto
    // qui écarte une méthode incluse : @UMLExclude, hérité compris.
    public static ImmutableList<UmlField> methodesEnAttributs(Class<?> clazz, Regles regles) {
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        for (Method method : MembresOrdonnes.methodes(clazz, regles)) {
            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);
            if (method.isSynthetic() || inclusion == null || !inclusion.asAttribute()) {
                continue;
            }
            if (method.isAnnotationPresent(UMLExclude.class) || excluParHeritage(clazz, method)) {
                continue;
            }
            String emplacement = clazz.getName() + "." + method.getName();
            checkArgument(method.getParameterCount() == 0,
                "%s carries @UMLInclude(asAttribute = true) but takes parameters: an attribute "
                    + "has none. Remove the parameters, or the notation.", emplacement);
            checkArgument(method.getReturnType() != void.class,
                "%s carries @UMLInclude(asAttribute = true) but returns nothing: an attribute "
                    + "is its type. Return something, or remove the notation.", emplacement);

            try {
                String multiplicite =
                    multiplicite(method, method.getGenericReturnType(), regles);
                result.add(new UmlField(
                    nomAffiche(method),
                    // Le type est celui que la méthode rend — écrit comme sur un attribut, sans
                    // passer par « omit return void » : un attribut porte toujours son type.
                    multiplicite.isEmpty()
                        ? TypeAffiche.de(method.getGenericReturnType(), regles)
                        : TypeAffiche.deLElement(method.getGenericReturnType(), regles),
                    multiplicite,
                    symbole(method.getModifiers(), regles),
                    Modifier.isStatic(method.getModifiers()),
                    method.isAnnotationPresent(UMLDerived.class),
                    ConstraintExtractor.de(method, emplacement)));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                    "@UMLMultiplicity sur " + emplacement + " : " + e.getMessage(), e);
            }
        }
        return result.build();
    }

    // Ce que cette méthode redéfinit porte-t-il @UMLExclude ?
    //
    // Un membre déclaré par une interface et réalisé par trois classes s'exclut en un endroit :
    // sur la déclaration. L'exclure trois fois — une par override — était le coût observé sur un
    // projet réel, et il ne disait rien de plus. La recherche parcourt tous les surtypes ; une
    // méthode statique ou privée ne redéfinit rien, elle ne regarde qu'elle-même.
    private static boolean excluParHeritage(Class<?> clazz, Method method) {
        if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers())) {
            return false;
        }
        for (Class<?> surtype : surtypes(clazz)) {
            try {
                if (surtype.getDeclaredMethod(method.getName(), method.getParameterTypes())
                    .isAnnotationPresent(UMLExclude.class)) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
                // Ce surtype ne déclare pas la méthode : rien à lire chez lui.
            }
        }
        return false;
    }

    // Tous les surtypes d'une classe, elle exclue : la chaîne des parents et chaque interface,
    // transitivement. L'ordre de rencontre est conservé, mais il est indifférent — on cherche
    // une annotation, pas une préséance.
    private static Set<Class<?>> surtypes(Class<?> clazz) {
        Set<Class<?>> vus = new LinkedHashSet<>();
        List<Class<?>> aVisiter = new ArrayList<>();
        aVisiter.add(clazz);

        while (!aVisiter.isEmpty()) {
            Class<?> courant = aVisiter.remove(aVisiter.size() - 1);
            Class<?> parent = courant.getSuperclass();
            if (parent != null && vus.add(parent)) {
                aVisiter.add(parent);
            }
            for (Class<?> interface_ : courant.getInterfaces()) {
                if (vus.add(interface_)) {
                    aVisiter.add(interface_);
                }
            }
        }
        return vus;
    }
}
