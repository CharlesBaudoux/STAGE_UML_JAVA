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

// Vérifie ce que @UMLAssociation déclare sur un attribut.
final class VerificateurAssociation {

    // Les paramètres qui ne veulent dire quelque chose que le long d'un trait. Sous la notation
    // ATTRIBUTE, l'annotation garde ce qu'une ligne de boîte sait porter : target — le type
    // affiché —, targetMultiplicity — la multiplicité entre crochets — et targetRole, qui est
    // alors le nom même de la propriété.
    private static final ImmutableList<String> PARAMETRES_DE_TRAIT = ImmutableList.of(
        "type", "sourceMultiplicity", "sourceRole", "label", "direction");

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

        // Une association part d'une boîte : sans @UMLClass sur la classe porteuse, il n'y a
        // aucune boîte d'où partir, et l'annotation ne produirait rien. C'est presque toujours
        // un @UMLClass oublié.
        if (!rapporteur.exigerBoite(porteuse, contexte,
            "this association would be drawn from nowhere", champ, annotation)) {
            return;
        }

        // La cible est écrite, ou lue dans le type de l'attribut — « List<Commande> » la dit.
        String nomCible = cible.resoudre(annotation, "target", contexte, champ,
            typeAttribut.elementSeul(champ.asType()),
            typeAttribut.plainteCibleIndeductible(contexte, champ.asType()));
        if (nomCible == null) {
            // La cible est déjà en faute ; poursuivre ne ferait qu'empiler des messages sur la
            // même ligne.
            return;
        }

