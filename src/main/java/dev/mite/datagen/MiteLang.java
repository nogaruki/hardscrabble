package dev.mite.datagen;

import dev.mite.MiteMod;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** All translations, EN then FR. Keep keys grouped by domain. */
public final class MiteLang extends LanguageProvider {
    private final boolean fr;

    public MiteLang(PackOutput output, String locale) {
        super(output, MiteMod.MODID, locale);
        this.fr = locale.equals("fr_fr");
    }

    /** Adds one key in both languages. */
    private void t(String key, String en, String fr) {
        add(key, this.fr ? fr : en);
        if (key.startsWith("entity.hardscrabble.")) mobNames.put(key.substring("entity.hardscrabble.".length()), new String[] {en, fr});
    }

    private final java.util.Map<String, String[]> mobNames = new java.util.HashMap<>();

    /** Subtitles of the MITE mob voices (MobSounds): "<mob> groans / hurts / dies", French with the noun's gender. */
    private void mobSubtitles() {
        for (var d : dev.mite.entity.mob.MobSounds.DEFS) {
            String[] n = mobNames.get(d.mob());
            var v = dev.mite.entity.mob.MobSounds.VOICES.get(d.mob());
            boolean fem = dev.mite.entity.mob.MobSounds.FEMININE.contains(d.mob());
            String key = "subtitles.hardscrabble." + dev.mite.entity.mob.MobSounds.id(d.mob(), d.kind());
            switch (d.kind()) {
                case AMBIENT -> t(key, n[0] + " " + v.ambientEn(), n[1] + " " + v.ambientFr());
                case HURT -> t(key, n[0] + " hurts", n[1] + (fem ? " blessée" : " blessé"));
                case DEATH -> t(key, n[0] + " dies", n[1] + " meurt");
            }
        }
    }

