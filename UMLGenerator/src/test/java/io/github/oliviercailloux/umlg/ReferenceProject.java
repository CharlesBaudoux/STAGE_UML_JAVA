package io.github.oliviercailloux.umlg;

import io.github.oliviercailloux.umlg.annotation.UMLAssociation;
import io.github.oliviercailloux.umlg.annotation.UMLAssociationClass;
import io.github.oliviercailloux.umlg.annotation.UMLClass;
import io.github.oliviercailloux.umlg.annotation.UMLConstraint;
import io.github.oliviercailloux.umlg.annotation.UMLDependency;
import io.github.oliviercailloux.umlg.annotation.UMLDerived;
import io.github.oliviercailloux.umlg.annotation.UMLDirection;
import io.github.oliviercailloux.umlg.annotation.UMLExclude;
import io.github.oliviercailloux.umlg.annotation.UMLInclude;
import io.github.oliviercailloux.umlg.annotation.UMLMultiplicity;
import io.github.oliviercailloux.umlg.annotation.UMLNotation;
import io.github.oliviercailloux.umlg.annotation.UMLNote;
import io.github.oliviercailloux.umlg.annotation.UMLPackage;
import io.github.oliviercailloux.umlg.annotation.UMLRelationType;
import io.github.oliviercailloux.umlg.annotation.UMLVisibility;
import java.util.List;

// The fixture project of the reference test: a small billing domain that exercises, in one go,
// every annotation the user writes — containers aside, which are never written by hand — and
// every one of their parameters.
//
// The exact count is deliberately not written here: it would change with the first annotation
// added, and a comment that lies is worse than no comment at all. The rule, on the other hand,
// does not change: every new annotation has its place in this file, and the reference diagram is
// the only test that tells what it does to the whole document.
//
// This file is one half of a pair. The other is reference-diagram.puml, written by hand as well:
// one says what the developer writes, the other what the library must make of it. Reading them
// side by side is the shortest way to understand what the tool does.
//
// Two rules to keep when changing it:
//   - it must compile without the slightest warning from the annotation processor. A fixture the
//     library disapproves of would make a poor example;
//   - any change alters the diagram, hence the reference file. That is intended: reviewing that
//     difference is what gives the test its value.
public final class ReferenceProject {

    private ReferenceProject() {}

    // An interface: the target of an "implements", hence of a realization. With no strategy, its
    // box stays empty — the nominal case of @UMLClass, which must suffice on its own.
    @UMLClass
    public interface Identifiable {

        String identifier();
    }

    // An abstract class: the target of an "extends", hence of a generalization. PROTECTED shows
    // public and protected members.
    @UMLClass(strategy = UMLVisibility.PROTECTED)
    public abstract static class Person implements Identifiable {

        protected String name;

        public abstract void validate();
    }

    // An enumeration: its literals occupy their own compartment, with neither type nor visibility.
    // values() and valueOf(), which the compiler adds, are dropped by a pattern of the rules file
    // — they are not synthetic, and nothing would remove them unless asked.
    @UMLClass(strategy = UMLVisibility.PUBLIC)
    public enum Status {
        Active, Suspended
    }

    // The target of the association. It opens the "Billing" group, and it is the one declaring its
    // colour: the rectangle has only one, and two classes asking for two different ones would be
    // told so.
    @UMLClass
    @UMLPackage(name = "Billing", color = "#EEF3F7")
    public static class Order {}

    // The target of the dependency. Deliberately distinct from Order: a dependency towards a class
    // already reached by an association would be reported as a duplicate.
    //
    // The group is named, the colour is not: the name makes the identity of the rectangle, and two
    // classes enter it without having to repeat its appearance.
    @UMLClass
    @UMLPackage(name = "Billing")
    public static class ServiceBank {}

    // An association class: what a settlement carries — its amount — belongs neither to the order
    // nor to the bank, but to their link. UML draws it with a dotted stroke joining this box to the
    // middle of the association; declaring the class is enough, and the stroke between the two
    // endpoints is drawn at the same time.
    @UMLClass(strategy = UMLVisibility.PRIVATE)
    @UMLPackage(name = "Billing")
    @UMLAssociationClass(source = Order.class, target = ServiceBank.class)
    public static class Settlement {

        private double amount;
    }

    // The class carrying almost everything.
    //
    // @UMLClass shows its four parameters here, @UMLNote its two, @UMLDependency its two,
    // @UMLConstraint its own. The PACKAGE strategy displays public and package-private members, but
    // neither protected nor private ones — the exemptions are what catch the latter up.
    @UMLClass(name = "Client", color = "LightBlue", stereotype = "Entity",
        strategy = UMLVisibility.PACKAGE)
    @UMLNote(text = "Core of the domain, do not expose outside the module",
        direction = UMLDirection.UP)
    @UMLConstraint(rule = "holder of age")
    @UMLDependency(target = ServiceBank.class, label = "invoices via")
    public static class ClientInvoice extends Person {

        // Private, hence hidden by the strategy: @UMLInclude brings it back.
        @UMLInclude
        private int counter;

        // Public, hence displayed by the strategy: @UMLExclude takes it out.
        @UMLExclude
        public String log;

        // Computed rather than stored: UML marks it with a slash before its name.
        @UMLDerived
        int age;

        // The constraint is written after the field, between braces set by the rendering. The note,
        // for its part, hangs on the member: this is the only place of the diagram where PlantUML
        // imposes the block form, closed by "end note".
        @UMLNote(text = "Never negative")
        @UMLConstraint(rule = "balance positive")
        double balance;

        // A field whose type is itself a class of the diagram, and which nevertheless stays a
        // field: the library never infers a link from a type. Only @UMLAssociation creates one.
        Status status;

        // A multi-valued property that is not a link: there is no class at the other end of a set
        // of strings. @UMLMultiplicity says the count, and nothing else.
        @UMLMultiplicity("0..*")
        String labels;

        // The second notation of the same annotation: the association stays written in the box,
        // under the form "type name [multiplicity]". No stroke leaves it, and the line appears even
        // though the field is private — an association asked for by name wins over the strategy,
        // exactly like the stroke below.
        @UMLAssociation(
            target = Order.class,
            targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Order> archived;

        // The association, with its nine parameters. The constraints set on the same field leave
        // with the link and not in the box: the field no longer appears there. "ordered" is one
        // constraint like any other, since the isOrdered parameter — which produced the same
        // {ordered} by a second path — was removed.
        @UMLConstraint(rule = "ordered")
        @UMLConstraint(rule = "not empty")
        @UMLAssociation(
            target = Order.class,
            notation = UMLNotation.ARROW,
            type = UMLRelationType.COMPOSITION,
            sourceMultiplicity = "1",
            targetMultiplicity = "0..*",
            sourceRole = "buyer",
            targetRole = "purchases",
            label = "places",
            direction = UMLDirection.DOWN)
        private List<Order> orders;

        // A method with a parameter: the diagram names it, "amount : double". This project is
        // compiled with the "-parameters" option (see the pom); without it, the signature would
        // come down to "credit(double) : void".
        public void credit(double amount) {}

        // Overrides the abstract method of Person.
        @Override
        public void validate() {}

        // The same annotation on a method: there it is the returned value that is counted.
        @UMLMultiplicity("0..*")
        public String history() {
            return "";
        }

        // A derived method: it returns a computed value.
        @UMLDerived
        public String summarize() {
            return "";
        }

        // The contract of the interface, honoured by the parent class.
        @Override
        public String identifier() {
            return "";
        }

        // An accessor: the "hide get*" pattern drops it, and it does not appear on the diagram.
        public String getName() {
            return name;
        }
    }
}
