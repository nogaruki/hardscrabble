package dev.mite.e2e;

import static dev.mite.e2e.Showcase.Scene.gif;
import static dev.mite.e2e.Showcase.Scene.shot;
import static dev.mite.e2e.Showcase.run;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * README showcase, group "items": every item and block family of the mod. Items stand as fullbright item displays in tables
 * (one row per material) in front of a wall, blocks are set on a floor with their name floating above, armour sits on
 * armour stands. Everything is built at y 200 above the spawn; the camera is the survival player (strict mode refuses
 * Spectator) standing on an invisible barrier.
 */
final class ShowcaseItems {
    private ShowcaseItems() {}

    private static final String G = "items", HS = "hardscrabble:", MC = "minecraft:";
    private static final int Y = 200;          // floor top; the floor itself is at Y-1
    private static final double WALL_Z = -10;  // relative to the origin: the wall the item tables hang on
    private static final double TAN_V = Math.tan(Math.toRadians(35)), TAN_H = TAN_V * 16 / 9; // fov 70 at 16:9
    private static BlockPos o;                 // origin: the player's starting column
    private static final Set<String> SHOWN = new LinkedHashSet<>(); // every item placed somewhere (the rest goes to "misc")

    private static final String[] MATERIALS = {"wooden", "flint", "obsidian", "copper", "silver", "gold", "rusted_iron", "iron", "ancient_metal", "mithril", "adamantium"};
    private static final String[] MATERIAL_NAMES = {"Wood", "Flint", "Obsidian", "Copper", "Silver", "Gold", "Rusted iron", "Iron", "Ancient metal", "Mithril", "Adamantium"};
    private static final String[] METALS = {"copper", "silver", "gold", "iron", "ancient_metal", "mithril", "adamantium"};
    private static final String[] METAL_NAMES = {"Copper", "Silver", "Gold", "Iron", "Ancient metal", "Mithril", "Adamantium"};
    /** Vanilla items standing in for a material/column the mod does not register itself. */
    private static final Map<String, String> SPECIAL = Map.of("flint:arrow", MC + "arrow", "wooden:bow", MC + "bow", "iron:shears", MC + "shears",
            "iron:bucket", MC + "bucket", "iron:water_bucket", MC + "water_bucket", "iron:lava_bucket", MC + "lava_bucket",
            "iron:milk_bucket", MC + "milk_bucket", "iron:chain", MC + "iron_chain", "iron:ore", MC + "iron_ore", "gold:ore", MC + "gold_ore");

    private static boolean exists(String id) {
        return BuiltInRegistries.ITEM.containsKey(Identifier.parse(id));
    }

    /** The item for a material and a column (pickaxe, coin...), or null: mod first, then vanilla. */
    private static String find(String material, String column) {
        List<String> c = new ArrayList<>();
        if (SPECIAL.containsKey(material + ":" + column)) c.add(SPECIAL.get(material + ":" + column));
        List<String> prefixes = material.equals("gold") ? List.of("golden", "gold") : List.of(material);
        for (String p : prefixes) {
            String path = column.equals("raw") ? "raw_" + p : p + "_" + column;
            c.add(HS + path);
            if (!material.equals("wooden")) c.add(MC + path); // vanilla wooden tools are removed by the mod
        }
        for (String id : c) if (exists(id)) return id;
        return null;
    }

    private static String descriptionId(String id) {
        Identifier key = Identifier.parse(id);
        return BuiltInRegistries.BLOCK.containsKey(key) && BuiltInRegistries.ITEM.getValue(key) instanceof net.minecraft.world.item.BlockItem
                ? BuiltInRegistries.BLOCK.getValue(key).getDescriptionId() : BuiltInRegistries.ITEM.getValue(key).getDescriptionId();
    }

    // --- world helpers (coordinates relative to the origin) ---

    private static String p(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", o.getX() + x, y, o.getZ() + z);
    }

    private static String b(int x, int y, int z) {
        return String.format(Locale.ROOT, "%d %d %d", o.getX() + x, y, o.getZ() + z);
    }

