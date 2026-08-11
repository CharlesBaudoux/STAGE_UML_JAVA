package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.extraction.relation.CibleDeduite;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

// Ce que le type d'un attribut laisse deviner, du point de vue du diagramme.
//
// Rien ici ne décide de ce qui sera dessiné : la bibliothèque ne déduit jamais une relation d'un
// type — c'est @UMLAssociation qui dit tout, target() comprise. Ces deux questions ne servent
// donc qu'aux avertissements : comparer ce que l'annotation déclare à ce que le type suggère,
// et parler quand les deux se contredisent.
//
// La comparaison est possible ici, et seulement ici. Le processeur voit le type source complet,
// paramètres génériques inclus ; l'extraction, elle, travaille sur des classes compilées où
// « List<Commande> » n'est plus que « List ».
final class TypeAttribut {

    private final Types types;
    // Les deux types du JDK dont la présence signale « plusieurs objets à l'autre bout ».
    // Résolus une fois : ce sont des recherches dans la table des symboles de javac.
    private final TypeMirror collection;
    private final TypeMirror map;
    // Les enveloppes dont on sait extraire l'élément — les mêmes que TypeAffiche.CONTENEURS,
    // qui gouverne la déduction à l'exécution. Les deux listes doivent rester identiques :
    // c'est leur divergence qui faisait refuser à la compilation un « Optional<Commande> » que
    // la génération dessinait sans broncher.
    private final TypeMirror iterable;
    private final TypeMirror optional;

    TypeAttribut(Types types, Elements elements) {
        this.types = types;
        this.collection = erasureDe(types, elements, "java.util.Collection");
        this.map = erasureDe(types, elements, "java.util.Map");
        this.iterable = erasureDe(types, elements, "java.lang.Iterable");
        this.optional = erasureDe(types, elements, "java.util.Optional");
    }

