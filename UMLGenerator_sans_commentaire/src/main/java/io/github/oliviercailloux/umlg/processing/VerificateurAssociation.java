package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

final class VerificateurAssociation {

    private static final ImmutableList<String> PARAMETRES_DE_TRAIT = ImmutableList.of(
        "type", "sourceCardinality", "sourceRole", "label", "direction");

    private final Rapporteur rapporteur;
    private final Cible cible;
    private final TypeAttribut typeAttribut;

    VerificateurAssociation(Rapporteur rapporteur, Cible cible, TypeAttribut typeAttribut) {
        this.rapporteur = rapporteur;
        this.cible = cible;
        this.typeAttribut = typeAttribut;
    }

    void verifier(Element champ) {
        AnnotationMirror annotation = rapporteur.miroir(champ, UMLAssociation.class);
        if (annotation == null) {
            return;
        }
        TypeElement porteuse = (TypeElement) champ.getEnclosingElement();
        String contexte = "@UMLAssociation on " + porteuse.getQualifiedName() + "."
            + champ.getSimpleName();

        if (porteuse.getAnnotation(UMLClass.class) == null) {
            rapporteur.erreur(contexte + ": the carrying class does not carry @UMLClass, so it is "
                + "not part of the diagram and this association would be drawn from nowhere. "
                + "Annotate " + porteuse.getQualifiedName() + ", or remove this annotation.",
                champ, annotation);
            return;
        }

        String nomCible = cible.resoudre(annotation, "target", contexte, champ,
            typeAttribut.elementSeul(champ.asType()));
        if (nomCible == null) {
            return;
        }

        if (!verifierValeurs(champ, annotation, contexte, nomCible)) {
            return;
        }
        if (!Notation.trait(rapporteur, annotation)) {
            signalerParametresSansPlace(champ, annotation, contexte);
        }
        signalerIncoherences(champ, annotation, contexte);
    }

    private void signalerParametresSansPlace(Element champ, AnnotationMirror annotation,
        String contexte) {
        ImmutableList<String> ecrits = PARAMETRES_DE_TRAIT.stream()
            .filter(membre -> rapporteur.valeurEcrite(annotation, membre) != null)
            .collect(ImmutableList.toImmutableList());

        if (ecrits.isEmpty()) {
            return;
        }
        rapporteur.avertissement(contexte + ": notation = ATTRIBUTE writes this association "
            + "inside the box, as a line of the attribute compartment, which carries only a type "
            + "and a multiplicity. " + String.join(", ", ecrits) + " decorate a path between two "
            + "boxes, and nothing will be drawn from them. Remove them, or switch back to "
            + "notation = ARROW.", champ, annotation,
            rapporteur.valeurEcrite(annotation, "notation"));
    }

    private boolean verifierValeurs(Element champ, AnnotationMirror annotation, String contexte,
        String nomCible) {
        try {
            UmlAssociation.builder(champ.getSimpleName().toString(), nomCible)
                .sourceCardinality(rapporteur.texte(annotation, "sourceCardinality"))
                .targetCardinality(rapporteur.texte(annotation, "targetCardinality"))
                .sourceRole(rapporteur.texte(annotation, "sourceRole"))
                .targetRole(rapporteur.texte(annotation, "targetRole"))
                .label(rapporteur.texte(annotation, "label"))
                .build();
            return true;
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(contexte + " : " + e.getMessage(), champ, annotation);
            return false;
        }
    }

    private void signalerIncoherences(Element champ, AnnotationMirror annotation, String contexte) {
        TypeMirror type = champ.asType();
        AnnotationValue cibleEcrite = rapporteur.valeurEcrite(annotation, "target");

        if (cibleEcrite == null) {
            signalerMultiplicite(champ, annotation, contexte, type);
            return;
        }
        TypeMirror typeCible = (TypeMirror) cibleEcrite.getValue();

        if (typeAttribut.comparableALaCible(type) && !typeAttribut.contient(type, typeCible)) {
            rapporteur.avertissement(contexte + ": target = " + typeCible + " does not appear "
                + "anywhere in the field's type (" + type + "). " + typeCible + " is what will be "
                + "drawn all the same, since the annotation is what counts — check that this is "
                + "the class you meant.", champ, annotation,
                rapporteur.valeurEcrite(annotation, "target"));
        }

        signalerMultiplicite(champ, annotation, contexte, type);
    }

    private void signalerMultiplicite(Element champ, AnnotationMirror annotation, String contexte,
        TypeMirror type) {
        String multiplicite = rapporteur.texte(annotation, "targetCardinality");
        boolean multivalue = typeAttribut.multivalue(type);
        AnnotationValue position = rapporteur.valeurEcrite(annotation, "targetCardinality");

        if (multivalue && multiplicite.isEmpty()) {
            String porteur = Notation.trait(rapporteur, annotation) ? "link" : "attribute";

            rapporteur.avertissement(contexte + ": the field's type (" + type + ") holds several "
                + "objects, but targetCardinality is not set, so the " + porteur + " will carry "
                + "no multiplicity at all. Only you know whether it is \"0..*\" or \"1..*\".",
                champ, annotation, position);
            return;
        }
        if (multiplicite.isEmpty()) {
            return;
        }
        if (multivalue && !permetPlusieurs(multiplicite)) {
            rapporteur.avertissement(contexte + ": targetCardinality = \"" + multiplicite
                + "\" allows at most one object, but the field's type (" + type + ") holds "
                + "several.", champ, annotation, position);
            return;
        }
        if (!multivalue && permetPlusieurs(multiplicite) && !typeAttribut.indetermine(type)) {
            rapporteur.avertissement(contexte + ": targetCardinality = \"" + multiplicite
                + "\" allows several objects, but the field's type (" + type + ") holds a single "
                + "one.", champ, annotation, position);
        }
    }

    private static boolean permetPlusieurs(String multiplicite) {
        int separateur = multiplicite.indexOf("..");
        String borneHaute =
            separateur < 0 ? multiplicite : multiplicite.substring(separateur + 2);
        return borneHaute.equals("*") || Long.parseLong(borneHaute) > 1;
    }
}