    /** Common setup: noon, no weather or mobs, a clean volume, HUD hidden, first person. */
    private static void reset(String wall) {
        Minecraft mc = Minecraft.getInstance();
        if (o == null) o = mc.player.blockPosition();
        Showcase.hud(false);
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.gui.setScreen(null);
        for (String r : new String[] {"advance_time false", "advance_weather false", "spawn_mobs false", "send_command_feedback false",
                "show_advancement_messages false", "command_block_output false", "log_admin_commands false", "random_tick_speed 0"})
            run("gamerule " + r);
        run("time set 6000");
        run("weather clear");
        run("effect give @p minecraft:night_vision infinite 0 true");
        run("effect give @p minecraft:resistance infinite 255 true");
        run("effect give @p minecraft:saturation infinite 255 true");
        run("clear @p");
        for (int y = Y - 12; y < Y + 20; y += 8) run("fill " + b(-16, y, -20) + " " + b(16, Math.min(y + 7, Y + 19), 8) + " minecraft:air");
        run("kill @e[type=!player]"); // after the fill: broken doors and crops drop items
        if (wall != null) run("fill " + b(-16, Y - 12, (int) WALL_Z - 1) + " " + b(16, Y + 19, (int) WALL_Z - 1) + " " + wall);
    }

    /** Puts the player's feet at (x, feetY, z) on a barrier, looking (yaw, pitch). */
    private static void cam(double x, int feetY, double z, double yaw, double pitch) {
        run("setblock " + b((int) Math.floor(x), feetY - 1, (int) Math.floor(z)) + " minecraft:barrier");
        run(String.format(Locale.ROOT, "tp @p %s %.2f %.2f", p(x, feetY, z), yaw, pitch));
    }

    private static void item(String id, double x, double y, double z, double scale) {
        SHOWN.add(id);
        run(String.format(Locale.ROOT, "summon minecraft:item_display %s {item:{id:\"%s\",count:1},item_display:\"gui\",brightness:{sky:15,block:15},"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
                p(x, y, z), id, scale, scale, scale));
    }

