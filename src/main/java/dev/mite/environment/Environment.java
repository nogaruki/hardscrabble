package dev.mite.environment;

import dev.mite.MiteMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import org.jspecify.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Small world rules: obstructed functional blocks (EF-CRA-09), doused furnaces and TNT sparing metal ores (EF-MET-10),
 * MITE weather (EF-LUN-05: shorter rain; rain no longer drops torches since R123), halved ravines kept out of the oceans
 * (EF-MON-08, changelog l.441, datagen) and lava, explosions and mycelium (changelog l.375, l.436, l.444).
 */
public final class Environment {
    private Environment() {}

    /** EF-LUN-05: rain lasts 25 % less. */
    static final float RAIN_FACTOR = 0.75f;

    // ---- EF-CRA-09 ----

    /**
     * The side that must stay free, null for a block without the rule (EF-CRA-09): the front of furnaces, dispensers and droppers
     * (changelog l.652, R126), the top of anvils, enchanting tables and jukeboxes (l.653), cauldrons (l.613, R127) and workbenches.
     */
    static @Nullable Direction obstructionSide(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof AbstractFurnaceBlock) return state.getValue(AbstractFurnaceBlock.FACING);
        if (b instanceof DispenserBlock) return state.getValue(DispenserBlock.FACING); // droppers included
        return b instanceof CraftingTableBlock || b instanceof AnvilBlock || b instanceof EnchantingTableBlock || b instanceof JukeboxBlock
                || b instanceof AbstractCauldronBlock ? Direction.UP : null;
    }

    /**
     * The free side is covered by an opaque block (vanilla chest test). A workbench is also blocked by a liquid (l.702, R122) or a
     * layer of thin snow (l.535, R131) on top.
     */
    public static boolean isObstructed(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Direction side = obstructionSide(state);
        if (side == null) return false;
        BlockPos next = pos.relative(side);
        BlockState cover = level.getBlockState(next);
        if (cover.isRedstoneConductor(level, next)) return true;
        return state.getBlock() instanceof CraftingTableBlock && (!cover.getFluidState().isEmpty() || cover.getBlock() instanceof SnowLayerBlock);
    }

    // ---- EF-MET-10 ----

    /** A furnace goes out with an opaque block in front of it or flowing water touching it. Called by AbstractFurnaceBlockEntityMixin. */
    public static boolean isDoused(Level level, BlockPos pos, BlockState furnace) {
        BlockPos front = pos.relative(furnace.getValue(AbstractFurnaceBlock.FACING));
        if (level.getBlockState(front).isSolidRender()) return true;
        for (Direction d : Direction.values()) {
            FluidState fluid = level.getFluidState(pos.relative(d));
            if (fluid.is(FluidTags.WATER) && !fluid.isSource()) return true;
        }
        return false;
    }

    /** Metal ores survive TNT; coal and gems do not. */
    static void spareOres(ExplosionEvent.Detonate e) {
        if (e.getExplosion().getDirectSourceEntity() instanceof PrimedTnt)
            e.getAffectedBlocks().removeIf(p -> e.getLevel().getBlockState(p).is(dev.mite.metallurgy.Metallurgy.SPARED_BY_TNT));
    }

    // ---- EF-LUN-05 ----

    private static int rainTimeBefore = -1;

    public static void register(IEventBus modBus) {
        var bus = NeoForge.EVENT_BUS;
        // Only the block's own function is denied: items are still used and blocks still placed against it (vanilla blocked chest).
        // Before the iron anvil handler, which skips a denied block use.
        bus.addListener(EventPriority.HIGH, (PlayerInteractEvent.RightClickBlock e) -> {
            if (!isObstructed(e.getLevel(), e.getPos())) return;
            e.setUseBlock(TriState.FALSE);
            boolean sneakUse = e.getEntity().isSecondaryUseActive() && !(e.getEntity().getMainHandItem().isEmpty() && e.getEntity().getOffhandItem().isEmpty());
            if (!sneakUse && e.getEntity() instanceof ServerPlayer p) p.sendOverlayMessage(Component.translatable("hardscrabble.obstructed"));
        });
        bus.addListener(Environment::spareOres);
        bus.addListener(Environment::blastMycelium);
        bus.addListener((BlockEvent.NeighborNotifyEvent e) -> {
            if (e.getLevel() instanceof ServerLevel level) scorch(level, e.getPos());
        });
        // Rain duration is sampled when the rain starts (rain time 0 while raining): shorten it once.
        bus.addListener((LevelTickEvent.Pre e) -> {
            if (e.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) rainTimeBefore = level.getWeatherData().getRainTime();
        });
        bus.addListener((LevelTickEvent.Post e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
            shortenRain(rainTimeBefore, level.getWeatherData());
        });
    }

    /** A rain that just started this tick (rain time 0 before, sampled after) lasts {@link #RAIN_FACTOR} of its duration. */
    static void shortenRain(int rainTimeBefore, WeatherData weather) {
        if (rainTimeBefore == 0 && weather.isRaining() && weather.getRainTime() > 0) weather.setRainTime((int) (weather.getRainTime() * RAIN_FACTOR));
    }

    // ---- EF-MON-08 ----

    /** L03: the overworld has no mushroom fields. Called by OverworldBiomeBuilderMixin. */
    public static boolean keepsBiome(ResourceKey<Biome> biome) {
        return biome != Biomes.MUSHROOM_FIELDS;
    }


    /**
     * Changelog l.440-441 (R139): ravines are 50 % rarer, caves are not (the 26.3 noise caves are the "epic caves"). Only
     * {@code minecraft:canyon} is overridden; every other carver keeps its vanilla file.
     */
    public static void carvers(BootstrapContext<WorldCarver> c) {
        dev.mite.worldgen.WorldgenData.overrideVanilla(c, Carvers::bootstrap, (key, value) -> key == Carvers.CANYON ? halve(value) : null);
    }

    static WorldCarver halve(WorldCarver carver) {
        return carver instanceof CanyonWorldCarver w ? new CanyonWorldCarver(w.probability() / 2, w.y(), w.verticalRotation(), w.shape()) : carver;
    }

    /** Changelog l.441 (R139): no ravine under the oceans. */
    public static void biomeModifiers(BootstrapContext<BiomeModifier> c) {
        c.register(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, MiteMod.id("no_ocean_ravines")), new BiomeModifiers.RemoveCarversBiomeModifier(
                c.lookup(Registries.BIOME).getOrThrow(BiomeTags.IS_OCEAN), HolderSet.direct(c.lookup(Registries.CARVER).getOrThrow(Carvers.CANYON))));
    }

    // ---- Lava, explosions and mycelium ----

    /**
     * Changelog l.375 (R147) and l.444 (R139): grass and mycelium under lava turn to dirt at once, flowing lava included (vanilla
     * only does it on a random tick under a full source). Called for every block change, at the changed position.
     */
    static void scorch(ServerLevel level, BlockPos lava) {
        if (!level.getFluidState(lava).is(FluidTags.LAVA)) return;
        BlockPos below = lava.below();
        BlockState ground = level.getBlockState(below);
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.MYCELIUM)) level.setBlockAndUpdate(below, Blocks.DIRT.defaultBlockState());
    }

    /** Changelog l.436 (R140): every mycelium block within the blast radius that the explosion does not destroy becomes dirt. */
    static void blastMycelium(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        Vec3 center = e.getExplosion().center();
        int r = Mth.ceil(e.getExplosion().radius());
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(center).offset(-r, -r, -r), BlockPos.containing(center).offset(r, r, r))) {
            if (p.distToCenterSqr(center) <= r * r && level.getBlockState(p).is(Blocks.MYCELIUM) && !e.getAffectedBlocks().contains(p))
                level.setBlockAndUpdate(p, Blocks.DIRT.defaultBlockState());
        }
    }
}
