package io.github.oliviercailloux.umlg.rendering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.oliviercailloux.umlg.model.UmlEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.plantuml.BlockUml;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.core.Diagram;

/**
 * Outils de vérification du code PlantUML produit.
 *
 * <p>Point clé : PlantUML n'échoue jamais bruyamment. Face à une syntaxe invalide, il
 * produit une <em>image d'erreur</em> parfaitement valide (un PNG/SVG rouge affichant
 * "syntax error"). Un test qui se contente de vérifier {@code Files.exists(...)} et
 * {@code size > 0} passe donc au vert alors que le diagramme livré est illisible.
 * {@link #assertCompile} interroge le moteur PlantUML lui-même pour distinguer un vrai
 * diagramme d'un diagramme d'erreur.
 */
public final class PlantUmlAssert {

    private static final Pattern DECLARATION_CLASSE =
        Pattern.compile("^class \"([^\"]*)\" as (\\S+)", Pattern.MULTILINE);

    private PlantUmlAssert() {}

    /**
     * Vérifie que PlantUML accepte réellement {@code source} : au moins un diagramme, et
     * aucun d'entre eux n'est un diagramme d'erreur.
     */
    public static void assertCompile(String source) {
        List<BlockUml> blocs = new SourceStringReader(source).getBlocks();

        assertFalse(blocs.isEmpty(),
            () -> "PlantUML n'a reconnu aucun diagramme dans la source :\n" + source);

        for (BlockUml bloc : blocs) {
            Diagram diagramme = bloc.getDiagram();
            String probleme = diagramme.getWarningOrError();
            if (probleme != null) {
                fail("PlantUML a rejeté la syntaxe générée (" + diagramme.getClass().getSimpleName()
                    + ") : " + probleme.strip() + "\n--- source rejetée ---\n" + source);
            }
        }
    }

    /** Rend {@code entity} dans un document complet et renvoie le code PlantUML. */
    public static String documentPour(UmlEntity... entities) {
        PlantUmlSyntaxBuilder builder = new PlantUmlSyntaxBuilder();
        builder.startDocument();
        for (UmlEntity entity : entities) {
            builder.addClass(entity);
        }
        builder.endDocument();
        return builder.build();
    }

    /** Les alias déclarés par les lignes {@code class "..." as <alias>}, dans l'ordre. */
    public static List<String> aliasDeclares(String source) {
        List<String> alias = new ArrayList<>();
        Matcher m = DECLARATION_CLASSE.matcher(source);
        while (m.find()) {
            alias.add(m.group(2));
        }
        return alias;
    }

    /** Les noms affichés déclarés par les lignes {@code class "<nom>" as ...}, dans l'ordre. */
    public static List<String> nomsDeclares(String source) {
        List<String> noms = new ArrayList<>();
        Matcher m = DECLARATION_CLASSE.matcher(source);
        while (m.find()) {
            noms.add(m.group(1));
        }
        return noms;
    }
}
