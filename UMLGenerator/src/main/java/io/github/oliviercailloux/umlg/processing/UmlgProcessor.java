package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLConstraints;
import io.github.oliviercailloux.umlg.annotation.UMLDependencies;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLNotes;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.extraction.rules.Regles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

// Le processeur d'annotations de la bibliothèque : il refuse la compilation d'un code annoté à
// tort.
//
// Il n'engendre aucun fichier. Son seul effet est de faire échouer javac, en expliquant la faute
// et en pointant la valeur en cause — un @UMLClass oublié, une multiplicité impossible, une
// couleur inconnue. Une annotation mal écrite étant une faute de code, elle doit se voir là où
// elle est écrite, et non à la génération du diagramme.
//
// Découverte. Le processeur est déclaré deux fois, parce qu'il y a deux façons de le trouver :
// META-INF/services pour un usage sur le classpath, « provides » dans module-info pour le module
// path. Il reste conseillé de le nommer explicitement dans le pom de l'utilisateur, via
// <annotationProcessorPaths> : javac ne découvre plus tout seul les processeurs du classpath.
public final class UmlgProcessor extends AbstractProcessor {

    // Les annotations qui déclenchent une vérification. @UMLDependencies y figure sans être
    // jamais écrite par l'utilisateur : c'est sous ce nom que le compilateur range plusieurs
    // @UMLDependency, et sans elle les dépendances répétées ne seraient jamais examinées.
    private static final ImmutableList<Class<? extends Annotation>> SURVEILLEES = ImmutableList.of(
        UMLClass.class,
        UMLAssociation.class,
        UMLDependency.class,
        UMLDependencies.class,
        UMLInclude.class,
        UMLExclude.class,
        UMLDerived.class,
        UMLMultiplicity.class,
        UMLNote.class,
        UMLNotes.class,
        UMLConstraint.class,
        UMLConstraints.class,
        UMLPackage.class,
        UMLAssociationClass.class);

    // L'option qui fait taire les avertissements : -Aumlg.warnings=none sur la ligne de javac,
    // ou <compilerArgs> dans le pom. Les erreurs, elles, restent : elles disent qu'aucun
    // diagramme ne peut être produit, ce dont personne ne peut vouloir être dispensé.
    private static final String OPTION_AVERTISSEMENTS = "umlg.warnings";

    private static final String AVERTISSEMENTS_MUETS = "none";

    // L'option qui donne au processeur le chemin du fichier de règles :
    // -Aumlg.rules=src/main/resources/umlg.json, ou <compilerArgs> dans le pom.
    //
    // Le nom de l'option ne suit pas celui du fichier : elle désigne les règles, non leur format,
    // et elle s'appelait déjà ainsi quand le fichier s'appelait umlg.rules. La renommer aurait
    // cassé les poms qui la posent sans rien apprendre à personne.
    //
    // Elle existe parce que javac ne peut pas le trouver seul : le fichier vit à la racine du
    // répertoire des classes compilées, et rien, dans une compilation, ne dit ce que la
    // construction fera du résultat. Sans elle, le processeur se tait sur tout ce qui dépend de
    // la stratégie du projet (voir Strategie).
    private static final String OPTION_REGLES = "umlg.rules";

    private Rapporteur rapporteur;
    private VerificateurClasse classes;
    private VerificateurAssociation associations;
    private VerificateurDependance dependances;
    private VerificateurMembre membres;
    private VerificateurDerive derives;
    private VerificateurMultiplicite multiplicites;
    private VerificateurNote notes;
    private VerificateurContrainte contraintes;
    private VerificateurPackage groupes;
    private VerificateurClasseDAssociation classesDAssociation;

