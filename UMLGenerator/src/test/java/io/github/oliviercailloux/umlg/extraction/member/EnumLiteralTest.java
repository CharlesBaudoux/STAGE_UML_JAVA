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

// Les littéraux d'une énumération.
//
// Pour la réflexion ce sont des attributs publics, statiques et finaux ; pour UML, des
// EnumerationLiteral, qui ne portent que leur nom. Ces tests vérifient qu'ils quittent le
// compartiment des attributs sans pour autant emporter les vrais attributs de l'énumération.
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
        // C'est le défaut corrigé : sans ce tri, EURO apparaissait comme « + {static} Devise
        // EURO », ce qui n'est pas la notation UML d'un littéral.
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
        // Une énumération peut avoir de vrais attributs en plus de ses littéraux : les deux
        // compartiments doivent coexister, chacun avec sa notation.
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
        // Un littéral reste soumis à la stratégie de la classe, comme n'importe quel membre :
        // « aucune stratégie » veut dire une boîte vide, littéraux compris.
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
        // Une constante à corps engendre une sous-classe anonyme ; le littéral doit rester
        // reconnu comme tel malgré cela.
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
        // L'annotation était lue pour décider de la visibilité du littéral, puis oubliée pour son
        // nom : l'auteur écrivait @UMLInclude(name = ...), le processeur l'acceptait, et le
        // dessin gardait EURO. Un littéral se nomme comme n'importe quel autre membre.
        assertEquals(ImmutableList.of("Euro", "DOLLAR"),
            EntityExtractor.extract(DeviseNommee.class).literals());
    }

    @Test
    void unMotifDuFichierDeRegles_atteintUnLitteral() {
        // Le seul chemin d'extraction qui ne recevait pas les règles du projet : « hide EU* »
        // vidait tous les compartiments sauf celui-ci, sans que rien ne le documente.
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
        // isEnumConstant() ne vaut que pour les constantes déclarées par l'énumération
        // elle-même : un attribut qui a seulement ce type n'est pas concerné.
        UmlEntity entity = EntityExtractor.extract(Portefeuille.class);

        assertTrue(entity.literals().isEmpty());
        assertEquals(ImmutableList.of("devise"),
            entity.fields().stream().map(UmlField::name).toList());
    }
}
