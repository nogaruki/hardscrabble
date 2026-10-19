package dev.mite.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.mite.player.XpCurve;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config (per world, synced). Defaults reproduce R196 (docs/02 §8.1). One push/pop section per domain.
 * Every key carries an EN and a FR comment line; its translation key is the NeoForge default {@code hardscrabble.configuration.<key>}
 * (EF-CFG-02 CA-1).
 */
public final class MiteConfig {
    private MiteConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    /** The configured value, or its default before the config is loaded (worldgen, early server start). */
    public static int intOrDefault(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.getAsInt() : value.getDefault();
    }

    /** Every value of the given specs, in declaration order (datagen and GameTests). */
    public static List<ModConfigSpec.ConfigValue<?>> values(ModConfigSpec... specs) {
        List<ModConfigSpec.ConfigValue<?>> out = new ArrayList<>();
        for (ModConfigSpec spec : specs) collect(spec.getValues(), out);
        return out;
    }

    private static void collect(UnmodifiableConfig config, List<ModConfigSpec.ConfigValue<?>> out) {
        for (UnmodifiableConfig.Entry e : config.entrySet()) {
            Object v = e.getValue();
            if (v instanceof ModConfigSpec.ConfigValue<?> value) out.add(value);
            else if (v instanceof UnmodifiableConfig inner) collect(inner, out);
        }
    }