    @Override
    public synchronized void init(ProcessingEnvironment environnement) {
        super.init(environnement);

        String demande = environnement.getOptions().get(OPTION_AVERTISSEMENTS);
        boolean avertir = !AVERTISSEMENTS_MUETS.equals(demande);

        rapporteur = new Rapporteur(environnement.getMessager(), environnement.getElementUtils(),
            avertir);

        // La valeur de l'option est vérifiée, et l'erreur passe outre le silence demandé.
        //
        // « -Aumlg.warnings=NONE », « =off », « =false » ne valaient rien : la seule valeur
        // reconnue est « none », et toute autre laissait les avertissements passer sans que rien
        // ne signale la faute de frappe. L'utilisateur croyait avoir fait taire le processeur, et
        // se demandait pourquoi il parlait encore.
        if (demande != null && !AVERTISSEMENTS_MUETS.equals(demande)) {
            rapporteur.erreur("-A" + OPTION_AVERTISSEMENTS + "=" + demande + ": unknown value. "
                + "The only one this processor understands is \"" + AVERTISSEMENTS_MUETS
                + "\", which silences its warnings; remove the option to keep them.");
        }

        // Le fichier de règles est lu avant que le silence ne s'applique à ce qui le concerne :
        // « -Aumlg.warnings=none » demande de taire ce que le processeur a trouvé dans le code,
        // pas de taire que le processeur lui-même est mal configuré. Un fichier de règles
        // introuvable désarme la moitié des contrôles, et le taire ferait passer un processeur
        // aveugle pour un processeur satisfait.
        Strategie strategie = new Strategie(
            lireRegles(environnement.getOptions().get(OPTION_REGLES)));
        Cible cible = new Cible(rapporteur, environnement.getTypeUtils());
        TypeAttribut typeAttribut = new TypeAttribut(environnement.getTypeUtils(),
            environnement.getElementUtils());

        classes = new VerificateurClasse(rapporteur);
        associations = new VerificateurAssociation(rapporteur, cible, typeAttribut);
        dependances = new VerificateurDependance(rapporteur, cible);
        membres = new VerificateurMembre(rapporteur, strategie);
        derives = new VerificateurDerive(rapporteur, strategie);
        multiplicites = new VerificateurMultiplicite(rapporteur, strategie);
        notes = new VerificateurNote(rapporteur, strategie);
        contraintes = new VerificateurContrainte(rapporteur, strategie);
        groupes = new VerificateurPackage(rapporteur);
        classesDAssociation = new VerificateurClasseDAssociation(rapporteur, cible);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        // Dérivé des classes elles-mêmes plutôt qu'écrit en toutes lettres : un renommage
        // d'annotation ne peut pas laisser derrière lui une chaîne devenue fausse.
        return SURVEILLEES.stream().map(Class::getCanonicalName)
            .collect(ImmutableSet.toImmutableSet());
    }

    // Déclarer les options évite l'avertissement de javac sur un -A qu'aucun processeur ne
    // réclame.
    @Override
    public Set<String> getSupportedOptions() {
        return ImmutableSet.of(OPTION_AVERTISSEMENTS, OPTION_REGLES);
    }

