package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.umlg.annotation.UMLDiagram;
import io.github.oliviercailloux.umlg.model.UmlDiagram;

// Traduit une @UMLDiagram, lue sur un package-info, en réglages du modèle.
//
// C'est le pendant d'EntityExtractor pour ce qui n'appartient à aucune boîte. La classe est
// courte parce que l'annotation l'est : trois valeurs, aucune cible à résoudre, aucun membre à
// filtrer.
//
// Comme ClassHeaderValidator, la validation vit ici et non dans le modèle : c'est ici seulement
// que l'on sait quel paquetage nommer dans le message.
public final class DiagramExtractor {

    // Le nom sous lequel javac compile un package-info.java. Les fichiers portant ce nom sont
    // les seuls à pouvoir porter l'annotation, puisque c'est le seul endroit où Java permet
    // d'annoter un paquetage.
    public static final String PACKAGE_INFO = "package-info";

    private DiagramExtractor() {}

    // Vrai si cette classe est un package-info porteur de @UMLDiagram, c'est-à-dire s'il y a
    // quelque chose à en tirer. Le scanner s'en sert pour trier ce qu'il rencontre.
    public static boolean porteLeDiagramme(Class<?> clazz) {
        checkNotNull(clazz);
        return clazz.isAnnotationPresent(UMLDiagram.class);
    }

    // Les réglages déclarés par ce package-info.
    //
    // Le nom du paquetage est passé plutôt que déduit : sur un package-info, getSimpleName() rend
    // « package-info » et getPackageName() le paquetage lui-même — c'est ce dernier que le
    // lecteur du message reconnaîtra dans son arborescence.
    public static UmlDiagram extraire(Class<?> packageInfo) {
        checkNotNull(packageInfo);
        UMLDiagram annotation = packageInfo.getAnnotation(UMLDiagram.class);
        checkArgument(annotation != null, "Package %s does not carry the @UMLDiagram annotation.",
            packageInfo.getPackageName());

        validerTitre(annotation.title(), packageInfo.getPackageName());

        return new UmlDiagram(annotation.title(), annotation.direction(),
            annotation.hideEmptyMembers());
    }

    // La chaîne vide est le défaut : elle veut dire « aucun titre ». Une chaîne d'espaces, elle,
    // réserverait en tête du dessin la hauteur d'un titre que personne ne lirait.
    private static void validerTitre(String titre, String paquetage) {
        if (titre.isEmpty()) {
            return;
        }
        checkArgument(!titre.isBlank(),
            "@UMLDiagram on package %s: title() cannot be made of whitespace only. Leave it out "
                + "for no title.",
            paquetage);
    }
}
