<p align="right"><a href="README.md">English</a> · <b>Français</b></p>

<p align="center"><img src="docs/media/logo.png" width="160" alt="Hardscrabble logo: the ghoul before a blood moon"></p>

# Hardscrabble

Une refonte de la survie en mode exigeant pour **Minecraft Java 26.3 / NeoForge** : progression lente, métal après
métal, vraie faim, nouveaux monstres et une dimension sous la bedrock.

<p align="center"><img src="docs/media/mobs/lineup.png" width="100%" alt="Tous les monstres de Hardscrabble réunis"></p>

Hardscrabble s'inspire de **MITE** (*Minecraft Is Too Easy*) d'Avernite. Il n'est **pas affilié** à MITE ni à son
auteur. C'est une réimplémentation en salle blanche, écrite à partir de spécifications et du changelog public de MITE :
aucun code ni asset original de MITE n'est utilisé (voir [`PROVENANCE.md`](PROVENANCE.md)). Toutes les textures et tous
les modèles sont originaux.

Version `0.1.0-alpha`. Complet par rapport à sa feuille de route et passé par un premier playtest ; des tests plus larges restent à faire
(voir [État](#état-et-manques-connus)).

## Points forts

<table>
<tr>
<td width="25%"><img src="docs/media/mechanics/no_bare_hand_logs.gif" width="100%" alt="Frapper un tronc à mains nues ne fait rien ; une hachette en silex le coupe"><br>Pas de bûches à mains nues : on commence au silex et à la hachette.</td>
<td width="25%"><img src="docs/media/ui/craft_workbench.gif" width="100%" alt="Fabrication chronométrée sur un établi de cuivre avec le bouton de qualité visée et son coût en XP"><br>Fabrication chronométrée, 9 établis qui verrouillent chaque palier de métal.</td>
<td width="25%"><img src="docs/media/ui/furnace_too_cold.png" width="100%" alt="Le mithril brut demande la chaleur 3 : le fourneau en pierre est trop froid"><br>Les fours ont un niveau de chaleur ; les meilleurs minerais demandent plus chaud.</td>
<td width="25%"><img src="docs/media/ui/hud_malnourished.png" width="100%" alt="Malnutrition : marqueurs P et V (ni protéines ni phytonutriments) et l'astuce unique"><br>Satiété et nutrition : protéines et phytonutriments comptent.</td>
</tr>
<tr>
<td><img src="docs/media/mobs/dire_wolf_pack.gif" width="100%" alt="Une meute de loups terribles en chasse"><br>29 nouveaux monstres avec leurs propres modèles.</td>
<td><img src="docs/media/world/moon_rise.gif" width="100%" alt="Une lune de sang se lève au crépuscule"><br>Calendrier lunaire : lunes de sang, bleues et des moissons.</td>
<td><img src="docs/media/world/portal_travel.png" width="100%" alt="Sortie du portail dans l'Underworld"><br>L'Underworld, atteint par un portail bâti sur la bedrock.</td>
<td><img src="docs/media/ui/death.png" width="100%" alt="Écran de mort avec le compte à rebours de réapparition forcée de 120 s"><br>Peu de cœurs à bas niveau, pénalité de mort, réapparition forcée en 120 s.</td>
</tr>
</table>

## Sommaire

[Progression et fabrication](#progression-et-fabrication) ·
[Survie](#survie) ·
[Combat et armures](#combat-et-armures) ·
[Monstres](#monstres) ·
[Monde](#monde) ·
[Interfaces](#interfaces) ·
[Configuration](#configuration) ·
[Installer et jouer](#installer-et-jouer) ·
[Tests](#tests) ·
[État](#état-et-manques-connus) ·
[Crédits](#crédits) ·
[Licences](#licences)

## Progression et fabrication

Silex → cuivre → argent / or → fer → métal ancien → mithril → adamantium. Les bûches ne se cassent pas à mains nues ;
le gravier donne des éclats de silex pour la première hachette, le premier couteau, la première hache et la première
pelle. Les outils en bois (sauf la pelle), en pierre et en diamant sont retirés.

Fabriquer prend du temps : on maintient le clic sur la case résultat jusqu'à remplir la barre. Chaque métal demande son
établi, et chaque établi demande le précédent : aucun palier ne peut être sauté. Un établi peut viser une qualité
supérieure (8 niveaux) contre de l'XP.

<table>
<tr>
<td width="50%"><img src="docs/media/ui/craft_inventory.gif" width="100%" alt="Fabrication chronométrée dans la grille 2x2 : maintenir le clic sur le résultat jusqu'à remplir la barre"><br>Fabrication chronométrée dans la grille 2x2.</td>
<td width="50%"><img src="docs/media/ui/craft_workbench.gif" width="100%" alt="Fabrication chronométrée sur un établi de cuivre avec le bouton de qualité visée et son coût en XP"><br>Établi de cuivre avec le bouton de qualité visée et son coût en XP.</td>
</tr>
<tr>
<td><img src="docs/media/ui/craft_refused.png" width="100%" alt="Une recette en fer est refusée sur un établi de cuivre"><br>Une recette en fer est refusée sur un établi de cuivre…</td>
<td><img src="docs/media/ui/craft_refused_hud.png" width="100%" alt="Le refus nomme l'établi nécessaire (barre d'action)"><br>…et la barre d'action nomme l'établi nécessaire.</td>
</tr>
</table>

<p align="center"><img src="docs/media/items/tools.png" width="760" alt="Outils par matériau"><br>
<em>Outils par matériau : pioches, pelles, haches, hachettes, houes, pioches-haches, faux, cisailles, cannes à pêche.</em></p>

78 nouveaux outils et armes (hachettes, couteaux, dagues, massues, haches de bataille, marteaux de guerre,
pioches-haches, faux, cisailles dans chaque métal…) s'ajoutent aux outils vanilla en cuivre, fer et or retravaillés,
plus une canne à pêche par matériau. Durabilité ×100, usure selon la dureté du bloc ; les outils ont une allonge et
peuvent parer.

<table>
<tr>
<td width="50%"><img src="docs/media/items/workbenches.png" width="100%" alt="Les neuf établis qui verrouillent chaque palier de métal"><br>Les neuf établis qui verrouillent chaque palier de métal.</td>
<td width="50%"><img src="docs/media/items/spin.gif" width="100%" alt="Un support d'armure en adamantium qui tourne au milieu des établis"><br>Un support d'armure en adamantium qui tourne au milieu des établis.</td>
</tr>
<tr>
<td><img src="docs/media/items/furnaces.png" width="100%" alt="Les cinq fours à niveau de chaleur, du four en argile au four en netherrack"><br>Les cinq fours à niveau de chaleur, du four en argile au four en netherrack.</td>
<td><img src="docs/media/items/metals.png" width="100%" alt="Métallurgie : minerai, métal brut, pépite, lingot, pièce, chaîne et bloc par métal"><br>Minerai, métal brut, pépite, lingot, pièce, chaîne et bloc par métal.</td>
</tr>
</table>

<details>
<summary>Toutes les planches d'objets (blocs, matériaux, nourriture…)</summary>

<table>
<tr>
<td width="33%"><img src="docs/media/items/ores.png" width="100%" alt="Minerais d'argent, de mithril et d'adamantium, variantes pierre et ardoise des abîmes"><br>Minerais d'argent, de mithril et d'adamantium, variantes pierre et ardoise des abîmes</td>
<td width="33%"><img src="docs/media/items/metal_blocks.png" width="100%" alt="Les blocs de stockage de chaque métal"><br>Les blocs de stockage de chaque métal</td>
<td width="33%"><img src="docs/media/items/doors_bars.png" width="100%" alt="Portes et barreaux de métal"><br>Portes et barreaux de métal</td>
</tr>
<tr>
<td><img src="docs/media/items/anvils.png" width="100%" alt="Les enclumes de métal, réparées avec des pépites du même métal"><br>Les enclumes de métal, réparées avec des pépites du même métal</td>
<td><img src="docs/media/items/strongboxes.png" width="100%" alt="Les coffres-forts en sept métaux"><br>Les coffres-forts en sept métaux</td>
<td><img src="docs/media/items/runestones.png" width="100%" alt="Pierres runiques de mithril et d'adamantium et table d'enchantement en émeraude"><br>Pierres runiques de mithril et d'adamantium et table d'enchantement en émeraude</td>
</tr>
<tr>
<td><img src="docs/media/items/buckets.png" width="100%" alt="Seaux de métal, vides ou remplis d'eau, de lave, de lait ou de pierre"><br>Seaux de métal, vides ou remplis d'eau, de lave, de lait ou de pierre</td>
<td><img src="docs/media/items/coins.png" width="100%" alt="Les six pièces de monnaie"><br>Les six pièces de monnaie</td>
<td><img src="docs/media/items/shards.png" width="100%" alt="Éclats : silex, obsidienne, verre, quartz, émeraude, diamant"><br>Éclats : silex, obsidienne, verre, quartz, émeraude, diamant</td>
</tr>
<tr>
<td><img src="docs/media/items/food.png" width="100%" alt="Nouveaux aliments, bols et soupes"><br>Nouveaux aliments, bols et soupes</td>
<td><img src="docs/media/items/crops.png" width="100%" alt="Oignons à chaque stade de croissance, et blé, carottes, pommes de terre et oignons morts"><br>Oignons à chaque stade, et blé, carottes, pommes de terre et oignons morts</td>
<td><img src="docs/media/items/misc.png" width="100%" alt="Butin de monstres, fioles d'enchantement et de désenchantement, fumier, armure de cheval en argent"><br>Butin de monstres, fioles d'enchantement et de désenchantement, fumier, armure de cheval en argent</td>
</tr>
<tr>
<td><img src="docs/media/items/mantle_core.png" width="100%" alt="Manteau et Noyau, les blocs sous l'Underworld et le Nether"><br>Manteau et Noyau, les blocs sous l'Underworld et le Nether</td>
<td></td><td></td>
</tr>
</table>
</details>

## Survie

On commence avec 3 cœurs ; la santé maximale augmente avec le niveau jusqu'à 10 cœurs. La courbe d'XP est celle de MITE,
les niveaux peuvent devenir négatifs et la barre de nourriture est plafonnée par le niveau. La faim suit la satiété et la
nutrition, les protéines et les phytonutriments, l'insuline et l'humidité ; la régénération naturelle est lente. Dormir
n'accélère jamais le temps sur un serveur dédié.

<table>
<tr>
<td width="50%"><img src="docs/media/ui/hud.png" width="100%" alt="HUD au niveau 3 : peu de cœurs, niveau MITE, barre de nourriture plafonnée par le niveau"><br>HUD au niveau 3 : peu de cœurs, niveau MITE, barre de nourriture plafonnée par le niveau.</td>
<td width="50%"><img src="docs/media/ui/hud_malnourished.png" width="100%" alt="Malnutrition : marqueurs P et V (ni protéines ni phytonutriments) et l'astuce unique"><br>Malnutrition : marqueurs P et V (ni protéines ni phytonutriments) et l'astuce unique.</td>
</tr>
<tr>
<td><img src="docs/media/ui/tooltip_food.png" width="100%" alt="Infobulle de nourriture : satiété, nutrition et nutriments"><br>Infobulle de nourriture : satiété, nutrition et nutriments.</td>
<td><img src="docs/media/ui/death_fr.png" width="100%" alt="Le même écran en français"><br>Écran de mort avec le compte à rebours de réapparition forcée de 120 s.</td>
</tr>
</table>

Les sorcières peuvent vous maudire ; l'icône de malédiction apparaît une fois la malédiction découverte, et une fiole de
désenchantement la lève.

<table>
<tr>
<td width="33%"><img src="docs/media/ui/hud_curse.png" width="100%" alt="Malédiction de sorcière : icône en haut à gauche une fois découverte, messages dans le chat"><br>Malédiction de sorcière : icône en haut à gauche, messages dans le chat</td>
<td width="33%"><img src="docs/media/ui/hud_curse_tooltip.png" width="100%" alt="Survoler l'icône de malédiction dans un inventaire nomme la malédiction"><br>Survoler l'icône dans un inventaire nomme la malédiction</td>
<td width="33%"><img src="docs/media/ui/chat_hints.png" width="100%" alt="Astuces contextuelles uniques, et une fiole de désenchantement qui lève la malédiction"><br>Astuces contextuelles uniques, et une fiole de désenchantement qui lève la malédiction</td>
</tr>
</table>

### Mécaniques du monde

<table>
<tr>
<td width="33%"><img src="docs/media/mechanics/no_bare_hand_logs.gif" width="100%" alt="Frapper un tronc à mains nues ne fait rien ; une hachette en silex le coupe"><br>Frapper un tronc à mains nues ne fait rien ; une hachette en silex le coupe</td>
<td width="33%"><img src="docs/media/mechanics/gravel_flint.gif" width="100%" alt="Le gravier donne surtout du gravier, parfois des éclats de silex ou des pépites"><br>Le gravier donne surtout du gravier, parfois des éclats de silex ou des pépites</td>
<td width="33%"><img src="docs/media/mechanics/dirt_gravity.gif" width="100%" alt="Retirez son support et la terre tombe"><br>Retirez son support et la terre tombe</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/campfire_goes_out.gif" width="100%" alt="Trop d'aliments sur un feu de camp l'éteignent"><br>Trop d'aliments sur un feu de camp l'éteignent</td>
<td><img src="docs/media/mechanics/gem_sacrifice.gif" width="100%" alt="Clic droit sur une gemme pour la sacrifier contre de l'XP, 3 émeraudes par jour au plus"><br>Clic droit sur une gemme pour la sacrifier contre de l'XP, 3 émeraudes par jour au plus</td>
<td><img src="docs/media/mechanics/crop_blight.gif" width="100%" alt="La rouille se propage et tue les cultures (ticks aléatoires accélérés)"><br>La rouille se propage et tue les cultures (ticks aléatoires accélérés)</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/zombies_dig.gif" width="100%" alt="Les zombies creusent la terre pour atteindre le joueur"><br>Les zombies creusent la terre pour atteindre le joueur</td>
<td><img src="docs/media/mechanics/squid_smashes_boat.gif" width="100%" alt="Un calmar chasse le joueur en barque et brise la barque"><br>Un calmar chasse le joueur en barque et brise la barque</td>
<td><img src="docs/media/mechanics/horse_shies.gif" width="100%" alt="Un cheval sauvage laisse approcher un joueur accroupi, puis s'éloigne"><br>Un cheval sauvage laisse approcher un joueur accroupi, puis s'éloigne</td>
</tr>
</table>

Aussi : les cultures poussent mieux en rangs, le bétail broute son pâturage, les échanges des villageois ne se
renouvellent pas et ne donnent pas d'XP, les monstres apparaissent jusqu'à une lumière de bloc de 7.

## Combat et armures

Chaque arme a ses dégâts et sa vitesse ; la protection d'une armure baisse avec l'usure. Neuf armures de plaques et
huit cottes de mailles, des arcs avec 10 pointes de flèche, et l'argent fait plus de dégâts aux morts-vivants.

<p align="center"><img src="docs/media/items/armour_worn.png" width="640" alt="Un joueur en armure de mithril complète, hache de bataille et dague de mithril en main"><br>
<em>Armure de mithril complète, hache de bataille et dague de mithril.</em></p>

<table>
<tr>
<td width="50%"><img src="docs/media/items/armour_sets.png" width="100%" alt="Les neuf armures de plaques, du cuir à l'adamantium"><br>Les neuf armures de plaques, du cuir à l'adamantium</td>
<td width="50%"><img src="docs/media/items/chainmail_sets.png" width="100%" alt="Les huit cottes de mailles, du cuivre à l'adamantium"><br>Les huit cottes de mailles, du cuivre à l'adamantium</td>
</tr>
<tr>
<td><img src="docs/media/items/weapons.png" width="100%" alt="Armes par matériau : épées, dagues, couteaux, massue et gourdin, haches de bataille, marteaux de guerre, arcs et les 10 pointes de flèche"><br>Épées, dagues, couteaux, massue et gourdin, haches de bataille, marteaux de guerre, arcs et les 10 pointes de flèche</td>
<td><img src="docs/media/ui/tooltip_tool.png" width="49%" alt="Infobulle d'outil : durabilité, matériau, niveau de minage, qualité"> <img src="docs/media/ui/tooltip_armor.png" width="49%" alt="Infobulle d'armure : durabilité et matériau"><br>Infobulles : durabilité, matériau, niveau de minage, qualité</td>
</tr>
</table>

## Monstres

29 nouveaux monstres, chacun avec son modèle (sources Blockbench dans `docs/art/models/`). Les zombies creusent, les
yeux des araignées brillent dans le noir et les profondeurs font apparaître des monstres plus durs. Les nuits de lune de
sang, morts-vivants frénétiques, goules et revenants envahissent la surface.

<table>
<tr>
<td width="50%"><img src="docs/media/mobs/family_undead.png" width="100%" alt="Les morts-vivants : seigneurs d'os, longdeads, ombre, rôdeur invisible, revenant, spectre, goule"><br>Les morts-vivants : seigneurs d'os, longdeads, ombre, rôdeur invisible, revenant, spectre, goule</td>
<td width="50%"><img src="docs/media/mobs/family_spiders.png" width="100%" alt="Araignée démoniaque, araignée de phase, veuve noire et araignée des bois dans leur grotte"><br>Araignée démoniaque, araignée de phase, veuve noire et araignée des bois dans leur grotte</td>
</tr>
<tr>
<td><img src="docs/media/mobs/family_elementals.png" width="100%" alt="Golem d'argile, élémentaire de feu et élémentaire de terre"><br>Golem d'argile, élémentaire de feu et élémentaire de terre</td>
<td><img src="docs/media/mobs/family_cubes.png" width="100%" alt="Les cubes gélatineux : gelée, blob, vase et pudding"><br>Les cubes gélatineux : gelée, blob, vase et pudding</td>
</tr>
<tr>
<td><img src="docs/media/mobs/family_bats.png" width="100%" alt="Aile-de-nuit, chauve-souris vampire géante et chauve-souris vampire"><br>Aile-de-nuit, chauve-souris vampire géante et chauve-souris vampire</td>
<td><img src="docs/media/mobs/family_silverfish.png" width="100%" alt="Variantes de poisson d'argent : poisson d'argent chenu, engeance du Nether et épine-de-cuivre"><br>Poisson d'argent chenu, engeance du Nether et épine-de-cuivre</td>
</tr>
</table>

<table>
<tr>
<td width="33%"><img src="docs/media/mobs/ghoul_walk.gif" width="100%" alt="Une goule s'avance vers vous"><br>Une goule s'avance vers vous</td>
<td width="33%"><img src="docs/media/mobs/cube_bounce.gif" width="100%" alt="Des cubes gélatineux approchent en bondissant"><br>Des cubes gélatineux approchent en bondissant</td>
<td width="33%"><img src="docs/media/mobs/fire_elemental_alive.gif" width="100%" alt="Un élémentaire de feu en action"><br>Un élémentaire de feu en action</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/shadow_snuffs_lights.gif" width="100%" alt="Une Ombre souffle les bougies et casse les torches autour d'elle"><br>Une Ombre souffle les bougies et casse les torches</td>
<td><img src="docs/media/mechanics/jelly_digests_items.gif" width="100%" alt="Une Gelée digère la viande et le cuir au sol ; le fer reste"><br>Une Gelée digère la viande et le cuir au sol ; le fer reste</td>
<td><img src="docs/media/mechanics/earth_elemental_heated.gif" width="100%" alt="La foudre chauffe un Élémentaire de terre : il brûle et marche dans la lave"><br>La foudre chauffe un Élémentaire de terre : il brûle et marche dans la lave</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/fire_elemental_water.gif" width="100%" alt="Un seau ou un bol d'eau blesse un Élémentaire de feu et se vide"><br>Un seau ou un bol d'eau blesse un Élémentaire de feu</td>
<td><img src="docs/media/mobs/spider_eyes_night.png" width="100%" alt="Les yeux des araignées brillent dans le noir"><br>Les yeux des araignées brillent dans le noir</td>
<td></td>
</tr>
</table>

### Loups terribles

Les loups terribles chassent en meute. Apprivoisés, ils portent un collier et une armure de loup teignable qui se
fissure à l'usure.

<table>
<tr>
<td width="33%"><img src="docs/media/mobs/dire_wolf_pack.gif" width="100%" alt="Une meute de loups terribles en chasse"><br>Une meute de loups terribles en chasse</td>
<td width="33%"><img src="docs/media/mobs/dire_wolf_tamed.png" width="100%" alt="Loups terribles apprivoisés : collier seul, armure teinte, assis"><br>Apprivoisés : collier seul, armure teinte, assis</td>
<td width="33%"><img src="docs/media/mobs/dire_wolf_cracked.png" width="100%" alt="L'armure du loup terrible se fissure à l'usure"><br>L'armure se fissure à l'usure</td>
</tr>
</table>

### Bestiaire

<table>
<tr><th colspan="4">Morts-vivants</th></tr>
<tr>
<td width="25%" align="center"><img src="docs/media/mobs/ghoul.png" width="100%" alt="Goule"><br>Goule</td>
<td width="25%" align="center"><img src="docs/media/mobs/wight.png" width="100%" alt="Spectre (wight)"><br>Spectre (wight)</td>
<td width="25%" align="center"><img src="docs/media/mobs/revenant.png" width="100%" alt="Revenant en armure de fer rouillé"><br>Revenant (armure de fer rouillé)</td>
<td width="25%" align="center"><img src="docs/media/mobs/shadow.png" width="100%" alt="Ombre, mort-vivant immatériel des profondeurs"><br>Ombre (profondeurs, immatérielle)</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/invisible_stalker.png" width="100%" alt="Rôdeur invisible, un spectre à peine visible (souligné ici)"><br>Rôdeur invisible (souligné)</td>
<td align="center"><img src="docs/media/mobs/longdead.png" width="100%" alt="Longdead casqué de métal ancien, avec son arc"><br>Longdead</td>
<td align="center"><img src="docs/media/mobs/longdead_guardian.png" width="100%" alt="Gardien longdead, épée et plastron de métal ancien"><br>Gardien longdead</td>
<td align="center"><img src="docs/media/mobs/bone_lord.png" width="100%" alt="Seigneur d'os"><br>Seigneur d'os</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/ancient_bone_lord.png" width="100%" alt="Seigneur d'os ancien"><br>Seigneur d'os ancien</td>
<td></td><td></td><td></td>
</tr>
<tr><th colspan="4">Araignées</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/wood_spider.png" width="100%" alt="Araignée des bois"><br>Araignée des bois</td>
<td align="center"><img src="docs/media/mobs/black_widow.png" width="100%" alt="Veuve noire"><br>Veuve noire</td>
<td align="center"><img src="docs/media/mobs/phase_spider.png" width="100%" alt="Araignée de phase"><br>Araignée de phase</td>
<td align="center"><img src="docs/media/mobs/demon_spider.png" width="100%" alt="Araignée démoniaque"><br>Araignée démoniaque</td>
</tr>
<tr><th colspan="4">Élémentaires et golems</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/clay_golem.png" width="100%" alt="Golem d'argile"><br>Golem d'argile</td>
<td align="center"><img src="docs/media/mobs/earth_elemental.png" width="100%" alt="Élémentaire de terre"><br>Élémentaire de terre</td>
<td align="center"><img src="docs/media/mobs/fire_elemental.png" width="100%" alt="Élémentaire de feu dans le Nether"><br>Élémentaire de feu (Nether)</td>
<td align="center"><img src="docs/media/mobs/infernal_creeper.png" width="100%" alt="Creeper infernal"><br>Creeper infernal</td>
</tr>
<tr><th colspan="4">Cubes gélatineux</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/jelly.png" width="100%" alt="Gelée"><br>Gelée</td>
<td align="center"><img src="docs/media/mobs/blob.png" width="100%" alt="Blob"><br>Blob</td>
<td align="center"><img src="docs/media/mobs/ooze.png" width="100%" alt="Vase"><br>Vase</td>
<td align="center"><img src="docs/media/mobs/pudding.png" width="100%" alt="Pudding"><br>Pudding</td>
</tr>
<tr><th colspan="4">Chauves-souris et poissons d'argent</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/vampire_bat.png" width="100%" alt="Chauve-souris vampire"><br>Chauve-souris vampire</td>
<td align="center"><img src="docs/media/mobs/giant_vampire_bat.png" width="100%" alt="Chauve-souris vampire géante"><br>Chauve-souris vampire géante</td>
<td align="center"><img src="docs/media/mobs/nightwing.png" width="100%" alt="Aile-de-nuit dans le Nether"><br>Aile-de-nuit (Nether)</td>
<td align="center"><img src="docs/media/mobs/copperspine.png" width="100%" alt="Épine-de-cuivre"><br>Épine-de-cuivre</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/hoary_silverfish.png" width="100%" alt="Poisson d'argent chenu"><br>Poisson d'argent chenu</td>
<td align="center"><img src="docs/media/mobs/netherspawn.png" width="100%" alt="Engeance du Nether"><br>Engeance du Nether</td>
<td></td><td></td>
</tr>
<tr><th colspan="4">Loups</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/dire_wolf.png" width="100%" alt="Loup terrible"><br>Loup terrible</td>
<td align="center"><img src="docs/media/mobs/hellhound.png" width="100%" alt="Chien de l'enfer dans le Nether"><br>Chien de l'enfer (Nether)</td>
<td></td><td></td>
</tr>
</table>

## Monde

### L'Underworld

Une dimension de cavernes sous la bedrock de l'Overworld, dans un brouillard gris qui s'épaissit sur un cycle de
32 jours. Un portail bâti sur la bedrock de l'Overworld y mène ; un portail construit ailleurs ramène au point
d'apparition de l'Overworld. L'adamantium ne se trouve qu'au fond de l'Underworld, au-dessus d'un sol de Manteau
incassable. Les donjons de l'Underworld abritent des générateurs de Longdead et du butin.

<p align="center"><img src="docs/media/world/underworld_landscape.png" width="760" alt="L'Underworld : une dimension de cavernes sous la bedrock, brouillard gris"></p>

<table>
<tr>
<td width="33%"><img src="docs/media/world/portal_bedrock.png" width="100%" alt="Un portail bâti sur la bedrock de l'Overworld mène à l'Underworld"><br>Un portail bâti sur la bedrock de l'Overworld</td>
<td width="33%"><img src="docs/media/world/portal_travel.png" width="100%" alt="Sortie du portail dans l'Underworld"><br>Sortie du portail dans l'Underworld</td>
<td width="33%"><img src="docs/media/world/portal_arrival.png" width="100%" alt="Le portail de sortie dans l'Underworld"><br>Le portail de sortie dans l'Underworld</td>
</tr>
<tr>
<td><img src="docs/media/world/underworld_fog_cycle.gif" width="100%" alt="Le brouillard de l'Underworld s'épaissit sur un cycle de 32 jours"><br>Le brouillard s'épaissit sur un cycle de 32 jours</td>
<td><img src="docs/media/world/underworld_fog_dense.png" width="100%" alt="Le brouillard de l'Underworld au plus dense, le jour de la lune de sang"><br>Le brouillard au plus dense, le jour de la lune de sang</td>
<td><img src="docs/media/world/underworld_dungeon.png" width="100%" alt="Donjon de l'Underworld : générateur de Longdead et coffre de butin"><br>Donjon de l'Underworld : générateur de Longdead et coffre de butin</td>
</tr>
<tr>
<td><img src="docs/media/world/underworld_mantle.png" width="100%" alt="Fond de l'Underworld sans la pierre : plaque de bedrock (au fond), sol de Manteau incassable (devant)"><br>Fond sans la pierre : plaque de bedrock (au fond), sol de Manteau (devant)</td>
<td><img src="docs/media/world/adamantium_ore.png" width="100%" alt="Minerai d'adamantium, uniquement au fond de l'Underworld (roche autour retirée)"><br>Minerai d'adamantium, au fond de l'Underworld (roche retirée)</td>
<td><img src="docs/media/world/nether_core.png" width="100%" alt="Sol du Nether ouvert : la couche de Noyau sous la bedrock"><br>Sol du Nether ouvert : la couche de Noyau sous la bedrock</td>
</tr>
</table>

### Lunes et météo

Une pleine lune tous les 8 jours. Une lune de sang tous les 32 jours, annoncée par une lune des moissons ; une lune
bleue tous les 128 jours, annoncée par une parhélie lunaire, empêche les monstres d'apparaître sous le ciel ouvert.
L'horloge annonce la lune du soir. Environ un matin sur huit, un épais brouillard se lève.

<table>
<tr>
<td width="33%"><img src="docs/media/world/moon_rise.gif" width="100%" alt="Une lune de sang se lève au crépuscule"><br>Une lune de sang se lève au crépuscule</td>
<td width="33%"><img src="docs/media/world/moon_blood.png" width="100%" alt="Lune de sang (tous les 32 jours) : morts-vivants frénétiques, goules et revenants en surface"><br>Lune de sang : morts-vivants frénétiques en surface</td>
<td width="33%"><img src="docs/media/world/moon_blue.png" width="100%" alt="Lune bleue (tous les 128 jours) : aucun monstre sous le ciel ouvert, loups plus faciles à apprivoiser"><br>Lune bleue : aucun monstre sous le ciel ouvert</td>
</tr>
<tr>
<td><img src="docs/media/world/moon_normal.png" width="100%" alt="Une pleine lune normale (une pleine lune tous les 8 jours)"><br>Une pleine lune normale</td>
<td><img src="docs/media/world/moon_harvest.png" width="100%" alt="Lune des moissons, 8 jours avant une lune de sang"><br>Lune des moissons, 8 jours avant une lune de sang</td>
<td><img src="docs/media/world/moon_dog.png" width="100%" alt="Parhélie lunaire : la pleine lune auréolée qui annonce une lune bleue 8 jours plus tard"><br>Parhélie lunaire : annonce une lune bleue 8 jours plus tard</td>
</tr>
<tr>
<td><img src="docs/media/world/morning_fog.png" width="100%" alt="Épais brouillard matinal, environ un jour sur huit"><br>Épais brouillard matinal</td>
<td><img src="docs/media/ui/tooltip_clock.png" width="100%" alt="L'horloge annonce la lune de ce soir"><br>L'horloge annonce la lune de ce soir</td>
<td></td>
</tr>
</table>

### Villages, runegates, minerais

Les villages n'apparaissent qu'après la fabrication d'une première pioche en fer, et ils sont abandonnés, avec des
cultures mortes. Une runegate (un portail avec une pierre runique à chaque coin) envoie au loin un joueur les mains
vides. Les minerais d'argent, de mithril et d'adamantium suivent la répartition en profondeur de MITE.

<table>
<tr>
<td width="33%"><img src="docs/media/world/village.png" width="100%" alt="Les villages n'apparaissent qu'après la fabrication d'une pioche en fer, et sont abandonnés"><br>Un village abandonné</td>
<td width="33%"><img src="docs/media/world/village_dead_crops.png" width="100%" alt="Les champs des villages ne portent que des cultures mortes"><br>Les champs des villages ne portent que des cultures mortes</td>
<td width="33%"><img src="docs/media/world/runegate.png" width="100%" alt="Runegate : un portail avec une pierre runique à chaque coin envoie au loin un joueur les mains vides"><br>Runegate</td>
</tr>
<tr>
<td><img src="docs/media/world/silver_ore.png" width="100%" alt="Minerai d'argent (roche autour retirée)"><br>Minerai d'argent (roche retirée)</td>
<td><img src="docs/media/world/mithril_ore.png" width="100%" alt="Minerai de mithril, en profondeur dans l'Overworld (roche autour retirée)"><br>Minerai de mithril, en profondeur dans l'Overworld (roche retirée)</td>
<td></td>
</tr>
</table>

## Interfaces

<table>
<tr>
<td width="33%"><img src="docs/media/ui/furnace_clay.png" width="100%" alt="Four en argile : le charbon est trop chaud, seulement bâtons ou charbon de bois"><br>Four en argile : le charbon est trop chaud, seulement bâtons ou charbon de bois</td>
<td width="33%"><img src="docs/media/ui/furnace_stone.png" width="100%" alt="Fourneau en pierre fondant du cuivre brut à chaleur 2/2"><br>Fourneau en pierre fondant du cuivre brut à chaleur 2/2</td>
<td width="33%"><img src="docs/media/ui/furnace_too_cold.png" width="100%" alt="Le mithril brut demande la chaleur 3 : le fourneau en pierre est trop froid"><br>Le mithril brut demande la chaleur 3 : trop froid</td>
</tr>
<tr>
<td><img src="docs/media/ui/anvil.png" width="100%" alt="Enclume en cuivre : réparation complète payée en pépites de cuivre"><br>Enclume en cuivre : réparation payée en pépites de cuivre</td>
<td><img src="docs/media/ui/enchanting.png" width="100%" alt="Table d'enchantement : les coûts sont payés en points d'XP MITE"><br>Table d'enchantement : coûts payés en points d'XP MITE</td>
<td><img src="docs/media/ui/advancements.png" width="100%" alt="L'onglet de progrès du mod"><br>L'onglet de progrès du mod (49 progrès plus la racine de l'onglet)</td>
</tr>
<tr>
<td><img src="docs/media/ui/strongbox.png" width="100%" alt="Coffre-fort en argent : rangement que seul son propriétaire ouvre"><br>Coffre-fort en argent : seul son propriétaire l'ouvre</td>
<td><img src="docs/media/ui/strongbox_locked.png" width="100%" alt="Le coffre-fort d'un autre reste fermé"><br>Le coffre-fort d'un autre reste fermé</td>
<td><img src="docs/media/ui/create_world.png" width="100%" alt="La création de monde démarre en Difficile (Grands biomes dans l'onglet Monde)"><br>La création de monde démarre en Difficile</td>
</tr>
<tr>
<td><img src="docs/media/ui/chat_commands.png" width="100%" alt="/commands et /villages (conditions de déblocage des villages)"><br><code>/commands</code> et <code>/villages</code> (conditions de déblocage des villages)</td>
<td><img src="docs/media/ui/chat_stats.png" width="100%" alt="/stats et /state : niveau, bonus, nutrition, malédiction, lune"><br><code>/stats</code> et <code>/state</code> : niveau, bonus, nutrition, malédiction, lune</td>
<td><img src="docs/media/ui/death.png" width="100%" alt="Écran de mort avec le compte à rebours de réapparition forcée de 120 s"><br>Tous les textes sont traduits (anglais et français)</td>
</tr>
</table>

La table d'enchantement propose 17 nouveaux enchantements ; les enclumes de métal réparent avec des pépites ; une fiole
de désenchantement retire les enchantements.

## Configuration

**Mods → Hardscrabble → Config** ouvre l'éditeur en jeu ; on peut aussi modifier les fichiers.

<table>
<tr>
<td width="33%"><img src="docs/media/ui/mods_config.png" width="100%" alt="Mods > Hardscrabble : le bouton Config ouvre l'éditeur de configuration"><br>Mods → Hardscrabble → Config</td>
<td width="33%"><img src="docs/media/ui/config_root.png" width="100%" alt="Accueil de l'éditeur : réglages client et règles du monde synchronisées"><br>Réglages client et règles du monde synchronisées</td>
<td width="33%"><img src="docs/media/ui/config.png" width="100%" alt="Éditeur de configuration en jeu (section règles synchronisées)"><br>Section règles synchronisées</td>
</tr>
</table>

- **Règles serveur** : `config/hardscrabble-synced.toml` contient les valeurs par défaut, remplacées par monde par
  `saves/<monde>/syncedconfig/hardscrabble-synced.toml` ; le fichier est synchronisé vers les clients. Chaque clé a un
  commentaire en anglais et en français.
- **Client** : `config/hardscrabble-client.toml` (`hints.contextual_hints` : messages d'aide uniques, indicateurs du HUD,
  durabilité en pourcentage).

Clés utiles :

| Clé | Effet |
|---|---|
| `rules.strict_mode` | Ni Créatif ni triche, keepInventory désactivé, pas de Spectateur (activé par défaut) |
| `rules.allowed_ops` | Joueurs exemptés du mode strict |
| `rules.lock_difficulty` | Force la difficulté Difficile et interdit de la changer |
| `rules.profile` | `CUSTOM` (défaut, clés telles qu'écrites), `STRICT_R196`, `MODERN` (verrous levés, pénalité de mort allégée), `SERVER` |
| `rules.respawn_timer_seconds` | Délai de réapparition forcée (120) |
| `player.death_penalty` | Règle d'XP à la mort : `R196_CODE`, `WIKI_THIRD`, `MCMOD_LEVEL` |
| `metabolism.hunger_global_multiplier` | Vitesse de la faim |
| `lunar.blood_moon_period` | Jours entre deux lunes de sang (32) |
| `structures.village_lock` | Les villages attendent la première pioche en fer |
| `equipment.elytra_firework_boost` | Les fusées propulsent l'élytre en vol plané (désactivé : impossible d'en tirer en vol plané) |

> [!WARNING]
> `rules.dev_profile` lève le mode strict et active `/mitedev` pour les opérateurs. C'est une porte de sortie pour le
> développement : ne l'activez jamais sur un vrai monde.

## Installer et jouer

Prérequis : **Minecraft Java 26.3**, **NeoForge 26.3.0.88**, **Java 25**. Aucun jar n'est encore publié ; il faut le
construire depuis les sources :

```
./gradlew runClient        # client, créer un monde Survie
./gradlew runServer        # serveur dédié (run/eula.txt doit être accepté par vous)
./gradlew build            # jar dans build/libs/
```

### Sauvegardes

Les données sauvegardées portent une version de schéma et les anciennes versions sont migrées au chargement. Ouvrir un
monde joué d'abord sans le mod n'est pas pris en charge : il se charge avec les valeurs par défaut et les joueurs sont
avertis. Retirer le mod d'un monde n'est pas pris en charge. Les mondes créés avant que l'identifiant du mod devienne
`hardscrabble` (2026-10-19, c'était `mite`) ne se chargent pas : leurs blocs, objets et données utilisent l'ancien
espace de noms. Ils n'existaient qu'en développement ; créez un nouveau monde.

## Tests

```
python -I tools/test_all.py      # tout ce qui suit sauf le client (ajouter --client pour lui)
./gradlew build                  # compilation + JUnit (formules pures, vérification des assets)
./gradlew runGameTestServer      # tests en jeu (règles, objets, monstres, audit des registres)
python -I tools/e2e/run_e2e.py   # serveur dédié piloté en RCON : génération, Underworld, portails, structures,
                                 # temps par tick, persistance après redémarrage, erreurs du journal (rapport dans run-e2e/)
python -I tools/e2e/run_client_e2e.py  # vrai client sur une copie de ce monde : HUD, inventaire, rendu de chaque
                                       # monstre, gros plans des modèles (étape "models"), lune de sang, Underworld,
                                       # Nether ; captures dans run/screenshots/e2e_*.png
```

Au 2026-10-19 : 42 tests JUnit, 529 GameTests et 36 vérifications de bout en bout sur un vrai serveur dédié,
tous au vert.

Les images de ce README sont produites par une session client scriptée. Pour les régénérer (il faut le monde produit par
`run_e2e.py`) :

```
python -I tools/showcase/run_showcase.py [groupe]   # groupe : mobs, items, ui, world, mechanics (séparés par des virgules) ou all
```

Chaque `docs/media/<groupe>/CAPTIONS.md` contient les légendes en anglais et en français.

## Vue d'ensemble

| Domaine | Ce qui existe |
|---|---|
| Progression | Silex → cuivre → argent/or → fer → métal ancien → mithril → adamantium ; pas de bûches à mains nues ; outils en bois, pierre et diamant retirés |
| Outils | 78 nouveaux outils et armes, plus ceux en cuivre, fer et or de vanilla et une canne à pêche par matériau ; durabilité ×100, usure selon la dureté, qualité (8 niveaux, payée en XP), allonge, parade |
| Fabrication | Fabrication chronométrée (2×2 et établis), 9 établis qui verrouillent les métaux, 2×2 restreinte, crafter restreint |
| Métallurgie | Fours à niveau de chaleur (argile → netherrack), chaleur des combustibles, répartition des minerais de MITE avec filons selon la profondeur |
| Joueur | Courbe d'XP de MITE, niveaux négatifs, santé max 6 → 20 selon le niveau, pénalité de mort, réapparition forcée en 120 s, sacrifice de gemmes |
| Faim | Satiété + nutrition, protéines/phyto, insuline, humidité, régénération lente, famine, sommeil de MITE, bols, HUD |
| Combat | Dégâts et vitesse par arme, 9 armures de plaques + 8 cottes de mailles, usure qui réduit la protection, arcs, 10 pointes de flèche, argent contre les morts-vivants |
| Enchantement | Table de MITE (points d'XP), 17 enchantements, enclumes de métal (réparation en pépites), fiole de désenchantement, coffres-forts |
| Monstres | 29 nouveaux monstres (goule, spectre, revenant, ombre, longdead, élémentaires, seigneurs d'os, cubes, chauves-souris…) avec leurs propres modèles, zombies qui creusent, apparitions selon la profondeur |
| Monde | Dimension Underworld, portails liés à la bedrock, donjons de l'Underworld, villages verrouillés, runegates, gravité de la terre, noyau du Nether |
| Temps | Calendrier lunaire (lunes de sang, bleues, des moissons), couleurs de lune, malédictions de sorcières, 50 progrès (49 plus la racine de l'onglet) |

## État et manques connus

Tous les jalons de la feuille de route jusqu'à M6, plus le backlog post-1.0, sont implémentés et les tests automatiques
passent. Un premier
playtest par l'auteur (2026-10-19) a jugé justes toutes les valeurs d'équilibrage inférées, et ses bugs sont corrigés.
Reste à faire : tests plus larges, audio original et publication.

- **Pas d'audio original.** Les voix des monstres sont des sons vanilla rejoués à une autre hauteur et un autre volume,
  avec sous-titres en anglais et en français.
- **Valeurs d'équilibrage inférées.** Là où la spécification ne dit rien, les valeurs sont inférées et marquées
  `[INFÉRÉ]` ou `[NON VÉRIFIÉ]` dans le code.
- Toutes les textures sont du pixel art original (`docs/art/`). Les modèles des monstres viennent de sources Blockbench
  dans `docs/art/models/*.bbmodel` ; `tools/pixelart/models3d.py` génère les couches Java et les skins.

## Contribuer

Voir [`DEVELOPMENT.md`](DEVELOPMENT.md) (en anglais) pour les commandes, l'organisation et les conventions, et
[`PROVENANCE.md`](PROVENANCE.md) pour les règles de salle blanche que chaque contribution doit suivre.

## Kit presse

Vous préparez une vidéo ou un article ? Le kit presse ([français](docs/press/Hardscrabble-press-kit-fr.pdf),
[anglais](docs/press/Hardscrabble-press-kit-en.pdf)) contient le pitch, des idées de vidéo et les images clés. Toutes
les captures de [`docs/media`](docs/media) sont libres d'usage dans les vidéos et miniatures.

## Crédits

- **Le code, les textures, les modèles des monstres, les sons et la documentation** ont été écrits par **Claude**
  (l'IA d'Anthropic, Claude Opus 5.5 via Claude Code), sous la direction de [nogaruki](https://github.com/nogaruki), porteur du projet, qui l'a testé et relu.
- **MITE** (*Minecraft Is Too Easy*) d'**Avernite** est l'inspiration de ce mod. Hardscrabble n'est ni affilié à
  Avernite ni approuvé par Avernite ; aucun code ni asset de MITE n'est inclus.
- Minecraft est une marque de Mojang Studios. Hardscrabble n'est pas un produit officiel Minecraft.
- Construit sur [NeoForge](https://neoforged.net/).

## Licences

- Code : GNU LGPL v3 uniquement (`LGPL-3.0-only`) : voir [`LICENSE`](LICENSE) et le texte de la GPL v3 auquel il renvoie
  dans [`COPYING`](COPYING).
- Assets (textures, fichiers de structures, documentation graphique) : [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/),
  attribution « Hardscrabble authors » ; voir [`LICENSE-ASSETS`](LICENSE-ASSETS).
- Provenance et règles de salle blanche : [`PROVENANCE.md`](PROVENANCE.md).