    // Les règles désignées par l'option, ou null si elle n'a pas été donnée.
    //
    // Trois issues, et trois messages différents, parce que les trois situations n'ont pas la
    // même gravité :
    //   - option absente : le cas ordinaire, rien à signaler. Le processeur se taira sur ce qu'il
    //     ne peut pas savoir, ce qui est son comportement normal ;
    //   - fichier introuvable : l'utilisateur a nommé un fichier qu'il n'a pas écrit. Rien n'est
    //     faux dans son code, mais il attend des vérifications qu'il n'aura pas — d'où un
    //     avertissement, et non une erreur ;
    //   - fichier fautif : c'est une erreur. Le même fichier arrêtera la génération pour la même
    //     raison, et le dire ici le dit plus tôt, ce qui est tout l'intérêt du processeur.
    private Regles lireRegles(String chemin) {
        if (chemin == null || chemin.isBlank()) {
            return null;
        }
        Path fichier = Paths.get(chemin);

        if (!Files.isRegularFile(fichier)) {
            rapporteur.avertissementDeConfiguration("-A" + OPTION_REGLES + "=" + chemin + ": no such file. The "
                + "checks that depend on the project strategy cannot conclude, and will stay "
                + "silent.");
            return null;
        }
        try {
            return Regles.duFichier(fichier);
        } catch (IllegalArgumentException e) {
            rapporteur.erreur(chemin + " : " + e.getMessage());
            return null;
        } catch (IOException e) {
            rapporteur.avertissementDeConfiguration("-A" + OPTION_REGLES + "=" + chemin + ": cannot be read ("
                + e.getMessage() + "). The checks that depend on the project strategy will stay "
                + "silent.");
            return null;
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment tour) {
        // Le dernier tour ne contient plus de code à examiner : il sert à ceux qui engendrent des
        // fichiers, ce que nous ne faisons pas.
        if (tour.processingOver()) {
            return false;
        }

        // Les types à examiner : ceux qui portent @UMLClass, et ceux qui portent une dépendance
        // sans porter @UMLClass — ce sont justement ceux-là qu'il faut signaler.
        for (Element classe : porteurs(tour, UMLClass.class, UMLDependency.class,
            UMLDependencies.class, UMLPackage.class, UMLAssociationClass.class)) {
            classes.verifier((TypeElement) classe);
            dependances.verifier((TypeElement) classe);
            groupes.verifier((TypeElement) classe);
            classesDAssociation.verifier((TypeElement) classe);
        }

        for (Element champ : porteurs(tour, UMLAssociation.class)) {
            associations.verifier(champ);
        }

        // Les deux dérogations ensemble : un membre portant @UMLInclude et @UMLExclude à la fois
        // ne doit être examiné qu'une fois, sans quoi la contradiction serait signalée deux fois.
        for (Element membre : porteurs(tour, UMLInclude.class, UMLExclude.class)) {
            membres.verifier(membre);
        }

        for (Element membre : porteurs(tour, UMLDerived.class)) {
            derives.verifier(membre);
        }

        for (Element membre : porteurs(tour, UMLMultiplicity.class)) {
            multiplicites.verifier(membre);
        }

        for (Element porteur : porteurs(tour, UMLNote.class, UMLNotes.class)) {
            notes.verifier(porteur);
        }

        for (Element porteur : porteurs(tour, UMLConstraint.class, UMLConstraints.class)) {
            contraintes.verifier(porteur);
        }


        // false : les annotations ne sont pas « consommées ». Elles restent visibles des autres
        // processeurs, ce qui est la moindre des politesses pour un processeur qui se contente
        // de vérifier.
        return false;
    }

    // Les éléments portant l'une au moins des annotations données, chacun une seule fois.
    //
    // Le dédoublonnage est la raison d'être de cette méthode. Il sert deux fois : pour les
    // annotations répétables, dont le compilateur remplace la forme isolée par un conteneur, et
    // pour @UMLInclude et @UMLExclude, qu'un même membre peut porter toutes les deux.
    //
    // ImmutableSet.Builder conserve l'ordre de première rencontre, ce qui rend l'ordre des
    // messages reproductible d'une compilation à l'autre — et il ne rend qu'une collection
    // immuable, que l'appelant ne peut pas modifier par mégarde.
    @SafeVarargs
    private static ImmutableSet<Element> porteurs(RoundEnvironment tour,
        Class<? extends Annotation>... annotations) {
        ImmutableSet.Builder<Element> porteurs = ImmutableSet.builder();
        for (Class<? extends Annotation> annotation : annotations) {
            porteurs.addAll(tour.getElementsAnnotatedWith(annotation));
        }
        return sansLesClonesDeComposants(porteurs.build());
    }

    // Écarte l'accesseur d'un composant de record quand le champ correspondant est déjà là.
    //
    // Une annotation écrite sur un composant — « record T(@UMLInclude(name = "  ") String s) » —
    // est recopiée par javac sur le champ ET sur l'accesseur. Ce sont deux Element distincts, que
    // le dédoublonnage par identité laissait donc tous les deux passer : l'utilisateur recevait
    // deux fois le même message pour une seule annotation écrite, dont la seconde fois sans
    // fichier ni ligne, l'accesseur engendré n'existant nulle part dans son code.
    //
    // Le champ est celui qu'on garde, parce que c'est lui que javac sait situer. Un @UMLInclude
    // posé sur un accesseur que l'auteur a écrit lui-même n'est pas concerné : il n'a pas de
    // champ jumeau dans l'ensemble, et rien ne l'écarte.
    private static ImmutableSet<Element> sansLesClonesDeComposants(ImmutableSet<Element> tous) {
        ImmutableSet<String> champsDeComposant = tous.stream()
            .filter(element -> element.getKind() == ElementKind.FIELD)
            .filter(Membre::vientDUnComposant)
            .map(Membre::emplacement)
            .collect(ImmutableSet.toImmutableSet());

        if (champsDeComposant.isEmpty()) {
            return tous;
        }
        return tous.stream()
            .filter(element -> element.getKind() != ElementKind.METHOD
                || !Membre.vientDUnComposant(element)
                || !champsDeComposant.contains(Membre.emplacement(element)))
            .collect(ImmutableSet.toImmutableSet());
    }
}
