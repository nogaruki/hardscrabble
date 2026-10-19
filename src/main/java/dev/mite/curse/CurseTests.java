package dev.mite.curse;

import dev.mite.crafting.CraftingContent;
import dev.mite.crafting.TimedCrafting;
import dev.mite.crafting.Workbench;
import dev.mite.crafting.WorkbenchBlock;
import dev.mite.enchanting.EnchantingContent;
import dev.mite.gametest.MiteGameTests;
import dev.mite.metabolism.MetabolismTests;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enderman;
import dev.mite.metabolism.MetabolismContent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/** GameTests for EF-MAL-01 (selection, 1-in-4 draw, delay, lifting, persistence), EF-MAL-02 (one per effect) and EF-ENC-06 CA-1. */
public final class CurseTests {
    private CurseTests() {}

    /** Curses the player with an already active curse from {@code witch}. */
    static void curseNow(ServerPlayer p, UUID witch, Curse curse) {
        Curses.curse(p, witch, curse, p.level().getGameTime() - Curses.DELAY);
    }

    /** A player in the player list that records the keys of the system messages it receives. */
    static ServerPlayer listener(GameTestHelper h, List<String> keys) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void sendSystemMessage(Component message, boolean overlay) {
                if (message.getContents() instanceof TranslatableContents t) keys.add(t.getKey());
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        return p;
    }