    // « Ce type peut-il en contenir plusieurs ? » : un tableau, une Collection, une Map.
    //
    // Volontairement limité à ces trois formes. Un type maison qui regrouperait des objets sans
    // implémenter Collection existe, mais rien ne permet de le reconnaître — et un avertissement
    // qui se trompe coûte plus cher que celui qui manque un cas.
    boolean multivalue(TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return true;
        }
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeMirror brut = types.erasure(type);
        return (collection != null && types.isAssignable(brut, collection))
            || (map != null && types.isAssignable(brut, map));
    }

    // « La classe visée apparaît-elle quelque part dans ce type ? »
    //
    // Cherche en profondeur : le type lui-même, la composante d'un tableau, les paramètres
    // génériques et les bornes des jokers. « Map<String, List<Commande>> » contient donc bien
    // Commande, tout comme « Commande[] » ou « List<? extends Commande> ».
    boolean contient(TypeMirror type, TypeMirror cible) {
        return switch (type.getKind()) {
            case ARRAY -> contient(((ArrayType) type).getComponentType(), cible);
            case WILDCARD -> {
                WildcardType joker = (WildcardType) type;
                TypeMirror borne = joker.getExtendsBound() != null
                    ? joker.getExtendsBound()
                    : joker.getSuperBound();
                yield borne != null && contient(borne, cible);
            }
            case DECLARED -> {
                if (types.isSameType(types.erasure(type), types.erasure(cible))) {
                    yield true;
                }
                for (TypeMirror argument : ((DeclaredType) type).getTypeArguments()) {
                    if (contient(argument, cible)) {
                        yield true;
                    }
                }
                yield false;
            }
            // Une variable de type — « T » — pourrait valoir n'importe quoi : on ne peut ni
            // confirmer ni infirmer, et le doute doit profiter à l'utilisateur.
            case TYPEVAR -> true;
            default -> false;
        };
    }

    // La classe que ce type désigne, quand il n'en désigne qu'une : le pendant, côté
    // compilation, de TypeAffiche.elementSeul et du repli de CibleDeduite.
    //
    // « List<Commande> » et « Optional<Commande> » donnent Commande, « Commande » se donne
    // lui-même. Null pour tout ce qui n'en désigne pas une seule — un conteneur écrit sans
    // paramètre, un tableau —, auquel cas la cible doit être écrite.
    //
    // Une variable de type désigne sa borne : « T extends Commande » vaut Commande, ce que rend
    // déjà l'effacement. C'est exactement ce que voit la génération, où field.getType() rend
    // l'effacement du type déclaré ; s'en écarter faisait refuser à la compilation un champ que
    // la génération dessinait sans broncher — le plus coûteux des écarts, puisqu'il bloque.
    // Une variable non bornée s'efface en Object, qui ne désigne aucune classe : la cible doit
    // alors être écrite, et la génération l'exige tout autant.
    //
    // Une Map n'est pas un conteneur au sens d'ici : elle se donne elle-même, exactement comme
    // à l'exécution, et c'est alors l'absence de @UMLClass sur Map qui sera signalée.
    TypeMirror elementSeul(TypeMirror type) {
        TypeMirror examine = type.getKind() == TypeKind.TYPEVAR ? types.erasure(type) : type;
        if (examine.getKind() != TypeKind.DECLARED || indetermine(examine)) {
            return null;
        }
        DeclaredType declare = (DeclaredType) examine;
        if (!estUnConteneur(examine)) {
            // Un type ordinaire est lui-même la cible ; ses éventuels paramètres ne changent
            // rien à la classe visée.
            return types.erasure(examine);
        }
        // Un conteneur : c'est son unique paramètre qui nomme la cible.
        if (declare.getTypeArguments().size() != 1) {
            return null;
        }
        TypeMirror argument = declare.getTypeArguments().get(0);
        return argument.getKind() == TypeKind.DECLARED ? types.erasure(argument) : null;
    }

    // Ce qu'il faut dire quand elementSeul n'a rien trouvé et que target() n'est pas écrit.
    //
    // Le texte vient de CibleDeduite, à qui la règle appartient : le refus de javac est mot pour
    // mot celui de la génération, et nomme comme lui le type fautif. La version du processeur
    // l'omettait, laissant l'auteur chercher lequel de ses champs était visé — c'est exactement
    // ainsi que deux textes recopiés à la main finissent par diverger.
    String plainteCibleIndeductible(String contexte, TypeMirror type) {
        String nom = nomSimple(type);
        // Le partage de la génération : une collection dit qu'elle ne sait pas ce qu'elle
        // contient, tout autre type qu'il ne nomme aucune classe.
        return estIterable(type)
            ? CibleDeduite.messageContenuIndeductible(contexte, nom)
            : CibleDeduite.messageCibleIndeductible(contexte, nom);
    }

    // Le nom court d'un type, celui que la génération lit sur la Class : « List », « int »,
    // « Commande[] ». asElement ne rend rien pour un tableau ou une primitive, dont le toString
    // de l'effacement est déjà la bonne réponse.
    private String nomSimple(TypeMirror type) {
        TypeMirror brut = types.erasure(type);
        Element element = types.asElement(brut);
        return element == null ? brut.toString() : element.getSimpleName().toString();
    }

    private boolean estIterable(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED && iterable != null
            && types.isAssignable(types.erasure(type), iterable);
    }

    // « Ce type est-il une enveloppe dont l'élément est la vraie propriété ? » : un Iterable ou
    // un Optional, c'est-à-dire ce que TypeAffiche.estUnConteneur reconnaît à l'exécution.
    private boolean estUnConteneur(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) {
            return false;
        }
        TypeMirror brut = types.erasure(type);
        return (iterable != null && types.isAssignable(brut, iterable))
            || (optional != null && types.isAssignable(brut, optional));
    }

    // Vrai si l'on peut confronter ce type à la cible déclarée sans risquer un faux reproche.
    //
    // Deux cas s'y refusent. Les types indéterminés, d'abord. Les collections écrites sans
    // paramètre — « List » nu, ou toute collection maison non générique — ensuite : elles ne
    // disent rien de ce qu'elles contiennent, et c'est exactement le manque que target() est là
    // pour combler. Reprocher à l'utilisateur d'avoir eu recours au remède serait absurde.
    boolean comparableALaCible(TypeMirror type) {
        if (indetermine(type)) {
            return false;
        }
        return !(type.getKind() == TypeKind.DECLARED && (multivalue(type) || estUnConteneur(type))
            && ((DeclaredType) type).getTypeArguments().isEmpty());
    }

    // Vrai si le type ne permet aucune comparaison utile : une variable de type, un type
    // d'erreur (classe pas encore compilée), ou Object, qui contient tout par construction.
    boolean indetermine(TypeMirror type) {
        return type.getKind() == TypeKind.TYPEVAR
            || type.getKind() == TypeKind.ERROR
            || (type.getKind() == TypeKind.DECLARED
                && types.erasure(type).toString().equals("java.lang.Object"));
    }

    private static TypeMirror erasureDe(Types types, Elements elements, String nom) {
        TypeElement element = elements.getTypeElement(nom);
        // Null si la classe est absente du chemin de compilation — inconcevable pour java.util,
        // mais un processeur qui suppose ne fait que déplacer la panne.
        return element == null ? null : types.erasure(element.asType());
    }
}
