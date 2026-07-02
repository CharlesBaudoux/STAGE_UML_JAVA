package fr.dauphine.uml;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;

record UmlEntity(String name) {
    
    static UmlEntity from(Class<?> targetClass) {
        checkNotNull(targetClass, "La classe cible ne peut pas être null.");
        checkArgument(targetClass.isAnnotationPresent(UMLClass.class), 
            "La classe %s ne possède pas l'annotation @UMLClass.", targetClass.getName());
            
        return new UmlEntity(targetClass.getSimpleName());
    }

    UmlEntity {
        checkNotNull(name, "Le nom de la classe est requis.");
        checkArgument(!name.isBlank(), "Le nom de la classe ne peut pas être vide.");
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name", name)
            .toString();
    }
}