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

// The project rules go all the way down to the relation extractors.
//
// "order declaration" spoke to the compartments only: the strokes still came out in
// alphabet. On one same box, fields therefore followed the code and links followed the
// names — two orders for a single drawing.
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
        // The one-argument overload is the "no project rule" case, and nothing else:
        // it is the default of the contract, and it calls the two-argument one.
        assertEquals(associationsNames(Rules.no()),
            new AssociationExtractor().extract(Linked.class).stream()
                .map(UmlAssociation::fieldName).toList());
    }

    // --- Those the order does not concern ---

    @UMLClass
    static class Child extends Linked {}

    @Test
    void memberlessExtractors_ignoreTheRules() {
        // Inheritance is read in the class signature, dependencies and association
        // association classes in its annotations: none of the three reads a member again, and all
        // therefore receive rules they have nothing to do with. Checking it prevents one day
        // one of them returns something else depending on the rules received.
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
