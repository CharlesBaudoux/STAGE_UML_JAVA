package io.github.oliviercailloux.umlg.rendering;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.umlg.UmlStyle;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlNote;
import io.github.oliviercailloux.umlg.model.UmlPackage;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.rendering.element.ClassBoxRenderer;
import io.github.oliviercailloux.umlg.rendering.element.NoteRenderer;
import io.github.oliviercailloux.umlg.rendering.element.PackageRenderer;
import io.github.oliviercailloux.umlg.rendering.element.RelationRenderer;
import io.github.oliviercailloux.umlg.rendering.syntax.AliasRegistry;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlEscaper;
import io.github.oliviercailloux.umlg.rendering.syntax.PlantUmlLayout;
import java.util.Map;

// Assemble le texte PlantUML complet, morceau par morceau.
//
// Il ne met en forme rien lui-même : les boîtes viennent de ClassBoxRenderer, les traits de
// RelationRenderer, le style de PlantUmlLayout. Son rôle est de tenir l'état commun à un
// diagramme — le registre d'alias, l'accumulateur de largeur — et de placer les morceaux dans
// le bon ordre.
final class PlantUmlSyntaxBuilder {

    private static final String OUVERTURE = "@startuml\n";

    private final StringBuilder builder = new StringBuilder();
    // Une instance par diagramme : les alias ne valent que dans le fichier courant.
    private final AliasRegistry alias = new AliasRegistry();
    // L'apparence voulue. Elle traverse cette classe sans y servir directement : le bloc de
    // style est écrit par PlantUmlLayout, l'habillage des noms de liens par RelationRenderer.
    private final UmlStyle style;
    // Les réglages d'ensemble : le titre, le sens de lecture, le sort des compartiments vides.
    // Tous trois s'écrivent en tête du fichier, et nulle part ailleurs.
    private final UmlDiagram diagramme;
    private final RelationRenderer liens;
    // Nom complet d'une boîte -> chemin relatif de la vue qui la détaille.
    private Map<String, String> navigation = Map.of();

    // Sans réglages déclarés : ceux par défaut, c'est-à-dire ce que décrit un projet dont le
    // fichier de règles ne dit rien du dessin.
    PlantUmlSyntaxBuilder(UmlStyle style) {
        this(style, UmlDiagram.defaut());
    }

    PlantUmlSyntaxBuilder(UmlStyle style, UmlDiagram diagramme) {
        this.style = checkNotNull(style);
        this.diagramme = checkNotNull(diagramme);
        this.liens = new RelationRenderer(style);
    }

    public void startDocument() {
        builder.append(OUVERTURE);
    }

    public void endDocument() {
        // Le bloc de style s'insère après coup, juste derrière @startuml : il dépend de la plus
        // large étiquette du diagramme, laquelle n'est connue qu'une fois toutes les relations
        // écrites. D'où l'insert au lieu d'un append, à la position OUVERTURE.length().
        //
        // L'en-tête du diagramme le précède, et par la même insertion : titre et sens de lecture
        // ne dépendent de rien, mais les écrire au début du fichier est ce qui les rend lisibles
        // à qui ouvre la source — l'ordre n'a en revanche aucune importance pour PlantUML.
        builder.insert(OUVERTURE.length(), enTete()
            + PlantUmlLayout.bloc(style, liens.largeurEtiquetteMax(),
                diagramme.hideEmptyMembers())
            + "\n");
        builder.append("@enduml\n");
    }

    // Ce que les réglages d'ensemble écrivent en tête du fichier, hors du bloc de style.
    //
    // Rien n'est écrit pour les valeurs par défaut : sans titre déclaré il n'y a pas de ligne
    // « title » — une ligne vide occuperait tout de même de la hauteur —, et TOP_TO_BOTTOM est
    // déjà ce que PlantUML fait sans qu'on le lui demande. Une directive qui ne change rien
    // n'apprendrait rien à qui relit la source.
    private String enTete() {
        StringBuilder tete = new StringBuilder();

        // Le titre est un texte de l'utilisateur, et passe donc par l'échappement comme tous les
        // autres : PlantUML lit « title » jusqu'à la fin de la ligne, et un retour à la ligne
        // brut y ferait commencer une directive que personne n'a écrite.
        if (diagramme.aUnTitre()) {
            tete.append("title ").append(PlantUmlEscaper.label(diagramme.title())).append('\n');
        }
        if (diagramme.direction() == UMLFlow.LEFT_TO_RIGHT) {
            tete.append("left to right direction\n");
        }
        return tete.toString();
    }