        // Avant la validation des valeurs, et non après : sous la notation ATTRIBUTE, un
        // paramètre de trait est refusé s'il est mal écrit — comme partout ailleurs — et
        // signalé s'il est inutile. Signaler d'abord épargne à l'auteur de corriger une valeur
        // pour s'entendre dire ensuite de l'effacer.
        if (!Notation.trait(rapporteur, annotation)) {
            signalerParametresSansPlace(champ, annotation, contexte);
        }
        if (!verifierValeurs(champ, annotation, contexte, nomCible)) {
            // Une multiplicité illisible ne peut pas être confrontée au type de l'attribut : le
            // second message ne ferait que répéter le premier autrement.
            return;
        }
        signalerIncoherences(champ, annotation, contexte);
    }

    // Les paramètres qui décorent un trait, et qu'une ligne d'attribut ne sait pas porter.
    //
    // Une ligne de boîte n'a qu'un type et une multiplicité. Le losange d'une composition, le
    // rôle écrit le long du trait, le libellé posé au milieu, la contrainte de placement : tout
    // cela suppose un trait, et disparaît avec lui. L'auteur a écrit ces valeurs pour obtenir un
    // effet qu'il n'aura pas, d'où l'avertissement — le diagramme, lui, reste dessinable, ce qui
    // interdit l'erreur.
    //
    // Inutile ne veut pas dire dispensé de sens : verifierValeurs valide ces paramètres quelle
    // que soit la notation, et la génération les refuse de même. Une multiplicité « 3..1 » reste
    // une multiplicité impossible, qu'elle finisse dessinée ou non ; les tolérer ici serait
    // accepter qu'une annotation dise n'importe quoi pourvu que personne ne la lise, et graver
    // dans le code une valeur fausse que la première bascule vers ARROW ferait surgir.
    private void signalerParametresSansPlace(Element champ, AnnotationMirror annotation,
        String contexte) {
        // Seuls les paramètres réellement écrits dans le source sont cités : reprocher un défaut
        // que personne n'a tapé serait incompréhensible.
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

    // Les multiplicités, les rôles et le label sont validés en construisant l'objet du modèle
    // avec ces mêmes valeurs.
    //
    // Aucune règle n'est donc réécrite ici : la grammaire des multiplicités reste écrite une
    // seule fois, dans UmlAssociation, et le message affiché par javac est mot pour mot celui
    // que la génération aurait produit. Les paramètres qui ne participent à aucune validation
    // — type, direction — sont laissés à leur défaut, leur lecture serait inutile.
    private boolean verifierValeurs(Element champ, AnnotationMirror annotation, String contexte,
        String nomCible) {
        try {
            UmlAssociation.builder(champ.getSimpleName().toString(), nomCible)
                .sourceMultiplicity(rapporteur.texte(annotation, "sourceMultiplicity"))
                .targetMultiplicity(rapporteur.texte(annotation, "targetMultiplicity"))
                .sourceRole(rapporteur.texte(annotation, "sourceRole"))
                .targetRole(rapporteur.texte(annotation, "targetRole"))
                .label(rapporteur.texte(annotation, "label"))
                .build();
            return true;
        } catch (IllegalArgumentException e) {
            // Le message du modèle nomme lui-même le paramètre fautif ; l'erreur est donc posée
            // sur l'annotation entière sans que le lecteur ait à chercher.
            rapporteur.erreur(contexte + " : " + e.getMessage(), champ, annotation);
            return false;
        }
    }

    // Ce que l'annotation déclare, confronté à ce que le type de l'attribut laisse attendre.
    //
    // Rien de tout cela n'est une faute : l'annotation fait foi, et un modèle peut légitimement
    // s'écarter du code — c'est même la raison d'être de @UMLAssociation. Mais l'écart est assez
    // souvent une étourderie, un copier-coller ou une multiplicité oubliée, pour mériter d'être
    // dit. Chaque contrôle se tait dès que le type ne permet pas de conclure.
    private void signalerIncoherences(Element champ, AnnotationMirror annotation, String contexte) {
        TypeMirror type = champ.asType();
        AnnotationValue cibleEcrite = rapporteur.valeurEcrite(annotation, "target");

        // Confronter la cible au type n'a de sens que si l'utilisateur a écrit les deux. Quand
        // target() est laissé de côté, la cible EST tirée du type : les comparer reviendrait à
        // comparer le type à lui-même — et la valeur lue serait la sentinelle void, dont le
        // message « target = void does not appear in the field's type » n'apprenait rien à
        // personne, sur chacune des associations du projet.
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

    // La multiplicité côté cible, confrontée au nombre d'objets que le type peut tenir.
    private void signalerMultiplicite(Element champ, AnnotationMirror annotation, String contexte,
        TypeMirror type) {
        String multiplicite = rapporteur.texte(annotation, "targetMultiplicity");
        boolean multivalue = typeAttribut.multivalue(type);
        // Souligne « targetMultiplicity = "1" » là où il est écrit ; null quand la valeur vient du
        // défaut, et le Rapporteur se rabat alors sur l'annotation entière.
        AnnotationValue position = rapporteur.valeurEcrite(annotation, "targetMultiplicity");

        if (multivalue && multiplicite.isEmpty()) {
            // « le lien » ou « l'attribut », selon la notation : c'est la même multiplicité
            // manquante, mais elle manquerait à deux endroits différents du dessin.
            String porteur = Notation.trait(rapporteur, annotation) ? "link" : "attribute";

            rapporteur.avertissement(contexte + ": the field's type (" + type + ") holds several "
                + "objects, but targetMultiplicity is not set, so the " + porteur + " will carry "
                + "no multiplicity at all. Only you know whether it is \"0..*\" or \"1..*\".",
                champ, annotation, position);
            return;
        }
        if (multiplicite.isEmpty()) {
            return;
        }
        if (multivalue && !permetPlusieurs(multiplicite)) {
            rapporteur.avertissement(contexte + ": targetMultiplicity = \"" + multiplicite
                + "\" allows at most one object, but the field's type (" + type + ") holds "
                + "several.", champ, annotation, position);
            return;
        }
        if (!multivalue && permetPlusieurs(multiplicite) && !typeAttribut.indetermine(type)) {
            rapporteur.avertissement(contexte + ": targetMultiplicity = \"" + multiplicite
                + "\" allows several objects, but the field's type (" + type + ") holds a single "
                + "one.", champ, annotation, position);
        }
    }

    // Vrai si la multiplicité autorise plus d'un objet à cette extrémité.
    //
    // Ne valide rien : elle n'est appelée qu'après que le modèle a accepté la valeur, et se
    // contente donc d'en lire la borne haute. C'est la seule lecture d'une multiplicité hors du
    // modèle, et elle ne saurait le contredire — une forme qu'il aurait refusée n'arrive jamais
    // jusqu'ici.
    private static boolean permetPlusieurs(String multiplicite) {
        int separateur = multiplicite.indexOf("..");
        String borneHaute =
            separateur < 0 ? multiplicite : multiplicite.substring(separateur + 2);
        return borneHaute.equals("*") || Long.parseLong(borneHaute) > 1;
    }
}
