package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLLayout;
import io.github.oliviercailloux.umlg.annotation.UMLLayouts;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.TypeElement;

// Vérifie les @UMLLayout posées sur une classe, y compris répétées.
final class VerificateurLayout {

    private final Rapporteur rapporteur;
    private final Cible cible;

    VerificateurLayout(Rapporteur rapporteur, Cible cible) {
        this.rapporteur = rapporteur;
        this.cible = cible;
    }

    void verifier(TypeElement classe) {
        List<AnnotationMirror> placements =
            rapporteur.repetees(classe, UMLLayout.class, UMLLayouts.class);
        if (placements.isEmpty()) {
            return;
        }
        String contexte = "@UMLLayout on " + classe.getQualifiedName();

        if (classe.getAnnotation(UMLClass.class) == null) {
            rapporteur.erreur(contexte + ": the declaring class does not carry @UMLClass, so it "
                + "is not part of the diagram and there is no box to place. Annotate it, or "
                + "remove this annotation.", classe, placements.get(0));
            return;
        }

        // Les cibles déjà placées, et non les placements déjà écrits : deux directions
        // différentes vers la même boîte sont justement ce qu'il faut refuser.
        Set<String> deja = new HashSet<>();

        for (AnnotationMirror annotation : placements) {
            String nomCible = cible.resoudre(annotation, "target", contexte, classe);
            if (nomCible == null) {
                continue;
            }

            if (nomCible.contentEquals(classe.getQualifiedName())) {
                rapporteur.erreur(contexte + ": a class cannot be placed relative to itself.",
                    classe, annotation, rapporteur.valeurEcrite(annotation, "target"));
                continue;
            }

            if (!deja.add(nomCible)) {
                rapporteur.erreur(contexte + ": the placement of " + nomCible + " is declared "
                    + "twice; keep the one that says where you want it.", classe, annotation,
                    rapporteur.valeurEcrite(annotation, "target"));
                continue;
            }

            signalerPlacementSansCote(classe, annotation, contexte);
        }
    }

    // Un placement sans direction pose bien un lien invisible, mais ne dit pas de quel côté :
    // il rapproche les deux boîtes, et laisse le moteur décider du reste.
    //
    // Ce n'est pas une faute — c'est même parfois ce qu'on veut —, mais l'annotation est écrite
    // pour corriger un placement, et celle-ci ne corrige presque rien. Le dire coûte une ligne ;
    // le taire laisse chercher pourquoi le dessin n'a pas bougé.
    private void signalerPlacementSansCote(TypeElement classe, AnnotationMirror annotation,
        String contexte) {
        if (!UMLDirection.NONE.name().equals(rapporteur.texte(annotation, "direction"))) {
            return;
        }
        rapporteur.avertissement(contexte + ": direction = NONE only pulls the two boxes "
            + "together, without saying on which side. Set UP, DOWN, LEFT or RIGHT to place the "
            + "target where you want it.", classe, annotation);
    }
}
