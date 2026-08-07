# Rapport d'analyse : le jeu d'annotations et le fichier `umlg.rules`

*Analyse du code de `Bibliotheque_V0` au 6 août 2026, à la lumière du corpus de diagrammes
écrits à la main dans `Keyboardd/`. Aucune modification n'a été apportée au code.*

---

## Résumé

Trois constats, par ordre d'importance.

1. **Trois annotations sur treize ne disent rien du modèle UML.** `@UMLDiagram`, `@UMLLayout` et
   `@UMLPackage` décrivent la *présentation* du dessin, pas la structure du code. Le principe
   directeur du document — « ce qui s'applique partout se déclare une seule fois dans
   `umlg.rules` » — désigne le fichier de règles comme leur place naturelle. Les y déplacer
   ramènerait le jeu à dix annotations, toutes sémantiques, et supprimerait au passage la
   machinerie de lecture des `package-info.class`.

2. **La stratégie de visibilité doit être répétée sur chaque classe.** Il n'existe aucun défaut
   de projet. Sur un projet de trente classes, c'est trente fois la même chose écrite. C'est le
   manque le plus coûteux du fichier de règles actuel, et le plus simple à combler.

3. **Le filtre anti-bruit est entièrement câblé et ne concerne que les méthodes.** Six familles
   sont reconnues dans `NoiseFilter`, aucune n'est configurable, et les attributs n'y passent
   jamais. Mais — mesure faite sur `Keyboardd` — un filtre par préfixe `get*` n'aurait presque
   aucun effet sur du code moderne : le bruit y prend d'autres formes.

---

## Partie 1 — État des lieux chiffré

### 1.1 Le coût actuel pour l'utilisateur

| | Nombre |
|---|---|
| Annotations publiques | 13 |
| Paramètres cumulés | 29 |
| Énumérés à connaître | 5 |
| Mots-clés du fichier `umlg.rules` | 2 (`type`, `hide return`) |

Répartition des paramètres :

| Annotation | Paramètres | Remarque |
|---|---|---|
| `@UMLAssociation` | **10** | à elle seule, un tiers de la surface de l'API |
| `@UMLClass` | 4 | |
| `@UMLDiagram` | 3 | |
| `@UMLNote`, `@UMLDependency`, `@UMLPackage`, `@UMLLayout`, `@UMLAssociationClass` | 2 | |
| `@UMLConstraint`, `@UMLMultiplicity` | 1 | |
| `@UMLDerived`, `@UMLInclude`, `@UMLExclude` | 0 | marqueurs |

### 1.2 Ce que coûterait le corpus du professeur

En reproduisant les quatre diagrammes de `Keyboardd` avec la bibliothèque actuelle, à partir de
`strategy = NONE` :

