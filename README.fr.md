<p align="right"><a href="README.md">English</a> · <b>Français</b></p>

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
<td width="25%"><img src="docs/media/mechanics/no_bare_hand_logs.gif" width="100%"><br>Pas de bûches à mains nues : on commence au silex et à la hachette.</td>
<td width="25%"><img src="docs/media/ui/craft_workbench.gif" width="100%"><br>Fabrication chronométrée, 9 établis qui verrouillent chaque palier de métal.</td>
<td width="25%"><img src="docs/media/ui/furnace_too_cold.png" width="100%"><br>Les fours ont un niveau de chaleur ; les meilleurs minerais demandent plus chaud.</td>
<td width="25%"><img src="docs/media/ui/hud_malnourished.png" width="100%"><br>Satiété et nutrition : protéines et phytonutriments comptent.</td>
</tr>
<tr>
<td><img src="docs/media/mobs/dire_wolf_pack.gif" width="100%"><br>29 nouveaux monstres avec leurs propres modèles.</td>
<td><img src="docs/media/world/moon_rise.gif" width="100%"><br>Calendrier lunaire : lunes de sang, bleues et des moissons.</td>
<td><img src="docs/media/world/portal_travel.png" width="100%"><br>L'Underworld, atteint par un portail bâti sur la bedrock.</td>
<td><img src="docs/media/ui/death.png" width="100%"><br>Peu de cœurs à bas niveau, pénalité de mort, réapparition forcée en 120 s.</td>
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
<td width="50%"><img src="docs/media/ui/craft_inventory.gif" width="100%"><br>Fabrication chronométrée dans la grille 2x2.</td>
<td width="50%"><img src="docs/media/ui/craft_workbench.gif" width="100%"><br>Établi de cuivre avec le bouton de qualité visée et son coût en XP.</td>
</tr>
<tr>
<td><img src="docs/media/ui/craft_refused.png" width="100%"><br>Une recette en fer est refusée sur un établi de cuivre…</td>
<td><img src="docs/media/ui/craft_refused_hud.png" width="100%"><br>…et la barre d'action nomme l'établi nécessaire.</td>
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
<td width="50%"><img src="docs/media/items/workbenches.png" width="100%"><br>Les neuf établis qui verrouillent chaque palier de métal.</td>
<td width="50%"><img src="docs/media/items/spin.gif" width="100%"><br>Un support d'armure en adamantium qui tourne au milieu des établis.</td>
</tr>
<tr>
<td><img src="docs/media/items/furnaces.png" width="100%"><br>Les cinq fours à niveau de chaleur, du four en argile au four en netherrack.</td>
<td><img src="docs/media/items/metals.png" width="100%"><br>Minerai, métal brut, pépite, lingot, pièce, chaîne et bloc par métal.</td>
</tr>
</table>

<details>
<summary>Toutes les planches d'objets (blocs, matériaux, nourriture…)</summary>

