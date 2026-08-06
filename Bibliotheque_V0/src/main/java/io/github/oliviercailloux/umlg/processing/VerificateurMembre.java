package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// Vérifie les deux dérogations, @UMLInclude et @UMLExclude, posées sur un attribut ou une
// méthode.
final class VerificateurMembre {

    private final Rapporteur rapporteur;

    VerificateurMembre(Rapporteur rapporteur) {
        this.rapporteur = rapporteur;
    }

    void verifier(Element membre) {
        AnnotationMirror inclusion = rapporteur.miroir(membre, UMLInclude.class);
        AnnotationMirror exclusion = rapporteur.miroir(membre, UMLExclude.class);

        // Les deux à la fois : l'intention est indécidable. La bibliothèque saurait quoi faire
        // — @UMLExclude l'emporte —, mais appliquer une règle de départage reviendrait à choisir
        // à la place de l'auteur, en silence, dans un code qui se contredit.
        if (inclusion != null && exclusion != null) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries both @UMLInclude and "
                + "@UMLExclude: the intent is contradictory. Keep the one that says what you "
                + "mean.", membre, exclusion);
            // La contradiction signalée, tout ce qu'on pourrait ajouter porterait sur une
            // intention qu'on vient précisément de déclarer illisible.
            return;
        }

        AnnotationMirror presente = inclusion != null ? inclusion : exclusion;
        if (presente == null) {
            return;
        }

        // Une dérogation ne déroge à rien si la classe n'est pas sur le diagramme.
        TypeElement porteuse = (TypeElement) membre.getEnclosingElement();
        UMLClass classe = porteuse.getAnnotation(UMLClass.class);
        if (classe == null) {
            rapporteur.erreur(Membre.emplacement(membre) + ": the declaring class does not "
                + "carry @UMLClass, so it is not part of the diagram and this annotation has no "
                + "effect. Annotate " + porteuse.getQualifiedName() + ", or remove it.",
                membre, presente);
            return;
        }

        signalerDerogationSansEffet(membre, classe, inclusion != null, presente);
    }

    // Les cas où la dérogation est écrite, licite, et ne change pourtant rien au dessin.
    //
    // Aucun n'est une faute : le code décrit un diagramme parfaitement valide. Mais l'auteur a
    // écrit quelque chose pour obtenir un effet et n'en obtiendra aucun — mieux vaut le lui dire
    // ici que le lui laisser chercher sur l'image.
    private void signalerDerogationSansEffet(Element membre, UMLClass classe, boolean inclusion,
        AnnotationMirror presente) {

        // Un attribut promu en association ne consulte jamais la stratégie : MemberExtractor
        // l'écarte de la boîte quand le lien se dessine en trait, et l'y garde sans condition
        // quand il s'écrit dans la boîte. Dans les deux cas, une dérogation de visibilité n'a
        // rien à arbitrer — mais ce qu'elle rate n'est pas le même dessin.
        AnnotationMirror lien = rapporteur.miroir(membre, UMLAssociation.class);
        boolean association = lien != null;
        boolean trait = association && Notation.trait(rapporteur, lien);

        // La même question que se posera l'extraction, posée à la même règle, dérogations mises
        // de côté : « ce membre serait-il affiché si celle-ci n'était pas là ? »
        boolean visibleSansDerogation =
            VisibilityPolicy.estVisible(Membre.bits(membre), classe.strategy(), false, false);

        if (inclusion) {
            if (trait) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                    + "@UMLAssociation, so it is drawn as a link between boxes and never inside "
                    + "one; @UMLInclude cannot bring it back into the attribute compartment. "
                    + "Remove one of the two annotations, or set notation = ATTRIBUTE on the "
                    + "association.", membre, presente);
                return;
            }
            if (association) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude has no "
                    + "effect, this field carries @UMLAssociation(notation = ATTRIBUTE) and is "
                    + "therefore already shown in the attribute compartment, whatever the "
                    + "strategy. Remove it.", membre, presente);
                return;
            }
            // Restreint aux attributs : une méthode peut être publique et pourtant écartée par
            // le filtre anti-bruit — un getter —, auquel cas @UMLInclude sert bel et bien. Ce
            // filtre travaille sur des Method, que le processeur n'a pas ; plutôt que de
            // réécrire sa règle, on se tait sur les méthodes.
            if (Membre.estAttribut(membre) && visibleSansDerogation) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude has no "
                    + "effect, the field is already shown by strategy = " + classe.strategy()
                    + " on the declaring class. Remove it, or narrow the strategy.",
                    membre, presente);
            }
            return;
        }

        if (trait) {
            // Documenté, mais rarement anticipé : l'attribut n'était de toute façon pas dans la
            // boîte ; ce que @UMLExclude retire ici, c'est le trait entre les deux boîtes.
            rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                + "@UMLAssociation, so @UMLExclude removes the link between the two boxes, not "
                + "just a line inside one. Remove @UMLAssociation instead if that is what you "
                + "meant.", membre, presente);
            return;
        }
        if (association) {
            // L'association s'écrivant ici dans la boîte, @UMLExclude fait bien disparaître une
            // ligne — mais celle que @UMLAssociation demandait, pas un attribut ordinaire.
            rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                + "@UMLAssociation(notation = ATTRIBUTE), so @UMLExclude removes the very line "
                + "that association asked for, and the target class keeps no trace of it. Remove "
                + "@UMLAssociation instead if that is what you meant.", membre, presente);
            return;
        }
        if (!visibleSansDerogation) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLExclude has no effect, "
                + "strategy = " + classe.strategy() + " on the declaring class already hides "
                + "this member.", membre, presente);
        }
    }
}
