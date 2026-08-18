package io.github.oliviercailloux.umlg.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;
import org.junit.jupiter.api.Test;

class UmlEntityRelationsTest {

    private static final UmlAssociation Held =
        UmlAssociation.builder("field", "pkg.Target").build();
    private static final UmlDependency Calls = new UmlDependency("pkg.Service", "calls");
    private static final UmlDependency Trace = new UmlDependency("pkg.Log", "trace");

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

    @Test
    void viewByNature_keepsItsOwnOnly() {
        UmlEntity entity = entity().relations(List.of(Held, Calls, Trace)).build();

        assertEquals(List.of(Held), entity.relations(UmlAssociation.class));
        assertEquals(List.of(Calls, Trace), entity.relations(UmlDependency.class));
    }

    @Test
    void namedViews_areEquivalentFilters() {
        UmlEntity entity = entity().relations(List.of(Held, Calls)).build();

        assertEquals(entity.relations(UmlAssociation.class), entity.associations());
        assertEquals(entity.relations(UmlDependency.class), entity.dependencies());
    }

    @Test
    void successiveCalls_addUpRatherThanReplaceEachOther() {
        UmlEntity entity = entity()
            .associations(List.of(Held))
            .dependencies(List.of(Calls))
            .build();

        assertEquals(List.of(Held, Calls), entity.relations());
    }

    @Test
    void declarationOrder_isKept() {
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
        List<UmlRelation> source = new java.util.ArrayList<>(List.of(Held, Calls));

        UmlEntity entity = entity().relations(source).build();
        source.clear();

        assertEquals(2, entity.relations().size());
    }

    @Test
    void nullLink_isRefused() {
        assertThrows(NullPointerException.class,
            () -> entity().relations(java.util.Collections.singletonList(null)));
        assertThrows(NullPointerException.class, () -> entity().relations(null));
    }

    @Test
    void shortBuilder_declaresNoLink() {
        UmlEntity entity =
            new UmlEntity("E", "", "", UMLVisibility.NONE, List.of(), List.of());

        assertTrue(entity.relations().isEmpty());
    }
}
