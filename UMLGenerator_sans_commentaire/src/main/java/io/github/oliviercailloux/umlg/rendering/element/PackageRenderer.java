package io.github.oliviercailloux.umlg.rendering.element;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.rendering.syntax.ColorCodec;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import io.github.oliviercailloux.umlg.warning.Avertisseur;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PackageRenderer {

    public static final String FERMETURE = "}\n\n";

    private PackageRenderer() {}

    public static ImmutableList<Map.Entry<UmlPackage, ImmutableList<UmlEntity>>> groupes(
        Iterable<UmlEntity> entites) {

        Map<String, UmlPackage> retenus = new LinkedHashMap<>();
        Map<String, ImmutableList.Builder<UmlEntity>> membres = new LinkedHashMap<>();
        Map<String, String> premiereCouleur = new LinkedHashMap<>();

        for (UmlEntity entite : entites) {
            UmlPackage groupe = entite.group();
            String cle = groupe.name();

            UmlPackage retenu = retenus.get(cle);
            if (retenu == null) {
                retenus.put(cle, groupe);
                membres.put(cle, ImmutableList.builder());
                if (groupe.isGrouped() && !groupe.color().isEmpty()) {
                    premiereCouleur.put(cle, entite.qualifiedName());
                }
            } else if (groupe.isGrouped() && !groupe.color().isEmpty()
                && !groupe.color().equals(retenu.color())) {

                if (retenu.color().isEmpty()) {
                    retenus.put(cle, groupe);
                    premiereCouleur.put(cle, entite.qualifiedName());
                } else {
                    signalerCouleurEnDouble(entite, groupe, retenu, premiereCouleur.get(cle));
                }
            }
            membres.get(cle).add(entite);
        }

        ImmutableList.Builder<Map.Entry<UmlPackage, ImmutableList<UmlEntity>>> resultat =
            ImmutableList.builder();
        for (Map.Entry<String, UmlPackage> groupe : retenus.entrySet()) {
            resultat.add(Map.entry(groupe.getValue(), membres.get(groupe.getKey()).build()));
        }
        return resultat.build();
    }

    public static String ouverture(UmlPackage groupe) {
        StringBuilder ligne = new StringBuilder("package \"")
            .append(PlantUmlEscaper.debutDeLigne(groupe.name())).append('"');

        if (!groupe.color().isEmpty()) {
            ligne.append(' ').append(ColorCodec.code(groupe.color(), groupe.name()));
        }
        return ligne.append(" {\n").toString();
    }

    private static void signalerCouleurEnDouble(UmlEntity entite, UmlPackage declare,
        UmlPackage retenu, String premiere) {
        Avertisseur.dire("@UMLPackage on " + entite.qualifiedName() + ": group \""
            + declare.name() + "\" already has the color \"" + retenu.color() + "\""
            + (premiere == null ? "" : ", set by " + premiere)
            + "; a rectangle has only one, so \"" + declare.color() + "\" will not be drawn.");
    }
}