    // Déclare toutes les boîtes, chacune dans le rectangle où @UMLPackage la range.
    //
    // Elles sont déclarées ensemble, et non une par une, parce qu'un groupe n'est pas une
    // propriété de la boîte mais un rectangle qui en contient plusieurs : ouvrir et refermer le
    // bon au bon moment demande de les avoir toutes sous les yeux. C'est PackageRenderer qui les
    // range ; ici, on écrit ce qu'il a rangé.
    public PlantUmlSyntaxBuilder addClasses(Iterable<UmlEntity> entities) {
        for (Map.Entry<UmlPackage, ImmutableList<UmlEntity>> groupe
            : PackageRenderer.groupes(entities)) {

            boolean groupee = groupe.getKey().estGroupee();
            if (groupee) {
                builder.append(PackageRenderer.ouverture(groupe.getKey()));
            }
            for (UmlEntity entity : groupe.getValue()) {
                addClass(entity);
            }
            if (groupee) {
                builder.append(PackageRenderer.FERMETURE);
            }
        }
        return this;
    }

    // Déclare une boîte, en lui attribuant au passage son alias.
    //
    // Le groupe qu'elle déclare est ignoré ici : cette méthode écrit une boîte, pas le rectangle
    // qui l'entoure. Passer par addClasses est donc la règle ; celle-ci reste pour les appelants
    // qui n'ont qu'une boîte et aucun groupe à dessiner.
    public PlantUmlSyntaxBuilder addClass(UmlEntity entity) {
        builder.append(ClassBoxRenderer.boite(entity,
            alias.enregistrer(entity.qualifiedName(), entity.name()),
            navigation.getOrDefault(entity.qualifiedName(), "")));
        return this;
    }

    // Où mène chaque boîte quand on clique dessus : nom complet -> chemin relatif du fichier.
    //
    // Vide par défaut : un diagramme isolé n'a nulle part où renvoyer, et c'est le cas de tous
    // les appelants qui n'écrivent qu'une image.
    public PlantUmlSyntaxBuilder navigation(Map<String, String> versLesVues) {
        this.navigation = checkNotNull(versLesVues);
        return this;
    }

    // Écrit les relations que porte une entité. Suppose que toutes les boîtes ont déjà été
    // déclarées : c'est pour cela que PlantUmlWriter fait deux passes.
    public PlantUmlSyntaxBuilder addRelations(UmlEntity entity) {
        return addRelations(entity, entity.relations());
    }

    // Les mêmes relations, mais choisies par l'appelant.
    //
    // PlantUmlWriter s'en sert pour écrire les paires réciproques fusionnées : la liste passée
    // n'est alors plus celle du modèle, qui dit fidèlement ce que chaque classe déclare, mais
    // celle du dessin, où deux attributs qui se font face n'ont qu'un trait.
    public PlantUmlSyntaxBuilder addRelations(UmlEntity entity,
        Iterable<? extends UmlRelation> relations) {
        String aliasSource = alias.aliasSource(entity.qualifiedName());

        // Les relations mêlent associations et dépendances ; RelationRenderer les distingue
        // lui-même par un switch de motif.
        for (UmlRelation relation : relations) {
            builder.append(liens.ligne(entity, relation, aliasSource, alias));
        }
        return this;
    }

    // Écrit les bulles de commentaire d'une entité. Comme les relations, elles désignent des
    // boîtes par leur alias et supposent donc toutes les déclarations déjà écrites.
    public PlantUmlSyntaxBuilder addNotes(UmlEntity entity) {
        String alias = this.alias.aliasSource(entity.qualifiedName());

        for (UmlNote note : entity.notes()) {
            builder.append(NoteRenderer.note(note, alias));
        }
        // Les contraintes de la classe entière suivent le même chemin : UML les dessine à côté
        // de la boîte, ce qu'une bulle reproduit. Celles des attributs et des liens, elles, sont
        // écrites au fil du texte par ClassBoxRenderer et RelationRenderer.
        for (String regle : entity.constraints()) {
            builder.append(NoteRenderer.contrainte(regle, alias));
        }
        return this;
    }

    public String build() {
        return builder.toString();
    }
}
