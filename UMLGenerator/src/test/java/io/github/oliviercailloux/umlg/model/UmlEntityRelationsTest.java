package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;
import org.junit.jupiter.api.Test;

// The storage of relations in UmlEntity: a single list for both natures, filtered on
// the request. These tests fix the behaviour the rendering depends on — order kept, additions
// cumulative, immutable list.
class UmlEntityRelationsTest {

    // Three witness relations, reused everywhere: one association and two dependencies, which
    // is enough to check filtering by nature as well as the order.
    private static final UmlAssociation Held =
        UmlAssociation.builder("field", "pkg.Target").build();
    private static final UmlDependency Calls = new UmlDependency("pkg.Service", "calls");
    private static final UmlDependency Trace = new UmlDependency("pkg.Log", "trace");

    // Returns a fresh builder on each call: the tests cannot contaminate each other.
    private static UmlEntity.Builder entity() {
        return UmlEntity.builder("pkg.Source", "Source");
    }

    @Test
    void bareEntity_hasNoLink() {
        assertTrue(entity().build().relations().isEmpty());
    }

    @Test
    void bothNatures_coexistInASingleList() {
        UmlEntity entity = entity().relations(List.of(Held, Calls)).build();

        assertEquals(List.of(Held, Calls), entity.relations());
    }

    // The filter of relations(Class): each view returns its nature only, in the original order.
    @Test
    void viewByNature_keepsItsOwnOnly() {
        UmlEntity entity = entity().relations(List.of(Held, Calls, Trace)).build();

        assertEquals(List.of(Held), entity.relations(UmlAssociation.class));
        assertEquals(List.of(Calls, Trace), entity.relations(UmlDependency.class));
    }

    // associations() and dependencies() are mere shortcuts to relations(Class): nothing
    // must diverge between the two writings.
    @Test
    void namedViews_areEquivalentFilters() {
        UmlEntity entity = entity().relations(List.of(Held, Calls)).build();

        assertEquals(entity.relations(UmlAssociation.class), entity.associations());
        assertEquals(entity.relations(UmlDependency.class), entity.dependencies());
    }

    @Test
    void successiveCalls_addUpRatherThanReplaceEachOther() {
        // The links of an entity come from as many sources as there are annotations
        // recognized: none must erase the work of the others.
        UmlEntity entity = entity()
            .associations(List.of(Held))
            .dependencies(List.of(Calls))
            .build();

        assertEquals(List.of(Held, Calls), entity.relations());
    }

    @Test
    void declarationOrder_isKept() {
        // It is this order that decides the stroke order in the produced document.
        UmlEntity entity = entity().relations(List.of(Trace, Held, Calls)).build();

        assertEquals(List.of(Trace, Held, Calls), entity.relations());
    }

    @Test
    void linkList_isImmutable() {
        UmlEntity entity = entity().relations(List.of(Held)).build();

        assertThrows(UnsupportedOperationException.class, () -> entity.relations().clear());
    }

    @Test
    void theReceivedList_isNotTrackedAfterConstruction() {
        // Without a defensive copy, the caller could change the content of the diagram afterwards.
        List<UmlRelation> source = new java.util.ArrayList<>(List.of(Held, Calls));

        UmlEntity entity = entity().relations(source).build();
        source.clear();

        assertEquals(2, entity.relations().size());
    }

    @Test
    void nullLink_isRefused() {
        // singletonList accepts null, unlike List.of: it is the only way of building
        // a list holding a null to exercise the check.
        assertThrows(NullPointerException.class,
            () -> entity().relations(java.util.Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> entity().relations(null));
    }

    // The six-argument constructor passes ImmutableList.of() for the relations: an entity
    // built this way has no relations, and above all not null.
    @Test
    void shortBuilder_declaresNoLink() {
        UmlEntity entity =
            new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), List.of());

        assertTrue(entity.relations().isEmpty());
    }
}