| Poste | Estimation |
|---|---|
| `@UMLClass` | ~20 |
| `@UMLInclude` (sélection des membres) | ~47 |
| `@UMLMultiplicity` | ~6 |
| `@UMLLayout` (les `-[hidden]>` qu'il écrit) | 6 |
| **Total** | **~80 annotations pour 20 classes** |

Soit **quatre annotations par classe**. L'objectif affiché du document — « dans le cas nominal,
`@UMLClass` doit suffire » — n'est pas atteint dès qu'on veut un diagramme aussi soigné que les
siens.

Une précision importante : **la majorité de ce coût (les 47 `@UMLInclude`) est irréductible.**
Choisir 6 méthodes parmi 40 est une décision éditoriale, membre par membre ; aucune règle
générale ne peut la prendre. Le fichier de règles ne fera pas disparaître ce poste — il faut le
dire d'emblée pour ne pas en attendre ce qu'il ne peut pas donner.

### 1.3 Mesure sur du code réel

Le point le plus instructif de l'analyse. Sur `SvgKeyboard` (40 méthodes publiques, dont le
professeur n'en montre que 6), la répartition des noms est la suivante :

| Forme | Occurrences |
|---|---|
| `maxWidthPerCp` (**7 surcharges du même nom**) | 7 |
| préfixe `to…` | 4 |
| préfixe `from…` | 3 |
| `reprs` | 3 |
| autres noms métier | ~20 |
| `equals`, `hashCode` | 2 |
| **préfixe `get…` / `set…` / `is…`** | **0** |

**Aucun getter, aucun setter.** Le style du projet est immuable et fluide, à la manière de Guava.
Une règle `hide method get*` y serait sans effet.

Le vrai bruit de cette classe est ailleurs : **sept surcharges d'un même nom** occupent sept
lignes pour dire une seule chose. C'est cette observation, et non l'intuition de départ, qui
devrait guider les règles proposées en partie 3.

Conséquence de méthode : les règles doivent couvrir **deux styles de code**, le JavaBean
classique (`getX`/`setX`, attributs techniques, loggers) et le style immuable moderne
(surcharges, `from…`/`to…`, `withX`). Un jeu de règles taillé pour le premier seul serait inutile
sur `Keyboardd`.

---

## Partie 2 — Analyse annotation par annotation

Chaque annotation est jugée sur trois critères : **dit-elle quelque chose du modèle** (ou
seulement du dessin) ; **son coût est-il proportionnel à son apport** ; **existe-t-il un doublon**.

### 2.1 Les annotations qui ne posent aucun problème

**`@UMLClass`** — le pivot. Trois de ses quatre paramètres sont justifiés (`name`, `stereotype`,
`strategy`). Voir 2.4 pour `color` et 3.2 pour `strategy`.

**`@UMLDerived`, `@UMLInclude`, `@UMLExclude`** — trois marqueurs sans paramètre. Rien à retirer.
`@UMLInclude`/`@UMLExclude` sont même l'outil le plus efficace du jeu : sur `SvgKeyboard`,
`NONE` + 6 `@UMLInclude` bat de loin `PUBLIC` + 34 `@UMLExclude`.

**`@UMLConstraint`** — un paramètre, répétable, sans redondance. Exemplaire.

**`@UMLDependency`** — `target` + `label`. Le seul moyen de dire une relation qu'aucun attribut
ne porte ; la lecture par réflexion ne verra jamais le corps des méthodes. Justifiée.

**`@UMLAssociationClass`** — `source` + `target`, tous deux irréductibles (l'effacement de type
interdit toute déduction). Justifiée.

**`@UMLNote`** — `text` + `direction`. Voir la réserve en 2.5.

### 2.2 `@UMLAssociation` : dix paramètres, dont six morts la moitié du temps

C'est l'annotation la plus lourde de la bibliothèque, et la seule dont la surface pose vraiment
question. Le document le reconnaît déjà : sous `notation = ATTRIBUTE`, six des dix paramètres
n'ont « nulle part où s'écrire » et le compilateur les signale un à un.

Une annotation dont **60 % des paramètres deviennent inertes selon la valeur d'un septième** est
le signe qu'elle recouvre deux objets distincts : la sémantique du lien (`target`, `type`,
cardinalités, rôles) et sa forme dessinée (`notation`, `direction`, `label`, `qualifier`).

**`isOrdered` est un doublon avéré.** Le paramètre produit littéralement `{ordered}`, exactement
ce que produirait `@UMLConstraint(rule = "ordered")` — laquelle s'applique déjà au lien quand
l'attribut porte une association. Le document l'admet sans le voir : « rien ne distingue alors un
`{subset}` écrit par le développeur d'un `{ordered}` engendré, **et rien ne doit les
distinguer** ». Si rien ne doit les distinguer à la lecture, rien ne justifie deux façons de les
écrire.
→ **Retrait recommandé.** Gain : un paramètre et un concept en moins. Coût : quatre caractères de
plus à écrire dans le cas rare où on veut `{ordered}`.

**`qualifier` est le candidat suivant.** Notion UML avancée, dont le rendu est déjà un pis-aller
assumé (« PlantUML ne sachant pas le faire, il est écrit entre crochets »). Il n'apparaît dans
aucun des quatre diagrammes du corpus. Le retirer allégerait l'annotation d'un paramètre dont le
résultat n'est de toute façon pas la notation UML attendue.
→ **Retrait à envisager**, moins évident que le précédent : c'est une perte fonctionnelle réelle,
là où `isOrdered` n'en est pas une.

