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

public final class ReferenceProject {

    private ReferenceProject() {}

    @UMLClass
    public interface Identifiable {

        String identifier();
    }

    @UMLClass(strategy = UMLVisibility.PROTECTED)
    public abstract static class Person implements Identifiable {

        protected String name;

        public abstract void validate();
    }

    @UMLClass(strategy = UMLVisibility.PUBLIC)
    public enum Status {
        Active, Suspended
    }

    @UMLClass
    @UMLPackage(name = "Billing", color = "#EEF3F7")
    public static class Order {}

    @UMLClass
    @UMLPackage(name = "Billing")
    public static class ServiceBank {}

    @UMLClass(strategy = UMLVisibility.PRIVATE)
    @UMLPackage(name = "Billing")
    @UMLAssociationClass(source = Order.class, target = ServiceBank.class)
    public static class Settlement {

        private double amount;
    }

    @UMLClass(name = "Client", color = "LightBlue", stereotype = "Entity",
        strategy = UMLVisibility.PACKAGE)
    @UMLNote(text = "Core of the domain, do not expose outside the module",
        direction = UMLDirection.UP)
    @UMLConstraint(rule = "holder of age")
    @UMLDependency(target = ServiceBank.class, label = "invoices via")
    public static class ClientInvoice extends Person {

        @UMLInclude
        private int counter;

        @UMLExclude
        public String log;

        @UMLDerived
        int age;

        @UMLNote(text = "Never negative")
        @UMLConstraint(rule = "balance positive")
        double balance;

        Status status;

        @UMLMultiplicity("0..*")
        String labels;

        @UMLAssociation(
            target = Order.class,
            targetMultiplicity = "0..*",
            notation = UMLNotation.ATTRIBUTE)
        private List<Order> archived;

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

        public void credit(double amount) {}

        @Override
        public void validate() {}

        @UMLMultiplicity("0..*")
        public String history() {
            return "";
        }

        @UMLDerived
        public String summarize() {
            return "";
        }

        @Override
        public String identifier() {
            return "";
        }

        public String getName() {
            return name;
        }
    }
}
