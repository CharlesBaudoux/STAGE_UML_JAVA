package io.github.oliviercailloux.umlg.extraction.relation;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.extraction.ConstraintExtractor;
import io.github.oliviercailloux.umlg.extraction.MembresOrdonnes;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import java.lang.reflect.Field;

// Relit les attributs d'une classe et en tire les associations.
public final class AssociationExtractor implements RelationExtractor {

    // Le défaut du contrat, redit ici pour son seul type de retour : qui tient un
    // AssociationExtractor attend des UmlAssociation, et non le « ? extends UmlRelation » que
    // l'interface promet à qui ne sait pas à quel extracteur il parle.
    @Override
    public ImmutableList<UmlAssociation> extraire(Class<?> clazz) {
        return extraire(clazz, Regles.aucune());
    }

    // Les règles du projet n'ajoutent ni ne retirent aucun lien : elles disent dans
    // quel ordre les attributs sont relus, donc dans quel ordre les traits sont écrits. Un projet
    // qui demande « order declaration » l'obtient sur ses liens comme sur ses compartiments.
    @Override
    public ImmutableList<UmlAssociation> extraire(Class<?> clazz, Regles regles) {
        ImmutableList.Builder<UmlAssociation> result = ImmutableList.builder();

        // Les attributs déclarés par cette classe, quelle que soit leur visibilité, mais sans
        // ceux hérités — l'héritage se dessine par un autre trait. L'ordre est stable, sans quoi
        // deux exécutions dessineraient les mêmes liens dans un ordre différent.
        for (Field field : MembresOrdonnes.champs(clazz, regles)) {
            // isSynthetic écarte ce que le compilateur a fabriqué (référence this$0 d'une classe
            // interne, tables de switch sur enum) : ce code n'existe pas pour l'utilisateur.
            if (field.isSynthetic() || !field.isAnnotationPresent(UMLAssociation.class)) {
                continue;
            }
            // @UMLExclude a le dernier mot : l'attribut est annoté, mais on n'en veut pas.
            if (field.isAnnotationPresent(UMLExclude.class)) {
                continue;
            }
            UMLAssociation annotation = field.getAnnotation(UMLAssociation.class);

            // L'autre notation du même lien : il s'écrit dans la boîte de départ, et c'est
            // MemberExtractor qui en fait une ligne du compartiment des attributs. Rien à
            // dessiner ici — un trait en plus doublerait ce que la boîte dit déjà.
            if (annotation.notation() != UMLNotation.ARROW) {
                continue;
            }
            result.add(traduire(field, annotation, field.isAnnotationPresent(UMLDerived.class)));
        }
        return result.build();
    }

    // Recopie les paramètres de l'annotation dans le modèle, un pour un.
    //
    // La dérivation ne vient pas de @UMLAssociation mais de @UMLDerived, posée sur le même
    // attribut : quand les deux se rencontrent, c'est le lien qui est dit dérivé, l'attribut
    // n'étant de toute façon plus dans la boîte.
    private static UmlAssociation traduire(Field field, UMLAssociation annotation,
        boolean derived) {
        // Calculé une fois, réutilisé dans chaque message d'erreur : le lecteur saura toujours
        // quel attribut de quelle classe est en cause.
        String contexte = "@UMLAssociation sur " + Emplacement.de(field);
        // target() est obligatoire (sans default), donc toujours renseignée : reste à vérifier
        // qu'elle désigne bien une classe du diagramme.
        // Écrite, ou lue dans le type de l'attribut : « List<Commande> » dit déjà la cible.
        Class<?> cible = CibleDeduite.de(field, annotation, contexte);
        TargetResolver.validerCible(cible, contexte, "target()");

        try {
            // cible.getName() : le nom complet, celui qui sert d'identité aux entités.
            return UmlAssociation.builder(field.getName(), cible.getName())
                .type(annotation.type())
                .sourceCardinality(annotation.sourceCardinality())
                .targetCardinality(annotation.targetCardinality())
                .sourceRole(annotation.sourceRole())
                .targetRole(annotation.targetRole())
                .label(annotation.label())
                .direction(annotation.direction())
                .derived(derived)
                // Règle d'arbitrage : sur un attribut porteur de @UMLAssociation, la contrainte
                // vise le lien et non l'attribut — lequel n'est plus dans la boîte.
                .constraints(ConstraintExtractor.de(field, Emplacement.de(field)))
                .build();
        } catch (IllegalArgumentException | NullPointerException e) {
            // Le modèle a détecté la faute (une multiplicité "3..1", par exemple) mais ignore
            // d'où elle vient. On rejoue son message en le préfixant de l'emplacement, en
            // gardant l'exception d'origine comme cause.
            throw new IllegalArgumentException(contexte + " : " + e.getMessage(), e);
        }
    }
}
