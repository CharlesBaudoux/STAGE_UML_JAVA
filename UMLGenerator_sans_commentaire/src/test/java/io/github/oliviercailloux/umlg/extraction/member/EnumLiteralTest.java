package io.github.oliviercailloux.umlg.extraction.member;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.EntityExtractor;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlField;
import org.junit.jupiter.api.Test;

class EnumLiteralTest {

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    enum Devise {
        EURO, DOLLAR
    }

    @Test
    void litteraux_sontExtraitsDansLeurPropreListe() {
        assertEquals(ImmutableList.of("EURO", "DOLLAR"),
            EntityExtractor.extract(Devise.class).literals());
    }

    @Test
    void litteraux_neSontPlusDesAttributs() {
        assertTrue(EntityExtractor.extract(Devise.class).fields().isEmpty());
    }

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    enum Planete {
        TERRE(1.0), MARS(0.5);

        private final double masse;

        Planete(double masse) {
            this.masse = masse;
        }

        double masse() {
            return masse;
        }
    }

    @Test
    void enumerationAvecAttributs_gardeLesDeux() {
        UmlEntity entity = EntityExtractor.extract(Planete.class);

        assertEquals(ImmutableList.of("TERRE", "MARS"), entity.literals());
        assertEquals(ImmutableList.of("masse"),
            entity.fields().stream().map(UmlField::name).toList());
    }

    @UMLClass
    enum SansStrategie {
        A, B
    }

    @Test
    void strategieNone_neMontreAucunLitteral() {
        assertTrue(EntityExtractor.extract(SansStrategie.class).literals().isEmpty());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    enum AvecExclusion {
        VISIBLE,

        @UMLExclude
        INTERNE
    }

    @Test
    void exclude_retireUnLitteral() {
        assertEquals(ImmutableList.of("VISIBLE"),
            EntityExtractor.extract(AvecExclusion.class).literals());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    enum Operateur {
        PLUS {
            @Override
            int appliquer(int a, int b) {
                return a + b;
            }
        },
        MOINS {
            @Override
            int appliquer(int a, int b) {
                return a - b;
            }
        };

        abstract int appliquer(int a, int b);
    }

    @Test
    void litteralAvecCorps_estReconnu() {
        assertEquals(ImmutableList.of("PLUS", "MOINS"),
            EntityExtractor.extract(Operateur.class).literals());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    enum DeviseNommee {
        @UMLInclude(name = "Euro")
        EURO,

        DOLLAR
    }

    @Test
    void unLitteralRenomme_paraitSousLeNomDuDiagramme() {
        assertEquals(ImmutableList.of("Euro", "DOLLAR"),
            EntityExtractor.extract(DeviseNommee.class).literals());
    }

    @Test
    void unMotifDuFichierDeRegles_atteintUnLitteral() {
        Regles regles = Regles.lues("{ \"hide\": [\"DOL*\"] }", "umlg.json");

        assertEquals(ImmutableList.of("Euro"),
            EntityExtractor.extract(DeviseNommee.class, regles).literals());
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    static class Portefeuille {
        public Devise devise;
    }

    @Test
    void attributDeTypeEnumeration_resteUnAttribut() {
        UmlEntity entity = EntityExtractor.extract(Portefeuille.class);

        assertTrue(entity.literals().isEmpty());
        assertEquals(ImmutableList.of("devise"),
            entity.fields().stream().map(UmlField::name).toList());
    }
}
