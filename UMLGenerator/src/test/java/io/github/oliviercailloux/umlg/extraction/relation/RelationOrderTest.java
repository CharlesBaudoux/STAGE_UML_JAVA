package io.github.oliviercailloux.umlg.extraction.relation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.extraction.rules.Rules;
import io.github.oliviercailloux.umlg.model.UmlAssociation;
import io.github.oliviercailloux.umlg.model.UmlDependency;
import io.github.oliviercailloux.umlg.model.UmlInheritance;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelationOrderTest {

    private static Rules declaration() {
        return Rules.read("{ \"order\": \"declaration\" }", "umlg.json");
    }

    @UMLClass
    static class Target {}

    @UMLClass
    @UMLDependency(target = Target.class)
    static class Linked {

        @UMLAssociation(target = Target.class)
        private Target zebra;

        @UMLAssociation(target = Target.class)
        private Target ostrich;

        @UMLAssociation(target = Target.class)
        private Target blackbird;
    }

    private static List<String> associationsNames(Rules rules) {
        return new AssociationExtractor().extract(Linked.class, rules).stream()
            .map(UmlAssociation::fieldName).toList();
    }

    @Test
    void byDefault_theStrokesFollowTheAlphabet() {
        assertEquals(List.of("blackbird", "ostrich", "zebra"), associationsNames(Rules.no()));
    }

    @Test
    void onRequest_theStrokesFollowTheCodeOrder() {
        assertEquals(List.of("zebra", "ostrich", "blackbird"), associationsNames(declaration()));
    }

    @Test
    void withoutRules_theExtractorReturnsTheSameAsWithNone() {
        assertEquals(associationsNames(Rules.no()),
            new AssociationExtractor().extract(Linked.class).stream()
                .map(UmlAssociation::fieldName).toList());
    }

    @UMLClass
    static class Child extends Linked {}

    @Test
    void memberlessExtractors_ignoreTheRules() {
        for (RelationExtractor extractor : List.of(new DependencyExtractor(),
            new InheritanceExtractor(), new AssociationClassExtractor())) {

            ImmutableList<? extends UmlRelation> without = extractor.extract(Child.class);
            ImmutableList<? extends UmlRelation> with =
                extractor.extract(Child.class, declaration());

            assertEquals(without, with);
        }
        assertEquals(List.of(new UmlDependency(Target.class.getName(), "")),
            new DependencyExtractor().extract(Linked.class, declaration()));
        assertEquals(List.of(UmlInheritance.generalization(Linked.class.getName())),
            new InheritanceExtractor().extract(Child.class, declaration()));
    }
}