**`direction` n'est pas un doublon de `@UMLLayout`**, contrairement à ce qu'on pourrait croire :
`direction` oriente un trait qui existe déjà, tandis que `@UMLLayout` ajoute un trait invisible
entre deux classes qui n'en ont pas. Les remplacer l'un par l'autre ajouterait un lien caché
redondant entre deux boîtes déjà reliées. **À conserver** — mais noter que les deux relèvent de
la mise en page, donc du fichier de règles si l'on suit la partie 3.

**Bilan** : de dix paramètres à huit sans perte sémantique, à sept si l'on renonce au
qualificateur.

### 2.3 Les trois annotations qui ne parlent pas du modèle

C'est le constat central de ce rapport.

Le document pose lui-même le critère de partage : « une annotation dit une chose sur *cet*
attribut ou *cette* classe ; ce qui s'applique partout se déclare une seule fois dans le fichier
`umlg.rules` ». Appliquons-le.

**`@UMLDiagram`** (3 paramètres) — `title`, `direction` et `hideEmptyMembers` sont des réglages
du projet entier. Ils ne disent rien d'une classe : ils sont posés sur un `package-info.java`
faute d'un meilleur endroit, et une seule déclaration est autorisée dans tout le projet. C'est la
définition même de ce que le fichier de règles doit porter. Le coût de ce choix est visible dans
le code : un `DiagramExtractor`, un `VerificateurDiagramme`, la lecture des `package-info.class`
par nom binaire, une règle d'unicité vérifiée à deux endroits — pour trois valeurs qui tiendraient
en trois lignes de fichier.
→ **Migration recommandée.** L'annotation disparaîtrait entièrement.

**`@UMLLayout`** (2 paramètres) — l'annotation le dit d'elle-même dans son propre commentaire :
« c'est la seule annotation sans aucun sens UML ». Elle corrige un dessin. La placer dans le code
source revient à mêler au modèle des considérations d'esthétique que le document écarte partout
ailleurs (« les directives de rendu purement esthétiques sont volontairement écartées »).
→ **Migration recommandée**, avec la réserve de 3.6 (le fichier devrait alors nommer des classes).

**`@UMLPackage`** (2 paramètres) — un groupe est un rectangle de présentation. Surtout, il oblige
à répéter la même annotation sur chaque classe du groupe : quinze classes dans « Facturation »,
c'est quinze fois `@UMLPackage(name = "Facturation")`. Exactement la répétition que le fichier de
règles existe pour supprimer : « écrire la même directive sur chacun des deux cents attributs
d'un projet est un travail que personne ne fait, et que personne ne tient à jour ».
→ **Migration recommandée** sous la forme `group package com.exemple.modele -> "Modèle"`, une
ligne pour tout un paquetage.

**Ce que cela donnerait** : dix annotations au lieu de treize, 24 paramètres au lieu de 29, et un
critère net à énoncer dans le document — *une annotation ne décrit jamais que le modèle ; tout ce
qui touche à la présentation vit dans le fichier de règles*. Le jeu restant serait entièrement
sémantique, ce qui est plus facile à apprendre qu'un jeu mixte.

### 2.4 Deux incohérences mineures relevées au passage

**`color` sur `@UMLClass`** est de l'esthétique pure, tolérée alors que le style des liens est
écarté comme « gain purement esthétique pour un coût élevé en surface d'API ». La règle n'est pas
appliquée uniformément. Une couleur *par stéréotype*, déclarée dans le fichier
(`color stereotype Service -> LightBlue`), serait plus cohérente et plus économique : une ligne
pour toutes les classes d'une même nature, au lieu d'une annotation par classe.

