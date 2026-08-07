package io.github.oliviercailloux.umlg.extraction;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLFlow;
import io.github.oliviercailloux.umlg.extraction.rules.DefinitionDeVue;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import io.github.oliviercailloux.umlg.extraction.rules.Selecteur;
import io.github.oliviercailloux.umlg.model.UmlAssociationClass;
import io.github.oliviercailloux.umlg.model.UmlDiagram;
import io.github.oliviercailloux.umlg.model.UmlEntity;
import io.github.oliviercailloux.umlg.model.UmlLayout;
import io.github.oliviercailloux.umlg.model.UmlRelation;
import io.github.oliviercailloux.umlg.model.UmlView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Taille le projet en vues : la vue intégrale, celles que le fichier déclare, celles qu'il fait
// calculer.
//
// TROIS OPÉRATIONS, ET ELLES SE SUIVENT DANS CET ORDRE
//
// Choisir les boîtes — résoudre les sélecteurs contre le projet. Élaguer les traits — une
// relation dont l'autre bout n'est pas dans la vue ne peut pas être dessinée. Poser l'ordre —
// les boîtes que rien ne relie sont enchaînées par des liens invisibles, dans l'ordre où
// l'utilisateur les a écrites.
//
// SUR L'ÉLAGAGE, QUI SURPREND PARFOIS
//
// Un lien qui sort de la vue disparaît sans un mot. C'est le même arbitrage que pour un héritage
// dont le parent n'est pas annoté : restreindre la vue EST la demande de l'auteur, il n'y a donc
// aucun écart à lui signaler. Le contraire — dessiner la boîte manquante pour sauver le trait —
// ferait déborder chaque vue de proche en proche, jusqu'à ce qu'elles se ressemblent toutes.
public final class ConstructeurDeVues {

    // Le nom de la vue qui montre tout. Elle est toujours produite : c'est l'inventaire du
    // projet, celui qu'on ouvre pour savoir ce qui existe.
    public static final String VUE_INTEGRALE = "Projet";

    private ConstructeurDeVues() {}

