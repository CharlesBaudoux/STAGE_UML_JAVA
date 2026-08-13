package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrdreDesRelationsTest {

    private static Regles declaration() {
        return Regles.lues("{ \"order\": \"declaration\" }", "umlg.json");
    }

    @UMLClass
    static class Cible {}

    @UMLClass
    @UMLDependency(target = Cible.class)
    static class Liee {

        @UMLAssociation(target = Cible.class)
        private Cible zebre;

        @UMLAssociation(target = Cible.class)
        private Cible autruche;

        @UMLAssociation(target = Cible.class)
        private Cible merle;
    }

    private static List<String> nomsDesAssociations(Regles regles) {
        return new AssociationExtractor().extraire(Liee.class, regles).stream()
            .map(UmlAssociation::fieldName).toList();
    }

    @Test
    void parDefaut_lesTraitsSuiventLAlphabet() {
        assertEquals(List.of("autruche", "merle", "zebre"), nomsDesAssociations(Regles.aucune()));
    }

    @Test
    void surDemande_lesTraitsSuiventLOrdreDuCode() {
        assertEquals(List.of("zebre", "autruche", "merle"), nomsDesAssociations(declaration()));
    }

    @Test
    void sansRegles_lExtracteurRendLaMemeChoseQuAvecAucune() {
        assertEquals(nomsDesAssociations(Regles.aucune()),
            new AssociationExtractor().extraire(Liee.class).stream()
                .map(UmlAssociation::fieldName).toList());
    }


    @UMLClass
    static class Fille extends Liee {}

    @Test
    void lesExtracteursSansMembres_ignorentLesRegles() {
        for (RelationExtractor extracteur : List.of(new DependencyExtractor(),
            new InheritanceExtractor(), new AssociationClassExtractor())) {

            ImmutableList<? extends UmlRelation> sans = extracteur.extraire(Fille.class);
            ImmutableList<? extends UmlRelation> avec =
                extracteur.extraire(Fille.class, declaration());

            assertEquals(sans, avec);
        }
        assertEquals(List.of(new UmlDependency(Cible.class.getName(), "")),
            new DependencyExtractor().extraire(Liee.class, declaration()));
        assertEquals(List.of(UmlInheritance.generalization(Liee.class.getName())),
            new InheritanceExtractor().extraire(Fille.class, declaration()));
    }
}