**Les constructeurs sont hors d'atteinte.** Le document range les constructeurs parmi ce
qu'écarte le filtre anti-bruit ; en réalité `MemberExtractor` ne lit que `getDeclaredMethods()`,
si bien qu'un constructeur n'est jamais candidat. La conséquence pratique est qu'**aucune
annotation ne peut faire apparaître un constructeur** : `@UMLInclude` ne vise que `FIELD` et
`METHOD`, jamais `CONSTRUCTOR`. Ce n'est pas un défaut — un constructeur est rarement
intéressant — mais le document décrit un filtre là où il y a une impossibilité, et
`@UMLInclude` promet une dérogation qui n'existe pas dans ce cas.

### 2.5 Une réserve sur `UMLDirection`

Le document revendique « un seul énuméré de direction » utilisé partout. C'est simple à
apprendre, mais l'énuméré n'a pas le même sens ni la même validité selon la cible : sur une note
de membre, `UP` et `DOWN` sont **silencieusement ramenés à la droite** par PlantUML, et le
processeur doit émettre un avertissement dédié pour prévenir. Un énuméré dont deux valeurs sur
cinq sont invalides dans un contexte donné est un compromis, pas une simplification. Il est
défendable — la seule alternative serait deux énumérés — mais il vaut mieux l'assumer comme tel
dans le document que le présenter comme un gain net.

---

## Partie 3 — Ce que le fichier `umlg.rules` devrait pouvoir faire

Le fichier connaît aujourd'hui **deux** mots-clés. Voici ce qu'il pourrait porter, classé par
valeur décroissante. Chaque proposition indique ce qu'elle évite d'écrire.

### 3.1 Principe préalable : la règle de priorité

Rien de ce qui suit n'a de sens sans énoncer d'abord l'ordre de préséance. Je propose, du plus
fort au plus faible :

1. `@UMLExclude` / `@UMLInclude` sur le membre — le cas particulier, écrit à la main ;
2. la règle la plus spécifique du fichier (un nom exact bat un motif) ;
3. la règle générale du fichier ;
4. le défaut de la bibliothèque.

En un mot : **l'annotation gagne toujours contre le fichier**. C'est ce qui permet d'écrire une
règle large sans craindre de perdre les décisions déjà prises au cas par cas.

### 3.2 Le défaut de visibilité du projet — *le plus rentable*

```
strategy public
```

Évite d'écrire `@UMLClass(strategy = UMLVisibility.PUBLIC)` sur chacune des trente classes d'un
projet. `@UMLClass` seule redevient suffisante, ce que le document promet sans le tenir
aujourd'hui.

Variante plus fine, si le besoin s'en fait sentir :

```
strategy public
strategy private for com.exemple.modele
```

### 3.3 Le filtre anti-bruit configurable — *la demande initiale*

Aujourd'hui `NoiseFilter` reconnaît six familles, en dur, et **seulement sur les méthodes**.
Trois manques : on ne peut pas ajouter de famille, on ne peut pas en retirer, et les attributs ne
sont jamais filtrés.

**Ajouter ses propres motifs** — la demande explicite :

```
hide method get*              # tous les getters, même hors convention JavaBean
hide method set*
hide field log, LOGGER        # le bruit technique classique
hide field *Cache, tmp*
```

**Rétablir une exception**, puisque `show` bat `hide` :

```
hide method get*
show method getKeyboardMap    # celui-ci porte le métier
```

**Désactiver ou restreindre les familles câblées**, ce qui est aujourd'hui impossible :

```
noise off                     # plus aucun filtrage automatique
show accessors                # rétablit getters et setters
show object-methods           # rétablit equals, hashCode, toString
show record-accessors
```

**Filtrer les attributs par leur type** — impossible aujourd'hui autrement qu'`@UMLExclude` un
par un :

```
hide field of type Logger
hide field of type org.slf4j.*
```

**Filtrer par annotation déjà présente dans le projet** — à mon avis la règle la plus puissante
de toute cette liste, parce qu'elle réutilise ce que le code porte déjà, sans rien y ajouter :

```
hide annotated java.lang.Deprecated
hide annotated com.fasterxml.jackson.annotation.JsonIgnore
hide annotated javax.annotation.Generated
```

