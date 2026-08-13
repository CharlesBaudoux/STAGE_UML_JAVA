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
import io.github.oliviercailloux.umlg.warning.Avertisseur;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Remplit les deux compartiments d'une boîte : les attributs et les méthodes.
//
// Tout l'enjeu est de trier. Une classe réelle a beaucoup plus de membres qu'un diagramme
// lisible n'en supporte ; deux filtres se succèdent donc, dans cet ordre :
//   1. le technique — ce que le compilateur a fabriqué (isSynthetic), ou recopié par ricochet
//      (estAccesseurPropage), et que l'utilisateur n'a jamais écrit ; c'est le seul filtre que la
//      bibliothèque applique de son propre chef, et le seul que la boucle tienne elle-même ;
//   2. ce que l'utilisateur a demandé — @UMLExclude, @UMLInclude, la stratégie de visibilité et
//      les motifs du fichier de règles. Cette décision-là s'écrit à un seul endroit, la méthode
//      « retenu », et les quatre compartiments (attributs, méthodes, méthodes en notation
//      attribut, littéraux) l'appellent sans jamais la réécrire.
public final class MemberExtractor {

    private MemberExtractor() {}

    // Les littéraux d'une énumération : EURO, DOLLAR…
    //
    // Ils sont bien des attributs pour la réflexion — publics, statiques et finaux —, mais pas
    // pour UML, où un EnumerationLiteral ne porte que son nom. Les extraire à part est ce qui
    // permet de les écrire « EURO » plutôt que « + {static} Devise EURO », et laisse une
    // énumération avoir en plus de vrais attributs, qui eux restent dans extractFields.
    //
    // Un littéral reste un membre comme un autre : il passe par la même décision que les
    // attributs et les méthodes — la stratégie, les motifs du fichier de règles, les deux vetos
    // — et il paraît sous le nom que @UMLInclude(name = ...) lui donne, s'il en donne un. Étant
    // implicitement public, il paraît pour toute stratégie sauf NONE.
    public static ImmutableList<String> extractLiterals(Class<?> clazz, UMLVisibility strategy) {
        return extractLiterals(clazz, strategy, Regles.aucune());
    }

