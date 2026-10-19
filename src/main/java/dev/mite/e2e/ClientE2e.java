package dev.mite.e2e;

import dev.mite.MiteMod;
import dev.mite.entity.mob.MobContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client e2e scenario (tools/e2e/run_client_e2e.py), active only with {@code -Dhardscrabble.clientE2e=true}: once the player is in
 * the quick-play world it runs a fixed list of steps, grabs a screenshot after each one (run/screenshots/e2e_*.png),
 * logs {@code CLIENT_E2E <step> OK} and quits. Rendering crashes show up as a missing step and errors in the log.
 */
@EventBusSubscriber(modid = MiteMod.MODID, value = Dist.CLIENT)
public final class ClientE2e {
    private ClientE2e() {}

    private record Step(String name, int waitTicks, Runnable action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int step = -1, wait = 200, waitingTicks; // let chunks load before the first step

    static {
        STEPS.add(new Step("hud", 40, () -> {
            run("effect give @p minecraft:resistance infinite 255 true");
            run("effect give @p minecraft:night_vision infinite 0 true");
            run("time set 1000");
        }));
        STEPS.add(new Step("inventory", 20, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(new InventoryScreen(mc.player));
        }));
        STEPS.add(new Step("items", 40, () -> {
            Minecraft.getInstance().gui.setScreen(null);
            run("give @p hardscrabble:flint_hatchet");
            run("give @p hardscrabble:copper_workbench");
            run("give @p hardscrabble:mithril_pickaxe");
            run("give @p hardscrabble:silver_coin 8");
            run("give @p hardscrabble:raw_mithril 16");
            run("give @p hardscrabble:silver_ingot 32");
        }));
        STEPS.add(new Step("mobs", 80, () -> {
            run("execute at @p run fill ~-9 199 ~-9 ~9 199 ~9 minecraft:smooth_stone");
            run("execute at @p run fill ~-9 200 ~-9 ~9 210 ~9 minecraft:air");
            run("execute at @p run tp @p ~ 200 ~ 0 10");
            run("time set 18000");
            int i = 0;
            for (var type : MobContent.MOBS) {
                double angle = Math.PI * (0.15 + 0.7 * i++ / (MobContent.MOBS.size() - 1)); // arc in front of the player
                run(String.format(java.util.Locale.ROOT, "execute at @p run summon %s ~%.1f 200 ~%.1f {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(type.get()), -Math.cos(angle) * 7, Math.sin(angle) * 7));
            }
        }));
        STEPS.add(new Step("models", 60, () -> { // close-up of the mobs with their own model (MiteModelLayers), a zombie for scale
            run("kill @e[type=!player]");
            run("time set 6000");
            run("execute at @p run tp @p ~ 200 ~ 0 15");
            run("execute at @p run fill ~-12 204 ~1 ~12 204 ~11 minecraft:smooth_stone"); // shade: the undead burn in daylight
            String[] modelled = {"minecraft:zombie", "hardscrabble:ghoul", "hardscrabble:earth_elemental", "hardscrabble:clay_golem", "hardscrabble:revenant", "hardscrabble:wight", "hardscrabble:shadow", "hardscrabble:invisible_stalker", "hardscrabble:longdead", "hardscrabble:longdead_guardian", "hardscrabble:bone_lord", "hardscrabble:ancient_bone_lord", "hardscrabble:dire_wolf", "hardscrabble:hellhound", "hardscrabble:phase_spider", "hardscrabble:black_widow", "hardscrabble:demon_spider", "hardscrabble:wood_spider", "hardscrabble:infernal_creeper", "hardscrabble:fire_elemental", "hardscrabble:jelly", "hardscrabble:ooze", "hardscrabble:vampire_bat", "hardscrabble:copperspine"};
            int[] owner = net.minecraft.core.UUIDUtil.uuidToIntArray(Minecraft.getInstance().player.getUUID());
            // a tamed dire wolf with a red collar and dyed, badly damaged wolf armour (MITE collar, armour and crack layers)
            run(String.format(java.util.Locale.ROOT, "execute at @p run summon hardscrabble:dire_wolf ~-2 200 ~3 {NoAI:1b,PersistenceRequired:1b,Rotation:[120f,0f],"
                    + "Owner:[I;%d,%d,%d,%d],CollarColor:14b,equipment:{body:{id:\"minecraft:wolf_armor\",components:{\"minecraft:dyed_color\":3949738,\"minecraft:damage\":52}}}}",
                    owner[0], owner[1], owner[2], owner[3]));
            run(String.format(java.util.Locale.ROOT, "execute at @p run summon hardscrabble:dire_wolf ~2 200 ~3 {NoAI:1b,PersistenceRequired:1b,Rotation:[240f,0f],"
                    + "Owner:[I;%d,%d,%d,%d],Sitting:1b,equipment:{body:{id:\"minecraft:wolf_armor\"}}}", owner[0], owner[1], owner[2], owner[3])); // sitting, plain new armour
            int perRow = (modelled.length + 1) / 2;   // humanoids at the back, beasts in front
            for (int i = 0; i < modelled.length; i++)
                run(String.format(java.util.Locale.ROOT, "execute at @p run summon %s ~%.1f 200 ~%d {NoAI:1b,PersistenceRequired:1b,Rotation:[165f,0f]}",
                        modelled[i], ((perRow - 1) / 2.0 - i % perRow) * 1.3, i < perRow ? 9 : 5));
        }));
        STEPS.add(new Step("blocks", 60, () -> {
            run("kill @e[type=!player]");
            run("time set 6000");
            String[] blocks = {"hardscrabble:copper_workbench", "hardscrabble:iron_workbench", "hardscrabble:mithril_workbench", "hardscrabble:adamantium_workbench",
                    "hardscrabble:clay_furnace", "hardscrabble:obsidian_furnace", "hardscrabble:netherrack_furnace", "hardscrabble:silver_ore", "hardscrabble:mithril_ore",
                    "hardscrabble:adamantium_ore", "hardscrabble:deepslate_mithril_ore", "hardscrabble:silver_block", "hardscrabble:mithril_block", "hardscrabble:adamantium_block",
                    "hardscrabble:ancient_metal_block", "hardscrabble:copper_anvil", "hardscrabble:mithril_anvil", "hardscrabble:silver_strongbox", "hardscrabble:mithril_runestone"};
            for (int i = 0; i < blocks.length; i++)
                run(String.format(java.util.Locale.ROOT, "execute at @p run setblock ~%d 200 ~%d %s", i % 7 - 3, 4 + i / 7 * 2, blocks[i]));
            Minecraft.getInstance().player.setXRot(35);
        }));
        STEPS.add(new Step("armor", 60, () -> {
            run("item replace entity @p armor.head with hardscrabble:mithril_helmet");
            run("item replace entity @p armor.chest with hardscrabble:adamantium_chestplate");
            run("item replace entity @p armor.legs with hardscrabble:silver_chainmail_leggings");
            run("item replace entity @p armor.feet with hardscrabble:ancient_metal_boots");
            run("item replace entity @p weapon.mainhand with hardscrabble:mithril_battle_axe");
            Minecraft mc = Minecraft.getInstance();
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            mc.player.setXRot(10);
        }));
        STEPS.add(new Step("bloodmoon", 60, () -> {
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            run("kill @e[type=!player]");
            run("time set " + (31 * 24000L + 18000)); // day 32 = floor(ticks / 24000) + 1
            Minecraft.getInstance().player.setXRot(-85); // look at the moon
        }));
        STEPS.add(new Step("moondog", 60, () -> {
            run("time set " + (119 * 24000L + 18000)); // day 120: the full moon before a blue moon, with its halo
            Minecraft.getInstance().player.setXRot(-85);
        }));
        STEPS.add(new Step("underworld", 200, () -> {
            run("execute in hardscrabble:underworld run forceload add -16 -16 16 16"); // fill fails silently in unloaded chunks
            run("execute in hardscrabble:underworld run fill -3 -61 -3 3 -61 3 minecraft:glass");
            run("execute in hardscrabble:underworld run fill -3 -60 -3 3 -56 3 minecraft:air");
            run("execute in hardscrabble:underworld run tp @p 0 -60 0 0 10");
        }));
        STEPS.add(new Step("nether", 200, () -> {
            run("execute in minecraft:the_nether run forceload add -16 -16 16 16");
            run("execute in minecraft:the_nether run fill -3 69 -3 3 69 3 minecraft:glass");
            run("execute in minecraft:the_nether run fill -3 70 -3 3 74 3 minecraft:air");
            run("execute in minecraft:the_nether run fill -3 70 3 3 72 3 minecraft:glowstone");
            run("execute in minecraft:the_nether run tp @p 0 70 0 0 10");
        }));
    }

    /** Runs a command as the integrated server console (strict mode only filters player commands). */
    private static void run(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("hardscrabble.clientE2e")) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false; // in memory only: the scenario must not pause when the window loses focus
        if (mc.player == null && ++waitingTicks % 200 == 0) // stuck before the world: log the screen in the way
            MiteMod.LOGGER.info("CLIENT_E2E waiting screen={} title={}", mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName(), mc.gui.screen() == null ? "" : mc.gui.screen().getTitle().getString());
        if (mc.player == null || mc.level == null || --wait > 0) return;
        if (step >= 0) {
            String name = STEPS.get(step).name();
            Screenshot.grab(mc.gameDirectory, "e2e_" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1, msg -> {});
            MiteMod.LOGGER.info("CLIENT_E2E {} OK moon={} fog={} dim={} pos={} ahead={} chunk={} screen={}", name, dev.mite.lunar.Lunar.moon(mc.level),
                    mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.FOG_END_DISTANCE, mc.player.position()),
                    mc.level.dimension().identifier(), mc.player.blockPosition().toShortString(),
                    BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(mc.player.blockPosition().south(3)).getBlock()),
                    mc.level.getChunkSource().hasChunk(mc.player.blockPosition().getX() >> 4, mc.player.blockPosition().getZ() >> 4),
                    mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
        }
        if (++step >= STEPS.size()) {
            MiteMod.LOGGER.info("CLIENT_E2E DONE");
            mc.stop();
            return;
        }
        Step s = STEPS.get(step);
        s.action().run();
        wait = s.waitTicks();
    }
}
