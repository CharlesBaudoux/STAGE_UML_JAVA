package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// Vérifie @UMLDerived, posée sur un attribut ou une méthode.
//
// L'annotation ne porte aucun paramètre : il n'y a donc rien à valider dans ce qu'elle dit, et
// tout à vérifier dans ce qu'elle produira. Une barre oblique ne se voit que si l'élément
// qu'elle marque est dessiné ; ce vérificateur ne parle que de cela.
final class VerificateurDerive {

    private final Rapporteur rapporteur;
    private final Strategie strategie;

    VerificateurDerive(Rapporteur rapporteur, Strategie strategie) {
        this.rapporteur = rapporteur;
        this.strategie = strategie;
    }

    void verifier(Element membre) {
        AnnotationMirror annotation = rapporteur.miroir(membre, UMLDerived.class);
        if (annotation == null) {
            return;
        }
        TypeElement porteuse = (TypeElement) membre.getEnclosingElement();
        if (!rapporteur.exigerBoite(porteuse, Membre.emplacement(membre),
            "this annotation has no effect", membre, annotation)) {
            return;
        }
        UMLClass classe = porteuse.getAnnotation(UMLClass.class);

        AnnotationMirror association = rapporteur.miroir(membre, UMLAssociation.class);
        if (association != null) {
            // Sous la notation ATTRIBUTE, la barre oblique retrouve sa place ordinaire : devant
            // le nom de l'attribut, lequel est écrit dans la boîte quelle que soit la stratégie.
            // Il n'y a donc rien à signaler — ni rôle manquant, ni membre invisible.
            if (Notation.trait(rapporteur, association)) {
                signalerLienSansRole(membre, annotation, association);
            }
            return;
        }
        signalerMembreInvisible(membre, classe, annotation);
    }

    // Sur un attribut porteur de @UMLAssociation, la dérivation quitte l'attribut pour le lien,
    // et la barre oblique se pose devant le rôle cible — le nom que porte la propriété sur le
    // dessin. Sans rôle écrit, cette propriété n'est nommée nulle part : il n'y a rien à marquer,
    // et l'annotation reste sans effet visible.
    private void signalerLienSansRole(Element membre, AnnotationMirror annotation,
        AnnotationMirror association) {
        if (!rapporteur.texte(association, "targetRole").isEmpty()) {
            return;
        }
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLDerived marks the link as "
            + "derived, and the slash goes in front of the target role — but targetRole is not "
            + "set on @UMLAssociation, so nothing on the link would carry it. Set targetRole, or "
            + "remove @UMLDerived.", membre, annotation);
    }

    // Un membre que la classe ne montre pas ne peut pas montrer sa barre oblique.
    //
    // La question vaut pour les attributs comme pour les méthodes : le filtre anti-bruit, que le
    // processeur n'a pas sous la main, ne fait qu'en retirer davantage. Ce que la stratégie
    // masque déjà est donc masqué à coup sûr, et le dire ici ne risque aucun faux reproche.
    private void signalerMembreInvisible(Element membre, UMLClass classe,
        AnnotationMirror annotation) {
        if (strategie.membreAffiche(membre, classe) != Strategie.Affichage.MASQUE) {
            return;
        }
        rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLDerived has no visible "
            + "effect, this member is not shown on the diagram (strategy = "
            + strategie.effective(classe)
            + " on the declaring class). Add @UMLInclude if you want to see it, slash included.",
            membre, annotation);
    }
}