    static {
        B.push("rules");
    }
    public static final ModConfigSpec.BooleanValue LOCK_DIFFICULTY = B.comment(
            "EF-REG-01: force Hard difficulty and forbid changing it (commands, functions, menu).",
            "EF-REG-01 : force la difficulté Difficile et interdit de la changer (commandes, fonctions, menu).")
            .define("lock_difficulty", true);
    public static final ModConfigSpec.BooleanValue STRICT_MODE = B.comment(
            "EF-REG-02: no Creative or Spectator mode, keepInventory and the locator bar forced off; /gamemode and every operator command (permission >= 2) are refused for players, ops included. The console stays free.",
            "EF-REG-02 : pas de mode Créatif ni Spectateur, keepInventory et la barre de localisation forcés à false ; /gamemode et toute commande d'opérateur (permission >= 2) sont refusés aux joueurs, opérateurs compris. La console reste libre.")
            .define("strict_mode", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ALLOWED_OPS = B.comment(
            "ET-A-02: player names or UUIDs exempt from strict_mode (Creative and operator commands allowed, within their permission level).",
            "ET-A-02 : noms ou UUID de joueurs exemptés de strict_mode (Créatif et commandes d'opérateur permis, selon leur niveau de permission).")
            .defineListAllowEmpty("allowed_ops", List.of(), () -> "", o -> o instanceof String);
    public static final ModConfigSpec.BooleanValue DEV_PROFILE = B.comment(
            "Developer profile: bypasses strict_mode and enables /mitedev for operators (EF-REG-05). Never enable on a real world.",
            "Profil développeur : lève strict_mode et active /mitedev pour les opérateurs (EF-REG-05). Jamais sur un vrai monde.")
            .define("dev_profile", false);
    public static final ModConfigSpec.EnumValue<Profile> PROFILE = B.comment(
            "EF-CFG-03: rule profile applied at each world load over the keys it lists. CUSTOM = the keys as written (their defaults are the STRICT_R196 profile).",
            "EF-CFG-03 : profil de règles appliqué à chaque chargement du monde sur les clés qu'il liste. CUSTOM = les clés telles qu'écrites (leurs défauts sont le profil STRICT_R196).")
            .defineEnum("profile", Profile.CUSTOM);
    public static final ModConfigSpec.IntValue RESPAWN_TIMER_SECONDS = B.comment(
            "EF-JOU-06: a dead player respawns automatically after this many seconds.",
            "EF-JOU-06 : un joueur mort réapparaît automatiquement après ce nombre de secondes.")
            .defineInRange("respawn_timer_seconds", 120, 1, 3600);
    static {
        B.pop();
    }

    static {
        B.push("metabolism");
    }
    public static final ModConfigSpec.DoubleValue HUNGER_GLOBAL_MULTIPLIER = B.comment(
            "Multiplier on action hunger costs (EF-NUT-01 R118, EF-NUT-02). The resting rate stays 1 unit / 2000 ticks.",
            "Multiplicateur des coûts de faim des actions (EF-NUT-01 R118, EF-NUT-02). Le rythme au repos reste 1 unité / 2000 ticks.")
            .defineInRange("hunger_global_multiplier", 0.75, 0.0, 10.0);
    public static final ModConfigSpec.BooleanValue SLEEP_MIN_HOUR = B.comment(
            "Forbid going to bed before 21:00 (tick 15000) and daytime naps (EF-NUT-11, Q-21).",
            "Interdit de se coucher avant 21 h (tick 15000) et les siestes de jour (EF-NUT-11, Q-21).")
            .define("sleep_min_hour", false);
    static {
        B.pop();
    }

    static {
        B.push("player");
    }
    public static final ModConfigSpec.EnumValue<XpCurve.DeathPenalty> DEATH_PENALTY = B.comment(
            "Death XP rule (EF-JOU-05): R196_CODE (0 XP from 30 points, else -30, no orb), WIKI_THIRD (a third dropped as orbs, -1 level at level <= 0), MCMOD_LEVEL (-1 level, no orb).",
            "Règle d'XP à la mort (EF-JOU-05) : R196_CODE (0 XP dès 30 points, sinon -30, sans orbe), WIKI_THIRD (un tiers lâché en orbes, -1 niveau au niveau <= 0), MCMOD_LEVEL (-1 niveau, sans orbe).")
            .defineEnum("death_penalty", XpCurve.DeathPenalty.R196_CODE);
    static {
        B.pop();
    }

    static {
        B.push("lunar");
    }
    public static final ModConfigSpec.IntValue BLOOD_MOON_PERIOD = B.comment(
            "Days between blood moons (EF-LUN-01). The harvest moon falls 8 days before.",
            "Jours entre deux lunes de sang (EF-LUN-01). La lune des moissons tombe 8 jours avant.")
            .defineInRange("blood_moon_period", 32, 1, 100000);
    public static final ModConfigSpec.IntValue BLUE_MOON_PERIOD = B.comment(
            "Days between blue moons (EF-LUN-01), which replace the blood moon. The moon dog falls 8 days before.",
            "Jours entre deux lunes bleues (EF-LUN-01), qui remplacent la lune de sang. Le chien de lune tombe 8 jours avant.")
            .defineInRange("blue_moon_period", 128, 1, 100000);
    static {
        B.pop();
    }

    static {
        B.push("curse");
    }
    public static final ModConfigSpec.BooleanValue CURSES_ENABLED = B.comment(
            "EF-MAL-01: witches curse players. When false, no new curse is cast and existing ones have no effect.",
            "EF-MAL-01 : les sorcières maudissent les joueurs. À false, aucune nouvelle malédiction et les existantes sont sans effet.")
            .define("curses_enabled", true);
    static {
        B.pop();
    }

    static {
        B.push("world");
    }
    public static final ModConfigSpec.IntValue TEMPLE_MIN_DISTANCE = B.comment(
            "Minimum distance in blocks from the world origin for desert/jungle temples, witch huts and igloos (EF-MON-03).",
            "Distance minimale en blocs depuis l'origine du monde pour les temples, cabanes de sorcière et igloos (EF-MON-03).")
            .defineInRange("temple_min_distance", 2000, 0, 1000000);
    public static final ModConfigSpec.IntValue STRONGHOLD_DISTANCE_MULTIPLIER = B.comment(
            "Stronghold ring distance multiplier relative to 1.6.4 (EF-MON-03). Read when the world loads.",
            "Multiplicateur de la distance des forteresses par rapport à la 1.6.4 (EF-MON-03). Lu au chargement du monde.")
            .defineInRange("stronghold_distance_multiplier", 16, 1, 64);
    static {
        B.pop();
    }

    static {
        B.push("combat");
    }
    public static final ModConfigSpec.DoubleValue SILVER_UNDEAD_BONUS = B.comment(
            "Damage bonus of silver weapons against undead and immaterial mobs (EF-CBT-06): 0.25 = +25 %.",
            "Bonus de dégâts des armes en argent contre les morts-vivants et les immatériels (EF-CBT-06) : 0.25 = +25 %.")
            .defineInRange("silver_undead_bonus", 0.25, 0.0, 10.0);
    static {
        B.pop();
    }

    static {
        B.push("crafting");
    }
    public static final ModConfigSpec.BooleanValue QUALITY_ENABLED = B.comment(
            "Crafting quality (EF-CRA-05): crafted tools and armor get a quality the player chooses and pays for in XP.",
            "Qualité de fabrication (EF-CRA-05) : outils et armures fabriqués reçoivent une qualité choisie et payée en XP.")
            .define("quality_enabled", true);
    public static final ModConfigSpec.BooleanValue TOOL_LASHING = B.comment(
            "Flint and obsidian tool recipes need string or sinew (EF-OUT-12).",
            "Les recettes d'outils en silex et en obsidienne demandent de la ficelle ou du tendon (EF-OUT-12).")
            .define("tool_lashing", true);
    public static final ModConfigSpec.BooleanValue CRAFTER_RESTRICTION = B.comment(
            "The crafter only runs metal-free recipes, with the MITE craft time at level 0 (EF-CRA-08).",
            "Le crafter n'exécute que les recettes sans métal, avec le temps de craft MITE du niveau 0 (EF-CRA-08).")
            .define("crafter_restriction", true);
    static {
        B.pop();
    }

    static {
        B.push("enchanting");
    }
    public static final ModConfigSpec.BooleanValue STRONGBOX_OP_ACCESS = B.comment(
            "Operators can open every strongbox (EF-ENC-08, EF-MUL-06).",
            "Les opérateurs peuvent ouvrir tous les coffres-forts (EF-ENC-08, EF-MUL-06).")
            .define("strongbox_op_access", false);
    public static final ModConfigSpec.BooleanValue COINS_ENABLED = B.comment(
            "Coins can be minted and spent for XP (EF-ENC-07).",
            "Les pièces peuvent être frappées et dépensées contre de l'XP (EF-ENC-07).")
            .define("coins_enabled", true);
    static {
        B.pop();
    }

    static {
        B.push("structures");
    }
    public static final ModConfigSpec.BooleanValue VILLAGE_LOCK = B.comment(
            "EF-MON-05: villages generate in new chunks only after a player crafted an iron pickaxe or a war hammer (R165).",
            "EF-MON-05 : les villages n'apparaissent dans les nouveaux chunks qu'après la fabrication d'une pioche en fer ou d'un marteau de guerre (R165).")
            .define("village_lock", true);
    public static final ModConfigSpec.IntValue VILLAGE_LOCK_MIN_DAYS = B.comment(
            "EF-MON-05: extra condition, the world must have reached this day (60 = rule before R165, 0 = off).",
            "EF-MON-05 : condition supplémentaire, le monde doit avoir atteint ce jour (60 = règle d'avant R165, 0 = désactivé).")
            .defineInRange("village_lock_min_days", 0, 0, 100000);
    public static final ModConfigSpec.IntValue RUNEGATE_MITHRIL_RANGE = B.comment(
            "EF-DIM-06: radius in blocks around the world origin reached by a mithril runegate; adamantium reaches 8x, at least 20 km away [Q-26, INFERRED].",
            "EF-DIM-06 : rayon en blocs autour de l'origine atteint par une runegate en mithril ; l'adamantium va 8 fois plus loin, à au moins 20 km [Q-26, INFÉRÉ].")
            .defineInRange("runegate_mithril_range", 5000, 100, 1000000);
    static {
        B.pop();
    }

    static {
        B.push("mobs");
    }
    public static final ModConfigSpec.IntValue CREEPER_EXPLOSION_RADIUS = B.comment(
            "Creeper explosion radius, doubled for the Infernal Creeper (EF-IA-09, EF-MOB-13). Vanilla 3 until Q-25 confirms the MITE scale.",
            "Rayon d'explosion du creeper, doublé pour le Creeper infernal (EF-IA-09, EF-MOB-13). 3 comme en vanilla tant que Q-25 n'a pas confirmé l'échelle MITE.")
            .defineInRange("creeper_explosion_radius", 3, 1, 16);
    public static final ModConfigSpec.BooleanValue ZOMBIE_DIGGING = B.comment(
            "EF-IA-01: zombies and the other digging mobs dig through blocks towards their target. false saves server time (ENF-PERF-08).",
            "EF-IA-01 : les zombies et les autres mobs creuseurs creusent vers leur cible. false économise du temps serveur (ENF-PERF-08).")
            .define("zombie_digging", true);
    public static final ModConfigSpec.BooleanValue GELATINOUS_DISSOLVE_ITEMS = B.comment(
            "Gelatinous cubes dissolve the dropped items they touch (EF-MOB-16, US-J-16).",
            "Les cubes gélatineux dissolvent les objets au sol qu'ils touchent (EF-MOB-16, US-J-16).")
            .define("gelatinous_dissolve_items", true);
    static {
        B.pop();
    }

    static {
        B.push("farming");
    }
    public static final ModConfigSpec.IntValue ITEM_LIFESPAN_TICKS = B.comment(
            "EF-AGR-11: lifetime of dropped items in ticks (vanilla 6000).",
            "EF-AGR-11 : durée de vie des objets au sol en ticks (6000 en vanilla).")
            .defineInRange("item_lifespan_ticks", 24000, 1, Short.MAX_VALUE - 1);
    public static final ModConfigSpec.BooleanValue DEDICATED_BLIGHT = B.comment(
            "EF-AGR-06 / EF-MUL-05: blood moons make blight more frequent on a dedicated server too.",
            "EF-AGR-06 / EF-MUL-05 : les lunes de sang rendent le mildiou plus fréquent aussi sur serveur dédié.")
            .define("dedicated_blight", false);
    public static final ModConfigSpec.BooleanValue LIVESTOCK_NEEDS = B.comment(
            "EF-AGR-08: livestock needs food, water and sunlight, falls sick and stops producing when neglected. false = always satisfied (ENF-PERF-08).",
            "EF-AGR-08 : le bétail a besoin de nourriture, d'eau et de soleil, tombe malade et ne produit plus s'il est négligé. false = toujours satisfait (ENF-PERF-08).")
            .define("livestock_needs", true);
    public static final ModConfigSpec.BooleanValue CHUNK_FAST_FORWARD = B.comment(
            "EF-AGR-12 / EF-MUL-05: crops of a reloaded chunk catch up the time it was unloaded (up to 768 000 ticks).",
            "EF-AGR-12 / EF-MUL-05 : les cultures d'un chunk rechargé rattrapent le temps où il était déchargé (jusqu'à 768 000 ticks).")
            .define("chunk_fast_forward", false);
    static {
        B.pop();
    }

    static {
        B.push("equipment");
    }
    public static final ModConfigSpec.IntValue FLUID_SOURCE_XP_COST = B.comment(
            "EF-JOU-09: XP paid to pour a water or lava source (sneak + use). Without paying the fluid flows and dries up. 0 = vanilla buckets.",
            "EF-JOU-09 : XP payée pour poser une source d'eau ou de lave (accroupi + utiliser). Sans payer, le fluide coule puis s'assèche. 0 = seaux vanilla.")
            .defineInRange("fluid_source_xp_cost", 100, 0, 100000);
    public static final ModConfigSpec.BooleanValue ELYTRA_FIREWORK_BOOST = B.comment(
            "EF-DIM-07 (§4.3 n°7): firework rockets boost a gliding elytra. When false, rockets cannot be fired while gliding.",
            "EF-DIM-07 (§4.3 n°7) : les fusées propulsent l'élytre en vol plané. À false, impossible de tirer une fusée en vol plané.")
            .define("elytra_firework_boost", true);
    static {
        B.pop();
    }

    static {
        B.push("trading");
    }
    public static final ModConfigSpec.BooleanValue WANDERING_TRADER = B.comment(
            "PD-06: wandering traders spawn, and only once villages are unlocked (R165). false = never.",
            "PD-06 : les marchands ambulants apparaissent, seulement une fois les villages débloqués (R165). false = jamais.")
            .define("wandering_trader", true);
    public static final ModConfigSpec.IntValue EMERALD_SACRIFICES_PER_DAY = B.comment(
            "RE-04 / audit 2 X1: emeralds a player can sacrifice for XP per day (250 XP each), so trading cannot exceed about 750 XP a day [INFERRED].",
            "RE-04 / audit 2 X1 : émeraudes qu'un joueur peut sacrifier contre de l'XP par jour (250 XP chacune), pour que le commerce ne dépasse pas environ 750 XP par jour [INFÉRÉ].")
            .defineInRange("emerald_sacrifices_per_day", 3, 0, 1000);
    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    /**
     * EF-CFG-03 rule profiles: Strict R196 (the defaults), Modern (locks off, mcmod death penalty; the contextual hints are a
     * client key, on by default) and Server (strict, operators open strongboxes, no blood moon blight boost).
     */
    public enum Profile {
        CUSTOM, STRICT_R196, MODERN, SERVER;

        /** Overrides the profile's keys in memory (the file is not rewritten). CUSTOM changes nothing. */
        public void apply() {
            if (this == CUSTOM) return;
            boolean modern = this == MODERN;
            STRICT_MODE.set(!modern);
            LOCK_DIFFICULTY.set(!modern);
            DEATH_PENALTY.set(modern ? XpCurve.DeathPenalty.MCMOD_LEVEL : XpCurve.DeathPenalty.R196_CODE);
            STRONGBOX_OP_ACCESS.set(this == SERVER);
            DEDICATED_BLIGHT.set(false);
        }
    }
}