    public static ImmutableList<String> extractLiterals(Class<?> clazz, UMLVisibility strategy,
        Regles regles) {
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
            retenu(field, strategy, regles).ifPresent(result::add);
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
            // targetMultiplicity qui compte — @UMLMultiplicity n'a plus rien à qualifier.
            checkArgument(association == null || !field.isAnnotationPresent(UMLMultiplicity.class),
                messageDeuxMultiplicites(emplacement(field)));

            signalerNotationAttributSurUnChamp(field);

            // Un attribut promu en trait quitte le compartiment des attributs : en UML, une
            // propriété se note soit dans la boîte, soit comme un lien entre boîtes, jamais les
            // deux à la fois — la répéter dessinerait deux fois la même information.
            //
            // Sous l'autre notation, c'est l'inverse : le trait n'existe pas, et l'attribut
            // reste ici. Il n'y a donc jamais de doublon, quelle que soit la notation choisie.
            if (association != null && association.notation() != UMLNotation.ATTRIBUTE) {
                continue;
            }

            Optional<String> nom = retenu(field, strategy, regles);
            if (nom.isEmpty()) {
                continue;
            }
            result.add(association == null
                ? champ(field, nom.get(), regles)
                : champAssocie(field, nom.get(), association, regles));
        }
        return result.build();
    }

    // Ce membre entre-t-il dans la boîte, et sous quel nom ?
    //
    // LA HIÉRARCHIE, ÉCRITE UNE FOIS
    //
    // Toute la bibliothèque énonce le même ordre de priorité : @UMLExclude d'abord — le veto
    // individuel a toujours le dernier mot —, puis @UMLInclude, puis les défauts généraux, qui
    // sont deux et vont ensemble : la stratégie de la classe et les motifs « hide » du fichier de
    // règles. Les quatre points d'entrée de l'extraction posaient chacun leur propre séquence de
    // gardes, et les quatre différaient — un motif battait un composant de record, un littéral
    // n'était touché ni par les motifs ni par le renommage. Ils passent désormais tous par ici,
    // et la hiérarchie n'est plus écrite qu'à un endroit.
    //
    // Rendre le nom avec la décision est ce qui ferme la seconde moitié du problème : un membre
    // retenu paraît sous le nom que @UMLInclude(name = ...) lui donne, quel que soit le
    // compartiment où il atterrit.
    private static Optional<String> retenu(Field field, UMLVisibility strategy, Regles regles) {
        // Deux demandes explicites, qu'aucun défaut général ne défait.
        //
        // Une association dessinée dans la boîte, d'abord : l'utilisateur a nommé ce lien, et
        // c'est déjà la règle sous la notation ARROW, où le trait se dessine quelle que soit la
        // visibilité de l'attribut porteur — passer d'une notation à l'autre ne doit pas faire
        // disparaître le lien.
        //
        // Un composant de record ensuite : « record Mnemonic(String keysymMnemonic) » ne dit rien
        // d'autre que ce composant, et une boîte qui le tairait serait vide de ce qu'elle est.
        boolean privilegie = field.isAnnotationPresent(UMLAssociation.class)
            || estComposantDeRecord(field);

        // getModifiers rend les mots-clés de déclaration sous forme d'entier de bits :
        // public, private, static… C'est ce que VisibilityPolicy sait lire.
        if (!entre(field.getName(), field.getModifiers(),
            field.isAnnotationPresent(UMLInclude.class),
            field.isAnnotationPresent(UMLExclude.class), privilegie, strategy, regles)) {
            return Optional.empty();
        }
        return Optional.of(nomAffiche(field));
    }

    // La même question pour une méthode. Elle n'a pas de cas privilégié — rien n'y joue le rôle
    // du composant de record —, mais son veto se lit plus loin que sur elle-même : un @UMLExclude
    // posé sur la déclaration que cette méthode redéfinit vaut pour elle (voir excluParHeritage).
    private static Optional<String> retenu(Class<?> clazz, Method method, UMLVisibility strategy,
        Regles regles) {
        boolean exclu = method.isAnnotationPresent(UMLExclude.class)
            || excluParHeritage(clazz, method);

        if (!entre(method.getName(), method.getModifiers(),
            method.isAnnotationPresent(UMLInclude.class), exclu, false, strategy, regles)) {
            return Optional.empty();
        }
        return Optional.of(nomAffiche(method));
    }

    // Le cœur de la décision, commun aux attributs, aux méthodes et aux littéraux.
    private static boolean entre(String nom, int modifiers, boolean inclus, boolean exclu,
        boolean privilegie, UMLVisibility strategy, Regles regles) {
        if (exclu) {
            return false;
        }
        if (inclus || privilegie) {
            return true;
        }
        // Les deux défauts généraux, au même rang : le projet tient ce nom pour du bruit — un
        // logger, un compteur interne —, ou la stratégie de la classe ne montre pas ce niveau de
        // visibilité. Ni l'un ni l'autre n'est une décision prise sur ce membre-ci, et c'est
        // pourquoi ni l'un ni l'autre ne défait ce que les lignes précédentes ont acquis.
        return !regles.membreMasque(nom) && VisibilityPolicy.parLaStrategie(modifiers, strategy);
    }

    // @UMLInclude(asAttribute = true) posé sur un attribut.
    //
    // Le processeur refuse ce code à la compilation ; la génération l'ignorait sans un mot, si
    // bien que le même code recevait deux réponses selon qu'on le compilait avec le processeur ou
    // qu'on le dessinait sans lui. La notation n'a pourtant rien à convertir ici — un attribut est
    // déjà un attribut —, et le dessin reste juste : un avertissement, du même texte que l'erreur
    // du processeur, est ce qui fait dire la même chose aux deux côtés.
    private static void signalerNotationAttributSurUnChamp(Field field) {
        UMLInclude inclusion = field.getAnnotation(UMLInclude.class);
        if (inclusion != null && inclusion.asAttribute()) {
            Avertisseur.dire(messageNotationAttributSurUnChamp(emplacement(field)));
        }
    }

    // LES QUATRE RÈGLES QUE LE PROCESSEUR D'ANNOTATIONS VIENT LIRE ICI
    //
    // Elles sont exactement les mêmes des deux côtés — mêmes conditions, mêmes mots —, et elles
    // étaient recopiées à la main dans processing/, où le paquetage promet pourtant de ne
    // réécrire aucune règle. Une duplication identique est une duplication en sursis : celles
    // qu'on n'a pas nommées avaient déjà divergé. Les messages vivent donc là où la règle
    // s'applique, et le processeur les lit.

    // Deux multiplicités sur la même propriété : l'une des deux serait fausse, et rien ne dirait
    // laquelle. Sur un attribut porteur de @UMLAssociation, c'est targetMultiplicity qui compte —
    // @UMLMultiplicity n'a plus rien à qualifier.
    public static String messageDeuxMultiplicites(String emplacement) {
        return emplacement + " carries both @UMLAssociation and @UMLMultiplicity: the "
            + "multiplicity of this property is already given by targetMultiplicity. Keep only one "
            + "of the two.";
    }

    // @UMLInclude(asAttribute = true) posé sur un attribut : la notation n'a rien à convertir, un
    // attribut est déjà un attribut.
    public static String messageNotationAttributSurUnChamp(String emplacement) {
        return emplacement + ": @UMLInclude(asAttribute = true) is for methods; a field is "
            + "already an attribute. Remove the attribute.";
    }

    // Les parenthèses retirées, il ne reste que le nom et le type : il faut donc que les deux
    // existent.
    public static String messageNotationAttributAvecParametres(String emplacement) {
        return emplacement + " carries @UMLInclude(asAttribute = true) but takes parameters: an "
            + "attribute has none. Remove the parameters, or the notation.";
    }

    public static String messageNotationAttributSansRetour(String emplacement) {
        return emplacement + " carries @UMLInclude(asAttribute = true) but returns nothing: an "
            + "attribute is its type. Return something, or remove the notation.";
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

    // L'accesseur d'un composant de record, atteint par la seule propagation de Java.
    //
    // Une annotation écrite sur un composant est recopiée par le compilateur sur tout ce à quoi
    // ses @Target la destinent : @UMLInclude visant FIELD et METHOD, elle se pose à la fois sur
    // le champ et sur l'accesseur. Une intention, deux porteurs — et deux lignes dans la boîte :
    //
    //     record Mnemonic(@UMLInclude(name = "mnemonic") String keysymMnemonic)
    //
    //     mnemonic : String        ← le composant, dessiné comme tout composant de record
    //     mnemonic() : String      ← l'accesseur, que l'inclusion force à paraître
    //
    // La seconde ne dit rien que la première ne dise, et personne ne l'a demandée. Elle est donc
    // écartée : c'est le composant que l'auteur nommait.
    //
    // Le test porte sur le champ et non sur la méthode, et c'est ce qui distingue les deux cas.
    // Une @UMLInclude écrite sur un accesseur déclaré à la main ne vient de nulle part — le champ
    // ne la porte pas — et cette méthode-là reste : l'auteur l'a bien visée, elle.
    private static boolean estAccesseurPropage(Class<?> clazz, Method method) {
        if (!clazz.isRecord() || method.getParameterCount() != 0
            || !method.isAnnotationPresent(UMLInclude.class)) {
            return false;
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getName().equals(method.getName()) && estComposantDeRecord(field)) {
                return field.isAnnotationPresent(UMLInclude.class);
            }
        }
        return false;
    }

    // Une ligne du compartiment des attributs, d'où qu'elle vienne.
    //
    // Trois choses seulement distinguent les trois provenances — un attribut du code, un
    // accesseur écrit en notation attribut, une association dessinée dans la boîte : le nom, le
    // type et la multiplicité. Tout le reste de la ligne se lit sur le membre Java lui-même, de
    // la même façon dans les trois cas, et s'écrit donc ici une fois pour toutes.
    //
    // Le paramètre est un membre annotable, c'est-à-dire un Field ou une Method : la borne dit
    // exactement ce dont cette méthode a besoin, les modificateurs et les annotations.
    private static <M extends AccessibleObject & Member> UmlField attribut(M membre, String nom,
        String type, String multiplicite, ImmutableList<String> contraintes, Regles regles) {
        return new UmlField(
            nom,
            type,
            multiplicite,
            symbole(membre.getModifiers(), regles),
            Modifier.isStatic(membre.getModifiers()),
            membre.isAnnotationPresent(UMLDerived.class),
            contraintes);
    }

    // Le type d'une propriété tel qu'il paraît après les deux-points.
    //
    // Avec une multiplicité, UML veut le type de l'ÉLÉMENT : « aliases: String [0..*] », et non
    // « Set [0..*] », qui se lirait « plusieurs ensembles ».
    private static String typeDeLaPropriete(Type type, String multiplicite, Regles regles) {
        return multiplicite.isEmpty()
            ? TypeAffiche.de(type, regles)
            : TypeAffiche.deLElement(type, regles);
    }

    // Un attribut ordinaire : son type est celui que le code déclare, sauf si une règle du projet
    // dit sous quel nom l'afficher — « ImmutableList » écrit « List ». Sa multiplicité, elle, ne
    // se lit nulle part dans le code : seule @UMLMultiplicity la donne.
    private static UmlField champ(Field field, String nom, Regles regles) {
        try {
            String multiplicite = multiplicite(field, field.getGenericType(), regles);
            return attribut(field, nom,
                typeDeLaPropriete(field.getGenericType(), multiplicite, regles),
                multiplicite, ConstraintExtractor.de(field, emplacement(field)), regles);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "@UMLMultiplicity sur " + emplacement(field) + " : " + e.getMessage(), e);
        }
    }

    // La multiplicité déclarée par @UMLMultiplicity, ou rien.
    //
    // Sur un attribut porteur de @UMLAssociation, la question est déjà tranchée : c'est
    // targetMultiplicity qui dit le nombre, et deux multiplicités sur la même propriété se
    // contrediraient tôt ou tard. La bibliothèque saurait en choisir une, mais le faire en
    // silence reviendrait à décider à la place de l'auteur, dans un code qui se contredit.
    private static String multiplicite(AnnotatedElement membre) {
        UMLMultiplicity annotation = membre.getAnnotation(UMLMultiplicity.class);
        return annotation == null ? "" : annotation.value();
    }

    // La multiplicité d'une propriété : celle que @UMLMultiplicity déclare, ou, si le projet a
    // demandé la traduction (« "multiplicity": "brackets" »), celle que la forme du type dit.
    //
    // L'annotation l'emporte : c'est la décision individuelle, et la règle n'est qu'un défaut —
    // le même ordre de préséance que partout ailleurs dans la bibliothèque.
    private static String multiplicite(AnnotatedElement membre, Type type,
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
    private static UmlField champAssocie(Field field, String nom, UMLAssociation association,
        Regles regles) {
        String contexte = "@UMLAssociation sur " + emplacement(field);
        // La même exigence que sous la notation ARROW : la cible est une classe du diagramme.
        Class<?> cible = CibleDeduite.de(field, association, contexte);
        TargetResolver.validerCible(cible, contexte, "target()");

        try {
            // Les multiplicités, les rôles et le label sont validés en construisant l'objet du
            // modèle avec ces mêmes valeurs — le procédé qu'emploie déjà le processeur
            // d'annotations. Aucune règle n'est donc réécrite ici : la grammaire des
            // multiplicités reste écrite une seule fois, dans UmlAssociation, et le refus est le
            // même sous les deux notations.
            //
            // LES TROIS PARAMÈTRES QUE CETTE NOTATION NE DESSINE PAS SONT VALIDÉS QUAND MÊME
            //
            // sourceMultiplicity, sourceRole et label décorent un trait, et la notation ATTRIBUTE
            // n'en trace aucun : ils ne sont pas recopiés dans la ligne engendrée, et l'ancienne
            // version ne les lisait donc pas du tout. Le processeur d'annotations, lui, les
            // validait quelle que soit la notation — si bien que « sourceMultiplicity = "3..1" »
            // arrêtait la compilation et passait la génération, sur le même code. C'est la
            // divergence 14(b), tranchée du côté dur : une multiplicité impossible reste
            // impossible, qu'elle finisse dessinée ou non, et la première bascule vers ARROW la
            // ferait surgir. Les deux moitiés posent maintenant la même question, à la même
            // grammaire.
            UmlAssociation lien =
                UmlAssociation.builder(field.getName(), cible.getName())
                    .sourceMultiplicity(association.sourceMultiplicity())
                    .sourceRole(association.sourceRole())
                    .targetRole(association.targetRole())
                    .targetMultiplicity(association.targetMultiplicity())
                    .label(association.label())
                    .constraints(ConstraintExtractor.de(field, emplacement(field)))
                    .build();

            // Les contraintes s'écrivent dans l'ordre où elles sont déclarées, le même que sur un
            // trait (voir Extremite), pour que déplacer un lien d'une notation à l'autre ne
            // réarrange rien. « ordered » est l'une d'elles, et n'a plus de traitement à part.
            ImmutableList<String> contraintes = lien.constraints();

            return attribut(field, nom,
                // getSimpleName et non les règles du projet : le document réserve le renommage
                // aux types lus dans le code, pour que le type écrit ici ne diverge jamais du nom
                // de la boîte voisine.
                cible.getSimpleName(),
                lien.targetMultiplicity(), contraintes, regles);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Le modèle a détecté la faute — une multiplicité « 3..1 », par exemple — mais ignore
            // d'où elle vient : on rejoue son message en le préfixant de l'emplacement.
            throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
        }
    }

    // Le type rendu par une méthode, tel qu'il paraît après les deux-points.
    //
    // La règle « "omit": ["return void"] » peut l'effacer tout à fait — c'est le seul endroit où un
    // type peut ne pas être écrit du tout, « valider() » disant déjà qu'il n'y a rien à rendre.
    // Sinon,
    // il s'écrit avec ses paramètres de type, et se réduit à l'élément quand une multiplicité le
    // suit : « names(): String [0..*] » se lit « rend zéro ou plusieurs chaînes ».
    private static String retour(Method method, String multiplicite, Regles regles) {
        if (regles.nomAfficheRetour(method.getReturnType()).isEmpty()) {
            return "";
        }
        return typeDeLaPropriete(method.getGenericReturnType(), multiplicite, regles);
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

    // Le même pour une méthode. La classe déclarante, et non celle qu'on est en train d'extraire :
    // c'est là que l'auteur a écrit la ligne fautive, et c'est là qu'il doit aller la corriger.
    private static String emplacement(Method method) {
        return method.getDeclaringClass().getName() + "." + method.getName();
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
            String type = typeDuParametre(parametre, regles);

            // « "omit": ["parameter names"] » : le projet a choisi les signatures courtes, celles des
            // diagrammes tenus à la main — le nom est tu même quand la compilation l'a gardé.
            parametres.add(parametre.isNamePresent() && !regles.omettreNomsDeParametres()
                ? new UmlParameter(parametre.getName(), type)
                : UmlParameter.anonyme(type));
        }
        return parametres.build();
    }

    // Le type d'un paramètre, tel que l'auteur l'a écrit.
    //
    // getParameterizedType, et non getType : « Map<String, Short> » plutôt que « Map ». Et
    // « String... » plutôt que « String[] » : le fichier .class ne connaît qu'un tableau, mais
    // l'auteur a écrit trois points, et le lecteur du diagramme s'attend à les y retrouver — un
    // « format(String, Object[]) » se lit comme une méthode qu'on appelle avec un tableau.
    // isVarArgs est la seule façon de distinguer les deux, le type étant le même.
    private static String typeDuParametre(Parameter parametre, Regles regles) {
        String type = TypeAffiche.de(parametre.getParameterizedType(), regles);
        return parametre.isVarArgs() && type.endsWith("[]")
            ? type.substring(0, type.length() - "[]".length()) + "..."
            : type;
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

            // L'accesseur qu'une annotation de composant a touché par ricochet : le composant
            // porte déjà la ligne (voir estAccesseurPropage).
            if (estAccesseurPropage(clazz, method)) {
                continue;
            }

            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);

            // Une méthode écrite en notation attribut quitte ce compartiment : elle paraît parmi
            // les attributs (voir methodesEnAttributs), et la dessiner ici la répéterait.
            if (inclusion != null && inclusion.asAttribute()) {
                continue;
            }

            Optional<String> nom = retenu(clazz, method, strategy, regles);
            if (nom.isEmpty()) {
                continue;
            }

            try {
                String multiplicite = multiplicite(method, method.getGenericReturnType(), regles);
                result.add(new UmlMethod(
                    nom.get(),
                    retour(method, multiplicite, regles),
                    multiplicite,
                    symbole(method.getModifiers(), regles),
                    Modifier.isStatic(method.getModifiers()),
                    parametres(method, regles),
                    method.isAnnotationPresent(UMLDerived.class),
                    // Une méthode d'interface est abstraite sans porter le mot-clé, sauf
                    // si elle est default ou static : le modificateur, lui, ne ment pas.
                    // « "omit": ["abstract"] » efface la marque, comme « "omit": ["visibility"] »
                    // efface le symbole : le modèle porte ce que le projet a demandé d'écrire.
                    Modifier.isAbstract(method.getModifiers()) && !regles.omettreAbstrait()
                ));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new IllegalArgumentException(
                    "@UMLMultiplicity sur " + emplacement(method) + " : " + e.getMessage(), e);
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
    // La décision est la même que pour les autres compartiments, et se prend au même endroit : la
    // dérogation étant du @UMLInclude, elle bat la stratégie et les motifs du fichier de règles,
    // et seul @UMLExclude — hérité compris — l'écarte. La stratégie passée n'y décide donc de
    // rien ; la donner quand même est ce qui évite d'écrire ici une seconde version de la règle.
    public static ImmutableList<UmlField> methodesEnAttributs(Class<?> clazz,
        UMLVisibility strategy, Regles regles) {
        ImmutableList.Builder<UmlField> result = ImmutableList.builder();

        for (Method method : MembresOrdonnes.methodes(clazz, regles)) {
            UMLInclude inclusion = method.getAnnotation(UMLInclude.class);
            if (method.isSynthetic() || inclusion == null || !inclusion.asAttribute()) {
                continue;
            }
            // Même ricochet, même écart : sans lui, « asAttribute » sur un composant de record
            // écrirait deux fois la même ligne d'attribut.
            if (estAccesseurPropage(clazz, method)) {
                continue;
            }
            Optional<String> nom = retenu(clazz, method, strategy, regles);
            if (nom.isEmpty()) {
                continue;
            }
            String emplacement = emplacement(method);
            checkArgument(method.getParameterCount() == 0,
                messageNotationAttributAvecParametres(emplacement));
            checkArgument(method.getReturnType() != void.class,
                messageNotationAttributSansRetour(emplacement));

            try {
                String multiplicite =
                    multiplicite(method, method.getGenericReturnType(), regles);
                result.add(attribut(method, nom.get(),
                    // Le type est celui que la méthode rend — écrit comme sur un attribut, sans
                    // passer par « "omit": ["return void"] » : un attribut porte toujours son type.
                    typeDeLaPropriete(method.getGenericReturnType(), multiplicite, regles),
                    multiplicite, ConstraintExtractor.de(method, emplacement), regles));
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
            for (Method declaree : surtype.getDeclaredMethods()) {
                if (redefinit(method, declaree)
                    && declaree.isAnnotationPresent(UMLExclude.class)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Cette méthode redéfinit-elle celle que le surtype déclare ?
    //
    // POURQUOI PAS getDeclaredMethod
    //
    // Chercher par getDeclaredMethod(nom, types) exigerait des types de paramètres identiques, ce
    // qu'une interface générique ne donne jamais : « Boite<T>.ranger(T) » s'écrit
    // « ranger(Object) » dans le .class — l'effacement de type —, tandis que la réalisation
    // « BoiteDeTexte.ranger(String) » porte String. La recherche échouait donc en silence, et un
    // @UMLExclude posé sur Comparator, Function ou n'importe quelle interface métier paramétrée
    // reparaissait sur chacune de ses réalisations.
    //
    // La relation à tester est celle qu'établit le pont synthétique que le compilateur engendre
    // dans ce cas — même nom, même arité, chaque paramètre déclaré assignable depuis celui de la
    // candidate. Le pont lui-même ne peut pas servir à la lire : la boucle l'a déjà écarté par
    // isSynthetic, et à raison, l'utilisateur ne l'ayant pas écrit.
    //
    // Deux surcharges dont l'une accepte plus large que l'autre — « f(Object) » et « f(String) »
    // — se ressemblent au point d'être ici confondues. C'est le seul faux positif possible, il
    // demande que le surtype ait de surcroît exclu la sienne, et il va dans le sens de ce que
    // l'auteur a écrit : taire.
    private static boolean redefinit(Method candidate, Method declaree) {
        if (!declaree.getName().equals(candidate.getName())
            || declaree.getParameterCount() != candidate.getParameterCount()) {
            return false;
        }
        Class<?>[] attendus = declaree.getParameterTypes();
        Class<?>[] ecrits = candidate.getParameterTypes();
        for (int i = 0; i < attendus.length; i++) {
            if (!attendus[i].isAssignableFrom(ecrits[i])) {
                return false;
            }
        }
        return true;
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
