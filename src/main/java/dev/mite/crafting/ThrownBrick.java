package dev.mite.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A thrown brick (changelog l.774, R119: "Bricks can now be thrown (+2 missile damage, breaks thin glass)"; l.679, R123: damage and
 * throw distance decreased). Right click with a brick throws it; the throw costs hunger like any thrown projectile (l.680,
 * {@code Formulas.HUNGER_THROW} in MetabolismService). The brick drops where it lands [INFÉRÉ].
 */
public class ThrownBrick extends ThrowableItemProjectile {
    /** Missile damage after the R123 decrease [INFÉRÉ: 2 halved]. */
    public static final float DAMAGE = 1;
    /** Launch speed [INFÉRÉ: R123 "decreased throw distance", below the snowball's 1.5]. */
    public static final float SPEED = 1;
    /** Ticks between two throws [INFÉRÉ]. */
    static final int COOLDOWN = 10;

    public ThrownBrick(EntityType<? extends ThrownBrick> type, Level level) {
        super(type, level);
    }

    public ThrownBrick(Level level, LivingEntity owner) {
        super(CraftingContent.THROWN_BRICK.get(), owner, level, new ItemStack(Items.BRICK));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.BRICK;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (level() instanceof ServerLevel level) hit.getEntity().hurtServer(level, damageSources().thrown(this, getOwner()), DAMAGE);
    }

    /** Thin glass (glass panes) breaks. */
    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        BlockPos pos = hit.getBlockPos();
        if (level() instanceof ServerLevel level && level.getBlockState(pos).is(Tags.BlockItems.GLASS_PANES.block())) level.destroyBlock(pos, true, this);
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level() instanceof ServerLevel level) {
            spawnAtLocation(level, new ItemStack(Items.BRICK));
            discard();
        }
    }

    /** Right click with a brick: throws it (both sides answer SUCCESS so the arm swings; the server spawns the brick). */
    static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        ItemStack stack = e.getItemStack();
        if (!stack.is(Items.BRICK)) return;
        Player player = e.getEntity();
        if (e.getLevel() instanceof ServerLevel level) throwFrom(level, player, stack);
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    public static ThrownBrick throwFrom(ServerLevel level, Player player, ItemStack stack) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F, 0.4F);
        ThrownBrick brick = new ThrownBrick(level, player);
        brick.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, SPEED, 1);
        level.addFreshEntity(brick);
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        stack.consume(1, player);
        return brick;
    }
}