    /** A text display; {@code text} is SNBT (a quoted string or a {translate:...} compound). */
    private static void text(String text, double x, double y, double z, double scale, int lineWidth, boolean billboard) {
        run(String.format(Locale.ROOT, "summon minecraft:text_display %s {text:%s,line_width:%d,billboard:\"%s\",shadow:1b,background:-1728053248,brightness:{sky:15,block:15},"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.2ff,%.2ff,%.2ff]}}",
                p(x, y, z), text, lineWidth, billboard ? "center" : "fixed", scale, scale, scale));
    }

    private static String quoted(String s) {
        return "\"" + s + "\"";
    }

    private static String translated(String id) {
        return "{translate:\"" + descriptionId(id) + "\"}";
    }

    /** Camera straight in front of a w x h area centred on x = cx, eye height eyeY, the area at depth z. */
    private static void frontCam(double cx, double w, double h, double z, int feetY) {
        double d = Math.max(w / (2 * TAN_H), h / (2 * TAN_V)) * 1.08;
        cam(cx, feetY, z + d, 180, 0);
    }

    // --- scene builders ---

    /** A table of items: one row per material (label at the left), one column per kind (header on top). */
    private static Runnable table(String[] codes, String[] names, String[] columns, String[] headers) {
        List<String> labels = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        for (int r = 0; r < codes.length; r++) {
            String[] row = new String[columns.length];
            boolean any = false;
            for (int c = 0; c < columns.length; c++) any |= (row[c] = find(codes[r], columns[c])) != null;
            if (any) {
                rows.add(row);
                labels.add(names[r]);
                for (String id : row) if (id != null) SHOWN.add(id);
            }
        }
        return () -> {
            reset("minecraft:light_gray_concrete");
            double cw = 1.6, lw = 3.0, w = lw + columns.length * cw, h = rows.size() + 0.9;
            int feetY = Y + 4;
            double eye = feetY + 1.62, x0 = 0.5 - w / 2, z = WALL_Z + 0.6, top = eye + h / 2 - 1.4;
            for (int c = 0; c < columns.length; c++)
                text(quoted(headers[c]), x0 + lw + (c + 0.5) * cw, top + 0.6, z, 0.95, 62, false);
            for (int r = 0; r < rows.size(); r++) {
                double y = top - r;
                text(quoted(labels.get(r)), x0 + lw / 2, y - 0.2, z, 1.25, 200, false);
                for (int c = 0; c < columns.length; c++)
                    if (rows.get(r)[c] != null) item(rows.get(r)[c], x0 + lw + (c + 0.5) * cw, y, z, 0.95);
            }
            frontCam(0.5, w + 0.6, h + 0.6, z, feetY);
        };
    }

    /** A grid of items, optionally named underneath. */
    private static Runnable grid(List<String> ids, int perRow, double scale, boolean names) {
        SHOWN.addAll(ids);
        return () -> {
            reset("minecraft:light_gray_concrete");
            double cw = names ? 2.0 : 1.3, ch = names ? 1.75 : 1.3;
            int rows = (ids.size() + perRow - 1) / perRow, feetY = Y + 4;
            double eye = feetY + 1.62, z = WALL_Z + 0.6, w = Math.min(perRow, ids.size()) * cw, h = rows * ch;
            double top = eye + h / 2 - ch / 2 + (names ? 0.25 : 0);
            for (int i = 0; i < ids.size(); i++) {
                int r = i / perRow, inRow = Math.min(perRow, ids.size() - r * perRow);
                double x = 0.5 - inRow * cw / 2 + (i % perRow + 0.5) * cw, y = top - r * ch;
                item(ids.get(i), x, y, z, scale);
                if (names) text(translated(ids.get(i)), x, y - scale * 0.5 - 0.45, z, 0.65, 95, false);
            }
            frontCam(0.5, w + 0.4, h + 0.4, z, feetY);
        };
    }

    /**
     * Blocks in rows on a floor, each named above (labels on). A state may hold two blocks separated by "|" (stacked: doors).
     * Rows go back from z = -6, three blocks apart; the camera looks down at 28 degrees.
     */
    private static Runnable blocks(List<String> states, int perRow, String floor, boolean labels, double spacing) {
        for (String s : states) for (String part : s.split("\\|")) {
            String id = part.replaceAll("\\[.*", "");
            if (exists(id)) SHOWN.add(id);
        }
        return () -> {
            reset(null);
            int rows = (states.size() + perRow - 1) / perRow;
            double w = (Math.min(perRow, states.size()) - 1) * spacing + 1;
            run("fill " + b(-16, Y - 1, -16) + " " + b(16, Y - 1, 2) + " " + floor);
            for (int i = 0; i < states.size(); i++) {
                int r = i / perRow, inRow = Math.min(perRow, states.size() - r * perRow);
                double fx = 0.5 - (inRow - 1) * spacing / 2 + (i % perRow) * spacing;
                int x = (int) Math.floor(fx), z = -6 - r * 3;
                String[] parts = states.get(i).split("\\|");
                String id = parts[0].replaceAll("\\[.*", "");
                int wide = id.endsWith("_bars") ? 2 : 1; // a pane of bars, not a lone post
                if (id.contains("onions") || id.contains("dead_")) run("setblock " + b(x, Y - 1, z) + " minecraft:farmland[moisture=7]");
                for (int k = 0; k < parts.length; k++) for (int dx = 0; dx < wide; dx++) run("setblock " + b(x + dx, Y + k, z) + " " + parts[k]);
                if (labels && BuiltInRegistries.BLOCK.containsKey(Identifier.parse(id)))
                    text("{translate:\"" + BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).getDescriptionId() + "\"}", x + 0.5, Y + parts.length + 0.12, z + 0.5, 0.8, 70, true);
            }
            run("kill @e[type=minecraft:item]");
            double cz = -6 - (rows - 1) * 1.5, cy = Y + 0.8;
            double dist = Math.max((w + 2) * 1.25 / (2 * TAN_H), 3.5 + rows), pitch = Math.toRadians(28);
            int feetY = (int) Math.round(cy + dist * Math.sin(pitch) - 1.62);
            double horiz = dist * Math.cos(pitch), realPitch = Math.toDegrees(Math.atan2(feetY + 1.62 - cy, horiz));
            cam(0.5, feetY, cz + 0.5 + horiz, 180, realPitch);
        };
    }

    private static List<String> prefixed(String ns, String... paths) {
        List<String> l = new ArrayList<>();
        for (String s : paths) l.add(ns + s);
        return l;
    }

    /** Armour stands in a row, one per set, the material named above. */
    private static Runnable stands(String[][] sets) {
        for (String[] s : sets) for (int k = 1; k < 5; k++) SHOWN.add(s[k]);
        return () -> {
            reset(null);
            run("fill " + b(-16, Y - 1, -16) + " " + b(16, Y - 1, 2) + " minecraft:polished_andesite");
            run("fill " + b(-16, Y, -9) + " " + b(16, Y + 6, -9) + " minecraft:spruce_planks");
            double sp = 1.6, w = (sets.length - 1) * sp + 1;
            for (int i = 0; i < sets.length; i++) {
                double x = 0.5 - (sets.length - 1) * sp / 2 + i * sp;
                String[] s = sets[i];
                run(String.format(Locale.ROOT, "summon minecraft:armor_stand %s {NoGravity:1b,ShowArms:1b,Rotation:[0f,0f],equipment:{head:{id:\"%s\"},chest:{id:\"%s\"},legs:{id:\"%s\"},feet:{id:\"%s\"}}}",
                        p(x, Y, -5.5), s[1], s[2], s[3], s[4]));
                text(quoted(s[0]), x, Y + 2.1, -5.5, 0.7, 60, false);
            }
            double d = (w + 1) / (2 * TAN_H);
            cam(0.5, Y + 1, -5.5 + d, 180, Math.toDegrees(Math.atan2(0.3, d)));
        };
    }

    private static String[] set(String name, String prefix) {
        return new String[] {name, prefix + "helmet", prefix + "chestplate", prefix + "leggings", prefix + "boots"};
    }

    static {
        String[] toolCols = {"pickaxe", "shovel", "axe", "hatchet", "hoe", "mattock", "scythe", "shears", "fishing_rod"};
        String[] toolHeads = {"Pickaxe", "Shovel", "Axe", "Hatchet", "Hoe", "Mattock", "Scythe", "Shears", "Fishing rod"};
        String[] weaponCols = {"sword", "dagger", "knife", "club", "cudgel", "battle_axe", "war_hammer", "bow", "arrow"};
        String[] weaponHeads = {"Sword", "Dagger", "Knife", "Club", "Cudgel", "Battle axe", "War hammer", "Bow", "Arrow"};
        Showcase.add(G, shot("tools", 240, table(MATERIALS, MATERIAL_NAMES, toolCols, toolHeads)));
        Showcase.add(G, shot("weapons", 100, table(MATERIALS, MATERIAL_NAMES, weaponCols, weaponHeads)));

        Showcase.add(G, shot("armour_sets", 100, stands(new String[][] {set("Leather", MC + "leather_"), set("Copper", MC + "copper_"),
                set("Silver", HS + "silver_"), set("Gold", MC + "golden_"), set("Rusted iron", HS + "rusted_iron_"), set("Iron", MC + "iron_"),
                set("Ancient metal", HS + "ancient_metal_"), set("Mithril", HS + "mithril_"), set("Adamantium", HS + "adamantium_")})));
        Showcase.add(G, shot("chainmail_sets", 100, stands(new String[][] {set("Copper", HS + "copper_chainmail_"), set("Silver", HS + "silver_chainmail_"),
                set("Gold", HS + "golden_chainmail_"), set("Rusted iron", HS + "rusted_iron_chainmail_"), set("Iron", MC + "chainmail_"),
                set("Ancient metal", HS + "ancient_metal_chainmail_"), set("Mithril", HS + "mithril_chainmail_"), set("Adamantium", HS + "adamantium_chainmail_")})));
        Showcase.add(G, shot("armour_worn", 100, () -> { // third person, facing the camera, a forge behind
            reset(null);
            run("fill " + b(-16, Y - 1, -16) + " " + b(16, Y - 1, 6) + " minecraft:polished_andesite");
            run("fill " + b(-16, Y, -6) + " " + b(16, Y + 6, -6) + " minecraft:spruce_planks");
            run("setblock " + b(-2, Y, -4) + " hardscrabble:mithril_workbench");
            run("setblock " + b(2, Y, -4) + " hardscrabble:mithril_anvil[facing=north]");
            run("setblock " + b(3, Y, -4) + " hardscrabble:mithril_strongbox");
            run("setblock " + b(-3, Y, -4) + " hardscrabble:obsidian_furnace[facing=south,lit=true]");
            run("item replace entity @p armor.head with hardscrabble:mithril_helmet");
            run("item replace entity @p armor.chest with hardscrabble:mithril_chestplate");
            run("item replace entity @p armor.legs with hardscrabble:mithril_leggings");
            run("item replace entity @p armor.feet with hardscrabble:mithril_boots");
            run("item replace entity @p weapon.mainhand with hardscrabble:mithril_battle_axe");
            run("item replace entity @p weapon.offhand with hardscrabble:mithril_dagger");
            cam(0.5, Y, -1.5, 0, 12);
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }));

        Showcase.add(G, shot("workbenches", 100, blocks(prefixed("", MC + "crafting_table", HS + "copper_workbench", HS + "silver_workbench",
                HS + "gold_workbench", HS + "iron_workbench", HS + "obsidian_workbench", HS + "ancient_metal_workbench", HS + "mithril_workbench",
                HS + "adamantium_workbench"), 9, "minecraft:spruce_planks", true, 2)));
        Showcase.add(G, shot("furnaces", 100, blocks(List.of(HS + "clay_furnace[facing=south,lit=true]", HS + "large_clay_furnace[facing=south,lit=true]",
                HS + "sandstone_furnace[facing=south,lit=true]", HS + "obsidian_furnace[facing=south,lit=true]", HS + "netherrack_furnace[facing=south,lit=true]"), 5, "minecraft:stone_bricks", true, 2)));
        Showcase.add(G, shot("anvils", 100, blocks(List.of(HS + "copper_anvil[facing=north]", HS + "silver_anvil[facing=north]", HS + "gold_anvil[facing=north]",
                MC + "anvil[facing=north]", HS + "ancient_metal_anvil[facing=north]", HS + "mithril_anvil[facing=north]", HS + "adamantium_anvil[facing=north]"),
                7, "minecraft:stone_bricks", true, 2)));
        Showcase.add(G, shot("strongboxes", 100, blocks(prefixed(HS, "copper_strongbox", "silver_strongbox", "gold_strongbox", "iron_strongbox",
                "ancient_metal_strongbox", "mithril_strongbox", "adamantium_strongbox"), 7, "minecraft:spruce_planks", true, 2)));
        Showcase.add(G, shot("metal_blocks", 100, blocks(List.of(MC + "copper_block", HS + "silver_block", MC + "gold_block", MC + "iron_block",
                HS + "ancient_metal_block", HS + "mithril_block", HS + "adamantium_block"), 7, "minecraft:polished_andesite", true, 2)));
        List<String> doors = new ArrayList<>();
        for (String m : new String[] {"iron", "silver", "gold", "ancient_metal", "mithril", "adamantium"}) {
            String ns = m.equals("iron") ? MC : HS;
            doors.add(ns + m + "_door[facing=south,half=lower]|" + ns + m + "_door[facing=south,half=upper]");
        }
        for (String m : new String[] {"iron", "silver", "gold", "ancient_metal", "mithril", "adamantium"})
            doors.add((m.equals("iron") ? MC : HS) + m + "_bars|" + (m.equals("iron") ? MC : HS) + m + "_bars");
        Showcase.add(G, shot("doors_bars", 100, blocks(doors, 6, "minecraft:polished_andesite", true, 3)));
        Showcase.add(G, shot("ores", 100, blocks(prefixed(HS, "silver_ore", "mithril_ore", "adamantium_ore", "deepslate_silver_ore",
                "deepslate_mithril_ore", "deepslate_adamantium_ore"), 3, "minecraft:stone", true, 2.5)));
        Showcase.add(G, shot("runestones", 100, blocks(prefixed(HS, "mithril_runestone", "emerald_enchanting_table", "adamantium_runestone"),
                3, "minecraft:polished_deepslate", true, 2.5)));
        Showcase.add(G, shot("mantle_core", 100, blocks(prefixed(HS, "mantle", "core"), 2, "minecraft:blackstone", true, 3)));
        List<String> crops = new ArrayList<>();
        for (int a = 0; a < 8; a++) crops.add(HS + "onions[age=" + a + "]");
        crops.addAll(prefixed(HS, "dead_wheat", "dead_carrots", "dead_potatoes", "dead_onions"));
        Showcase.add(G, shot("crops", 100, blocks(crops, 8, "minecraft:moss_block", false, 1)));

        Showcase.add(G, shot("metals", 100, table(METALS, METAL_NAMES, new String[] {"ore", "raw", "nugget", "ingot", "coin", "chain", "block"},
                new String[] {"Ore", "Raw", "Nugget", "Ingot", "Coin", "Chain", "Block"})));
        Showcase.add(G, shot("buckets", 100, table(METALS, METAL_NAMES, new String[] {"bucket", "water_bucket", "lava_bucket", "milk_bucket", "stone_bucket"},
                new String[] {"Empty", "Water", "Lava", "Milk", "Stone"})));
        Showcase.add(G, shot("coins", 100, grid(prefixed(HS, "copper_coin", "silver_coin", "gold_coin", "ancient_metal_coin", "mithril_coin", "adamantium_coin"),
                6, 1.3, true)));
        Showcase.add(G, shot("shards", 100, grid(prefixed(HS, "flint_shard", "obsidian_shard", "glass_shard", "quartz_shard", "emerald_shard", "diamond_shard"),
                6, 1.3, true)));
        Showcase.add(G, shot("food", 100, grid(prefixed(HS, "onion", "banana", "orange", "cheese", "chocolate", "flour", "dough", "worm", "cooked_worm",
                "water_bowl", "milk_bowl", "cereal", "porridge", "salad", "mashed_potatoes", "beef_stew", "chicken_soup", "vegetable_soup",
                "cream_of_vegetable_soup", "cream_of_mushroom_soup", "pumpkin_soup", "ice_cream", "sorbet"), 6, 1.0, true)));
        List<String> misc = new ArrayList<>(); // everything of the mod not shown yet, spawn eggs excepted (the "mobs" group)
        for (Identifier id : BuiltInRegistries.ITEM.keySet())
            if (id.getNamespace().equals("hardscrabble") && !id.getPath().endsWith("_spawn_egg") && !SHOWN.contains(id.toString())) misc.add(id.toString());
        misc.sort(null);
        Showcase.add(G, shot("misc", 100, grid(misc, Math.max(4, (int) Math.ceil(Math.sqrt(misc.size() * 1.6))), 1.0, true)));

        // spin: an adamantium-clad armour stand turning on its pedestal inside the ring of the nine workbenches. The camera stays
        // still so that only the stand changes between frames (a small GIF).
        int[] frame = {0};
        Showcase.add(G, gif("spin", 120, () -> {
            reset(null);
            run("fill " + b(-30, Y - 1, -36) + " " + b(30, Y - 1, 24) + " minecraft:light_gray_concrete"); // flat colours keep the GIF small
            Minecraft.getInstance().options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            String[] benches = {MC + "crafting_table", HS + "copper_workbench", HS + "silver_workbench", HS + "gold_workbench", HS + "iron_workbench",
                    HS + "obsidian_workbench", HS + "ancient_metal_workbench", HS + "mithril_workbench", HS + "adamantium_workbench"};
            for (int i = 0; i < benches.length; i++) {
                double a = Math.PI * i / 8; // a half ring behind the stand, from the left to the right
                run("setblock " + b((int) Math.floor(0.5 - 3.6 * Math.cos(a)), Y, (int) Math.floor(-6.5 - 3.6 * Math.sin(a))) + " " + benches[i]);
            }
            run("setblock " + b(0, Y, -7) + " hardscrabble:adamantium_block");
            run("summon minecraft:armor_stand " + p(0.5, Y + 1, -6.5) + " {NoGravity:1b,ShowArms:1b,Rotation:[0f,0f],equipment:{head:{id:\"hardscrabble:adamantium_helmet\"},"
                    + "chest:{id:\"hardscrabble:adamantium_chestplate\"},legs:{id:\"hardscrabble:adamantium_leggings\"},feet:{id:\"hardscrabble:adamantium_boots\"},"
                    + "mainhand:{id:\"hardscrabble:adamantium_war_hammer\"},offhand:{id:\"hardscrabble:adamantium_dagger\"}}}");
            cam(0.5, Y + 3, -0.5, 180, Math.toDegrees(Math.atan2(Y + 4.62 - (Y + 1.4), 6)));
            frame[0] = 0;
        }, 36, 3, () -> run(String.format(Locale.ROOT, "tp @e[type=minecraft:armor_stand,limit=1] %s %d 0", p(0.5, Y + 1, -6.5), frame[0]++ * 10))));
    }
}
