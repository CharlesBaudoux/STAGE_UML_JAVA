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
import io.github.oliviercailloux.umlg.annotation.UMLDiagram;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLLayout;
import io.github.oliviercailloux.umlg.annotation.UMLLayouts;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLNotes;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
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
        UMLDiagram.class,
        UMLPackage.class,
        UMLAssociationClass.class,
        UMLLayout.class,
        UMLLayouts.class);

    // L'option qui fait taire les avertissements : -Aumlg.warnings=none sur la ligne de javac,
    // ou <compilerArgs> dans le pom. Les erreurs, elles, restent : elles disent qu'aucun
    // diagramme ne peut être produit, ce dont personne ne peut vouloir être dispensé.
    private static final String OPTION_AVERTISSEMENTS = "umlg.warnings";

    private static final String AVERTISSEMENTS_MUETS = "none";

    private Rapporteur rapporteur;
    private VerificateurClasse classes;
    private VerificateurAssociation associations;
    private VerificateurDependance dependances;
    private VerificateurMembre membres;
    private VerificateurDerive derives;
    private VerificateurMultiplicite multiplicites;
    private VerificateurNote notes;
    private VerificateurContrainte contraintes;
    private VerificateurDiagramme diagrammes;
    private VerificateurPackage groupes;
    private VerificateurClasseDAssociation classesDAssociation;
    private VerificateurLayout placements;

    @Override
    public synchronized void init(ProcessingEnvironment environnement) {
        super.init(environnement);

        boolean avertir = !AVERTISSEMENTS_MUETS.equals(
            environnement.getOptions().get(OPTION_AVERTISSEMENTS));

        rapporteur = new Rapporteur(environnement.getMessager(), environnement.getElementUtils(),
            avertir);
        Cible cible = new Cible(rapporteur, environnement.getTypeUtils());
        TypeAttribut typeAttribut = new TypeAttribut(environnement.getTypeUtils(),
            environnement.getElementUtils());

        classes = new VerificateurClasse(rapporteur);
        associations = new VerificateurAssociation(rapporteur, cible, typeAttribut);
        dependances = new VerificateurDependance(rapporteur, cible);
        membres = new VerificateurMembre(rapporteur);
        derives = new VerificateurDerive(rapporteur);
        multiplicites = new VerificateurMultiplicite(rapporteur);
        notes = new VerificateurNote(rapporteur);
        contraintes = new VerificateurContrainte(rapporteur);
        diagrammes = new VerificateurDiagramme(rapporteur);
        groupes = new VerificateurPackage(rapporteur);
        classesDAssociation = new VerificateurClasseDAssociation(rapporteur, cible);
        placements = new VerificateurLayout(rapporteur, cible);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        // Dérivé des classes elles-mêmes plutôt qu'écrit en toutes lettres : un renommage
        // d'annotation ne peut pas laisser derrière lui une chaîne devenue fausse.
        return SURVEILLEES.stream().map(Class::getCanonicalName)
            .collect(ImmutableSet.toImmutableSet());
    }

    // Déclarer l'option évite l'avertissement de javac sur un -A qu'aucun processeur ne réclame.
    @Override
    public Set<String> getSupportedOptions() {
        return ImmutableSet.of(OPTION_AVERTISSEMENTS);
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
            UMLDependencies.class, UMLPackage.class, UMLAssociationClass.class, UMLLayout.class,
            UMLLayouts.class)) {
            classes.verifier((TypeElement) classe);
            dependances.verifier((TypeElement) classe);
            groupes.verifier((TypeElement) classe);
            classesDAssociation.verifier((TypeElement) classe);
            placements.verifier((TypeElement) classe);
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

        // Les paquetages, seuls porteurs possibles de @UMLDiagram : l'annotation ne vise pas un
        // élément du modèle mais le dessin dans son ensemble.
        for (Element paquetage : porteurs(tour, UMLDiagram.class)) {
            diagrammes.verifier(paquetage);
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
        return porteurs.build();
    }
}
