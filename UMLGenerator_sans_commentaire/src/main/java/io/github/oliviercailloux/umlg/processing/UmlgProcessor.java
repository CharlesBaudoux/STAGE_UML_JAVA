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

public final class UmlgProcessor extends AbstractProcessor {

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

    private static final String OPTION_AVERTISSEMENTS = "umlg.warnings";

    private static final String AVERTISSEMENTS_MUETS = "none";

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

        if (demande != null && !AVERTISSEMENTS_MUETS.equals(demande)) {
            rapporteur.erreur("-A" + OPTION_AVERTISSEMENTS + "=" + demande + ": unknown value. "
                + "The only one this processor understands is \"" + AVERTISSEMENTS_MUETS
                + "\", which silences its warnings; remove the option to keep them.");
        }

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
        return SURVEILLEES.stream().map(Class::getCanonicalName)
            .collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public Set<String> getSupportedOptions() {
        return ImmutableSet.of(OPTION_AVERTISSEMENTS, OPTION_REGLES);
    }

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
        if (tour.processingOver()) {
            return false;
        }

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


        return false;
    }

    @SafeVarargs
    private static ImmutableSet<Element> porteurs(RoundEnvironment tour,
        Class<? extends Annotation>... annotations) {
        ImmutableSet.Builder<Element> porteurs = ImmutableSet.builder();
        for (Class<? extends Annotation> annotation : annotations) {
            porteurs.addAll(tour.getElementsAnnotatedWith(annotation));
        }
        return sansLesClonesDeComposants(porteurs.build());
    }

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