<table>
<tr>
<td width="33%"><img src="docs/media/items/ores.png" width="100%"><br>Minerais d'argent, de mithril et d'adamantium, variantes pierre et ardoise des abîmes</td>
<td width="33%"><img src="docs/media/items/metal_blocks.png" width="100%"><br>Les blocs de stockage de chaque métal</td>
<td width="33%"><img src="docs/media/items/doors_bars.png" width="100%"><br>Portes et barreaux de métal</td>
</tr>
<tr>
<td><img src="docs/media/items/anvils.png" width="100%"><br>Les enclumes de métal, réparées avec des pépites du même métal</td>
<td><img src="docs/media/items/strongboxes.png" width="100%"><br>Les coffres-forts en sept métaux</td>
<td><img src="docs/media/items/runestones.png" width="100%"><br>Pierres runiques de mithril et d'adamantium et table d'enchantement en émeraude</td>
</tr>
<tr>
<td><img src="docs/media/items/buckets.png" width="100%"><br>Seaux de métal, vides ou remplis d'eau, de lave, de lait ou de pierre</td>
<td><img src="docs/media/items/coins.png" width="100%"><br>Les six pièces de monnaie</td>
<td><img src="docs/media/items/shards.png" width="100%"><br>Éclats : silex, obsidienne, verre, quartz, émeraude, diamant</td>
</tr>
<tr>
<td><img src="docs/media/items/food.png" width="100%"><br>Nouveaux aliments, bols et soupes</td>
<td><img src="docs/media/items/crops.png" width="100%"><br>Oignons à chaque stade, et blé, carottes, pommes de terre et oignons morts</td>
<td><img src="docs/media/items/misc.png" width="100%"><br>Butin de monstres, fioles d'enchantement et de désenchantement, fumier, armure de cheval en argent</td>
</tr>
<tr>
<td><img src="docs/media/items/mantle_core.png" width="100%"><br>Manteau et Noyau, les blocs sous l'Underworld et le Nether</td>
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
<td width="50%"><img src="docs/media/ui/hud.png" width="100%"><br>HUD au niveau 3 : peu de cœurs, niveau MITE, barre de nourriture plafonnée par le niveau.</td>
<td width="50%"><img src="docs/media/ui/hud_malnourished.png" width="100%"><br>Malnutrition : marqueurs P et V (ni protéines ni phytonutriments) et l'astuce unique.</td>
</tr>
<tr>
<td><img src="docs/media/ui/tooltip_food.png" width="100%"><br>Infobulle de nourriture : satiété, nutrition et nutriments.</td>
<td><img src="docs/media/ui/death_fr.png" width="100%"><br>Écran de mort avec le compte à rebours de réapparition forcée de 120 s.</td>
</tr>
</table>

Les sorcières peuvent vous maudire ; l'icône de malédiction apparaît une fois la malédiction découverte, et une fiole de
désenchantement la lève.

<table>
<tr>
<td width="33%"><img src="docs/media/ui/hud_curse.png" width="100%"><br>Malédiction de sorcière : icône en haut à gauche, messages dans le chat</td>
<td width="33%"><img src="docs/media/ui/hud_curse_tooltip.png" width="100%"><br>Survoler l'icône dans un inventaire nomme la malédiction</td>
<td width="33%"><img src="docs/media/ui/chat_hints.png" width="100%"><br>Astuces contextuelles uniques, et une fiole de désenchantement qui lève la malédiction</td>
</tr>
</table>

### Mécaniques du monde

<table>
<tr>
<td width="33%"><img src="docs/media/mechanics/no_bare_hand_logs.gif" width="100%"><br>Frapper un tronc à mains nues ne fait rien ; une hachette en silex le coupe</td>
<td width="33%"><img src="docs/media/mechanics/gravel_flint.gif" width="100%"><br>Le gravier donne surtout du gravier, parfois des éclats de silex ou des pépites</td>
<td width="33%"><img src="docs/media/mechanics/dirt_gravity.gif" width="100%"><br>Retirez son support et la terre tombe</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/campfire_goes_out.gif" width="100%"><br>Trop d'aliments sur un feu de camp l'éteignent</td>
<td><img src="docs/media/mechanics/gem_sacrifice.gif" width="100%"><br>Clic droit sur une gemme pour la sacrifier contre de l'XP, 3 émeraudes par jour au plus</td>
<td><img src="docs/media/mechanics/crop_blight.gif" width="100%"><br>La rouille se propage et tue les cultures (ticks aléatoires accélérés)</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/zombies_dig.gif" width="100%"><br>Les zombies creusent la terre pour atteindre le joueur</td>
<td><img src="docs/media/mechanics/squid_smashes_boat.gif" width="100%"><br>Un calmar chasse le joueur en barque et brise la barque</td>
<td><img src="docs/media/mechanics/horse_shies.gif" width="100%"><br>Un cheval sauvage laisse approcher un joueur accroupi, puis s'éloigne</td>
</tr>
</table>

Aussi : les cultures poussent mieux en rangs, le bétail broute son pâturage, les échanges des villageois ne se
renouvellent pas et ne donnent pas d'XP, les monstres apparaissent jusqu'à une lumière de bloc de 7.

