package io.github.oliviercailloux.umlg.processing;

import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import io.github.oliviercailloux.umlg.extraction.member.VisibilityPolicy;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import javax.lang.model.element.Element;

// Ce que le processeur sait de la stratégie d'une classe.
//
// LE PROBLÈME QU'IL RÉSOUT
//
// Le défaut de @UMLClass est PROJECT : « la stratégie que déclare le fichier umlg.json ». Or ce
// fichier vit à la racine du répertoire des classes compilées, et javac ne sait pas où il se
// trouve — il compile des sources, et rien ne lui dit ce que la construction fera de leur
// résultat. Le processeur ne peut donc pas savoir, de lui-même, si un membre sera dessiné.
//
// Tous les avertissements qui disent « ce membre n'est pas affiché » ou « il l'est déjà »
// deviennent alors indécidables. Ils se taisent, faute de pouvoir conclure : un avertissement qui
// se trompe coûte plus cher que celui qui manque un cas, puisqu'il apprend à ne plus les lire.
//
// COMMENT ON LUI REND LA VUE
//
// L'utilisateur peut donner le chemin du fichier à javac, par l'option -Aumlg.rules. Le
// processeur lit alors les mêmes règles que la génération, et retrouve la parole : rien de ce
// qu'il aurait dit n'est perdu. C'est la seule chose que la bibliothèque taise, et c'est la
// seule ligne de configuration qui l'en empêche.
final class Strategie {

    // Ce que le processeur peut dire de la présence d'un membre sur le diagramme.
    //
    // Trois réponses et non deux, parce que « je ne sais pas » n'est pas « il n'y sera pas ». La
    // stratégie d'une classe qui s'en remet au projet reste hors de portée de javac tant que
    // -Aumlg.rules n'a pas été donnée, et un avertissement qui devinerait se tromperait.
    //
    // MASQUE veut dire « masqué à coup sûr » : le filtre anti-bruit du fichier de règles ne fait
    // qu'en retirer davantage, si bien que ce qui est déjà masqué par la stratégie l'est quoi que
    // dise le fichier. AFFICHE, lui, ne dit rien de ce filtre — d'où reglesConnues() et
    // champMasqueParUneRegle, sans lesquels un « ce membre est déjà montré » serait un faux
    // reproche.
    enum Affichage {
        AFFICHE,
        MASQUE,
        INDECIDABLE
    }

    // Les règles du projet, ou null quand l'option n'a pas été donnée. Null plutôt qu'un jeu vide
    // : « aucune règle » et « des règles qui ne disent rien » mènent au même diagramme, mais pas
    // au même savoir — le second permet de conclure, le premier non.
    private final Regles regles;

    Strategie(Regles regles) {
        this.regles = regles;
    }

    // La stratégie de cette classe est-elle connaissable ici ?
    //
    // Elle l'est dès que la classe la déclare — le fichier n'a alors rien à dire —, et sinon dès
    // que le processeur a reçu les règles du projet.
    //
    // Privée : les vérificateurs ne posent plus cette question directement, ils demandent
    // membreAffiche, dont l'indécision est l'une des trois réponses. C'est ce qui a permis à
    // l'une des cinq copies de conclure autrement que les quatre autres.
    private boolean connue(UMLClass classe) {
        return classe.strategy() != UMLVisibility.PROJECT || regles != null;
    }

    // Le fichier de règles du projet est-il sous la main ?
    //
    // Question distincte de connue(), et c'est tout l'objet de la distinction. Une classe qui
    // déclare « strategy = PRIVATE » rend connue() vraie sans que le fichier ait été lu : l'axe
    // de la stratégie est alors décidable, celui des motifs « hide » ne l'est toujours pas. Or
    // les deux décident du même dessin.
    //
    // Les avertissements qui disent « ce membre est déjà montré, ta dérogation est inutile »
    // dépendent des deux axes : sans le fichier, ils accusaient un @UMLInclude qui était en
    // réalité le seul moyen de garder la ligne, et le suivre l'effaçait du diagramme. Ceux qui
    // disent l'inverse — « il est déjà masqué » — n'en dépendent pas, un motif ne pouvant que
    // masquer davantage.
    boolean reglesConnues() {
        return regles != null;
    }

    // Ce membre sera-t-il dessiné, ses dérogations comprises ?
    //
    // La question que se posent quatre vérificateurs — @UMLDerived, @UMLMultiplicity, @UMLNote,
    // @UMLConstraint — pour savoir si ce qu'ils décorent aura un porteur. Elle était écrite
    // quatre fois à l'identique, et une cinquième fois différemment ; c'est dans la copie
    // divergente que s'était logé le faux reproche que reglesConnues() corrige.
    Affichage membreAffiche(Element membre, UMLClass classe) {
        return decider(membre, classe,
            membre.getAnnotation(UMLInclude.class) != null,
            membre.getAnnotation(UMLExclude.class) != null);
    }

    // La même question, les dérogations mises de côté : « ce membre serait-il affiché si
    // celle-ci n'était pas là ? »
    //
    // C'est la seule façon de savoir si une @UMLInclude ou une @UMLExclude change quelque chose,
    // et donc la question du vérificateur des dérogations.
    Affichage membreAfficheSansDerogation(Element membre, UMLClass classe) {
        return decider(membre, classe, false, false);
    }

    private Affichage decider(Element membre, UMLClass classe, boolean inclusion,
        boolean exclusion) {
        // Sans les règles du projet, une stratégie laissée à PROJECT est hors de portée de
        // javac : mieux vaut ne pas conclure que deviner. L'option -Aumlg.rules la lui donne.
        if (!connue(classe)) {
            return Affichage.INDECIDABLE;
        }
        // Un composant de record est dessiné quelle que soit la stratégie : le déclarer invisible
        // serait un contresens, et l'avertissement désignerait de surcroît l'un des deux clones
        // que javac fabrique — champ et accesseur — donc une ligne que personne n'a écrite.
        if (Membre.vientDUnComposant(membre)) {
            return Affichage.AFFICHE;
        }
        return VisibilityPolicy.estVisible(Membre.bits(membre), effective(classe), inclusion,
            exclusion) ? Affichage.AFFICHE : Affichage.MASQUE;
    }

    // La stratégie qui s'appliquera réellement à cette classe.
    //
    // À n'appeler que lorsque connue() a répondu vrai : autrement, la réponse serait une
    // supposition. PROJECT sans règles rend NONE, le repli le plus sûr — une boîte vide se
    // remarque, un membre affiché par erreur non — mais aucun message ne devrait en dépendre.
    UMLVisibility effective(UMLClass classe) {
        if (classe.strategy() != UMLVisibility.PROJECT) {
            return classe.strategy();
        }
        return regles == null ? UMLVisibility.NONE : regles.strategie();
    }

    // Une règle du projet écarte-t-elle cet attribut, indépendamment de la stratégie ?
    //
    // Le filtre anti-bruit ne travaille pas que sur les méthodes : « "hide": ["log", "LOGGER"] » retire
    // aussi des attributs, un motif visant les deux sortes de membres indifféremment. Sans cette
    // question, le processeur reprochait à un @UMLInclude d'être « sans effet, le champ est déjà
    // montré par la stratégie » alors que c'était précisément la règle à laquelle il dérogeait —
    // sans lui, le champ aurait disparu.
    //
    // Faux quand les règles sont hors de portée : ne pas savoir, c'est ne pas conclure.
    boolean champMasqueParUneRegle(String nom) {
        return regles != null && regles.membreMasque(nom);
    }
}
