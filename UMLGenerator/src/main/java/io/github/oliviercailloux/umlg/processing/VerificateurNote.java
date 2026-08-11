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

// Vérifie les @UMLNote, posées sur une classe, un attribut ou une méthode.
//
// Une note n'existe qu'accrochée à quelque chose. Tout ce que ce vérificateur cherche, c'est un
// point d'ancrage qui ne sera pas là.
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
            // Le rang est écrit dans le texte parce que javac ne peut pas le montrer : plusieurs
            // @UMLNote sont emballées dans un conteneur synthétique, sans position source.
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

    // PlantUML ne pose une note de membre qu'à gauche ou à droite : « top » et « bottom » lui
    // sont propres aux notes de classe. Le rendu se rabat donc sur la droite (voir
    // NoteRenderer.placementSurMembre), et c'est ici qu'il faut le dire — sinon l'auteur
    // chercherait sa bulle en bas de la boîte sans jamais savoir pourquoi elle est à côté.
    //
    // Un avertissement, et non une erreur : le diagramme reste juste, seul son agencement
    // s'écarte de ce qui a été demandé.
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

    // Le texte est validé en construisant l'objet du modèle : la règle — non vide, pas seulement
    // des espaces — reste écrite une seule fois, dans UmlNote.
    private void verifierTexte(Element porteur, AnnotationMirror note, String contexte) {
        try {
            new UmlNote(rapporteur.texte(note, "text"), UMLDirection.NONE, "");
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(contexte + " : " + e.getMessage(), porteur, note,
                rapporteur.valeurEcrite(note, "text"));
        }
    }

    // Une note visant un membre que la boîte ne montre pas n'a rien où s'accrocher, et
    // l'extraction l'écarte en silence. C'est ici qu'il faut le dire.
    private void signalerAncrageIntrouvable(Element membre, UMLClass classe, AnnotationMirror note,
        String contexte) {

        if (membre.getKind() == ElementKind.FIELD
            && rapporteur.miroir(membre, UMLAssociation.class) != null) {
            // Un attribut promu en association a quitté la boîte pour devenir un trait, et une
            // note ne s'accroche pas à un trait — la bibliothèque ne dessine pas les notes de
            // lien. Poser la note sur la classe est la seule façon d'en parler.
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