## Combat et armures

Chaque arme a ses dégâts et sa vitesse ; la protection d'une armure baisse avec l'usure. Neuf armures de plaques et
huit cottes de mailles, des arcs avec 10 pointes de flèche, et l'argent fait plus de dégâts aux morts-vivants.

<p align="center"><img src="docs/media/items/armour_worn.png" width="640"><br>
<em>Armure de mithril complète, hache de bataille et dague de mithril.</em></p>

<table>
<tr>
<td width="50%"><img src="docs/media/items/armour_sets.png" width="100%"><br>Les neuf armures de plaques, du cuir à l'adamantium</td>
<td width="50%"><img src="docs/media/items/chainmail_sets.png" width="100%"><br>Les huit cottes de mailles, du cuivre à l'adamantium</td>
</tr>
<tr>
<td><img src="docs/media/items/weapons.png" width="100%"><br>Épées, dagues, couteaux, massue et gourdin, haches de bataille, marteaux de guerre, arcs et les 10 pointes de flèche</td>
<td><img src="docs/media/ui/tooltip_tool.png" width="49%"> <img src="docs/media/ui/tooltip_armor.png" width="49%"><br>Infobulles : durabilité, matériau, niveau de minage, qualité</td>
</tr>
</table>

## Monstres

29 nouveaux monstres, chacun avec son modèle (sources Blockbench dans `docs/art/models/`). Les zombies creusent, les
yeux des araignées brillent dans le noir et les profondeurs font apparaître des monstres plus durs. Les nuits de lune de
sang, morts-vivants frénétiques, goules et revenants envahissent la surface.

<table>
<tr>
<td width="50%"><img src="docs/media/mobs/family_undead.png" width="100%"><br>Les morts-vivants : seigneurs d'os, longdeads, ombre, rôdeur invisible, revenant, spectre, goule</td>
<td width="50%"><img src="docs/media/mobs/family_spiders.png" width="100%"><br>Araignée démoniaque, araignée de phase, veuve noire et araignée des bois dans leur grotte</td>
</tr>
<tr>
<td><img src="docs/media/mobs/family_elementals.png" width="100%"><br>Golem d'argile, élémentaire de feu et élémentaire de terre</td>
<td><img src="docs/media/mobs/family_cubes.png" width="100%"><br>Les cubes gélatineux : gelée, blob, vase et pudding</td>
</tr>
<tr>
<td><img src="docs/media/mobs/family_bats.png" width="100%"><br>Aile-de-nuit, chauve-souris vampire géante et chauve-souris vampire</td>
<td><img src="docs/media/mobs/family_silverfish.png" width="100%"><br>Poisson d'argent chenu, engeance du Nether et épine-de-cuivre</td>
</tr>
</table>

<table>
<tr>
<td width="33%"><img src="docs/media/mobs/ghoul_walk.gif" width="100%"><br>Une goule s'avance vers vous</td>
<td width="33%"><img src="docs/media/mobs/cube_bounce.gif" width="100%"><br>Des cubes gélatineux approchent en bondissant</td>
<td width="33%"><img src="docs/media/mobs/fire_elemental_alive.gif" width="100%"><br>Un élémentaire de feu en action</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/shadow_snuffs_lights.gif" width="100%"><br>Une Ombre souffle les bougies et casse les torches</td>
<td><img src="docs/media/mechanics/jelly_digests_items.gif" width="100%"><br>Une Gelée digère la viande et le cuir au sol ; le fer reste</td>
<td><img src="docs/media/mechanics/earth_elemental_heated.gif" width="100%"><br>La foudre chauffe un Élémentaire de terre : il brûle et marche dans la lave</td>
</tr>
<tr>
<td><img src="docs/media/mechanics/fire_elemental_water.gif" width="100%"><br>Un seau ou un bol d'eau blesse un Élémentaire de feu</td>
<td><img src="docs/media/mobs/spider_eyes_night.png" width="100%"><br>Les yeux des araignées brillent dans le noir</td>
<td></td>
</tr>
</table>

