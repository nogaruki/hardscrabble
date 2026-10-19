package dev.mite.enchanting;

import dev.mite.core.Formulas;
import dev.mite.core.Material;
import dev.mite.core.ToolType;
import dev.mite.gametest.MiteGameTests;
import dev.mite.harvest.ToolStats;
import dev.mite.metabolism.MetabolismTests;
import dev.mite.player.ProgressionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import dev.mite.config.MiteConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.advancements.predicates.BlockPredicate;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** GameTests for EF-ENC-02, 04, 06, 08 and 09 (H02, H04, H07, H09, H10). */
public final class EnchantingTests2 {
    private EnchantingTests2() {}

    private static final BlockPos POS = new BlockPos(3, 1, 3);

    /** The vanilla exhaustion accrued since the last food tick (FoodData keeps it private). */
    private static float exhaustion(ServerPlayer p) {
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        p.getFoodData().addAdditionalSaveData(out);
        return out.buildResult().getFloatOr("foodExhaustionLevel", 0);
    }

    private static ItemStack enchanted(GameTestHelper h, Item item, ResourceKey<Enchantment> key, int level) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(holder(h, key), level);
        return stack;
    }

    private static Holder<Enchantment> holder(GameTestHelper h, ResourceKey<Enchantment> key) {
        return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
    }

    private static Item tool(ToolType type) {
        return ToolStats.all().entrySet().stream().filter(e -> e.getValue().type() == type).findFirst().orElseThrow().getKey();
    }

    private static int count(GameTestHelper h, Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(3)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    public static void register() {
        // A-4 (EF-CFG-02 CA-1, EF-MUL-06): strongbox_op_access lets an operator open another player's strongbox.
        MiteGameTests.add("config_strongbox_op_access", 20, h -> {
            h.setBlock(POS, EnchantingContent.STRONGBOXES.get(Material.IRON).get());
            StrongboxBlockEntity box = (StrongboxBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(POS));
            box.setOwner(java.util.UUID.randomUUID());
            ServerPlayer op = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-op"),
                    net.minecraft.server.level.ClientInformation.createDefault()) {
                @Override
                public net.minecraft.server.permissions.PermissionSet permissions() {
                    return net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER;
                }
            };
            h.assertTrue(MiteGameTests.withConfig(dev.mite.config.MiteConfig.STRONGBOX_OP_ACCESS, true, () -> box.canOpen(op)), "operator refused with strongbox_op_access=true");
            h.assertFalse(box.canOpen(op), "operator opens with strongbox_op_access=false");
            h.succeed();
        });

        // EF-ENC-02 CA-1: enchanting debits the XP points of the row level (MITE curve), not 1-3 levels.
        MiteGameTests.add("enchanting_table_xp_cost", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            ProgressionService.setXp(p, EnchantingTable.cost(30));
            h.setBlock(POS, Blocks.ENCHANTING_TABLE);
            EnchantmentMenu menu = new EnchantmentMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)));
            p.containerMenu = menu;
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            menu.getSlot(0).set(new ItemStack(Items.IRON_PICKAXE));
            int row = menu.costs[2] > 0 ? 2 : 0, level = menu.costs[row];
            h.assertTrue(level > 0, "row cost");
            h.assertTrue(menu.clickMenuButton(p, row), "enchant accepted");
            h.assertValueEqual(ProgressionService.xp(p), EnchantingTable.cost(30) - EnchantingTable.cost(level), "XP after enchanting");
            h.assertTrue(EnchantmentHelper.hasAnyEnchantments(menu.getSlot(0).getItem()), "pickaxe enchanted");
            h.assertTrue(EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(0).getItem()).size() <= EnchantingTable.MAX_ENCHANTMENTS, "at most 3");
            h.succeed();
        });
        // EF-ENC-02: an enchanting that rolls more than 3 enchantments keeps 3, in roll order, the table hint (enchantClue) first.
        MiteGameTests.add("enchanting_table_three_max", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            List<EnchantmentInstance> rolled = new ArrayList<>();
            for (ResourceKey<Enchantment> key : List.of(Enchantments.EFFICIENCY, Enchantments.UNBREAKING, Enchantments.FORTUNE, Enchantments.SHARPNESS, Enchantments.SMITE))
                rolled.add(new EnchantmentInstance(holder(h, key), 1));
            ItemStack plain = new ItemStack(Items.IRON_PICKAXE), hinted = new ItemStack(Items.IRON_PICKAXE);
            for (EnchantmentInstance e : rolled) {
                plain.enchant(e.enchantment(), 1);
                hinted.enchant(e.enchantment(), 1);
            }
            NeoForge.EVENT_BUS.post(new PlayerEnchantItemEvent(p, plain, rolled));
            h.assertValueEqual(EnchantmentHelper.getEnchantmentsForCrafting(plain).keySet(),
                    Set.copyOf(rolled.subList(0, 3).stream().map(EnchantmentInstance::enchantment).toList()), "first 3 rolled kept");
            h.setBlock(POS, Blocks.ENCHANTING_TABLE);
            EnchantmentMenu menu = new EnchantmentMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)));
            p.containerMenu = menu;
            menu.enchantClue[1] = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).asHolderIdMap().getId(rolled.get(4).enchantment());
            NeoForge.EVENT_BUS.post(new PlayerEnchantItemEvent(p, hinted, rolled));
            var kept = EnchantmentHelper.getEnchantmentsForCrafting(hinted).keySet();
            h.assertValueEqual(kept.size(), 3, "3 enchantments");
            h.assertTrue(kept.contains(rolled.get(4).enchantment()) && kept.contains(rolled.get(0).enchantment()) && kept.contains(rolled.get(1).enchantment()),
                    "hinted enchantment kept: " + kept);
            p.containerMenu = p.inventoryMenu;
            h.succeed();
        });
        // EF-ENC-04 CA-1: every MITE enchantment is registered and offered by the table (non_treasure); Mending stays out.
        MiteGameTests.add("enchanting_mite_enchantments_exist", 20, h -> {
            h.assertValueEqual(MiteEnchantments.ALL.size(), 17, "16 enchantments + arrow recovery");
            for (ResourceKey<Enchantment> key : MiteEnchantments.ALL) {
                Holder<Enchantment> e = holder(h, key);
                h.assertTrue(e.is(EnchantmentTags.IN_ENCHANTING_TABLE), key.identifier() + " in the table");
                h.assertFalse(e.value().effects().isEmpty(), key.identifier() + " has effects");
            }
            h.assertFalse(holder(h, Enchantments.MENDING).is(EnchantmentTags.IN_ENCHANTING_TABLE), "mending out");
            h.succeed();
        });
        // Tree felling: breaking the bottom log fells the trunk. H-5: each felled log wears the axe and costs the hunger of a break.
        MiteGameTests.add("enchanting_tree_felling", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            for (int y = 1; y <= 3; y++) h.setBlock(3, y, 3, Blocks.OAK_LOG);
            ItemStack axe = enchanted(h, Items.IRON_AXE, MiteEnchantments.TREE_FELLING, 1);
            p.setItemInHand(InteractionHand.MAIN_HAND, axe);
            float before = exhaustion(p);
            p.gameMode.destroyBlock(h.absolutePos(POS));
            for (int y = 1; y <= 3; y++) h.assertBlockPresent(Blocks.AIR, 3, y, 3);
            int wear = Formulas.blockWear(dev.mite.harvest.HarvestRules.hardness(Blocks.OAK_LOG.defaultBlockState()).hardness(), ToolType.AXE);
            h.assertValueEqual(p.getMainHandItem().getDamageValue(), 3 * wear, "axe wear for 3 logs");
            h.assertTrue(exhaustion(p) - before >= 3 * Formulas.HUNGER_BREAK - 1e-4, "hunger for 3 logs: " + (exhaustion(p) - before));
            // An axe that the next log would break stops felling: only the broken log goes.
            for (int y = 1; y <= 3; y++) h.setBlock(3, y, 3, Blocks.OAK_LOG);
            ItemStack worn = enchanted(h, Items.IRON_AXE, MiteEnchantments.TREE_FELLING, 1);
            worn.setDamageValue(worn.getMaxDamage() - 2 * wear);
            p.setItemInHand(InteractionHand.MAIN_HAND, worn);
            p.gameMode.destroyBlock(h.absolutePos(POS));
            h.assertBlockPresent(Blocks.AIR, 3, 1, 3);
            h.assertBlockPresent(Blocks.OAK_LOG, 3, 2, 3);
            h.assertFalse(p.getMainHandItem().isEmpty(), "felling broke the axe");
            h.succeed();
        });
        // Tree felling breaks each log through the game mode: an adventure player whose axe may break oak only fells no birch.
        MiteGameTests.add("enchanting_tree_felling_rules", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.gameMode.changeGameModeForPlayer(GameType.ADVENTURE);
            h.setBlock(3, 1, 3, Blocks.OAK_LOG);
            h.setBlock(3, 2, 3, Blocks.BIRCH_LOG);
            h.setBlock(3, 3, 3, Blocks.OAK_LOG);
            ItemStack axe = enchanted(h, Items.IRON_AXE, MiteEnchantments.TREE_FELLING, 1);
            axe.set(DataComponents.CAN_BREAK, new AdventureModePredicate(List.of(BlockPredicate.Builder.block()
                    .of(h.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK), Blocks.OAK_LOG).build())));
            p.setItemInHand(InteractionHand.MAIN_HAND, axe);
            p.gameMode.destroyBlock(h.absolutePos(POS));
            h.assertBlockPresent(Blocks.AIR, 3, 1, 3);
            h.assertBlockPresent(Blocks.BIRCH_LOG, 3, 2, 3);
            h.assertBlockPresent(Blocks.OAK_LOG, 3, 3, 3);
            h.succeed();
        });
        // Harvesting (+1 per drop stack at level 2) and fertility (replants at level 3).
        MiteGameTests.add("enchanting_harvesting_fertility", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.setBlock(3, 0, 3, Blocks.FARMLAND);
            h.setBlock(POS, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
            ItemStack hoe = enchanted(h, Items.IRON_HOE, MiteEnchantments.HARVESTING, 2);
            hoe.enchant(holder(h, MiteEnchantments.FERTILITY), 3);
            p.setItemInHand(InteractionHand.MAIN_HAND, hoe);
            p.gameMode.destroyBlock(h.absolutePos(POS));
            h.assertValueEqual(count(h, Items.WHEAT), 2, "wheat with harvesting");
            h.assertBlockState(POS, s -> s.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(s) == 0, s -> Component.literal("replanted wheat expected, got " + s));
            h.succeed();
        });
        // Vampiric heals 10 % of the damage per level; disarming makes the victim drop its weapon; stunning slows (JSON post_attack).
        MiteGameTests.add("enchanting_melee_effects", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.setHealth(2);
            p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(h, Items.IRON_SWORD, MiteEnchantments.VAMPIRIC, 3));
            Zombie zombie = h.spawn(EntityTypes.ZOMBIE, POS);
            zombie.hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 5);
            h.assertTrue(p.getHealth() > 2.5f, "vampiric heal, health " + p.getHealth());
            p.setHealth(2);
            zombie.damageCooldownTime = 0;
            zombie.hurtServer(h.getLevel(), p.damageSources().thorns(p), 5);
            h.assertValueEqual(p.getHealth(), 2f, "vampiric heal from Thorns damage");

            ItemStack sword = enchanted(h, Items.IRON_SWORD, MiteEnchantments.DISARMING, 3);
            sword.enchant(holder(h, MiteEnchantments.STUNNING), 1);
            p.setItemInHand(InteractionHand.MAIN_HAND, sword);
            zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
            for (int i = 0; i < 200 && !zombie.getMainHandItem().isEmpty(); i++) {
                zombie.damageCooldownTime = 0;
                zombie.setHealth(zombie.getMaxHealth());
                zombie.hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 0.5f);
            }
            h.assertTrue(zombie.getMainHandItem().isEmpty(), "zombie disarmed");
            EnchantmentHelper.doPostAttackEffects(h.getLevel(), zombie, p.damageSources().playerAttack(p));
            h.assertTrue(zombie.hasEffect(MobEffects.SLOWNESS), "stunned");
            h.succeed();
        });
        // Butchering: +1 per meat stack at level 2.
        MiteGameTests.add("enchanting_butchering", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemInHand(InteractionHand.MAIN_HAND, enchanted(h, tool(ToolType.KNIFE), MiteEnchantments.BUTCHERING, 2));
            var cow = h.spawn(EntityTypes.COW, POS);
            ItemEntity beef = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.BEEF));
            ItemEntity leather = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.LEATHER));
            EnchantmentEffects.onDrops(new LivingDropsEvent(cow, p.damageSources().playerAttack(p), new ArrayList<>(List.of(beef, leather)), true));
            h.assertValueEqual(beef.getItem().getCount(), 2, "beef");
            h.assertValueEqual(leather.getItem().getCount(), 1, "leather untouched");
            h.succeed();
        });
        // Regeneration (+25 %/level on heals) and endurance (-20 %/level hunger) on the chestplate.
        MiteGameTests.add("enchanting_chest_effects", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            ItemStack chest = enchanted(h, Items.IRON_CHESTPLATE, MiteEnchantments.REGENERATION, 2);
            chest.enchant(holder(h, MiteEnchantments.ENDURANCE), 2);
            p.setItemSlot(EquipmentSlot.CHEST, chest);
            p.setHealth(1);
            p.heal(2);
            h.assertTrue(Math.abs(p.getHealth() - 4f) < 1e-4, "healed 2 x 1.5, health " + p.getHealth());
            h.assertTrue(Math.abs(EnchantmentEffects.hungerMultiplier(p) - 0.6f) < 1e-4, "endurance multiplier");
            p.setItemSlot(EquipmentSlot.MAINHAND, chest.copy());
            p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            h.assertValueEqual(EnchantmentEffects.hungerMultiplier(p), 1f, "endurance only counts in the chest slot");
            h.succeed();
        });
        // Endurance (changelog R145): action hunger such as placing a block is discounted, running and jumping are not.
        MiteGameTests.add("enchanting_endurance_actions", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            p.setItemSlot(EquipmentSlot.CHEST, enchanted(h, Items.IRON_CHESTPLATE, MiteEnchantments.ENDURANCE, 2));
            p.setOnGround(true);
            p.setSprinting(true);
            float before = exhaustion(p);
            p.checkMovementStatistics(1, 0, 0);
            h.assertTrue(Math.abs(exhaustion(p) - before - 0.1f) < 1e-4, "sprinting 1 m: " + (exhaustion(p) - before));
            before = exhaustion(p);
            p.jumpFromGround();
            // C-2: the 1.6.4 sprint jump cost (0.8), not reduced by Endurance.
            h.assertTrue(Math.abs(exhaustion(p) - before - Formulas.HUNGER_SPRINT_JUMP) < 1e-4, "sprint jump: " + (exhaustion(p) - before));
            p.setSprinting(false);
            before = exhaustion(p);
            p.jumpFromGround();
            h.assertTrue(Math.abs(exhaustion(p) - before - Formulas.HUNGER_JUMP) < 1e-4, "jump: " + (exhaustion(p) - before));
            before = exhaustion(p);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), h.absolutePos(new BlockPos(2, 1, 2))),
                    Blocks.STONE.defaultBlockState(), p));
            h.assertTrue(Math.abs(exhaustion(p) - before - Formulas.HUNGER_PLACE * 0.6f) < 1e-5, "placing with Endurance II: " + (exhaustion(p) - before));
            h.succeed();
        });
        // Quickness (draw = 20 - level), true flight (faster arrow), arrow recovery (H-4: +10 %/level recovery chance, the arrow is always used).
        MiteGameTests.add("enchanting_bow_effects", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            ItemStack bow = enchanted(h, Items.BOW, MiteEnchantments.QUICKNESS, 5);
            h.assertValueEqual(EventHooks.onArrowLoose(bow, h.getLevel(), p, 15, true), 20, "15 ticks are a full draw with quickness V");
            // Not added to the level (no stray arrow): the join event is posted by hand, then again as on a dimension change.
            Arrow arrow = new Arrow(h.getLevel(), p, new ItemStack(Items.ARROW), enchanted(h, Items.BOW, MiteEnchantments.TRUE_FLIGHT, 3));
            arrow.setDeltaMovement(new Vec3(0, 0, 1));
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(arrow, h.getLevel(), false));
            h.assertTrue(Math.abs(arrow.getDeltaMovement().length() - 1.3) < 1e-4, "true flight speed " + arrow.getDeltaMovement().length());
            Arrow crossed = new Arrow(EntityTypes.ARROW, h.getLevel());
            crossed.restoreFrom(arrow);
            NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(crossed, h.getLevel(), false));
            h.assertTrue(Math.abs(crossed.getDeltaMovement().length() - 1.3) < 1e-4, "true flight after a dimension change " + crossed.getDeltaMovement().length());
            ItemStack recovery = enchanted(h, Items.BOW, MiteEnchantments.ARROW_RECOVERY, 3);
            for (int i = 0; i < 100; i++)
                h.assertValueEqual(EnchantmentHelper.processAmmoUse(h.getLevel(), recovery, new ItemStack(Items.ARROW), 1), 1, "arrow recovery never saves the shot (no partial Infinity)");
            Arrow recovered = new Arrow(h.getLevel(), p, new ItemStack(Items.ARROW), recovery);
            h.assertTrue(Math.abs(dev.mite.combat.Archery.recoveryBonus(recovered) - 0.3f) < 1e-6, "arrow recovery III adds 30 %");
            net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(7);
            int kept = 0;
            for (int i = 0; i < 1000; i++) if (dev.mite.combat.Archery.recovers(Material.FLINT, random, 0.3f)) kept++;
            h.assertTrue(kept > 550 && kept < 650, "flint arrow with recovery III kept ~60 %, got " + kept);
            h.succeed();
        });
        // EF-ENC-08 CA-1: a second player can neither open a strongbox nor empty it with a hopper.
        MiteGameTests.add("enchanting_strongbox_owner", 20, h -> {
            ServerPlayer owner = MetabolismTests.player(h), other = MetabolismTests.player(h);
            h.setBlock(POS, EnchantingContent.STRONGBOXES.get(Material.IRON).get());
            BlockPos abs = h.absolutePos(POS);
            StrongboxBlockEntity box = (StrongboxBlockEntity) h.getLevel().getBlockEntity(abs);
            box.setOwner(owner.getUUID());
            box.items.setItem(0, new ItemStack(Items.DIAMOND));
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), other, hit);
            h.assertTrue(other.containerMenu == other.inventoryMenu, "non-owner cannot open");
            h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), owner, hit);
            h.assertTrue(owner.containerMenu != owner.inventoryMenu, "owner opens");
            h.assertTrue(HopperBlockEntity.getContainerOrHandlerAt(h.getLevel(), abs, Direction.UP).isEmpty(), "no container for hoppers");
            // a spectator opens a block's menu provider directly, without useWithoutItem's owner check: there is none
            h.assertTrue(h.getLevel().getBlockState(abs).getMenuProvider(h.getLevel(), abs) == null, "strongbox menu provider");
            h.succeed();
        });
        // EF-ENC-08: the placer owns the box; owner and items survive a save and reload; strongbox_op_access lets operators in.
        MiteGameTests.add("enchanting_strongbox_persistence", 20, h -> {
            ServerPlayer owner = MetabolismTests.player(h), other = MetabolismTests.player(h);
            ServerPlayer op = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "test-op"), ClientInformation.createDefault()) {
                @Override
                public PermissionSet permissions() {
                    return LevelBasedPermissionSet.GAMEMASTER;
                }
            };
            h.setBlock(POS, EnchantingContent.STRONGBOXES.get(Material.IRON).get());
            BlockPos abs = h.absolutePos(POS);
            BlockState state = h.getLevel().getBlockState(abs);
            state.getBlock().setPlacedBy(h.getLevel(), abs, state, owner, new ItemStack(state.getBlock()));
            StrongboxBlockEntity box = (StrongboxBlockEntity) h.getLevel().getBlockEntity(abs);
            box.items.setItem(0, new ItemStack(Items.DIAMOND, 3));
            StrongboxBlockEntity loaded = (StrongboxBlockEntity) BlockEntity.loadStatic(abs, state, box.saveWithFullMetadata(h.getLevel().registryAccess()),
                    h.getLevel().registryAccess());
            h.assertTrue(loaded != null && loaded.canOpen(owner) && !loaded.canOpen(other), "owner after reload");
            h.assertTrue(ItemStack.matches(loaded.items.getItem(0), new ItemStack(Items.DIAMOND, 3)), "items after reload");
            boolean opAccess = MiteConfig.STRONGBOX_OP_ACCESS.get();
            boolean opOn, otherOn, opOff;
            try {
                MiteConfig.STRONGBOX_OP_ACCESS.set(true);
                opOn = box.canOpen(op);
                otherOn = box.canOpen(other);
                MiteConfig.STRONGBOX_OP_ACCESS.set(false);
                opOff = box.canOpen(op);
            } finally {
                MiteConfig.STRONGBOX_OP_ACCESS.set(opAccess);
            }
            h.assertTrue(opOn && !otherOn, "operator opens with strongbox_op_access, a player does not");
            h.assertFalse(opOff, "operator opens without strongbox_op_access");
            h.succeed();
        });
        // EF-ENC-09: the grindstone disenchants and gives no XP.
        MiteGameTests.add("enchanting_grindstone_no_xp", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.setBlock(POS, Blocks.GRINDSTONE);
            GrindstoneMenu menu = new GrindstoneMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)));
            menu.getSlot(0).set(enchanted(h, Items.IRON_SWORD, Enchantments.SHARPNESS, 5));
            menu.clicked(2, 0, ContainerInput.PICKUP, p);
            h.assertFalse(EnchantmentHelper.hasAnyEnchantments(menu.getCarried()), "disenchanted");
            h.assertTrue(menu.getCarried().is(Items.IRON_SWORD), "sword taken");
            h.assertEntityNotPresent(EntityTypes.EXPERIENCE_ORB);
            h.assertValueEqual(ProgressionService.xp(p), 0, "no XP");
            // X9: two damaged pickaxes give nothing: no combination repair on the grindstone (enchanted or not).
            GrindstoneMenu combine = new GrindstoneMenu(2, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)));
            for (int slot : new int[] {0, 1}) {
                ItemStack pick = slot == 0 ? new ItemStack(Items.IRON_PICKAXE) : enchanted(h, Items.IRON_PICKAXE, Enchantments.EFFICIENCY, 1);
                pick.setDamageValue(1000);
                combine.getSlot(slot).set(pick);
            }
            h.assertTrue(combine.getSlot(2).getItem().isEmpty(), "grindstone combined two pickaxes: " + combine.getSlot(2).getItem());
            h.succeed();
        });
        // EF-ENC-06: a disenchanting bottle strips an item on the anvil; the enchanting bottle stores 200 XP and gives them back.
        MiteGameTests.add("enchanting_bottles", 20, h -> {
            ServerPlayer p = MetabolismTests.player(h);
            h.setBlock(POS, Blocks.ANVIL);
            MiteAnvilMenu menu = new MiteAnvilMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(POS)), Material.IRON);
            menu.getSlot(0).set(enchanted(h, Items.IRON_SWORD, Enchantments.SHARPNESS, 3));
            menu.getSlot(1).set(new ItemStack(EnchantingContent.DISENCHANTING_BOTTLE.get(), 2));
            menu.clicked(2, 0, ContainerInput.PICKUP, p);
            h.assertTrue(menu.getCarried().is(Items.IRON_SWORD) && !EnchantmentHelper.hasAnyEnchantments(menu.getCarried()), "sword disenchanted");
            h.assertValueEqual(menu.getSlot(1).getItem().getCount(), 1, "one bottle used");
            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
            enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, enchanted(h, Items.IRON_SWORD, Enchantments.SHARPNESS, 3).get(DataComponents.ENCHANTMENTS));
            menu.getSlot(0).set(enchantedBook);
            h.assertTrue(menu.getSlot(2).getItem().is(Items.BOOK), "a disenchanted book is a plain book: " + menu.getSlot(2).getItem());
            menu.getSlot(0).set(ItemStack.EMPTY);

            ProgressionService.setXp(p, 500);
            ItemStack bottle = new ItemStack(EnchantingContent.ENCHANTING_BOTTLE.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, bottle);
            bottle.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertValueEqual(ProgressionService.xp(p), 300, "XP stored");
            h.assertValueEqual(bottle.get(EnchantingContent.STORED_XP.get()), 200, "stored_xp");
            bottle.use(h.getLevel(), p, InteractionHand.MAIN_HAND);
            h.assertValueEqual(ProgressionService.xp(p), 500, "XP back");
            h.assertValueEqual(bottle.get(EnchantingContent.STORED_XP.get()), 0, "bottle empty");
            h.succeed();
        });
    }
}