    /** Requirement ids (EF-REG-01, Q-25, R165...) and [INFERRED] marks stay in the TOML comment, not in the player's tooltip (Q-7). */
    static String playerText(String comment) {
        String id = "(?:EF|ET|ENF|PD|US|RE|CA|Q|R)-?[A-Z0-9]*(?:-[A-Z0-9]+)*";
        String s = comment.replaceAll("\\s*\\[[^\\]]*\\]", "") // [Q-26, INFERRED]
                .replaceAll("\\s*\\((?:" + id + "|§[^)]*|[A-Z]\\d+)(?:[ ,/]+(?:" + id + "|§[^)]*|n°\\d+|[A-Z]\\d+))*\\)", "") // (EF-NUT-11, Q-21)
                .replaceAll("^(?:" + id + "(?: \\([^)]*\\))?(?: / | )?)+ ?: ?", "") // "EF-AGR-06 / EF-MUL-05: "
                .replaceAll(" (?:until|tant que) Q-\\d+ [^.]*", "");
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void addTranslations() {
        t("itemGroup.hardscrabble", "Hardscrabble", "Hardscrabble"); // ENF-LEG-03: distinct name (decision 66)

        // rules
        t("hardscrabble.rules.difficulty_locked", "Difficulty is locked to Hard.", "La difficulté est verrouillée sur Difficile.");
        t("hardscrabble.rules.strict_mode", "Strict mode: this command is not allowed.", "Mode strict : cette commande est interdite.");
        t("hardscrabble.death.respawn_in", "Automatic respawn in %s s", "Réapparition automatique dans %s s");
        t("hardscrabble.command.stats.level", "Level %s (%s XP, %s to the next level)", "Niveau %s (%s XP, %s avant le niveau suivant)");
        t("hardscrabble.command.stats.health", "Max health %s, food cap %s", "Santé max %s, plafond de nourriture %s");
        t("hardscrabble.command.stats.bonus", "Melee damage %s, mining and crafting speed %s", "Dégâts au corps à corps %s, vitesse de minage et de fabrication %s");
        t("hardscrabble.command.state.food", "Satiety %s/%s, nutrition %s/%s", "Satiété %s/%s, nutrition %s/%s");
        t("hardscrabble.command.state.nutrients", "Protein %s, phytonutrients %s (max %s)", "Protéines %s, phytonutriments %s (max %s)");
        t("hardscrabble.command.state.insulin", "Insulin resistance %s/%s (level %s)", "Résistance à l'insuline %s/%s (niveau %s)");
        t("hardscrabble.command.state.malnourished", "Malnourished", "Mal nourri");
        t("hardscrabble.command.help.stats", "/stats: progression statistics", "/stats : statistiques de progression");
        t("hardscrabble.command.help.state", "/state: nutrition and nutrients", "/state : nutrition et nutriments");
        t("hardscrabble.command.help.villages", "/villages: village unlock conditions", "/villages : conditions de déblocage des villages");
        t("hardscrabble.command.help.commands", "/commands: this list", "/commands : cette liste");
        t("hardscrabble.command.state.rules", "Strict mode %s, difficulty lock %s", "Mode strict %s, verrou de difficulté %s");
        t("hardscrabble.command.state.moon", "Day %s, %s", "Jour %s, %s"); // moon names: moon.hardscrabble.* (P-5, one glossary)
        t("hardscrabble.command.stats.saved", "Statistics written to %s", "Statistiques écrites dans %s");
        t("hardscrabble.harvest.tool_too_weak", "Your tool is too weak for this block", "Votre outil est trop faible pour ce bloc");
        t("hardscrabble.hint.log", "Hint: logs cannot be broken by hand. Get sticks from leaves, flint from gravel and string or sinew to lash a flint hatchet.",
                "Astuce : les bûches ne se cassent pas à mains nues. Récupérez des bâtons dans les feuilles, du silex dans le gravier et de la ficelle ou du tendon pour ligaturer une hachette en silex.");
        t("hardscrabble.hint.gravel", "Hint: gravel sometimes gives flint shards, the first tool material.",
                "Astuce : le gravier donne parfois des éclats de silex, le premier matériau d'outil.");
        t("hardscrabble.hint.night", "Hint: nights are deadly. Shelter, light up and sleep when you can.",
                "Astuce : les nuits sont mortelles. Abritez-vous, éclairez et dormez dès que possible.");
        t("hardscrabble.hint.malnutrition", "Hint: you are malnourished. Eat both meat or fish (protein) and plants (phytonutrients).",
                "Astuce : vous êtes mal nourri. Mangez de la viande ou du poisson (protéines) et des végétaux (phytonutriments).");
        t("hardscrabble.hint.workbench", "Hint: workbench needed for this recipe: %s. Each metal needs a workbench of its tier, crafted on the workbench of the tier below.",
                "Astuce : établi requis pour cette recette : %s. Chaque métal demande un établi de son palier, fabriqué sur l'établi du palier inférieur.");
        // config (EF-CFG-02 CA-1): name per key, tooltip = the EN and FR comment lines of the spec
        java.util.Map<String, String[]> configNames = new java.util.HashMap<>();
        for (String[] n : new String[][] {{"lock_difficulty", "Lock difficulty", "Verrouiller la difficulté"},
                {"strict_mode", "Strict mode", "Mode strict"}, {"allowed_ops", "Players exempt from strict mode", "Joueurs exemptés du mode strict"},
                {"dev_profile", "Developer profile", "Profil développeur"}, {"respawn_timer_seconds", "Respawn timer (s)", "Délai de réapparition (s)"},
                {"hunger_global_multiplier", "Hunger multiplier", "Multiplicateur de faim"}, {"sleep_min_hour", "No bed before 21:00", "Pas de lit avant 21 h"},
                {"death_penalty", "Death penalty", "Pénalité de mort"}, {"blood_moon_period", "Blood moon period", "Période de la lune de sang"},
                {"blue_moon_period", "Blue moon period", "Période de la lune bleue"}, {"curses_enabled", "Curses", "Malédictions"},
                {"temple_min_distance", "Temple minimum distance", "Distance minimale des temples"},
                {"stronghold_distance_multiplier", "Stronghold distance multiplier", "Multiplicateur de distance des forteresses"},
                {"silver_undead_bonus", "Silver bonus against undead", "Bonus de l'argent contre les morts-vivants"},
                {"quality_enabled", "Crafting quality", "Qualité de fabrication"}, {"tool_lashing", "Tool lashing", "Ligature des outils"},
                {"crafter_restriction", "Crafter restriction", "Restriction du crafter"},
                {"strongbox_op_access", "Operators open strongboxes", "Accès opérateur aux coffres-forts"}, {"coins_enabled", "Coins", "Pièces"},
                {"village_lock", "Village lock", "Verrou des villages"}, {"village_lock_min_days", "Village lock minimum day", "Jour minimal des villages"},
                {"runegate_mithril_range", "Mithril runegate range", "Portée de la runegate en mithril"},
                {"creeper_explosion_radius", "Creeper explosion radius", "Rayon d'explosion du creeper"},
                {"gelatinous_dissolve_items", "Gelatinous cubes dissolve items", "Les cubes gélatineux dissolvent les objets"},
                {"item_lifespan_ticks", "Dropped item lifetime (ticks)", "Durée de vie des objets au sol (ticks)"},
                {"dedicated_blight", "Blight on dedicated servers", "Mildiou sur serveur dédié"},
                {"chunk_fast_forward", "Chunk fast-forward", "Avance rapide des chunks"},
                {"fluid_source_xp_cost", "Fluid source XP cost", "Coût en XP d'une source de fluide"},
                {"elytra_firework_boost", "Elytra firework boost", "Propulsion de l'élytre par fusée"},
                {"contextual_hints", "Contextual hints", "Aide contextuelle"},
                {"wandering_trader", "Wandering trader", "Marchand ambulant"},
                {"emerald_sacrifices_per_day", "Emerald sacrifices per day", "Sacrifices d'émeraudes par jour"},
                {"profile", "Rule profile", "Profil de règles"},
                {"zombie_digging", "Digging zombies", "Zombies creuseurs"},
                {"livestock_needs", "Livestock needs", "Besoins du bétail"},
                {"show_indicators", "Food bar indicators", "Indicateurs de la barre de nourriture"},
                {"durability_as_percent", "Durability in percent", "Durabilité en pourcentage"}}) configNames.put(n[0], n);
        for (var value : dev.mite.config.MiteConfig.values(dev.mite.config.MiteConfig.SPEC, dev.mite.config.MiteClientConfig.SPEC)) {
            String key = "hardscrabble.configuration." + value.getPath().getLast();
            String[] comment = value.getSpec().getComment().split("\n"), name = configNames.get(value.getPath().getLast());
            if (name == null) throw new IllegalStateException("No config name for " + key);
            t(key, name[1], name[2]);
            t(key + ".tooltip", playerText(comment[0]), playerText(comment[1]));
        }
        // config screen (Mods > Hardscrabble > Config): title, one button per file, one sub-screen per push() section
        t("hardscrabble.configuration.title", "Hardscrabble Configuration", "Configuration de Hardscrabble");
        t("hardscrabble.configuration.section.hardscrabble.synced.toml", "World Rules", "Règles du monde");
        t("hardscrabble.configuration.section.hardscrabble.synced.toml.title", "Hardscrabble World Rules", "Règles du monde de Hardscrabble");
        t("hardscrabble.configuration.section.hardscrabble.client.toml", "Client Settings", "Réglages client");
        t("hardscrabble.configuration.section.hardscrabble.client.toml.title", "Hardscrabble Client Settings", "Réglages client de Hardscrabble");
        java.util.Map<String, String[]> sections = new java.util.HashMap<>();
        for (String[] s : new String[][] {
                {"rules", "Rules", "Règles", "Difficulty lock, strict mode, rule profile and respawn timer", "Verrou de difficulté, mode strict, profil de règles et délai de réapparition"},
                {"metabolism", "Metabolism", "Métabolisme", "Hunger speed and sleeping hours", "Vitesse de la faim et heures de sommeil"},
                {"player", "Player", "Joueur", "What a death costs", "Ce que coûte une mort"},
                {"lunar", "Moons", "Lunes", "How often blood and blue moons rise", "Fréquence des lunes de sang et des lunes bleues"},
                {"curse", "Curses", "Malédictions", "Witch curses", "Malédictions des sorcières"},
                {"world", "World", "Monde", "Distance of temples and strongholds", "Distance des temples et des forteresses"},
                {"combat", "Combat", "Combat", "Silver weapons against the undead", "Armes en argent contre les morts-vivants"},
                {"crafting", "Crafting", "Fabrication", "Crafting quality, tool lashing and crafter", "Qualité de fabrication, ligature des outils et crafter"},
                {"enchanting", "Enchanting and Storage", "Enchantement et rangement", "Strongboxes and coins", "Coffres-forts et pièces"},
                {"structures", "Villages and Runegates", "Villages et runegates", "Village lock and runegate range", "Verrou des villages et portée des runegates"},
                {"mobs", "Mobs", "Monstres", "Creepers, digging zombies and gelatinous cubes", "Creepers, zombies creuseurs et cubes gélatineux"},
                {"farming", "Farming", "Agriculture", "Dropped items, blight, livestock and chunk fast-forward", "Objets au sol, mildiou, bétail et avance rapide des chunks"},
                {"equipment", "Equipment", "Équipement", "Fluid sources and elytra", "Sources de fluide et élytre"},
                {"trading", "Trading", "Commerce", "Wandering trader and emerald sacrifices", "Marchand ambulant et sacrifices d'émeraudes"},
                {"hints", "Hints", "Aide", "One-time help messages", "Messages d'aide uniques"},
                {"hud", "HUD", "Interface", "Food bar indicators", "Indicateurs de la barre de nourriture"},
                {"tooltip", "Tooltips", "Infobulles", "Item tooltip lines", "Lignes des infobulles d'objets"}}) sections.put(s[0], s);
        for (String section : dev.mite.config.MiteConfig.values(dev.mite.config.MiteConfig.SPEC, dev.mite.config.MiteClientConfig.SPEC).stream()
                .map(v -> v.getPath().getFirst()).distinct().toList()) {
            String[] s = sections.get(section);
            if (s == null) throw new IllegalStateException("No config section name for " + section);
            t("hardscrabble.configuration." + section, s[1], s[2]);
            t("hardscrabble.configuration." + section + ".tooltip", s[3], s[4]);
        }
        t("hardscrabble.command.dev.heal", "Health restored", "Santé restaurée");
        t("hardscrabble.command.dev.nourish", "Metabolism filled", "Métabolisme rempli");
        t("hardscrabble.command.dev.malnourish", "Protein and phytonutrients emptied", "Protéines et phytonutriments vidés");
        t("hardscrabble.command.dev.killmobs", "%s hostile mobs killed", "%s monstres tués");
        t("hardscrabble.command.dev.grow", "%s plants grown", "%s plantes poussées");
        t("hardscrabble.command.dev.dungeon", "Nearest spawner: %s, %s blocks away (click to teleport)", "Générateur le plus proche : %s, à %s blocs (cliquer pour s'y téléporter)");
        t("hardscrabble.command.dev.dungeon.none", "No spawner in the loaded chunks within %s chunks", "Aucun générateur dans les chunks chargés à moins de %s chunks");
        t("tooltip.hardscrabble.durability", "Durability: %s%%", "Durabilité : %s %%");
        t("tooltip.hardscrabble.durability_points", "Durability: %s / %s", "Durabilité : %s / %s");
        t("tooltip.hardscrabble.material", "Material: %s", "Matériau : %s");
        t("tooltip.hardscrabble.harvest_level", "Harvest level: %s", "Niveau de récolte : %s");
        t("tooltip.hardscrabble.fuel_heat", "Fuel heat: %s", "Chaleur du combustible : %s");
        t("tooltip.hardscrabble.food", "Satiety %s, nutrition %s", "Satiété %s, nutrition %s");
        t("tooltip.hardscrabble.protein", "Protein", "Protéines");
        t("tooltip.hardscrabble.phyto", "Phytonutrients", "Phytonutriments");
        t("tooltip.hardscrabble.sugar", "Sugar: %s", "Sucre : %s");
        String[][] materials = {{"wood", "Wood", "Bois"}, {"flint", "Flint", "Silex"}, {"obsidian", "Obsidian", "Obsidienne"},
                {"rusted_iron", "Rusted Iron", "Fer rouillé"}, {"copper", "Copper", "Cuivre"}, {"silver", "Silver", "Argent"}, {"gold", "Gold", "Or"},
                {"iron", "Iron", "Fer"}, {"ancient_metal", "Ancient Metal", "Métal ancien"}, {"mithril", "Mithril", "Mithril"}, {"adamantium", "Adamantium", "Adamantium"},
                {"leather", "Leather", "Cuir"}};
        for (String[] m : materials) t("material.hardscrabble." + m[0], m[1], m[2]);

        // material
        t("item.hardscrabble.flint_shard", "Flint Shard", "Éclat de silex");
        t("item.hardscrabble.obsidian_shard", "Obsidian Shard", "Éclat d'obsidienne");
        t("item.hardscrabble.emerald_shard", "Emerald Shard", "Éclat d'émeraude");
        t("item.hardscrabble.diamond_shard", "Diamond Shard", "Éclat de diamant");
        t("item.hardscrabble.quartz_shard", "Quartz Shard", "Éclat de quartz");
        t("item.hardscrabble.glass_shard", "Glass Shard", "Éclat de verre");
        t("item.hardscrabble.silver_nugget", "Silver Nugget", "Pépite d'argent");
        t("item.hardscrabble.silver_ingot", "Silver Ingot", "Lingot d'argent");
        t("item.hardscrabble.raw_silver", "Raw Silver", "Argent brut");
        t("item.hardscrabble.ancient_metal_nugget", "Ancient Metal Nugget", "Pépite de métal ancien");
        t("item.hardscrabble.ancient_metal_ingot", "Ancient Metal Ingot", "Lingot de métal ancien");
        t("item.hardscrabble.mithril_nugget", "Mithril Nugget", "Pépite de mithril");
        t("item.hardscrabble.mithril_ingot", "Mithril Ingot", "Lingot de mithril");
        t("item.hardscrabble.raw_mithril", "Raw Mithril", "Mithril brut");
        t("item.hardscrabble.adamantium_nugget", "Adamantium Nugget", "Pépite d'adamantium");
        t("item.hardscrabble.adamantium_ingot", "Adamantium Ingot", "Lingot d'adamantium");
        t("item.hardscrabble.raw_adamantium", "Raw Adamantium", "Adamantium brut");

        // metabolism
        t("message.hardscrabble.crave_protein", "You crave meat and dairy", "Vous avez envie de viande et de laitages");
        t("message.hardscrabble.crave_phyto", "You crave fruits and vegetables", "Vous avez envie de fruits et de légumes");
        t("hud.hardscrabble.protein", "P", "P");
        t("hud.hardscrabble.phyto", "V", "V");
        t("hud.hardscrabble.insulin", "I%s", "I%s");
        t("hud.hardscrabble.zero_energy", "∅", "∅");
        t("item.hardscrabble.water_bowl", "Bowl of Water", "Bol d'eau");
        t("item.hardscrabble.milk_bowl", "Bowl of Milk", "Bol de lait");
        t("message.hardscrabble.sleep_no_shelter", "You need a roof over your bed to sleep", "Il faut un toit au-dessus du lit pour dormir");
        t("message.hardscrabble.sleep_too_early", "You can only go to bed after 21:00", "Vous ne pouvez vous coucher qu'après 21 h");
        t("message.hardscrabble.woken_by_hunger", "You were woken up by hunger", "Vous avez été réveillé par la faim");
        t("message.hardscrabble.woken_by_mob", "Something woke you up", "Quelque chose vous a réveillé");
        t("message.hardscrabble.emerald_sacrifice_limit", "You cannot sacrifice more emeralds today", "Vous ne pouvez plus sacrifier d'émeraude aujourd'hui");
        // crafting
        t("block.hardscrabble.obsidian_workbench", "Obsidian Workbench", "Établi en obsidienne");
        t("block.hardscrabble.copper_workbench", "Copper Workbench", "Établi en cuivre");
        t("block.hardscrabble.silver_workbench", "Silver Workbench", "Établi en argent");
        t("block.hardscrabble.gold_workbench", "Gold Workbench", "Établi en or");
        t("block.hardscrabble.iron_workbench", "Iron Workbench", "Établi en fer");
        t("block.hardscrabble.ancient_metal_workbench", "Ancient Metal Workbench", "Établi en métal ancien");
        t("block.hardscrabble.mithril_workbench", "Mithril Workbench", "Établi en mithril");
        t("block.hardscrabble.adamantium_workbench", "Adamantium Workbench", "Établi en adamantium");
        t("hardscrabble.crafting.needs_better_workbench", "Workbench needed: %s or better", "Établi requis : %s ou mieux");
        t("hardscrabble.crafting.needs_workbench", "This recipe needs a workbench", "Cette recette nécessite un établi");
        t("hardscrabble.crafting.no_energy", "Too hungry to craft", "Trop affamé pour fabriquer");
        t("hardscrabble.crafting.in_web", "You cannot craft while caught in a web", "Impossible de fabriquer pris dans une toile"); // changelog l.114
        t("entity.hardscrabble.thrown_brick", "Thrown Brick", "Brique lancée"); // changelog l.774
        // crafting quality, lashing (EF-CRA-05, EF-OUT-12)
        t("item.hardscrabble.sinew", "Sinew", "Tendon");
        t("hardscrabble.crafting.post_dragon", "Only a Dragon slayer can craft this", "Seul un vainqueur du Dragon peut fabriquer cela");
        t("hardscrabble.combat.happy_ghast_post_dragon", "The happy ghast only carries Dragon slayers", "Le ghast heureux ne porte que les vainqueurs du Dragon");
        t("hardscrabble.crafting.needs_lashing", "Flint and obsidian tools need string or sinew", "Les outils en silex et en obsidienne exigent une ficelle ou un tendon");
        t("hardscrabble.crafting.damaged_knife", "Only an undamaged knife makes a workbench", "Seul un couteau intact permet de fabriquer un établi"); // changelog l.341
        t("gui.hardscrabble.crafting.target_quality", "Target quality (capped by material and XP)", "Qualité visée (limitée par le matériau et l'XP)");
        t("gui.hardscrabble.crafting.quality_cost", "%s: %s XP", "%s : %s XP");
        t("gui.hardscrabble.crafting.seconds", "%ss", "%s s"); // Q-6
        t("tooltip.hardscrabble.quality", "%s (%s%% durability)", "%s (durabilité %s %%)"); // changelog l.596
        String[][] qualities = {{"Wretched", "Misérable"}, {"Poor", "Médiocre"}, {"Average", "Moyenne"}, {"Fine", "Fine"}, {"Excellent", "Excellente"},
                {"Superb", "Superbe"}, {"Masterwork", "Chef-d'œuvre"}, {"Legendary", "Légendaire"}};
        for (dev.mite.core.Quality q : dev.mite.core.Quality.values())
            t(dev.mite.crafting.CraftQuality.langKey(q), qualities[q.ordinal()][0], qualities[q.ordinal()][1]);
        // harvest
        dev.mite.harvest.HarvestContent.TOOLS.forEach(tool -> t(tool.langKey(), tool.en(), tool.fr()));
        // metallurgy
        t("block.hardscrabble.clay_furnace", "Clay Oven", "Four en argile");
        t("block.hardscrabble.large_clay_furnace", "Large Clay Oven", "Grand four en argile");
        t("block.hardscrabble.sandstone_furnace", "Sandstone Furnace", "Four en grès");
        t("block.hardscrabble.obsidian_furnace", "Obsidian Furnace", "Four en obsidienne");
        t("block.hardscrabble.netherrack_furnace", "Netherrack Furnace", "Four en netherrack");
        t("gui.hardscrabble.furnace.heat", "Heat %s/%s", "Chaleur %s/%s");
        t("gui.hardscrabble.furnace.too_cold", "Heat %s/%s: too low", "Chaleur %s/%s : trop faible");
        t("gui.hardscrabble.furnace.food_only", "Food only", "Aliments seulement");
        // Clay ovens burn small fuels only (FurnaceHeat.acceptsFuel): coal (heat 2) and blocks such as logs are refused.
        t("gui.hardscrabble.furnace.fuel_refused", "Fuel refused: use sticks or charcoal", "Combustible refusé : bâtons ou charbon de bois");
        // combat
        String[][] armorMetals = {{"silver", "Silver", "argent"}, {"ancient_metal", "Ancient Metal", "métal ancien"}, {"mithril", "Mithril", "mithril"}, {"adamantium", "Adamantium", "adamantium"}};
        String[][] armorPieces = {{"helmet", "Helmet", "Casque"}, {"chestplate", "Chestplate", "Plastron"}, {"leggings", "Leggings", "Jambières"}, {"boots", "Boots", "Bottes"}};
        for (String[] m : armorMetals) for (String[] piece : armorPieces) t("item.hardscrabble." + m[0] + "_" + piece[0], m[1] + " " + piece[1], piece[2] + " en " + m[2]);
        for (String[] piece : armorPieces) t("item.hardscrabble.rusted_iron_" + piece[0], "Rusted Iron " + piece[1], piece[2] + " en fer rouillé");
        String[][] chainMetals = {{"copper", "Copper", "cuivre"}, {"silver", "Silver", "argent"}, {"golden", "Golden", "or"}, {"ancient_metal", "Ancient Metal", "métal ancien"},
                {"mithril", "Mithril", "mithril"}, {"adamantium", "Adamantium", "adamantium"}, {"rusted_iron", "Rusted Iron", "fer rouillé"}};
        for (String[] m : chainMetals) {
            for (String[] piece : armorPieces) t("item.hardscrabble." + m[0] + "_chainmail_" + piece[0], m[1] + " Chainmail " + piece[1], piece[2] + " de mailles en " + m[2]);
            if (!m[0].equals("copper") && !m[0].equals("rusted_iron")) t("item.hardscrabble." + m[0] + "_chain", m[1] + " Chain", "Maille en " + m[2]);
        }
        String[][] arrowTips = {{"obsidian", "Obsidian", "obsidienne"}, {"copper", "Copper", "cuivre"}, {"silver", "Silver", "argent"}, {"golden", "Golden", "or"},
                {"rusted_iron", "Rusted Iron", "fer rouillé"}, {"iron", "Iron", "fer"}, {"mithril", "Mithril", "mithril"}, {"adamantium", "Adamantium", "adamantium"}};
        for (String[] m : arrowTips) t("item.hardscrabble." + m[0] + "_arrow", m[1] + " Arrow", "Flèche en " + m[2]);
        t("item.hardscrabble.ancient_metal_bow", "Ancient Metal Bow", "Arc en métal ancien");
        t("item.hardscrabble.mithril_bow", "Mithril Bow", "Arc en mithril");
        t("item.hardscrabble.rusted_iron_sword", "Rusted Iron Sword", "Épée en fer rouillé");
        t("item.hardscrabble.rusted_iron_dagger", "Rusted Iron Dagger", "Dague en fer rouillé");
        // worldgen
        t("block.hardscrabble.silver_ore", "Silver Ore", "Minerai d'argent");
        t("block.hardscrabble.deepslate_silver_ore", "Deepslate Silver Ore", "Minerai d'argent des abîmes");
        t("block.hardscrabble.mithril_ore", "Mithril Ore", "Minerai de mithril");
        t("block.hardscrabble.deepslate_mithril_ore", "Deepslate Mithril Ore", "Minerai de mithril des abîmes");
        t("block.hardscrabble.adamantium_ore", "Adamantium Ore", "Minerai d'adamantium");
        t("block.hardscrabble.deepslate_adamantium_ore", "Deepslate Adamantium Ore", "Minerai d'adamantium des abîmes");
        t("block.hardscrabble.silver_block", "Block of Silver", "Bloc d'argent");
        t("block.hardscrabble.ancient_metal_block", "Block of Ancient Metal", "Bloc de métal ancien");
        t("block.hardscrabble.mithril_block", "Block of Mithril", "Bloc de mithril");
        t("block.hardscrabble.adamantium_block", "Block of Adamantium", "Bloc d'adamantium");
        // enchanting
        t("block.hardscrabble.copper_anvil", "Copper Anvil", "Enclume en cuivre");
        t("block.hardscrabble.silver_anvil", "Silver Anvil", "Enclume en argent");
        t("block.hardscrabble.gold_anvil", "Gold Anvil", "Enclume en or");
        t("block.hardscrabble.ancient_metal_anvil", "Ancient Metal Anvil", "Enclume en métal ancien");
        t("block.hardscrabble.mithril_anvil", "Mithril Anvil", "Enclume en mithril");
        t("block.hardscrabble.adamantium_anvil", "Adamantium Anvil", "Enclume en adamantium");
        t("container.hardscrabble.anvil", "Repair", "Réparation");
        t("gui.hardscrabble.anvil.cost", "Cost: %s nuggets", "Coût : %s pépites");
        t("hardscrabble.anvil.too_hard", "This anvil is too soft to repair this item", "Cette enclume est trop tendre pour réparer cet objet");
        for (String[] m : new String[][] {{"copper", "Copper", "cuivre"}, {"silver", "Silver", "argent"}, {"gold", "Gold", "or"}, {"iron", "Iron", "fer"},
                {"ancient_metal", "Ancient Metal", "métal ancien"}, {"mithril", "Mithril", "mithril"}, {"adamantium", "Adamantium", "adamantium"}}) {
            t("block.hardscrabble." + m[0] + "_strongbox", m[1] + " Strongbox", "Coffre-fort en " + m[2]);
        }
        t("container.hardscrabble.strongbox.locked", "This strongbox belongs to someone else", "Ce coffre-fort appartient à quelqu'un d'autre");
        t("item.hardscrabble.disenchanting_bottle", "Disenchanting Bottle", "Bouteille de désenchantement");
        t("item.hardscrabble.enchanting_bottle", "Enchanting Bottle", "Bouteille d'enchantement");
        t("enchantment.hardscrabble.stunning", "Stunning", "Étourdissement");
        t("enchantment.hardscrabble.fishing_fortune", "Fishing Fortune", "Fortune de pêche");
        t("enchantment.hardscrabble.fertility", "Fertility", "Fertilité");
        t("enchantment.hardscrabble.tree_felling", "Tree Felling", "Abattage");
        t("enchantment.hardscrabble.vampiric", "Vampiric", "Vampirique");
        t("enchantment.hardscrabble.speed", "Speed", "Vitesse");
        t("enchantment.hardscrabble.regeneration", "Regeneration", "Régénération");
        t("enchantment.hardscrabble.free_action", "Free Action", "Liberté de mouvement");
        t("enchantment.hardscrabble.quickness", "Quickness", "Rapidité");
        t("enchantment.hardscrabble.true_flight", "True Flight", "Vol rectiligne");
        t("enchantment.hardscrabble.poison", "Poison", "Poison");
        t("enchantment.hardscrabble.disarming", "Disarming", "Désarmement");
        t("enchantment.hardscrabble.cleaving", "Cleaving", "Perforation");
        t("enchantment.hardscrabble.harvesting", "Harvesting", "Récolte");
        t("enchantment.hardscrabble.butchering", "Butchering", "Boucherie");
        t("enchantment.hardscrabble.endurance", "Endurance", "Endurance");
        t("enchantment.hardscrabble.arrow_recovery", "Arrow Recovery", "Récupération de flèches");
        // dimension
        t("block.hardscrabble.mantle", "Mantle", "Manteau");
        t("biome.hardscrabble.underworld", "Underworld", "Underworld");

        // mob
        t("entity.hardscrabble.ghoul", "Ghoul", "Goule");
        t("entity.hardscrabble.wight", "Wight", "Wight");
        t("entity.hardscrabble.revenant", "Revenant", "Revenant");
        t("entity.hardscrabble.wood_spider", "Wood Spider", "Araignée des bois");
        t("item.hardscrabble.ghoul_spawn_egg", "Ghoul Spawn Egg", "Œuf d'apparition de goule");
        t("item.hardscrabble.wight_spawn_egg", "Wight Spawn Egg", "Œuf d'apparition de wight");
        t("item.hardscrabble.revenant_spawn_egg", "Revenant Spawn Egg", "Œuf d'apparition de revenant");
        t("item.hardscrabble.wood_spider_spawn_egg", "Wood Spider Spawn Egg", "Œuf d'apparition d'araignée des bois");
        t("entity.hardscrabble.longdead", "Longdead", "Longdead");
        t("entity.hardscrabble.longdead_guardian", "Longdead Guardian", "Gardien longdead");
        t("item.hardscrabble.longdead_spawn_egg", "Longdead Spawn Egg", "Œuf d'apparition de longdead");
        t("item.hardscrabble.longdead_guardian_spawn_egg", "Longdead Guardian Spawn Egg", "Œuf d'apparition de gardien longdead");
        t("entity.hardscrabble.shadow", "Shadow", "Ombre");
        t("entity.hardscrabble.invisible_stalker", "Invisible Stalker", "Traqueur invisible");
        t("entity.hardscrabble.dire_wolf", "Dire Wolf", "Loup redoutable");
        t("entity.hardscrabble.hellhound", "Hellhound", "Chien de l'enfer");
        t("entity.hardscrabble.demon_spider", "Demon Spider", "Araignée démoniaque");
        t("entity.hardscrabble.thrown_web", "Thrown Web", "Toile lancée");
        t("item.hardscrabble.shadow_spawn_egg", "Shadow Spawn Egg", "Œuf d'apparition d'ombre");
        t("item.hardscrabble.invisible_stalker_spawn_egg", "Invisible Stalker Spawn Egg", "Œuf d'apparition de traqueur invisible");
        t("item.hardscrabble.dire_wolf_spawn_egg", "Dire Wolf Spawn Egg", "Œuf d'apparition de loup redoutable");
        t("item.hardscrabble.hellhound_spawn_egg", "Hellhound Spawn Egg", "Œuf d'apparition de chien de l'enfer");
        t("item.hardscrabble.demon_spider_spawn_egg", "Demon Spider Spawn Egg", "Œuf d'apparition d'araignée démoniaque");
        t("entity.hardscrabble.infernal_creeper", "Infernal Creeper", "Creeper infernal");
        t("entity.hardscrabble.fire_elemental", "Fire Elemental", "Élémentaire de feu");
        t("entity.hardscrabble.earth_elemental", "Earth Elemental", "Élémentaire de terre");
        t("entity.hardscrabble.bone_lord", "Bone Lord", "Seigneur des os");
        t("entity.hardscrabble.ancient_bone_lord", "Ancient Bone Lord", "Seigneur des os ancien");
        t("item.hardscrabble.infernal_creeper_spawn_egg", "Infernal Creeper Spawn Egg", "Œuf d'apparition de creeper infernal");
        t("item.hardscrabble.fire_elemental_spawn_egg", "Fire Elemental Spawn Egg", "Œuf d'apparition d'élémentaire de feu");
        t("item.hardscrabble.earth_elemental_spawn_egg", "Earth Elemental Spawn Egg", "Œuf d'apparition d'élémentaire de terre");
        t("item.hardscrabble.bone_lord_spawn_egg", "Bone Lord Spawn Egg", "Œuf d'apparition de seigneur des os");
        t("item.hardscrabble.ancient_bone_lord_spawn_egg", "Ancient Bone Lord Spawn Egg", "Œuf d'apparition de seigneur des os ancien");
        t("entity.hardscrabble.phase_spider", "Phase Spider", "Araignée de phase");
        t("item.hardscrabble.phase_spider_spawn_egg", "Phase Spider Spawn Egg", "Œuf d'apparition d'araignée de phase");
        t("entity.hardscrabble.black_widow", "Black Widow", "Veuve noire");
        t("item.hardscrabble.black_widow_spawn_egg", "Black Widow Spawn Egg", "Œuf d'apparition de veuve noire");
        t("entity.hardscrabble.clay_golem", "Clay Golem", "Golem d'argile");
        t("item.hardscrabble.clay_golem_spawn_egg", "Clay Golem Spawn Egg", "Œuf d'apparition de golem d'argile");
        t("entity.hardscrabble.jelly", "Jelly", "Gelée");
        t("item.hardscrabble.jelly_spawn_egg", "Jelly Spawn Egg", "Œuf d'apparition de gelée");
        t("entity.hardscrabble.blob", "Blob", "Blob");
        t("item.hardscrabble.blob_spawn_egg", "Blob Spawn Egg", "Œuf d'apparition de blob");
        t("entity.hardscrabble.ooze", "Ooze", "Vase");
        t("item.hardscrabble.ooze_spawn_egg", "Ooze Spawn Egg", "Œuf d'apparition de vase");
        t("entity.hardscrabble.pudding", "Pudding", "Pouding");
        t("item.hardscrabble.pudding_spawn_egg", "Pudding Spawn Egg", "Œuf d'apparition de pouding");
        t("entity.hardscrabble.vampire_bat", "Vampire Bat", "Chauve-souris vampire");
        t("item.hardscrabble.vampire_bat_spawn_egg", "Vampire Bat Spawn Egg", "Œuf d'apparition de chauve-souris vampire");
        t("entity.hardscrabble.giant_vampire_bat", "Giant Vampire Bat", "Chauve-souris vampire géante");
        t("item.hardscrabble.giant_vampire_bat_spawn_egg", "Giant Vampire Bat Spawn Egg", "Œuf d'apparition de chauve-souris vampire géante");
        t("entity.hardscrabble.nightwing", "Nightwing", "Aile-de-nuit");
        t("item.hardscrabble.nightwing_spawn_egg", "Nightwing Spawn Egg", "Œuf d'apparition d'aile-de-nuit");
        t("entity.hardscrabble.copperspine", "Copperspine", "Épine-de-cuivre");
        t("item.hardscrabble.copperspine_spawn_egg", "Copperspine Spawn Egg", "Œuf d'apparition d'épine-de-cuivre");
        t("entity.hardscrabble.netherspawn", "Netherspawn", "Rejeton du Nether");
        t("item.hardscrabble.netherspawn_spawn_egg", "Netherspawn Spawn Egg", "Œuf d'apparition de rejeton du Nether");
        t("entity.hardscrabble.hoary_silverfish", "Hoary Silverfish", "Poisson d'argent chenu");
        mobSubtitles();
        t("item.hardscrabble.hoary_silverfish_spawn_egg", "Hoary Silverfish Spawn Egg", "Œuf d'apparition de poisson d'argent chenu");
        t("item.hardscrabble.jelly_ball", "Jelly Ball", "Boule de gelée");
        t("item.hardscrabble.blob_ball", "Blob Ball", "Boule de blob");
        t("item.hardscrabble.ooze_ball", "Ooze Ball", "Boule de vase");
        t("item.hardscrabble.pudding_ball", "Pudding Ball", "Boule de pouding");
        // underworld
        t("item.hardscrabble.ancient_metal_arrow", "Ancient Metal Arrow", "Flèche en métal ancien");
        // livestock
        t("hardscrabble.livestock.unproductive", "This animal is too neglected to produce anything.", "Cet animal est trop négligé pour produire quoi que ce soit.");
        // world: villages, dungeons, Nether, runegates
        t("hardscrabble.command.villages.tool_missing", "Missing: a player must craft an iron pickaxe or a war hammer.", "Manquant : un joueur doit fabriquer une pioche en fer ou un marteau de guerre.");
        t("hardscrabble.command.villages.tool_done", "Done: an iron pickaxe or a war hammer has been crafted.", "Fait : une pioche en fer ou un marteau de guerre a été fabriqué.");
        t("hardscrabble.command.villages.days", "World age: day %s of %s required.", "Âge du monde : jour %s sur %s requis.");
        t("hardscrabble.command.villages.locked", "Villages are locked: new chunks generate without villages.", "Villages verrouillés : les nouveaux chunks se génèrent sans village.");
        t("hardscrabble.command.villages.unlocked", "Villages are unlocked: they generate in new chunks, without villagers.", "Villages débloqués : ils se génèrent dans les nouveaux chunks, sans villageois.");
        t("block.hardscrabble.core", "Core", "Noyau");
        t("block.hardscrabble.mithril_runestone", "Mithril Runestone", "Pierre runique en mithril");
        t("block.hardscrabble.adamantium_runestone", "Adamantium Runestone", "Pierre runique en adamantium");
        t("hardscrabble.runegate.carrying", "The runegate only lets through those who carry nothing.", "La runegate ne laisse passer que ceux qui ne portent rien.");
        t("hardscrabble.runestone.rune", "Rune: %s", "Rune : %s");
        String[] runes = {"Fehu", "Uruz", "Thurisaz", "Ansuz", "Raido", "Kaunan", "Gebo", "Wunjo", "Hagalaz", "Naudiz", "Isa", "Jera", "Eihwaz",
                "Perth", "Algiz", "Sowilo"}; // M34: Elder Futhark names [INFÉRÉ: MITE's names are unknown]
        for (int i = 0; i < runes.length; i++) t("hardscrabble.rune." + i, runes[i], runes[i]);
        // farming
        t("block.hardscrabble.onions", "Onions", "Oignons");
        t("block.hardscrabble.dead_wheat", "Dead Wheat", "Blé mort");
        t("block.hardscrabble.dead_carrots", "Dead Carrots", "Carottes mortes");
        t("block.hardscrabble.dead_potatoes", "Dead Potatoes", "Pommes de terre mortes");
        t("block.hardscrabble.dead_onions", "Dead Onions", "Oignons morts");
        t("item.hardscrabble.onion", "Onion", "Oignon");
        t("item.hardscrabble.orange", "Orange", "Orange");
        t("item.hardscrabble.banana", "Banana", "Banane");
        t("item.hardscrabble.manure", "Manure", "Fumier");
        t("item.hardscrabble.flour", "Flour", "Farine");
        t("item.hardscrabble.dough", "Dough", "Pâte");
        t("item.hardscrabble.cheese", "Cheese", "Fromage");
        t("item.hardscrabble.chocolate", "Chocolate", "Chocolat");
        t("item.hardscrabble.beef_stew", "Beef Stew", "Ragoût de bœuf");
        t("item.hardscrabble.chicken_soup", "Chicken Soup", "Soupe de poulet");
        t("item.hardscrabble.vegetable_soup", "Vegetable Soup", "Soupe de légumes");
        t("item.hardscrabble.cream_of_mushroom_soup", "Cream of Mushroom Soup", "Crème de champignons");
        t("item.hardscrabble.cream_of_vegetable_soup", "Cream of Vegetable Soup", "Crème de légumes");
        t("item.hardscrabble.pumpkin_soup", "Pumpkin Soup", "Soupe de citrouille");
        t("item.hardscrabble.mashed_potatoes", "Mashed Potatoes", "Purée");
        t("item.hardscrabble.ice_cream", "Ice Cream", "Glace");
        t("item.hardscrabble.sorbet", "Sorbet", "Sorbet");
        t("item.hardscrabble.porridge", "Porridge", "Porridge");
        t("item.hardscrabble.cereal", "Cereal", "Céréales");
        t("item.hardscrabble.salad", "Salad", "Salade");
        // environment (EF-CRA-09)
        t("hardscrabble.obstructed", "Something is in the way.", "Quelque chose l'obstrue.");
        // progression (EF-UI-07)
        for (String[] a : dev.mite.progression.MiteAdvancements.TEXT) {
            t("advancements.hardscrabble." + a[0] + ".title", a[1], a[3]);
            t("advancements.hardscrabble." + a[0] + ".description", a[2], a[4]);
        }
        t("advancements.story.shiny_gear.title", "Cover Me with Mithril", "Couvert de mithril"); // MiteAdvancements.Story
        t("advancements.story.shiny_gear.description", "Mithril armor saves lives", "Une armure en mithril sauve des vies");
        t("block.minecraft.crafting_table", "Flint Workbench", "Établi en silex"); // the flint tier of the workbenches (changelog l.355)
        t("tooltip.hardscrabble.evening_moon", "Tonight: %s", "Ce soir : %s"); // EF-UI-08
        for (String[] m : new String[][] {{"normal", "Normal Moon", "Lune ordinaire"}, {"blood", "Blood Moon", "Lune de sang"},
                {"blue", "Blue Moon", "Lune bleue"}, {"harvest", "Harvest Moon", "Lune des moissons"}, {"moon_dog", "Moon Dog", "Parasélène"}})
            t("moon.hardscrabble." + m[0], m[1], m[2]);
        // equipment (D16, D17, I14, H11)
        for (String[] e : dev.mite.equipment.EquipmentContent.LANG) t(e[0], e[1], e[2]);
        // curse (EF-MAL-01, EF-MAL-02, EF-UI-08)
        t("hardscrabble.curse.cursed", "You have been cursed!", "Vous avez été maudit !");
        t("hardscrabble.curse.revealed", "You discover your curse: %s", "Vous découvrez votre malédiction : %s");
        t("hardscrabble.curse.lifted", "Your curse has been lifted", "Votre malédiction a été levée");
        t("hardscrabble.curse.hud", "Curse: %s", "Malédiction : %s");
        t("hardscrabble.curse.cant_sleep.refused", "Your curse keeps you awake", "Votre malédiction vous empêche de dormir");
        t("hardscrabble.curse.equipment_decay", "Equipment Decay", "Usure de l'équipement");
        t("hardscrabble.curse.cant_hold_breath", "Shortness of Breath", "Apnée");
        t("hardscrabble.curse.cant_run", "Leaden Feet", "Pieds de plomb");
        t("hardscrabble.curse.cant_eat_animals", "Meat Aversion", "Dégoût de la viande");
        t("hardscrabble.curse.cant_eat_plants", "Plant Aversion", "Dégoût des végétaux");
        t("hardscrabble.curse.cant_drink", "Parched Throat", "Gorge nouée");
        t("hardscrabble.curse.endermen_enemy", "Enderman Enmity", "Haine des Endermen");
        t("hardscrabble.curse.clumsiness", "Clumsiness", "Maladresse");
        t("hardscrabble.curse.entanglement", "Entanglement", "Enchevêtrement");
        t("hardscrabble.curse.cant_wear_armor", "Armor Rejection", "Rejet de l'armure");
        t("hardscrabble.curse.cant_open_chests", "Sealed Chests", "Coffres scellés");
        t("hardscrabble.curse.cant_sleep", "Insomnia", "Insomnie");
        t("hardscrabble.curse.fear_of_spiders", "Fear of Spiders", "Peur des araignées");
        t("hardscrabble.curse.fear_of_wolves", "Fear of Wolves", "Peur des loups");
        t("hardscrabble.curse.fear_of_creepers", "Fear of Creepers", "Peur des creepers");
        t("hardscrabble.curse.fear_of_undead", "Fear of the Undead", "Peur des morts-vivants");
        // treasure (EF-CBT-12, EF-ENC-03, EF-ENC-07, EF-UI-09)
        t("item.hardscrabble.silver_horse_armor", "Silver Horse Armor", "Armure de cheval en argent");
        t("block.hardscrabble.emerald_enchanting_table", "Emerald Enchanting Table", "Table d'enchantement en émeraude");
        t("item.hardscrabble.copper_coin", "Copper Coin", "Pièce de cuivre");
        t("item.hardscrabble.silver_coin", "Silver Coin", "Pièce d'argent");
        t("item.hardscrabble.gold_coin", "Gold Coin", "Pièce d'or");
        t("item.hardscrabble.ancient_metal_coin", "Ancient Metal Coin", "Pièce de métal ancien");
        t("item.hardscrabble.mithril_coin", "Mithril Coin", "Pièce de mithril");
        t("item.hardscrabble.adamantium_coin", "Adamantium Coin", "Pièce d'adamantium");
        t("hardscrabble.coin.not_enough_xp", "Not enough XP to mint this coin", "Pas assez d'XP pour frapper cette pièce");
        t("hardscrabble.coin.disabled", "Coins are disabled on this world", "Les pièces sont désactivées sur ce monde");
        // save (ENF-SAV-02)
        t("warning.hardscrabble.foreign_world", "This world was played without Hardscrabble: not supported, players start from the default values.",
                "Ce monde a été joué sans Hardscrabble : cas non supporté, les joueurs repartent des valeurs par défaut.");
        String[][] books = dev.mite.treasure.LoreBooks.TEXT;
        for (int b = 0; b < books.length; b++) {
            t(dev.mite.treasure.LoreBooks.titleKey(b), books[b][0], books[b][1]);
            for (int p = 0; p < dev.mite.treasure.LoreBooks.pages(b); p++) t(dev.mite.treasure.LoreBooks.pageKey(b, p), books[b][2 + 2 * p], books[b][3 + 2 * p]);
        }
    }
}
