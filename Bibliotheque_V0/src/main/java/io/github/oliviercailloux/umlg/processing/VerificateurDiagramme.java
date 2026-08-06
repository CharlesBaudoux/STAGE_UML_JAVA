package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLDiagram;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;

// Vérifie ce que @UMLDiagram déclare sur un package-info : le titre, et l'unicité de
// l'annotation dans le projet.
final class VerificateurDiagramme {

    private final Rapporteur rapporteur;

    // Le premier paquetage rencontré qui porte l'annotation. Cet état survit d'un tour de
    // compilation à l'autre, le processeur n'étant construit qu'une fois — c'est ce qui permet
    // de repérer un second paquetage annoté même s'il est compilé plus tard.
    private String premierPaquetage;

    VerificateurDiagramme(Rapporteur rapporteur) {
        this.rapporteur = rapporteur;
    }

    void verifier(Element paquetage) {
        AnnotationMirror annotation = rapporteur.miroir(paquetage, UMLDiagram.class);
        if (annotation == null) {
            return;
        }
        String nom = ((PackageElement) paquetage).getQualifiedName().toString();
        String contexte = "@UMLDiagram on package " + nom;

        if (signalerSecondDiagramme(paquetage, annotation, contexte, nom)) {
            // Le projet décrivant déjà deux diagrammes, examiner le contenu de celui-ci
            // reviendrait à discuter la formulation d'une phrase qu'on vient de déclarer de trop.
            return;
        }
        verifierTitre(paquetage, annotation, contexte);
        signalerAnnotationSansEffet(paquetage, annotation, contexte);
    }

    // Deux paquetages annotés décrivent deux diagrammes concurrents, et rien ne dit lequel fait
    // foi. C'est la même erreur que celle du scanner, dite plus tôt : à la compilation, à
    // l'endroit fautif, plutôt qu'au moment de dessiner.
    //
    // La compilation pouvant être partielle — javac ne voit pas toujours tout le projet d'un
    // coup —, ce contrôle ne remplace pas celui du scanner : il le double quand il le peut.
    private boolean signalerSecondDiagramme(Element paquetage, AnnotationMirror annotation,
        String contexte, String nom) {
        if (premierPaquetage == null || premierPaquetage.equals(nom)) {
            // Null : personne ne l'a encore prise. Égal au nôtre : le même paquetage revu à un
            // tour suivant.
            premierPaquetage = nom;
            return false;
        }
        rapporteur.erreur(contexte + ": package " + premierPaquetage + " already carries "
            + "@UMLDiagram. The annotation sets the diagram as a whole, so exactly one package "
            + "may carry it. Keep the one that describes the diagram you want.",
            paquetage, annotation);
        return true;
    }

    // Le titre est validé en construisant l'objet du modèle avec la valeur lue, comme partout
    // ailleurs : la règle n'est écrite qu'une fois, et le message de javac est mot pour mot celui
    // que la génération aurait produit.
    private void verifierTitre(Element paquetage, AnnotationMirror annotation, String contexte) {
        String titre = rapporteur.texte(annotation, "title");
        try {
            // Les deux autres paramètres ne participent à aucune validation — un énuméré et un
            // booléen ne peuvent pas être mal écrits —, d'où leurs valeurs par défaut ici.
            new UmlDiagram(titre, UMLFlow.TOP_TO_BOTTOM, true);
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(contexte + " : " + e.getMessage(), paquetage, annotation,
                rapporteur.valeurEcrite(annotation, "title"));
        }
    }

    // Une @UMLDiagram dont aucun paramètre n'est écrit ne demande rien : elle décrit exactement
    // le diagramme qu'on aurait sans elle.
    //
    // Ce n'est pas une faute — le diagramme reste celui qu'on veut —, mais personne n'écrit une
    // annotation pour n'obtenir aucun effet ; c'est plus vraisemblablement un paramètre oublié,
    // ou un package-info conservé après coup.
    private void signalerAnnotationSansEffet(Element paquetage, AnnotationMirror annotation,
        String contexte) {
        if (!annotation.getElementValues().isEmpty()) {
            return;
        }
        rapporteur.avertissement(contexte + ": no parameter is set, so this annotation describes "
            + "exactly the diagram you would get without it. Set title(), direction() or "
            + "hideEmptyMembers(), or remove it.", paquetage, annotation);
    }
}
