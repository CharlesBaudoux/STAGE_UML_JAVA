package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

// Vérifie les deux dérogations, @UMLInclude et @UMLExclude, posées sur un attribut ou une
// méthode.
final class VerificateurMembre {

    private final Rapporteur rapporteur;
    private final Strategie strategie;

    VerificateurMembre(Rapporteur rapporteur, Strategie strategie) {
        this.rapporteur = rapporteur;
        this.strategie = strategie;
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

        if (inclusion != null && !verifierRenommage(membre, inclusion)) {
            return;
        }
        if (inclusion != null && !verifierNotationAttribut(membre, inclusion)) {
            return;
        }

        signalerDerogationSansEffet(membre, classe, inclusion != null, presente);
    }

    // Le nom que @UMLInclude(name = ...) redonne au membre : trois écarts à dire, deux fautes et
    // une inutilité. Rend faux quand une erreur a été signalée — le reste n'aurait plus de sens.
    private boolean verifierRenommage(Element membre, AnnotationMirror inclusion) {
        String nom = membre.getAnnotation(UMLInclude.class).name();
        if (nom.isEmpty()) {
            return true;
        }
        // Un nom fait d'espaces dessinerait une ligne au nom invisible.
        if (nom.isBlank()) {
            rapporteur.erreur(Membre.emplacement(membre) + ": @UMLInclude(name = ...) cannot be "
                + "made of whitespace only. Write a real name, or leave the attribute out.",
                membre, inclusion);
            return false;
        }
        // Deux noms pour la même ligne : le rôle cible d'une association écrite dans la boîte
        // est déjà le nom de la propriété, et rien ne dirait lequel des deux l'emporte.
        AnnotationMirror lien = rapporteur.miroir(membre, UMLAssociation.class);
        if (lien != null && !Notation.trait(rapporteur, lien)
            && !membre.getAnnotation(UMLAssociation.class).targetRole().isEmpty()) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries both targetRole = \""
                + membre.getAnnotation(UMLAssociation.class).targetRole()
                + "\" and @UMLInclude(name = \"" + nom + "\"): two names for the same line. "
                + "Keep only one of the two.", membre, inclusion);
            return false;
        }
        if (nom.equals(membre.getSimpleName().toString())) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude(name = \"" + nom
                + "\") renames this member to the name it already has. Remove the attribute.",
                membre, inclusion);
        }
        return true;
    }

    // La notation attribut — asAttribute = true — exige une méthode sans paramètre qui rend
    // quelque chose : les parenthèses retirées, il ne reste que le nom et le type, et il faut
    // que les deux existent. Rend faux quand une erreur a été signalée.
    private boolean verifierNotationAttribut(Element membre, AnnotationMirror inclusion) {
        if (!membre.getAnnotation(UMLInclude.class).asAttribute()) {
            return true;
        }
        if (membre.getKind() != ElementKind.METHOD) {
            rapporteur.erreur(Membre.emplacement(membre) + ": @UMLInclude(asAttribute = true) is "
                + "for methods; a field is already an attribute. Remove the attribute.",
                membre, inclusion);
            return false;
        }
        ExecutableElement methode = (ExecutableElement) membre;
        if (!methode.getParameters().isEmpty()) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries @UMLInclude(asAttribute = "
                + "true) but takes parameters: an attribute has none. Remove the parameters, or "
                + "the notation.", membre, inclusion);
            return false;
        }
        if (methode.getReturnType().getKind() == TypeKind.VOID) {
            rapporteur.erreur(Membre.emplacement(membre) + " carries @UMLInclude(asAttribute = "
                + "true) but returns nothing: an attribute is its type. Return something, or "
                + "remove the notation.", membre, inclusion);
            return false;
        }
        return true;
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
        //
        // Elle n'a de réponse que si la classe déclare sa stratégie. Quand elle s'en remet au
        // fichier de règles, que javac ne lit pas, les deux avertissements qui en dépendent se
        // taisent — ceux qui portent sur les associations, eux, restent dus : ils ne consultent
        // aucune stratégie.
        // Les composants de record et leurs accesseurs échappent aux raisonnements qui suivent,
        // pour deux raisons qui se cumulent : le composant est dessiné quelle que soit la
        // stratégie — « déjà caché par strategy NONE » serait un contresens, @UMLExclude est
        // justement le seul moyen de le taire — et l'annotation écrite sur le composant est
        // clonée par javac sur le champ et sur l'accesseur, si bien qu'un avertissement
        // désignerait une ligne que l'auteur n'a pas écrite.
        if (Membre.vientDUnComposant(membre)) {
            return;
        }

        boolean strategieConnue = strategie.connue(classe);
        boolean visibleSansDerogation = strategieConnue
            && VisibilityPolicy.estVisible(Membre.bits(membre), strategie.effective(classe),
                false, false);

        if (inclusion) {
            // Un renommage est un effet : dès que name est écrit, @UMLInclude fait quelque
            // chose, et les avertissements « sans effet » ci-dessous n'ont plus lieu d'être.
            // Le seul cas où le nom lui-même reste lettre morte — un trait entre boîtes, qui
            // n'écrit aucune ligne — garde son avertissement, car rien du tout n'est dessiné.
            boolean renomme = !membre.getAnnotation(UMLInclude.class).name().isEmpty();

            if (trait) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": this field also carries "
                    + "@UMLAssociation, so it is drawn as a link between boxes and never inside "
                    + "one; @UMLInclude cannot bring it back into the attribute compartment. "
                    + "Remove one of the two annotations, or set notation = ATTRIBUTE on the "
                    + "association.", membre, presente);
                return;
            }
            if (association) {
                if (!renomme) {
                    rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude has no "
                        + "effect, this field carries @UMLAssociation(notation = ATTRIBUTE) and "
                        + "is therefore already shown in the attribute compartment, whatever "
                        + "the strategy. Remove it.", membre, presente);
                }
                return;
            }
            // Restreint aux attributs : une méthode peut être publique et pourtant écartée par
            // le filtre anti-bruit — un getter —, auquel cas @UMLInclude sert bel et bien. Ce
            // filtre travaille sur des Method, que le processeur n'a pas ; plutôt que de
            // réécrire sa règle, on se tait sur les méthodes.
            //
            // Les attributs, eux, ont leur propre façon d'être écartés : un motif « hide log* ».
            // Un @UMLInclude qui y déroge n'est pas sans effet, il est même le seul moyen de
            // ramener le champ — le reproche visait exactement le remède.
            if (!renomme && strategieConnue && Membre.estAttribut(membre)
                && visibleSansDerogation
                && !strategie.champMasqueParUneRegle(membre.getSimpleName().toString())) {
                rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLInclude has no "
                    + "effect, the field is already shown by strategy = "
                    + strategie.effective(classe)
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
        if (strategieConnue && !visibleSansDerogation) {
            rapporteur.avertissement(Membre.emplacement(membre) + ": @UMLExclude has no effect, "
                + "strategy = " + strategie.effective(classe)
                + " on the declaring class already hides "
                + "this member.", membre, presente);
        }
    }
}