Un projet qui marque déjà son code obtient un diagramme filtré sans écrire une seule annotation
UML.

**Replier les surcharges** — la règle que la mesure de la partie 1.3 réclame, et que je n'aurais
pas proposée sans elle :

```
collapse overloads            # 7 lignes « maxWidthPerCp(…) » deviennent une seule
```

Sur `SvgKeyboard`, cette seule règle retire six lignes sur quarante. C'est la seule proposition
de cette section qui ait un effet mesuré sur `Keyboardd`.

**Filtrer par nature :**

```
hide static                   # les membres de classe
hide fields                   # tout le compartiment des attributs
hide methods
```

### 3.4 Les réglages d'ensemble, si `@UMLDiagram` migre

```
title Architecture du module Facturation
direction left-to-right
hide empty members            # existe déjà, deviendrait modifiable
hide return void              # existe déjà
```

### 3.5 Le renommage et le masquage de types

Extension du `type` existant :

```
type ImmutableList -> List                    # existe déjà
type com.google.common.collect.* -> *         # motif : tout Guava sous son nom simple
hide type Logger                              # tout membre de ce type disparaît
```

### 3.6 Le regroupement, la couleur, le placement — *si les annotations migrent*

```
group package com.exemple.modele -> "Modèle"      # remplace 15 @UMLPackage
group package com.exemple.securite -> "Sécurité"
color group "Modèle" -> #EEEEEE
color stereotype Service -> LightBlue
color kind interface -> LightYellow

layout Voiture above Moteur                       # remplace @UMLLayout
```

**La réserve qui vaut pour ce dernier bloc.** Ces règles nomment des classes, ce que le fichier
évite scrupuleusement aujourd'hui — sa section « ce que les règles ne touchent pas » explique
qu'une classe est désignée dans le code, jamais dans le fichier. Trois conséquences à accepter :
aucun renommage d'IDE ne suivra ces noms ; la faute ne sera vue qu'à la génération, pas à la
compilation ; et il faudra refuser un nom introuvable, comme le fichier refuse déjà une règle
malformée.

Notons que `group package … -> "…"` échappe largement à cette réserve : il nomme un *paquetage*,
pas des classes, et reste donc valable quand une classe est ajoutée, renommée ou supprimée.
C'est un argument pour préférer partout où c'est possible une désignation par paquetage plutôt
que par classe.

### 3.7 Ce qu'il ne faut **pas** mettre dans le fichier

Aussi important que le reste.

- **Les multiplicités déduites** (`multiplicity List -> 0..*`). Le document est catégorique et il
  a raison : seul le métier sait si c'est `0..*` ou `1..*`. Une règle qui devinerait produirait
  un diagramme faux une fois sur deux, en silence.
- **Le renommage des boîtes** (`rename class Facture -> Fac`). Déjà exclu, et pour une bonne
  raison : le nom de la boîte divergerait du type écrit dans la boîte voisine.
- **Le style fin** (couleur et épaisseur des traits, polices, `skinparam` bruts). Déplacer ces
  réglages du code au fichier ne les rendrait pas moins coûteux : ce serait réécrire PlantUML en
  syntaxe de fichier.

### 3.8 Le risque à surveiller

Le fichier passerait de 2 à une vingtaine de mots-clés. On remplacerait un problème — trop
d'annotations — par un autre : **un langage à apprendre, sans compilateur, sans complétion, sans
coloration syntaxique**, dont la seule aide au diagnostic est le message d'erreur au numéro de
ligne.

Je propose un garde-fou explicite, à inscrire dans le document :

> Une règle mérite d'exister lorsqu'elle évite d'écrire trois fois la même annotation, ou
> lorsqu'elle exprime une décision qu'aucune annotation ne peut prendre. Toute autre règle
> déplace le problème au lieu de le résoudre.

À cette aune, dans la liste ci-dessus : `strategy`, `group package`, `hide annotated`,
`hide field of type` et `collapse overloads` passent nettement. `color kind`, `hide static` et
`layout` sont plus discutables.

