package io.github.oliviercailloux.umlg.processing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLConstraints;
import io.github.oliviercailloux.umlg.annotation.UMLDependencies;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLNotes;
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

    private static final ImmutableList<Class<? extends Annotation>> SURVEILLEES = ImmutableList.of(
        UMLClass.class,
        UMLAssociation.class,
        UMLDependency.class,
        UMLDependencies.class,
        UMLInclude.class,
        UMLExclude.class,
        UMLDerived.class,
        UMLNote.class,
        UMLNotes.class,
        UMLConstraint.class,
        UMLConstraints.class);

    private static final String OPTION_AVERTISSEMENTS = "umlg.warnings";

    private static final String AVERTISSEMENTS_MUETS = "none";

    private Rapporteur rapporteur;
    private VerificateurClasse classes;
    private VerificateurAssociation associations;
    private VerificateurDependance dependances;
    private VerificateurMembre membres;
    private VerificateurDerive derives;
    private VerificateurNote notes;
    private VerificateurContrainte contraintes;

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
        notes = new VerificateurNote(rapporteur);
        contraintes = new VerificateurContrainte(rapporteur);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SURVEILLEES.stream().map(Class::getCanonicalName)
            .collect(ImmutableSet.toImmutableSet());
    }

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
        if (tour.processingOver()) {
            return false;
        }

        for (Element classe : porteurs(tour, UMLClass.class, UMLDependency.class,
            UMLDependencies.class)) {
            classes.verifier((TypeElement) classe);
            dependances.verifier((TypeElement) classe);
        }

        for (Element champ : porteurs(tour, UMLAssociation.class)) {
            associations.verifier(champ);
        }

        for (Element membre : porteurs(tour, UMLInclude.class, UMLExclude.class)) {
            membres.verifier(membre);
        }

        for (Element membre : porteurs(tour, UMLDerived.class)) {
            derives.verifier(membre);
        }

        for (Element porteur : porteurs(tour, UMLNote.class, UMLNotes.class)) {
            notes.verifier(porteur);
        }

        for (Element porteur : porteurs(tour, UMLConstraint.class, UMLConstraints.class)) {
            contraintes.verifier(porteur);
        }

        return false;
    }

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
