/**
 * Génère un diagramme de classes PlantUML à partir de code Java annoté.
 *
 * <p>L'utilisateur annote ses classes, désigne un répertoire de classes compilées, et reçoit une
 * image :
 *
 * <p>  UMLGenerator.builder()
 *       .sourceDirectory("target/classes")
 *       .outputFile("architecture.svg")
 *       .build()
 *       .generate();
 *
 * <p>LE CHEMIN D'UNE CLASSE JUSQU'AU DESSIN
 *
 * <p>  code annoté  --->  extraction  --->  model  --->  rendering  --->  fichier .svg / .png
 *                      (réflexion)      (records)     (PlantUML)
 *
 * <p>Trois étapes, trois paquetages, et une frontière nette entre chacun : l'extraction est seule à
 * connaître java.lang.reflect, le rendu est seul à connaître PlantUML, et le modèle qui les
 * sépare ne connaît ni l'un ni l'autre. Ce qui traverse la frontière n'est jamais un Class ni un
 * Field, seulement des objets du paquetage model.
 *
 * <p>À côté de ce chemin, deux paquetages qui ne participent pas au dessin :
 *   - annotation : le vocabulaire que l'utilisateur écrit dans son code, et rien d'autre ;
 *   - processing : un processeur d'annotations qui vérifie ce vocabulaire au moment où javac
 *     compile le code de l'utilisateur, sans rien produire.
 *
 * <p>LES PAQUETAGES, DU PLUS VISIBLE AU PLUS INTERNE
 *
 * <p>  umlg              UMLGenerator, la façade — le seul point d'entrée.
 *   umlg.annotation   @UMLClass, @UMLAssociation, @UMLNote… ce que l'utilisateur écrit.
 *   umlg.model        UmlEntity, UmlAssociation, UmlNote… le diagramme tel qu'il est dit.
 *   umlg.extraction   Du code annoté au modèle. Sous-paquetages : member (le contenu des
 *                     boîtes), relation (ce qui les relie).
 *   umlg.rendering    Du modèle à l'image. Sous-paquetages : element (les formes dessinées),
 *                     syntax (les règles du langage PlantUML).
 *   umlg.processing   La vérification à la compilation.
 *   umlg.warning      Le mécanisme des avertissements — une classe, tenue hors de la surface
 *                     publique parce que « dire » est le geste de l'extraction et du rendu.
 *
 * <p>Les trois premiers sont exportés, et ce sont exactement ceux que la façade met en jeu. Les
 * quatre autres sont fermés : voir module-info.java, qui explique pourquoi l'unité
 * d'encapsulation est la bibliothèque entière et non le paquetage.
 *
 * <p>OÙ EST TRAITÉE CHAQUE ANNOTATION
 *
 * <p>Le découpage étant par étape et non par annotation, chacune traverse plusieurs paquetages.
 * Cette table dit lesquels. Elle ne cite que les fichiers qui portent le comportement de
 * l'annotation : beaucoup d'autres la consultent au passage — VerificateurNote demande si la
 * classe porte @UMLClass — sans rien en implémenter.
 *
 * <p>  @UMLClass, UMLVisibility
 *       model        UmlEntity, UmlEntityKind
 *       extraction   ProjectScanner (le filtre d'entrée), EntityExtractor, ClassHeaderValidator
 *       rendering    ClassBoxRenderer, ColorCodec, AliasRegistry
 *       processing   VerificateurClasse
 *
 * <p>  @UMLInclude, @UMLExclude
 *       model        aucun — ce sont des filtres, ils ne laissent aucune trace dans le modèle
 *       extraction   MemberExtractor, VisibilityPolicy (la règle), Regles (les motifs « hide »
 *                    que @UMLInclude rattrape)
 *       rendering    aucun
 *       processing   VerificateurMembre
 *
 * <p>  @UMLAssociation, UMLRelationType, UMLDirection
 *       model        UmlAssociation, UmlRelation
 *       extraction   AssociationExtractor, TargetResolver, Emplacement
 *       rendering    RelationRenderer, Extremite
 *       processing   VerificateurAssociation, Cible, TypeAttribut
 *
 * <p>  @UMLDependency, @UMLDependencies
 *       model        UmlDependency
 *       extraction   DependencyExtractor, TargetResolver
 *       rendering    RelationRenderer
 *       processing   VerificateurDependance, Cible
 *
 * <p>  @UMLDerived
 *       model        la composante « derived » d'UmlField, UmlMethod et UmlAssociation
 *       extraction   MemberExtractor, AssociationExtractor
 *       rendering    ClassBoxRenderer, Extremite
 *       processing   VerificateurDerive
 *
 * <p>  @UMLNote, @UMLNotes
 *       model        UmlNote, et la composante « notes » d'UmlEntity
 *       extraction   NoteExtractor
 *       rendering    NoteRenderer, PlantUmlEscaper (la forme en bloc)
 *       processing   VerificateurNote
 *
 * <p>  @UMLConstraint, @UMLConstraints
 *       model        Contraintes, et la composante « constraints » d'UmlEntity, UmlField et
 *                    UmlAssociation
 *       extraction   ConstraintExtractor
 *       rendering    ClassBoxRenderer, Extremite, NoteRenderer
 *       processing   VerificateurContrainte
 *
 * <p>  héritage — la seule relation qu'aucune annotation ne déclare : elle se lit dans « extends »
 *   et « implements », et n'a donc rien à vérifier
 *       model        UmlInheritance, UmlInheritanceType
 *       extraction   InheritanceExtractor
 *       rendering    RelationRenderer
 *
 * <p>Le reste ne sert aucune annotation en particulier : UMLGenerator et ProjectScanner mènent la
 * manœuvre, PlantUmlWriter et PlantUmlSyntaxBuilder assemblent le document, PlantUmlLayout,
 * PlantUmlEscaper et AliasRegistry tiennent les règles du langage PlantUML, Rapporteur et Membre
 * servent tous les vérificateurs.
 *
 * <p>La convention de nommage suit cette table, sans exception : une annotation @UMLXxx donne
 * XxxExtractor, XxxRenderer et VerificateurXxx. « grep -r Note src/main » retrouve donc les
 * fichiers d'@UMLNote sans passer par ici — la table sert à comprendre, la convention à trouver.
 *
 * <p>DEUX PRINCIPES QUI EXPLIQUENT LA PLUPART DES CHOIX
 *
 * <p>Rien n'est deviné, tout est déclaré. La lecture ayant lieu après compilation, ce que
 * l'effacement de type a supprimé est perdu : {@code List<Commande>} n'est plus que « List ». La
 * classe visée par un lien et sa multiplicité sont donc des paramètres d'annotation, jamais des
 * déductions. Une déduction serait de toute façon incapable de trancher entre 0..* et 1..*, qui
 * relèvent du métier.
 *
 * <p>Un objet valide, ou pas d'objet du tout. Le modèle n'est fait que de records immuables dont
 * les constructeurs compacts tiennent les invariants : une UmlAssociation qui existe est une
 * association valide, quel que soit le chemin qui l'a construite. Les extracteurs n'y ajoutent
 * que le « où » — sur quel attribut de quelle classe la faute a été commise.
 */
package io.github.oliviercailloux.umlg;