### 3.9 Une remarque de grammaire

Le fichier mélange déjà deux formes : `type X -> Y` (avec flèche) et `hide return void` (sans).
À vingt règles, l'irrégularité deviendra coûteuse. Une forme unique est possible :

```
<verbe> <objet> [<sélecteur>] [-> <valeur>]
```

avec un vocabulaire de verbes fermé — `hide`, `show`, `rename`, `color`, `group`, `set`,
`layout`. La règle `type X -> Y` s'écrirait alors `rename type X -> Y`, en gardant l'ancienne
forme acceptée pour ne rien casser. Le lecteur actuel (`LecteurDeRegles`) est bien placé pour
cela : il lit déjà le premier mot comme un verbe et délègue le reste.

---

## Partie 4 — Recommandations, par ordre de rapport valeur/coût

| # | Action | Gain | Coût |
|---|---|---|---|
| 1 | `strategy` par défaut dans le fichier | Supprime une annotation répétée sur chaque classe | Faible |
| 2 | Filtre anti-bruit configurable (`hide method`, `hide field`, `show`, `noise off`) | Répond au besoin exprimé ; ouvre la bibliothèque aux conventions de chaque projet | Moyen |
| 3 | `hide annotated` | Réutilise le marquage existant du projet, sans rien écrire | Faible |
| 4 | Retirer `isOrdered` de `@UMLAssociation` | Un doublon avéré en moins | Très faible |
| 5 | Migrer `@UMLDiagram` vers le fichier | −1 annotation, −3 paramètres, et supprime la lecture des `package-info` | Moyen |
| 6 | `group package … -> "…"` (et migration de `@UMLPackage`) | Remplace N annotations par une ligne | Moyen |
| 7 | `collapse overloads` | Seule règle à effet mesuré sur `Keyboardd` | Faible |
| 8 | Migrer `@UMLLayout` vers le fichier | Sort l'esthétique du code source | Moyen ; nomme des classes |
| 9 | Retirer `qualifier` | −1 paramètre sur l'annotation la plus lourde | Perte fonctionnelle réelle |
| 10 | Harmoniser la grammaire du fichier | Lisibilité à vingt règles | Moyen ; rétrocompatibilité à tenir |

### Deux décisions à prendre avant toute implémentation

**a. Le fichier a-t-il le droit de nommer des classes ?** C'est la question qui commande les
points 6, 8 et une partie de 3.6. Répondre « non » conserve la pureté du principe actuel et
interdit la migration de `@UMLLayout`. Répondre « oui » ouvre la modularité demandée, au prix
d'un couplage que ni le compilateur ni l'IDE ne surveilleront.

**b. Jusqu'où va la migration présentation → fichier ?** Si la réponse est « complètement »,
alors `color` doit aussi quitter `@UMLClass`, et le jeu d'annotations devient purement
sémantique — un principe fort, facile à énoncer et à défendre. Une migration à moitié laisserait
la règle inapplicable et le document difficile à justifier.

---

## Annexe — Ce que l'analyse a confirmé de sain

Pour l'équilibre, l'analyse n'a relevé aucune faiblesse sur les points suivants, qui méritent
d'être signalés comme acquis :

- **la double vérification** (processeur à la compilation, extraction à l'exécution) ne diverge
  jamais, parce que le processeur construit les objets du modèle plutôt que de réécrire ses
  règles ;
- **l'ordre des membres** (`MembresOrdonnes`) résout un vrai problème de reproductibilité, avec
  un choix — l'alphabet — assumé et expliqué ;
- **le filtre anti-bruit vérifie les signatures complètes** et non les seuls noms, si bien qu'un
  `getPrix(Client)` métier n'est pas confondu avec un getter ;
- **la règle du non-doublon entre les deux notations d'association** est tenue des deux côtés,
  sans que l'utilisateur ait à y penser ;
- **`@UMLInclude` sur `strategy = NONE`** est un outil plus efficace qu'il n'y paraît : c'est lui
  qui permettrait de reproduire fidèlement les boîtes très sélectives du corpus.