    public static void register() {
        // EF-MAL-01: "you have been cursed" once, when the delay ends; a witch's death tells only a player who knew of the curse.
        MiteGameTests.add("curse_cursed_and_lifted_messages", 20, h -> {
            List<String> keys = new ArrayList<>();
            ServerPlayer p = listener(h, keys);
            var data = Curses.data(h.getLevel().getServer());
            UUID witch = UUID.randomUUID();
            long now = h.getLevel().getGameTime();
            Curses.curse(p, witch, Curse.CANT_SLEEP, now - Curses.DELAY + 1);
            Curses.tick(p);
            boolean early = data.entries.get(p.getUUID()).notified();
            Curses.liftWitch(h.getLevel().getServer(), witch);
            boolean silent = Curses.cursed(p) == null && !keys.contains("hardscrabble.curse.lifted");
            Curses.curse(p, witch, Curse.CANT_SLEEP, now - Curses.DELAY);
            Curses.tick(p);
            boolean notified = data.entries.get(p.getUUID()).notified();
            Curses.tick(p);
            boolean still = data.entries.get(p.getUUID()).notified();
            long told = keys.stream().filter("hardscrabble.curse.cursed"::equals).count();
            Curses.liftWitch(h.getLevel().getServer(), witch);
            boolean lifted = keys.contains("hardscrabble.curse.lifted");
            h.assertFalse(early, "notified before the delay ended");
            h.assertTrue(silent, "lift message for a curse the player never learned about");
            h.assertTrue(notified && still, "not notified once the delay ended");
            h.assertValueEqual(told, 1L, "'cursed' messages");
            h.assertTrue(lifted, "no lift message for a known curse");
            h.succeed();
        });

        // ET-O-01: the same witch always picks the same curse for the same player; witches cover all 16.
        MiteGameTests.add("curse_selection_deterministic", 20, h -> {
            UUID witch = UUID.randomUUID();
            h.assertValueEqual(Curses.select(witch, "Steve"), Curses.select(new UUID(witch.getMostSignificantBits(), witch.getLeastSignificantBits()), "Steve"), "same witch, same player");
            Set<Curse> seen = new HashSet<>();
            for (int i = 0; i < 1000; i++) seen.add(Curses.select(new UUID(i, 31L * i), "Steve"));
            h.assertValueEqual(seen.size(), Curse.values().length, "curses reached");
            h.succeed();
        });

        // EF-MAL-01: no effect during the 6000-tick delay. EF-MAL-02 #8: Clumsiness doubles the craft time and is then known.
        MiteGameTests.add("curse_clumsiness_doubles_craft_time", 20, h -> {
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            BlockPos bench = new BlockPos(3, 1, 3);
            WorkbenchBlock block = CraftingContent.WORKBENCHES.get(Workbench.COPPER).get();
            h.setBlock(bench, block);
            AbstractContainerMenu menu = new WorkbenchBlock.Menu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(bench)), block);
            p.containerMenu = menu;
            for (int slot : new int[] {1, 2, 3}) menu.getSlot(slot).set(new ItemStack(Items.COPPER_INGOT));
            menu.getSlot(5).set(new ItemStack(Items.STICK));
            menu.getSlot(8).set(new ItemStack(Items.STICK));
            int base = TimedCrafting.pendingTicks(p);
            Curses.curse(p, UUID.randomUUID(), Curse.CLUMSINESS, p.level().getGameTime());
            menu.getSlot(8).set(new ItemStack(Items.STICK));
            h.assertValueEqual(TimedCrafting.pendingTicks(p), base, "craft time while the curse is pending");
            curseNow(p, UUID.randomUUID(), Curse.CLUMSINESS);
            menu.getSlot(8).set(new ItemStack(Items.STICK));
            int clumsy = TimedCrafting.pendingTicks(p);
            h.assertTrue(base > 0 && Math.abs(clumsy - 2 * base) <= 1, "clumsy craft " + clumsy + " ticks, normal " + base);
            h.assertTrue(Curses.data(h.getLevel().getServer()).entries.get(p.getUUID()).revealed(), "curse not revealed by its first effect");
            h.succeed();
        });

        // EF-ENC-06 CA-1: drinking a disenchanting bottle lifts the curse and uses the bottle.
        MiteGameTests.add("curse_disenchanting_bottle_lifts", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_RUN);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EnchantingContent.DISENCHANTING_BOTTLE.get(), 2));
            p.gameMode.useItem(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
            h.assertTrue(Curses.cursed(p) == null, "curse still there");
            h.assertValueEqual(p.getMainHandItem().getCount(), 1, "bottles left");
            h.succeed();
        });

        // EF-REG-04: /state shows the insulin resistance, and the curse only once the player knows it.
        MiteGameTests.add("curse_state_command", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            java.util.function.Supplier<java.util.List<String>> keys = () -> dev.mite.rules.MiteCommands.stateLines(p).stream()
                    .map(c -> c.getContents() instanceof TranslatableContents t ? t.getKey() : "").toList();
            h.assertTrue(keys.get().contains("hardscrabble.command.state.insulin"), "insulin line");
            curseNow(p, UUID.randomUUID(), Curse.CANT_RUN);
            h.assertFalse(keys.get().contains("hardscrabble.curse.hud"), "unknown curse shown");
            Curses.trigger(p, Curse.CANT_RUN);
            h.assertTrue(keys.get().contains("hardscrabble.curse.hud"), "known curse missing");
            h.succeed();
        });

        // EF-MAL-01 CA-1: the witch's death lifts all her curses, offline players included; another witch's curse stays.
        MiteGameTests.add("curse_witch_death_lifts", 20, h -> {
            Witch witch = h.spawn(EntityTypes.WITCH, 3, 1, 3);
            ServerPlayer p = MetabolismTests.player(h);
            UUID offline = UUID.randomUUID(), other = UUID.randomUUID();
            curseNow(p, witch.getUUID(), Curse.CANT_SLEEP);
            var data = Curses.data(h.getLevel().getServer());
            data.put(offline, new Curses.Entry(witch.getUUID(), Curse.CANT_RUN, 0, true, false));
            data.put(other, new Curses.Entry(UUID.randomUUID(), Curse.CANT_RUN, 0, true, false));
            witch.kill(h.getLevel());
            h.assertTrue(Curses.cursed(p) == null && !data.entries.containsKey(offline), "witch's curses not lifted");
            h.assertTrue(data.entries.containsKey(other), "another witch's curse lifted");
            data.put(other, null);
            h.succeed();
        });

        // EF-MAL-01 (dom-O-06): a real witch targeting a visible player curses in about 1 case in 4 and becomes persistent.
        MiteGameTests.add("curse_witch_draw_one_in_four", 20, h -> {
            Witch witch = h.spawn(EntityTypes.WITCH, 1, 1, 1);
            ServerPlayer p = MetabolismTests.player(h);
            p.snapTo(h.absoluteVec(new Vec3(5.5, 1, 5.5)));
            int cursed = 0;
            for (int i = 0; i < 400; i++) {
                witch.setTarget(null);
                witch.setTarget(p);
                if (Curses.cursed(p) != null) cursed++;
                Curses.data(h.getLevel().getServer()).put(p.getUUID(), null);
            }
            h.assertTrue(cursed >= 60 && cursed <= 140, "witch cursed " + cursed + " times out of 400");
            h.assertTrue(witch.isPersistenceRequired(), "a cursing witch must not despawn");
            h.succeed();
        });

        // EF-MAL-01 CA-2 (dom-O-06): the curse survives the player's death and respawn.
        MiteGameTests.add("curse_survives_death", 20, h -> {
            ServerPlayer p = MiteGameTests.levelPlayer(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_SLEEP);
            p.kill(h.getLevel());
            ServerPlayer respawned = h.getLevel().getServer().getPlayerList().respawn(p, false, Entity.RemovalReason.KILLED);
            h.assertValueEqual(Curses.active(respawned), Curse.CANT_SLEEP, "curse after respawn");
            Curses.lift(respawned);
            h.succeed();
        });

        // dom-O-10: a witch removed without dying (despawn, discard) lifts her curses like her death.
        MiteGameTests.add("curse_witch_discard_lifts", 20, h -> {
            Witch witch = h.spawn(EntityTypes.WITCH, 3, 1, 3);
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, witch.getUUID(), Curse.CANT_SLEEP);
            witch.discard();
            h.assertTrue(Curses.cursed(p) == null, "discarded witch's curse not lifted");
            h.succeed();
        });

        // EF-MAL-02 CA-1: one test per effect. #8 Clumsiness is curse_clumsiness_doubles_craft_time.
        // #1 (M40): block wear (MITE hardness x 100 x rate) and attack wear are doubled.
        MiteGameTests.add("curse_effect_equipment_decay", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            BlockPos pos = new BlockPos(3, 1, 3);
            int[] wear = new int[2];
            for (int cursed = 0; cursed < 2; cursed++) {
                if (cursed == 1) curseNow(p, UUID.randomUUID(), Curse.EQUIPMENT_DECAY);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_PICKAXE));
                h.setBlock(pos, Blocks.STONE);
                p.gameMode.destroyBlock(h.absolutePos(pos));
                wear[cursed] = p.getMainHandItem().getDamageValue();
            }
            h.assertTrue(wear[0] > 1 && wear[1] == 2 * wear[0], "block wear " + wear[1] + ", normal " + wear[0]);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            p.attack(h.spawn(EntityTypes.ZOMBIE, 1, 1, 1));
            // a sweep hits the other mobs in the same tick with the same source, at no wear
            h.spawn(EntityTypes.ZOMBIE, 1, 1, 2).hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 1);
            int perAttack = p.getMainHandItem().get(DataComponents.WEAPON).itemDamagePerAttack();
            h.assertValueEqual(p.getMainHandItem().getDamageValue(), 2 * perAttack, "attack wear");
            Curses.lift(p);
            h.succeed();
        });

        // #2: air drains 4x faster.
        MiteGameTests.add("curse_effect_cant_hold_breath", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_HOLD_BREATH);
            h.assertValueEqual(NeoForge.EVENT_BUS.post(new LivingBreatheEvent(p, false, 1, 0)).getConsumeAirAmount(), CurseEffects.BREATH_FACTOR, "air used per tick");
            h.assertValueEqual(NeoForge.EVENT_BUS.post(new LivingBreatheEvent(p, true, 0, 4)).getConsumeAirAmount(), 0, "air used while breathing");
            Curses.lift(p);
            h.succeed();
        });

        // #3 (M38): the server stops the sprint and reveals the curse; from then on the client may not sprint either.
        MiteGameTests.add("curse_effect_cant_run", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.assertTrue(Curses.canSprint(p), "uncursed player cannot sprint");
            curseNow(p, UUID.randomUUID(), Curse.CANT_RUN);
            p.setSprinting(true);
            CurseEffects.tickMovement(p);
            h.assertFalse(p.isSprinting(), "still sprinting");
            h.assertFalse(Curses.canSprint(p), "sprint allowed");
            h.assertValueEqual(p.getData(Curses.VIEW), new Curses.View(Curse.CANT_RUN.ordinal(), true), "client view");
            Curses.lift(p);
            h.succeed();
        });

        // #4 (dom-O-04): no meat, no milk (bucket or bowl); plants are fine.
        MiteGameTests.add("curse_effect_cant_eat_animals", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_EAT_ANIMALS);
            for (Item item : new Item[] {Items.COOKED_BEEF, Items.MILK_BUCKET, MetabolismContent.MILK_BOWL.get()}) {
                h.assertTrue(CurseEffects.refused(p, new ItemStack(item)), item + " accepted");
            }
            h.assertFalse(CurseEffects.refused(p, new ItemStack(Items.APPLE)), "apple refused");
            Curses.lift(p);
            h.succeed();
        });

        // #5: no plants; meat is fine.
        MiteGameTests.add("curse_effect_cant_eat_plants", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_EAT_PLANTS);
            h.assertTrue(CurseEffects.refused(p, new ItemStack(Items.APPLE)), "apple eaten");
            h.assertFalse(CurseEffects.refused(p, new ItemStack(Items.COOKED_BEEF)), "beef refused");
            Curses.lift(p);
            h.succeed();
        });

        // #6: no drinking (potion, milk); food is fine.
        MiteGameTests.add("curse_effect_cant_drink", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_DRINK);
            h.assertTrue(CurseEffects.refused(p, new ItemStack(Items.POTION)), "potion drunk");
            h.assertTrue(CurseEffects.refused(p, new ItemStack(Items.MILK_BUCKET)), "milk drunk");
            h.assertFalse(CurseEffects.refused(p, new ItemStack(Items.BREAD)), "bread refused");
            Curses.lift(p);
            h.succeed();
        });

        // #7: nearby endermen target the player.
        MiteGameTests.add("curse_effect_endermen_enemy", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.snapTo(h.absoluteVec(new Vec3(5.5, 1, 5.5)));
            Enderman enderman = h.spawn(EntityTypes.ENDERMAN, 1, 1, 1);
            curseNow(p, UUID.randomUUID(), Curse.ENDERMEN_ENEMY);
            CurseEffects.tickServer(p);
            h.assertTrue(enderman.getTarget() == p, "enderman not hostile");
            Curses.lift(p);
            h.succeed();
        });

        // #9 (M39): slowed in grass, fall distance kept (normal fall damage), not slowed in water.
        MiteGameTests.add("curse_effect_entanglement", 20, h -> {
            float[] lost = new float[2];
            for (int cursed = 0; cursed < 2; cursed++) {
                ServerPlayer p = MetabolismTests.player(h);
                if (cursed == 1) curseNow(p, UUID.randomUUID(), Curse.ENTANGLEMENT);
                h.setBlock(3, 1, 3, Blocks.SHORT_GRASS);
                p.snapTo(h.absoluteVec(new Vec3(3.5, 1, 3.5)));
                p.fallDistance = 5;
                p.setDeltaMovement(0.4, -1, 0.4);
                CurseEffects.tickMovement(p);
                h.assertValueEqual(p.fallDistance, 5.0, "fall distance in grass");
                h.assertValueEqual(p.getDeltaMovement(), cursed == 1 ? new Vec3(0.2, -1, 0.2) : new Vec3(0.4, -1, 0.4), "velocity in grass");
                float health = p.getHealth();
                p.causeFallDamage(p.fallDistance, 1, p.damageSources().fall());
                lost[cursed] = health - p.getHealth();
                if (cursed == 1) {
                    h.setBlock(3, 1, 3, Blocks.WATER);
                    p.setDeltaMovement(0.4, 0, 0.4);
                    CurseEffects.tickMovement(p);
                    h.assertValueEqual(p.getDeltaMovement(), new Vec3(0.4, 0, 0.4), "velocity in water");
                    Curses.lift(p);
                }
            }
            h.assertTrue(lost[0] > 0 && lost[1] == lost[0], "fall damage " + lost[1] + ", normal " + lost[0]);
            h.succeed();
        });

        // #10: armor is taken off.
        MiteGameTests.add("curse_effect_cant_wear_armor", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_WEAR_ARMOR);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            CurseEffects.tickServer(p);
            h.assertTrue(p.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && p.getInventory().contains(new ItemStack(Items.IRON_HELMET)), "helmet still worn");
            Curses.lift(p);
            h.succeed();
        });

        // #11 (dom-O-07): a chest does not open; strongboxes count as chests.
        MiteGameTests.add("curse_effect_cant_open_chests", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            BlockPos chest = h.absolutePos(new BlockPos(3, 1, 3));
            h.setBlock(3, 1, 3, Blocks.CHEST);
            curseNow(p, UUID.randomUUID(), Curse.CANT_OPEN_CHESTS);
            p.gameMode.useItemOn(p, h.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false));
            h.assertTrue(p.containerMenu == p.inventoryMenu, "chest opened");
            h.assertTrue(CurseEffects.isChest(EnchantingContent.STRONGBOXES.values().iterator().next().get().defaultBlockState()), "strongbox not a chest");
            Curses.lift(p);
            h.succeed();
        });

        // #11 (O-1): barrels, shulker boxes and chest boats (an entity, opened by click or from the seat) stay closed too.
        MiteGameTests.add("curse_effect_cant_open_modern_chests", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.assertTrue(CurseEffects.isChest(Blocks.BARREL.defaultBlockState()) && CurseEffects.isChest(Blocks.SHULKER_BOX.defaultBlockState()),
                    "barrel or shulker box not a chest");
            ChestBoat boat = h.spawn(EntityTypes.OAK_CHEST_BOAT, 3, 1, 3);
            curseNow(p, UUID.randomUUID(), Curse.CANT_OPEN_CHESTS);
            boat.openCustomInventoryScreen(p);
            h.assertTrue(p.containerMenu == p.inventoryMenu, "chest boat opened");
            Curses.lift(p);
            boat.openCustomInventoryScreen(p);
            h.assertTrue(p.containerMenu != p.inventoryMenu, "chest boat closed without a curse");
            p.closeContainer();
            boat.discard();
            h.succeed();
        });

        // #6 (O-2): bowl dishes are drunk too (MITE soups and vanilla stews); A-4: curses_enabled=false switches every curse off.
        MiteGameTests.add("curse_effect_cant_drink_bowls_and_config", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_DRINK);
            for (Item item : new Item[] {Items.MUSHROOM_STEW, Items.BEETROOT_SOUP, dev.mite.farming.FarmingContent.BEEF_STEW.get()}) {
                h.assertTrue(CurseEffects.refused(p, new ItemStack(item)), item + " eaten");
            }
            boolean off = MiteGameTests.withConfig(dev.mite.config.MiteConfig.CURSES_ENABLED, false, () -> !CurseEffects.refused(p, new ItemStack(Items.POTION)));
            h.assertTrue(off, "potion refused with curses_enabled=false");
            h.assertTrue(CurseEffects.refused(p, new ItemStack(Items.POTION)), "potion drunk with curses on");
            Curses.lift(p);
            h.succeed();
        });

        // EF-UI-08 (O-3): the HUD shows an icon, "?" while unknown, then one per curse; every sprite ships.
        MiteGameTests.add("curse_hud_icons", 20, h -> {
            List<String> missing = new ArrayList<>();
            List<String> names = new ArrayList<>(List.of("unknown"));
            for (Curse c : Curse.values()) names.add(c.name().toLowerCase(java.util.Locale.ROOT));
            for (String n : names) {
                if (CurseTests.class.getResource("/assets/hardscrabble/textures/gui/sprites/hud/curse/" + n + ".png") == null) missing.add(n);
            }
            h.assertTrue(missing.isEmpty(), "missing curse icons " + missing);
            h.succeed();
        });

        // #12: sleeping is refused.
        MiteGameTests.add("curse_effect_cant_sleep", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            curseNow(p, UUID.randomUUID(), Curse.CANT_SLEEP);
            h.assertTrue(NeoForge.EVENT_BUS.post(new CanPlayerSleepEvent(p, p.blockPosition(), null)).getProblem() != null, "player may sleep");
            Curses.lift(p);
            h.succeed();
        });

        // #13-16: about half the melee swings at the feared mob miss; other mobs are hit.
        fear("spiders", Curse.FEAR_OF_SPIDERS, EntityTypes.SPIDER);
        fear("wolves", Curse.FEAR_OF_WOLVES, EntityTypes.WOLF);
        fear("creepers", Curse.FEAR_OF_CREEPERS, EntityTypes.CREEPER);
        fear("undead", Curse.FEAR_OF_UNDEAD, EntityTypes.ZOMBIE);
    }

    private static void fear(String name, Curse curse, EntityType<? extends Mob> feared) {
        MiteGameTests.add("curse_effect_fear_of_" + name, 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            Mob target = h.spawn(feared, 1, 1, 1), other = h.spawn(EntityTypes.COW, 5, 1, 5);
            curseNow(p, UUID.randomUUID(), curse);
            int missed = 0;
            for (int i = 0; i < 200; i++) {
                if (!CommonHooks.onPlayerAttackTarget(p, target)) missed++;
                h.assertTrue(CommonHooks.onPlayerAttackTarget(p, other), "swing at a cow missed");
            }
            h.assertTrue(missed >= 60 && missed <= 140, missed + " misses out of 200");
            Curses.lift(p);
            h.succeed();
        });
    }
}