### Loups terribles

Les loups terribles chassent en meute. Apprivoisés, ils portent un collier et une armure de loup teignable qui se
fissure à l'usure.

<table>
<tr>
<td width="33%"><img src="docs/media/mobs/dire_wolf_pack.gif" width="100%"><br>Une meute de loups terribles en chasse</td>
<td width="33%"><img src="docs/media/mobs/dire_wolf_tamed.png" width="100%"><br>Apprivoisés : collier seul, armure teinte, assis</td>
<td width="33%"><img src="docs/media/mobs/dire_wolf_cracked.png" width="100%"><br>L'armure se fissure à l'usure</td>
</tr>
</table>

### Bestiaire

<table>
<tr><th colspan="4">Morts-vivants</th></tr>
<tr>
<td width="25%" align="center"><img src="docs/media/mobs/ghoul.png" width="100%"><br>Goule</td>
<td width="25%" align="center"><img src="docs/media/mobs/wight.png" width="100%"><br>Spectre (wight)</td>
<td width="25%" align="center"><img src="docs/media/mobs/revenant.png" width="100%"><br>Revenant (armure de fer rouillé)</td>
<td width="25%" align="center"><img src="docs/media/mobs/shadow.png" width="100%"><br>Ombre (profondeurs, immatérielle)</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/invisible_stalker.png" width="100%"><br>Rôdeur invisible (souligné)</td>
<td align="center"><img src="docs/media/mobs/longdead.png" width="100%"><br>Longdead</td>
<td align="center"><img src="docs/media/mobs/longdead_guardian.png" width="100%"><br>Gardien longdead</td>
<td align="center"><img src="docs/media/mobs/bone_lord.png" width="100%"><br>Seigneur d'os</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/ancient_bone_lord.png" width="100%"><br>Seigneur d'os ancien</td>
<td></td><td></td><td></td>
</tr>
<tr><th colspan="4">Araignées</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/wood_spider.png" width="100%"><br>Araignée des bois</td>
<td align="center"><img src="docs/media/mobs/black_widow.png" width="100%"><br>Veuve noire</td>
<td align="center"><img src="docs/media/mobs/phase_spider.png" width="100%"><br>Araignée de phase</td>
<td align="center"><img src="docs/media/mobs/demon_spider.png" width="100%"><br>Araignée démoniaque</td>
</tr>
<tr><th colspan="4">Élémentaires et golems</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/clay_golem.png" width="100%"><br>Golem d'argile</td>
<td align="center"><img src="docs/media/mobs/earth_elemental.png" width="100%"><br>Élémentaire de terre</td>
<td align="center"><img src="docs/media/mobs/fire_elemental.png" width="100%"><br>Élémentaire de feu (Nether)</td>
<td align="center"><img src="docs/media/mobs/infernal_creeper.png" width="100%"><br>Creeper infernal</td>
</tr>
<tr><th colspan="4">Cubes gélatineux</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/jelly.png" width="100%"><br>Gelée</td>
<td align="center"><img src="docs/media/mobs/blob.png" width="100%"><br>Blob</td>
<td align="center"><img src="docs/media/mobs/ooze.png" width="100%"><br>Vase</td>
<td align="center"><img src="docs/media/mobs/pudding.png" width="100%"><br>Pudding</td>
</tr>
<tr><th colspan="4">Chauves-souris et poissons d'argent</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/vampire_bat.png" width="100%"><br>Chauve-souris vampire</td>
<td align="center"><img src="docs/media/mobs/giant_vampire_bat.png" width="100%"><br>Chauve-souris vampire géante</td>
<td align="center"><img src="docs/media/mobs/nightwing.png" width="100%"><br>Aile-de-nuit (Nether)</td>
<td align="center"><img src="docs/media/mobs/copperspine.png" width="100%"><br>Épine-de-cuivre</td>
</tr>
<tr>
<td align="center"><img src="docs/media/mobs/hoary_silverfish.png" width="100%"><br>Poisson d'argent chenu</td>
<td align="center"><img src="docs/media/mobs/netherspawn.png" width="100%"><br>Engeance du Nether</td>
<td></td><td></td>
</tr>
<tr><th colspan="4">Loups</th></tr>
<tr>
<td align="center"><img src="docs/media/mobs/dire_wolf.png" width="100%"><br>Loup terrible</td>
<td align="center"><img src="docs/media/mobs/hellhound.png" width="100%"><br>Chien de l'enfer (Nether)</td>
<td></td><td></td>
</tr>
</table>

