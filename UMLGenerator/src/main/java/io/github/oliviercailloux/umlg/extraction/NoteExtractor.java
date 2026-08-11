package io.github.oliviercailloux.umlg.extraction;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.extraction.member.MemberExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlField;
import io.github.oliviercailloux.umlg.model.UmlMethod;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

// Rassemble les bulles de commentaire d'une classe : les siennes, et celles de ses membres.
//
// Une note ne se dessine qu'accrochée à quelque chose. Cet extracteur reçoit donc les membres
// déjà retenus par MemberExtractor, et écarte les notes qui viseraient un membre absent de la
// boîte — un privé sous une stratégie PUBLIC, un getter écarté par le filtre anti-bruit. Sans ce
// tri, le rendu demanderait à PlantUML d'accrocher une bulle à un membre qu'il ne connaît pas.
//
// L'utilisateur, lui, n'a pas à le deviner : le processeur d'annotations l'avertit à la
// compilation, là où la note est écrite.
final class NoteExtractor {

    private NoteExtractor() {}

    // Sans règles de projet : les membres sont parcourus dans l'ordre alphabétique, et les bulles
    // en suivent l'ordre. Reste appelable telle quelle par EntityExtractor tant que celui-ci ne
    // fait pas descendre ses Regles jusqu'ici.
    static ImmutableList<UmlNote> extraire(Class<?> clazz, List<UmlField> fields,
        List<UmlMethod> methods) {
        return extraire(clazz, fields, methods, Regles.aucune());
    }

    // Avec les règles du projet : elles ne servent ici qu'à ranger les membres, donc les bulles,
    // dans l'ordre demandé — « order declaration » vaut pour les notes comme pour les boîtes.
    static ImmutableList<UmlNote> extraire(Class<?> clazz, List<UmlField> fields,
        List<UmlMethod> methods, Regles regles) {

        ImmutableList.Builder<UmlNote> result = ImmutableList.builder();

        // Les notes de la classe entière, sans ancre.
        for (UMLNote annotation : clazz.getAnnotationsByType(UMLNote.class)) {
            result.add(traduire(annotation, "", clazz.getName()));
        }

        // Les noms des membres réellement dessinés. Un Set parce que la question posée est
        // « celui-ci est-il là ? », et qu'une méthode surchargée y figure une seule fois — les
        // deux versions partagent de toute façon le même point d'ancrage dans PlantUML.
        ImmutableSet<String> champsVisibles =
            fields.stream().map(UmlField::name).collect(ImmutableSet.toImmutableSet());
        ImmutableSet<String> methodesVisibles =
            methods.stream().map(UmlMethod::name).collect(ImmutableSet.toImmutableSet());

        for (Field field : MembresOrdonnes.champs(clazz, regles)) {
            // Le nom affiché, et non celui du champ : une association écrite dans la boîte peut
            // y paraître sous son rôle cible, et c'est ce nom-là que PlantUML sait accrocher.
            // L'emplacement, lui, reste celui du code — c'est là que l'auteur ira corriger.
            String affiche = MemberExtractor.nomAffiche(field);

            if (field.isSynthetic() || !champsVisibles.contains(affiche)) {
                continue;
            }
            for (UMLNote annotation : field.getAnnotationsByType(UMLNote.class)) {
                result.add(traduire(annotation, affiche,
                    clazz.getName() + "." + field.getName()));
            }
        }

        for (Method method : MembresOrdonnes.methodes(clazz, regles)) {
            // Le nom affiché, comme pour un attribut : @UMLInclude(name = ...) peut renommer la
            // méthode, et la bulle doit s'accrocher au nom qui paraît sur le dessin.
            String affiche = MemberExtractor.nomAffiche(method);

            // Les deux compartiments, et non le seul compartiment des méthodes : une méthode
            // écrite en notation attribut — @UMLInclude(asAttribute = true) — quitte
            // extractMethods pour devenir une UmlField, et ne figure donc que dans
            // champsVisibles. La question posée est « ce membre est-il dessiné ? », pas « dans
            // quel compartiment ? ».
            if (method.isSynthetic()
                || !(methodesVisibles.contains(affiche) || champsVisibles.contains(affiche))) {
                continue;
            }
            for (UMLNote annotation : method.getAnnotationsByType(UMLNote.class)) {
                result.add(traduire(annotation, affiche,
                    clazz.getName() + "." + method.getName()));
            }
        }
        return result.build();
    }

    // Le modèle sait dire « ce texte est vide » ; lui seul sait dire « sur Client.solde ».
    private static UmlNote traduire(UMLNote annotation, String ancre, String emplacement) {
        try {
            return new UmlNote(annotation.text(), annotation.direction(), ancre);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                "@UMLNote sur " + emplacement + " : " + e.getMessage(), e);
        }
    }
}
