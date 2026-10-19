package dev.mite.harvest;

import dev.mite.player.PlayerLevel;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.mite.MiteMod;
import dev.mite.core.Formulas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/** Block hardness and harvest level, mining speed and tool wear (EF-OUT-02..06). */
public final class HarvestRules {
    private HarvestRules() {}

    /** MITE hardness and minimum tool level of a block (EF-OUT-02). */
    public record Hardness(float hardness, int level) {
        public static final Codec<Hardness> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("hardness").forGetter(Hardness::hardness),
                Codec.INT.fieldOf("level").forGetter(Hardness::level)).apply(i, Hardness::new));
    }

    public static final DataMapType<Block, Hardness> BLOCK_HARDNESS = DataMapType.builder(MiteMod.id("block_hardness"), Registries.BLOCK, Hardness.CODEC)
            .synced(Hardness.CODEC, true).build();

    /**
     * The {@code hardscrabble:block_hardness} entry, or the fallback rule for blocks without one (EF-OUT-02 CA-2), always with the vanilla hardness:
     * logs level 1; blocks that vanilla requires the correct tool for and that a tool type mines efficiently (mineable tags)
     * level 2, 3 with {@code needs_iron_tool}, 4 with {@code needs_diamond_tool}; every other block level 0.
     */
    public static Hardness hardness(BlockState state) {
        Hardness entry = state.getData(BLOCK_HARDNESS);
        if (entry != null) return entry;
        float vanilla = state.getBlock().defaultDestroyTime();
        if (state.is(BlockTags.LOGS)) return new Hardness(vanilla, 1);
        boolean mineable = state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL) || state.is(BlockTags.MINEABLE_WITH_HOE);
        if (!state.requiresCorrectToolForDrops() || !mineable) return new Hardness(vanilla, 0);
        return new Hardness(vanilla, state.is(BlockTags.NEEDS_DIAMOND_TOOL) ? 4 : state.is(BlockTags.NEEDS_IRON_TOOL) ? 3 : 2);
    }

    /**
     * Level 0: anyone, except a fallback block vanilla requires a tool for without being mineable (cobweb): the vanilla verdict stays.
     * Level 1+: a tool efficient on the block whose material level is high enough (EF-OUT-02, EF-OUT-03, EF-OUT-12).
     */
    public static boolean canHarvest(ItemStack stack, BlockState state) {
        return canHarvest(stack, state, hardness(state));
    }

    private static boolean canHarvest(ItemStack stack, BlockState state, Hardness hardness) {
        int level = hardness.level();
        if (level <= 0) return state.getData(BLOCK_HARDNESS) != null || !state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state);
        Tool tool = stack.get(DataComponents.TOOL);
        return tool != null && tool.getMiningSpeed(state) > tool.defaultMiningSpeed()
                && ToolStats.of(stack).map(i -> i.material().toolHarvestLevel() >= level).orElse(false);
    }

    /**
     * EF-OUT-12: with {@code tool_lashing} on, a flint or obsidian tool recipe without string or sinew is refused, the flint hatchet
     * included (changelog R150: knife, hatchet, shovel, axe); flint and obsidian tools also have a lashed recipe variant.
     */
    public static boolean missingLashing(ItemStack result, java.util.List<ItemStack> grid) {
        return dev.mite.config.MiteConfig.TOOL_LASHING.get()
                && ToolStats.of(result).filter(i -> i.material() == dev.mite.core.Material.OBSIDIAN || i.material() == dev.mite.core.Material.FLINT).isPresent()
                && grid.stream().noneMatch(s -> s.is(HarvestContent.LASHINGS));
    }

    static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        event.setCanHarvest(canHarvest(event.getEntity().getMainHandItem(), event.getTargetBlock()));
    }

    /**
     * Speed 0 when a level 1+ block cannot be harvested; otherwise vanilla speed x (vanilla / MITE hardness) x level bonus, and
     * Mining Fatigue -20 % per level instead of the vanilla x0.3 per level (EF-OUT-04).
     */
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockState state = event.getState();
        Hardness h = hardness(state);
        if (h.level() > 0 && !canHarvest(player.getMainHandItem(), state, h)) {
            event.setNewSpeed(0);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                dev.mite.crafting.TimedCrafting.refuse(sp, "hardscrabble.harvest.tool_too_weak"); // EF-PRG-03
                if (player.getMainHandItem().isEmpty() && state.is(net.minecraft.tags.BlockTags.LOGS)) dev.mite.rules.Hints.show(sp, "log"); // EF-PRG-02 CA-2
            }
            return;
        }
        float mite = h.hardness();
        float vanilla = state.getBlock().defaultDestroyTime();
        float ratio = mite > 0 && vanilla > 0 ? vanilla / mite : 1;
        var fatigue = player.getEffect(MobEffects.MINING_FATIGUE);
        int f = fatigue == null ? 0 : fatigue.getAmplifier() + 1;
        float fatigueFix = f == 0 ? 1 : f >= 5 ? 0 : (1 - 0.2f * f) / (float) Math.pow(0.3, f);
        event.setNewSpeed(event.getNewSpeed() * ratio * fatigueFix * (1 + Formulas.levelSpeedBonus(PlayerLevel.get(player))));
    }

    /**
     * Block wear (EF-OUT-06). Applied when the drops spawn, after vanilla has decided the harvest with the intact tool,
     * so the block that breaks the tool still drops. The Equipment Decay curse multiplies it (EF-MAL-02 #1).
     */
    static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) return;
        ItemStack held = player.getMainHandItem();
        ToolStats.of(held).ifPresent(info ->
                held.hurtAndBreak(Formulas.blockWear(hardness(event.getState()).hardness(), info.type()) * dev.mite.curse.Curses.wearFactor(player), player, EquipmentSlot.MAINHAND));
    }
}