## Monde

### L'Underworld

Une dimension de cavernes sous la bedrock de l'Overworld, dans un brouillard gris qui s'épaissit sur un cycle de
32 jours. Un portail bâti sur la bedrock de l'Overworld y mène ; un portail construit ailleurs ramène au point
d'apparition de l'Overworld. L'adamantium ne se trouve qu'au fond de l'Underworld, au-dessus d'un sol de Manteau
incassable. Les donjons de l'Underworld abritent des générateurs de Longdead et du butin.

<p align="center"><img src="docs/media/world/underworld_landscape.png" width="760"></p>

<table>
<tr>
<td width="33%"><img src="docs/media/world/portal_bedrock.png" width="100%"><br>Un portail bâti sur la bedrock de l'Overworld</td>
<td width="33%"><img src="docs/media/world/portal_travel.png" width="100%"><br>Sortie du portail dans l'Underworld</td>
<td width="33%"><img src="docs/media/world/portal_arrival.png" width="100%"><br>Le portail de sortie dans l'Underworld</td>
</tr>
<tr>
<td><img src="docs/media/world/underworld_fog_cycle.gif" width="100%"><br>Le brouillard s'épaissit sur un cycle de 32 jours</td>
<td><img src="docs/media/world/underworld_fog_dense.png" width="100%"><br>Le brouillard au plus dense, le jour de la lune de sang</td>
<td><img src="docs/media/world/underworld_dungeon.png" width="100%"><br>Donjon de l'Underworld : générateur de Longdead et coffre de butin</td>
</tr>
<tr>
<td><img src="docs/media/world/underworld_mantle.png" width="100%"><br>Fond sans la pierre : plaque de bedrock (au fond), sol de Manteau (devant)</td>
<td><img src="docs/media/world/adamantium_ore.png" width="100%"><br>Minerai d'adamantium, au fond de l'Underworld (roche retirée)</td>
<td><img src="docs/media/world/nether_core.png" width="100%"><br>Sol du Nether ouvert : la couche de Noyau sous la bedrock</td>
</tr>
</table>

### Lunes et météo

Une pleine lune tous les 8 jours. Une lune de sang tous les 32 jours, annoncée par une lune des moissons ; une lune
bleue tous les 128 jours, annoncée par une parhélie lunaire, empêche les monstres d'apparaître sous le ciel ouvert.
L'horloge annonce la lune du soir. Environ un matin sur huit, un épais brouillard se lève.

<table>
<tr>
<td width="33%"><img src="docs/media/world/moon_rise.gif" width="100%"><br>Une lune de sang se lève au crépuscule</td>
<td width="33%"><img src="docs/media/world/moon_blood.png" width="100%"><br>Lune de sang : morts-vivants frénétiques en surface</td>
<td width="33%"><img src="docs/media/world/moon_blue.png" width="100%"><br>Lune bleue : aucun monstre sous le ciel ouvert</td>
</tr>
<tr>
<td><img src="docs/media/world/moon_normal.png" width="100%"><br>Une pleine lune normale</td>
<td><img src="docs/media/world/moon_harvest.png" width="100%"><br>Lune des moissons, 8 jours avant une lune de sang</td>
<td><img src="docs/media/world/moon_dog.png" width="100%"><br>Parhélie lunaire : annonce une lune bleue 8 jours plus tard</td>
</tr>
<tr>
<td><img src="docs/media/world/morning_fog.png" width="100%"><br>Épais brouillard matinal</td>
<td><img src="docs/media/ui/tooltip_clock.png" width="100%"><br>L'horloge annonce la lune de ce soir</td>
<td></td>
</tr>
</table>

