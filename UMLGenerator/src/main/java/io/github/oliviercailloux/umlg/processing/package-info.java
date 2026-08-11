// La vérification au moment de la compilation.
//
// Ce paquetage contient un processeur d'annotations (JSR 269), c'est-à-dire du code qui
// s'exécute à l'intérieur de javac. Il ne produit rien : il lit les annotations du code de
// l'utilisateur et refuse la compilation quand elles sont fautives, en expliquant pourquoi et en
// pointant la valeur en cause.
//
// Deux gravités, et la frontière entre elles est une règle, pas une nuance de ton :
//   - erreur : ce code ne décrit aucun diagramme. Une cible sans @UMLClass, une multiplicité
//     « 3..1 », une couleur que PlantUML ignore, un membre portant à la fois @UMLInclude et
//     @UMLExclude. La compilation s'arrête.
//   - avertissement : ce code décrit un diagramme, mais vraisemblablement pas celui que son
//     auteur croit. Une dérogation qui ne déroge à rien, une multiplicité que le type de
//     l'attribut contredit, deux boîtes portant la même étiquette. La compilation continue :
//     la bibliothèque n'est pas juge des intentions, et l'annotation fait toujours foi. Mais
//     taire l'écart laisserait l'utilisateur le découvrir sur l'image, sans indice sur sa cause.
//
// Les avertissements s'éteignent avec -Aumlg.warnings=none ; les erreurs, jamais.
//
// Pourquoi ici plutôt qu'à la génération. Une annotation mal écrite est une faute de code, pas
// un incident d'exécution. La signaler à la compilation la met sous les yeux de son auteur, au
// moment où il l'écrit, plutôt que quelques minutes plus tard au fond d'une pile d'exceptions.
//
// Ce que ce paquetage ne remplace pas. Trois catégories de fautes lui échappent par nature, et
// les Preconditions du modèle et de l'extraction restent donc en place :
//   - « la cible n'est pas dans le répertoire scanné » dépend de l'exécution, pas du code ;
//   - « deux classes portent le même nom dans le diagramme » aussi ;
//   - le paquetage model est exporté, donc constructible par programme, sans aucune annotation.
// La vérification est ainsi à deux niveaux : celui-ci pour le code annoté, celui du modèle pour
// l'API. Pour qu'ils ne divergent jamais, ce paquetage ne réécrit aucune règle : il construit
// les objets du modèle avec les valeurs lues dans l'annotation et traduit en erreur de
// compilation l'exception que le modèle lève déjà.
//
// Quand la question ne peut pas être posée au modèle — parce qu'elle porte sur le code annoté et
// non sur le diagramme —, c'est le message qui est partagé, jamais recopié : la règle vit là où
// elle s'applique, et ce paquetage vient l'y lire. Multiplicites, Contraintes et ColorCodec ont
// été nommées exactement pour cela ; AssociationClassExtractor, TargetResolver,
// DependencyExtractor, PackageExtractor, ClassHeaderValidator et MemberExtractor rendent de même
// les textes que les deux moitiés se partagent. Chacune de ces paires avait déjà divergé, ou
// n'attendait que le premier changement pour le faire.
//
// Non exporté par module-info : le processeur se déclare avec « provides », ce qui n'exige pas
// d'ouvrir le paquetage. L'utilisateur ne doit jamais y importer quoi que ce soit.
package io.github.oliviercailloux.umlg.processing;
