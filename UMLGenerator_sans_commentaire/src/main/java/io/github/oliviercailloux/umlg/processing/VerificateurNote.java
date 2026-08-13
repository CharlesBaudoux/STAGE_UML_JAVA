package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLNotes;
import io.github.oliviercailloux.umlg.model.UmlNote;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

final class VerificateurNote {

    private final Rapporteur rapporteur;
    private final Strategie strategie;

    VerificateurNote(Rapporteur rapporteur, Strategie strategie) {
        this.rapporteur = rapporteur;
        this.strategie = strategie;
    }

    void verifier(Element porteur) {
        List<AnnotationMirror> notes =
            rapporteur.repetees(porteur, UMLNote.class, UMLNotes.class);
        if (notes.isEmpty()) {
            return;
        }
        boolean surClasse = porteur.getKind().isClass() || porteur.getKind().isInterface();
        TypeElement boite = surClasse
            ? (TypeElement) porteur
            : (TypeElement) porteur.getEnclosingElement();
        String emplacement = surClasse
            ? boite.getQualifiedName().toString()
            : Membre.emplacement(porteur);
        String contexte = "@UMLNote on " + emplacement;

        UMLClass classe = boite.getAnnotation(UMLClass.class);
        if (!rapporteur.exigerBoite(boite, contexte, "the note would have nothing to hang on",
            porteur, notes.get(0))) {
            return;
        }

        for (int rang = 0; rang < notes.size(); rang++) {
            AnnotationMirror note = notes.get(rang);
            String contexteDeLaNote = Rapporteur.contexteDeLaRepetee("UMLNote", emplacement, rang,
                notes.size());
            verifierTexte(porteur, note, contexteDeLaNote);
            if (!surClasse) {
                signalerDirectionImpossible(porteur, note, contexteDeLaNote);
            }
        }

        if (!surClasse) {
            signalerAncrageIntrouvable(porteur, classe, notes.get(0), contexte);
        }
    }

    private void signalerDirectionImpossible(Element membre, AnnotationMirror note,
        String contexte) {
        String direction = rapporteur.texte(note, "direction");

        if (!direction.equals(UMLDirection.UP.name()) && !direction.equals(
            UMLDirection.DOWN.name())) {
            return;
        }
        rapporteur.avertissement(contexte + ": direction = " + direction + " has no effect on a "
            + "note attached to a member; PlantUML only places those to the left or to the right, "
            + "and the bubble will be drawn on the right. Use LEFT or RIGHT, or move the note "
            + "onto the class, where the four sides exist.", membre, note,
            rapporteur.valeurEcrite(note, "direction"));
    }

    private void verifierTexte(Element porteur, AnnotationMirror note, String contexte) {
        try {
            new UmlNote(rapporteur.texte(note, "text"), UMLDirection.NONE, "");
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(contexte + " : " + e.getMessage(), porteur, note,
                rapporteur.valeurEcrite(note, "text"));
        }
    }

    private void signalerAncrageIntrouvable(Element membre, UMLClass classe, AnnotationMirror note,
        String contexte) {

        if (membre.getKind() == ElementKind.FIELD
            && rapporteur.miroir(membre, UMLAssociation.class) != null) {
            rapporteur.avertissement(contexte + ": this field carries @UMLAssociation, so it is "
                + "drawn as a link and no longer sits inside the box; a note cannot hang on a "
                + "link. Move the note onto the class itself.", membre, note);
            return;
        }

        if (strategie.membreAffiche(membre, classe) != Strategie.Affichage.MASQUE) {
            return;
        }
        rapporteur.avertissement(contexte + ": this member is not shown on the diagram "
            + "(strategy = "
            + strategie.effective(classe) + " on the declaring class), so the note has "
            + "nothing to hang on and will not be drawn. Add @UMLInclude, or move the note onto "
            + "the class.", membre, note);
    }
}