### Villages, runegates, minerais

Les villages n'apparaissent qu'après la fabrication d'une première pioche en fer, et ils sont abandonnés, avec des
cultures mortes. Une runegate (un portail avec une pierre runique à chaque coin) envoie au loin un joueur les mains
vides. Les minerais d'argent, de mithril et d'adamantium suivent la répartition en profondeur de MITE.

<table>
<tr>
<td width="33%"><img src="docs/media/world/village.png" width="100%"><br>Un village abandonné</td>
<td width="33%"><img src="docs/media/world/village_dead_crops.png" width="100%"><br>Les champs des villages ne portent que des cultures mortes</td>
<td width="33%"><img src="docs/media/world/runegate.png" width="100%"><br>Runegate</td>
</tr>
<tr>
<td><img src="docs/media/world/silver_ore.png" width="100%"><br>Minerai d'argent (roche retirée)</td>
<td><img src="docs/media/world/mithril_ore.png" width="100%"><br>Minerai de mithril, en profondeur dans l'Overworld (roche retirée)</td>
<td></td>
</tr>
</table>

## Interfaces

<table>
<tr>
<td width="33%"><img src="docs/media/ui/furnace_clay.png" width="100%"><br>Four en argile : le charbon est trop chaud, seulement bâtons ou charbon de bois</td>
<td width="33%"><img src="docs/media/ui/furnace_stone.png" width="100%"><br>Fourneau en pierre fondant du cuivre brut à chaleur 2/2</td>
<td width="33%"><img src="docs/media/ui/furnace_too_cold.png" width="100%"><br>Le mithril brut demande la chaleur 3 : trop froid</td>
</tr>
<tr>
<td><img src="docs/media/ui/anvil.png" width="100%"><br>Enclume en cuivre : réparation payée en pépites de cuivre</td>
<td><img src="docs/media/ui/enchanting.png" width="100%"><br>Table d'enchantement : coûts payés en points d'XP MITE</td>
<td><img src="docs/media/ui/advancements.png" width="100%"><br>L'onglet de progrès du mod (49 progrès plus la racine de l'onglet)</td>
</tr>
<tr>
<td><img src="docs/media/ui/strongbox.png" width="100%"><br>Coffre-fort en argent : seul son propriétaire l'ouvre</td>
<td><img src="docs/media/ui/strongbox_locked.png" width="100%"><br>Le coffre-fort d'un autre reste fermé</td>
<td><img src="docs/media/ui/create_world.png" width="100%"><br>La création de monde démarre en Difficile</td>
</tr>
<tr>
<td><img src="docs/media/ui/chat_commands.png" width="100%"><br><code>/commands</code> et <code>/villages</code> (conditions de déblocage des villages)</td>
<td><img src="docs/media/ui/chat_stats.png" width="100%"><br><code>/stats</code> et <code>/state</code> : niveau, bonus, nutrition, malédiction, lune</td>
<td><img src="docs/media/ui/death.png" width="100%"><br>Tous les textes sont traduits (anglais et français)</td>
</tr>
</table>

La table d'enchantement propose 17 nouveaux enchantements ; les enclumes de métal réparent avec des pépites ; une fiole
de désenchantement retire les enchantements.

## Configuration

**Mods → Hardscrabble → Config** ouvre l'éditeur en jeu ; on peut aussi modifier les fichiers.

<table>
<tr>
<td width="33%"><img src="docs/media/ui/mods_config.png" width="100%"><br>Mods → Hardscrabble → Config</td>
<td width="33%"><img src="docs/media/ui/config_root.png" width="100%"><br>Réglages client et règles du monde synchronisées</td>
<td width="33%"><img src="docs/media/ui/config.png" width="100%"><br>Section règles synchronisées</td>
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