    public static ImmutableList<UmlView> construire(ImmutableSet<UmlEntity> entites,
        UmlDiagram diagramme, Regles regles) {
        checkNotNull(entites);
        checkNotNull(diagramme);
        checkNotNull(regles);

        // Un projet sans boîte n'a pas de vues, et ce n'est pas ici qu'on le lui reproche :
        // UmlProject refuse déjà l'ensemble vide, avec un message qui dit à l'utilisateur ce
        // qu'il doit corriger — une @UMLClass oubliée, un mauvais répertoire scanné. Échouer
        // avant lui, sur « cette vue est vide », le mettrait sur une fausse piste.
        if (entites.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<UmlView> vues = ImmutableList.builder();

        // L'intégrale d'abord : elle porte le titre et le sens de lecture du fichier de règles,
        // faute de quoi ces réglages n'auraient plus où s'appliquer.
        String titre = diagramme.aUnTitre() ? diagramme.title() : VUE_INTEGRALE;
        vues.add(new UmlView(titre, ImmutableList.copyOf(entites), diagramme.direction()));

        for (DefinitionDeVue definition : regles.vues()) {
            vues.add(declaree(definition, entites));
        }
        if (regles.vuesParPaquetage()) {
            vues.addAll(parPaquetage(entites));
        }
        return vues.build();
    }

    // Une vue déclarée : on résout ce qu'elle retient, on retranche ce qu'elle retire.
    private static UmlView declaree(DefinitionDeVue definition, ImmutableSet<UmlEntity> entites) {
        // LinkedHashSet : l'ordre d'écriture est conservé, et une classe citée deux fois — par
        // son nom puis par son paquetage — n'entre qu'une fois.
        Set<UmlEntity> retenues = new LinkedHashSet<>();
        for (Selecteur selecteur : definition.retenus()) {
            retenues.addAll(resoudre(selecteur, entites, definition.nom()));
        }
        for (Selecteur selecteur : definition.retires()) {
            retenues.removeAll(resoudre(selecteur, entites, definition.nom()));
        }
        checkArgument(!retenues.isEmpty(),
            "View \"%s\" retains no class; everything it named was also removed.",
            definition.nom());

        return new UmlView(definition.nom(), ordonnees(retenues), definition.direction());
    }

    // Une vue par paquetage Java, dans l'ordre alphabétique des paquetages.
    //
    // C'est le découpage que suivent la plupart des diagrammes tenus à la main, parce qu'un
    // paquetage rassemble déjà ce qui se comprend ensemble. Il ne dispense pas de déclarer des
    // vues : celles-ci retiennent tout le paquetage, là où un auteur en écarterait les classes
    // techniques.
    private static ImmutableList<UmlView> parPaquetage(ImmutableSet<UmlEntity> entites) {
        Map<String, List<UmlEntity>> parNom = new LinkedHashMap<>();
        for (UmlEntity entite : entites) {
            parNom.computeIfAbsent(paquetageDe(entite), nom -> new ArrayList<>()).add(entite);
        }
        ImmutableList.Builder<UmlView> vues = ImmutableList.builder();
        for (String paquetage : parNom.keySet().stream().sorted().toList()) {
            vues.add(new UmlView(paquetage, ordonnees(parNom.get(paquetage)),
                UMLFlow.TOP_TO_BOTTOM));
        }
        return vues.build();
    }

    // Le paquetage d'une entité, lu dans son nom complet. La chaîne vide pour le paquetage par
    // défaut, que la vue nommera « (défaut) » faute de mieux.
    private static String paquetageDe(UmlEntity entite) {
        String complet = entite.qualifiedName();
        int point = complet.lastIndexOf('.');
        return point < 0 ? "(défaut)" : complet.substring(0, point);
    }

    // Les entités que ce sélecteur désigne.
    private static ImmutableList<UmlEntity> resoudre(Selecteur selecteur,
        ImmutableSet<UmlEntity> entites, String vue) {

        ImmutableList<UmlEntity> trouvees = switch (selecteur.sorte()) {
            case CLASSE -> parNom(selecteur.valeur(), entites);
            case PAQUETAGE -> entites.stream()
                .filter(e -> paquetageDe(e).equals(selecteur.valeur()))
                .collect(ImmutableList.toImmutableList());
            case VOISINAGE -> voisinage(selecteur.valeur(), entites, vue);
        };

        // Un sélecteur qui ne désigne rien est une faute : le fichier n'a pas de compilateur pour
        // relire ce qu'on y écrit, et une classe renommée y laisse un nom mort que rien ne
        // signalerait. Le diagramme se dessinerait, amputé, sans que personne ne le sache.
        checkArgument(!trouvees.isEmpty(),
            "View \"%s\": \"%s\" matches no class of the diagram. A class must carry @UMLClass to "
                + "be named here, and its name must be spelled as in the code.",
            vue, selecteur.ecrit());

        return trouvees;
    }

    // Les entités portant ce nom, simple ou complet. Le nom complet ne peut désigner qu'une
    // classe ; le nom simple peut en désigner plusieurs, et les retient toutes — deux homonymes
    // de paquetages différents sont deux boîtes distinctes, et rien ne dit laquelle on voulait.
    private static ImmutableList<UmlEntity> parNom(String nom, ImmutableSet<UmlEntity> entites) {
        return entites.stream()
            .filter(e -> e.qualifiedName().equals(nom) || nomSimple(e).equals(nom))
            .collect(ImmutableList.toImmutableList());
    }

    private static String nomSimple(UmlEntity entite) {
        String complet = entite.qualifiedName();
        // Les classes internes se nomment « Externe$Interne » : c'est le dernier morceau qui est
        // le nom simple, comme le rendrait Class.getSimpleName().
        int separateur = Math.max(complet.lastIndexOf('.'), complet.lastIndexOf('$'));
        return separateur < 0 ? complet : complet.substring(separateur + 1);
    }

    // Une classe et ses voisines immédiates : celles qu'elle vise, celles qui la visent.
    //
    // La seule sélection qui se calcule, et elle répond à une question que les autres ne savent
    // pas poser : « que dois-je regarder pour comprendre cette classe-ci ? ». Le rayon est de un,
    // délibérément — à deux, le voisinage d'une classe centrale redevient le projet entier.
    private static ImmutableList<UmlEntity> voisinage(String nom, ImmutableSet<UmlEntity> entites,
        String vue) {
        ImmutableList<UmlEntity> centres = parNom(nom, entites);
        checkArgument(!centres.isEmpty(),
            "View \"%s\": \"around %s\" matches no class of the diagram.", vue, nom);

        Set<String> voulus = new LinkedHashSet<>();
        for (UmlEntity centre : centres) {
            voulus.add(centre.qualifiedName());
            // Ce que le centre vise.
            for (UmlRelation relation : centre.relations()) {
                voulus.addAll(extremites(relation));
            }
        }
        // Ce qui vise le centre. Deux passes plutôt qu'un index : un diagramme compte quelques
        // dizaines de boîtes, et un index inversé coûterait plus à lire qu'à exécuter.
        for (UmlEntity candidate : entites) {
            for (UmlRelation relation : candidate.relations()) {
                if (centres.stream()
                    .anyMatch(c -> extremites(relation).contains(c.qualifiedName()))) {
                    voulus.add(candidate.qualifiedName());
                }
            }
        }
        return entites.stream().filter(e -> voulus.contains(e.qualifiedName()))
            .collect(ImmutableList.toImmutableList());
    }

    // Les boîtes qu'une relation touche à son autre bout — deux pour une classe d'association,
    // une pour toutes les autres.
    private static ImmutableSet<String> extremites(UmlRelation relation) {
        if (relation instanceof UmlAssociationClass classe) {
            return ImmutableSet.of(classe.sourceQualifiedName(), classe.targetQualifiedName());
        }
        return ImmutableSet.of(relation.targetQualifiedName());
    }

    // Les entités de la vue, élaguées de leurs traits sortants puis enchaînées dans l'ordre.
    private static ImmutableList<UmlEntity> ordonnees(Iterable<UmlEntity> retenues) {
        ImmutableSet<String> presentes = ImmutableSet.copyOf(
            java.util.stream.StreamSupport.stream(retenues.spliterator(), false)
                .map(UmlEntity::qualifiedName).toList());

        List<UmlEntity> elaguees = new ArrayList<>();
        for (UmlEntity entite : retenues) {
            elaguees.add(elaguer(entite, presentes));
        }
        return enchainer(elaguees, presentes);
    }

    // La même entité, privée des relations dont l'autre bout n'est pas dessiné.
    private static UmlEntity elaguer(UmlEntity entite, ImmutableSet<String> presentes) {
        ImmutableList<UmlRelation> tenues = entite.relations().stream()
            .filter(relation -> presentes.containsAll(extremites(relation)))
            .collect(ImmutableList.toImmutableList());

        if (tenues.size() == entite.relations().size()) {
            // Rien à retirer : on rend l'originale plutôt qu'une copie identique.
            return entite;
        }
        return new UmlEntity(entite.qualifiedName(), entite.name(), entite.kind(),
            entite.stereotype(), entite.color(), entite.strategy(), entite.literals(),
            entite.fields(), entite.methods(), tenues, entite.notes(), entite.constraints(),
            entite.group());
    }

    // Enchaîne par des liens invisibles les blocs de boîtes que rien ne relie entre eux.
    //
    // POURQUOI CE N'EST PAS UN CALCUL
    //
    // Le moteur de rendu place déjà correctement ce qui est relié : une association pose sa
    // source au-dessus de sa cible, et l'ordre général suit les traits. Ce qu'il ne peut pas
    // deviner, c'est l'ordre de deux boîtes que rien ne relie — il les pose côte à côte, au
    // hasard de son algorithme.
    //
    // Cet ordre-là est celui de l'exposé qui accompagne le dessin : on présente d'abord la carte,
    // puis le lecteur, puis le convertisseur. Il n'est écrit nulle part dans le code, et c'est
    // pourquoi il est lu dans la déclaration de la vue — l'ordre d'écriture devient l'ordre de
    // lecture.
    //
    // POURQUOI DES BLOCS, ET NON DES PAIRES
    //
    // C'est le point délicat. Enchaîner deux à deux les boîtes consécutives qu'aucun trait ne
    // joint semble équivalent, et ne l'est pas : trois classes filles d'un même parent ne sont
    // pas reliées entre elles, et se retrouveraient empilées l'une sous l'autre alors que le
    // moteur les dispose en éventail sous leur parent — la forme même que l'héritage doit avoir.
    //
    // Ce qu'il faut ordonner, ce sont les groupes de boîtes qui se tiennent, pas les boîtes. On
    // calcule donc les blocs — au sens des composantes connexes de la vue —, on les range selon
    // la première de leurs boîtes écrite par l'utilisateur, et on tire un seul lien invisible
    // d'un bloc au suivant. Le reste, le moteur s'en charge, et il le fait mieux que nous.
    private static ImmutableList<UmlEntity> enchainer(List<UmlEntity> entites,
        ImmutableSet<String> presentes) {
        if (entites.size() < 2) {
            return ImmutableList.copyOf(entites);
        }
        List<List<UmlEntity>> blocs = blocs(entites);
        if (blocs.size() < 2) {
            // Tout se tient déjà : le moteur a tout ce qu'il lui faut pour placer la vue.
            return ImmutableList.copyOf(entites);
        }

        // Le représentant d'un bloc est sa première boîte écrite ; c'est elle qui porte le lien
        // vers le bloc suivant, et elle qu'on veut voir en tête du groupe.
        Map<String, String> versLeBlocSuivant = new LinkedHashMap<>();
        for (int i = 0; i + 1 < blocs.size(); i++) {
            versLeBlocSuivant.put(blocs.get(i).get(0).qualifiedName(),
                blocs.get(i + 1).get(0).qualifiedName());
        }

        ImmutableList.Builder<UmlEntity> resultat = ImmutableList.builder();
        for (UmlEntity entite : entites) {
            String suivant = versLeBlocSuivant.get(entite.qualifiedName());
            resultat.add(suivant == null ? entite : avecOrdre(entite, suivant));
        }
        return resultat.build();
    }

    // La même entité, plus un lien invisible vers la boîte donnée.
    private static UmlEntity avecOrdre(UmlEntity entite, String suivant) {
        ImmutableList<UmlRelation> relations = ImmutableList.<UmlRelation>builder()
            .addAll(entite.relations())
            .add(new UmlLayout(suivant, UMLDirection.NONE))
            .build();

        return new UmlEntity(entite.qualifiedName(), entite.name(), entite.kind(),
            entite.stereotype(), entite.color(), entite.strategy(), entite.literals(),
            entite.fields(), entite.methods(), relations, entite.notes(), entite.constraints(),
            entite.group());
    }

    // Les blocs de la vue : des groupes de boîtes que les traits relient, directement ou de
    // proche en proche. Chaque bloc conserve l'ordre d'écriture de ses membres, et les blocs
    // eux-mêmes sont rangés selon leur premier membre écrit.
    private static List<List<UmlEntity>> blocs(List<UmlEntity> entites) {
        Map<String, Set<String>> voisins = adjacence(entites);
        Map<String, UmlEntity> parNom = new LinkedHashMap<>();
        for (UmlEntity entite : entites) {
            parNom.put(entite.qualifiedName(), entite);
        }

        Set<String> vues = new LinkedHashSet<>();
        List<List<UmlEntity>> blocs = new ArrayList<>();

        for (UmlEntity depart : entites) {
            if (!vues.add(depart.qualifiedName())) {
                continue;
            }
            // Un parcours en largeur depuis cette boîte : tout ce qu'on atteint appartient au
            // même bloc. La file garde l'ordre de découverte, mais le bloc, lui, sera relu dans
            // l'ordre d'écriture — c'est celui-là qui compte pour l'utilisateur.
            Set<String> bloc = new LinkedHashSet<>();
            bloc.add(depart.qualifiedName());
            List<String> aVoir = new ArrayList<>(List.of(depart.qualifiedName()));

            while (!aVoir.isEmpty()) {
                String courant = aVoir.remove(0);
                for (String voisin : voisins.getOrDefault(courant, Set.of())) {
                    if (bloc.add(voisin)) {
                        vues.add(voisin);
                        aVoir.add(voisin);
                    }
                }
            }
            blocs.add(entites.stream().filter(e -> bloc.contains(e.qualifiedName())).toList());
        }
        return blocs;
    }

    // Qui touche qui, dans les deux sens : un trait relie ses deux extrémités, peu importe
    // laquelle le déclare.
    private static Map<String, Set<String>> adjacence(List<UmlEntity> entites) {
        Map<String, Set<String>> voisins = new LinkedHashMap<>();
        for (UmlEntity entite : entites) {
            for (UmlRelation relation : entite.relations()) {
                for (String bout : extremites(relation)) {
                    voisins.computeIfAbsent(entite.qualifiedName(), n -> new LinkedHashSet<>())
                        .add(bout);
                    voisins.computeIfAbsent(bout, n -> new LinkedHashSet<>())
                        .add(entite.qualifiedName());
                }
            }
        }
        return voisins;
    }
}
