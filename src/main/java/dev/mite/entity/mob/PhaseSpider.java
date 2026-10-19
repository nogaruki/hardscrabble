package dev.mite.entity.mob;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * EF-MOB-10 Phase Spider: spider health and speed, 3 damage, XP x2. Teleports short distances to attack victims (blinks a few
 * nodes ahead along its path) and to evade damage (changelog l.109, R180).
 */
public class PhaseSpider extends Spider {
    /** Blink every 3 s, up to 4 path nodes ahead [INFÉRÉ: "téléportation courte"]. */
    public static final int PHASE_INTERVAL = 60, PHASE_NODES = 4;
    /** l.109: 1 attack in 3 dodged by a blink of up to 4 blocks [INFÉRÉ]. */
    public static final int DODGE_ODDS = 3, DODGE_RANGE = 4;

    public PhaseSpider(EntityType<? extends PhaseSpider> type, Level level) {
        super(type, level);
        this.xpReward *= 2;
    }

    /** Health 12 of the MITE spider (analysis/03 §6.2), speed of the vanilla spider. */
    public static AttributeSupplier.Builder createAttributes() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (tickCount % PHASE_INTERVAL == 0) phase();
    }

    /** Teleports to a node further along the current path and skips the path there. */
    public boolean phase() {
        Path path = getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        int node = Math.min(path.getNextNodeIndex() + PHASE_NODES, path.getNodeCount() - 1);
        if (node <= path.getNextNodeIndex()) return false;
        Vec3 to = path.getEntityPosAtNode(this, node);
        if (!randomTeleport(to.x, to.y, to.z, true, state -> false)) return false;
        path.setNextNodeIndex(node);
        return true;
    }

    /** l.109: an attack (a blow or a shot, not lava, a fall or /kill) may be evaded by a blink, taking no damage. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.getEntity() != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !isInvulnerableTo(level, source)
                && getRandom().nextInt(DODGE_ODDS) == 0 && dodge()) return false;
        return super.hurtServer(level, source, damage);
    }

    /** Blinks to a random spot 1 to {@link #DODGE_RANGE} blocks away (16 tries, as the enderman). */
    public boolean dodge() {
        for (int i = 0; i < 16; i++) {
            double x = getX() + (getRandom().nextDouble() - 0.5) * 2 * DODGE_RANGE, z = getZ() + (getRandom().nextDouble() - 0.5) * 2 * DODGE_RANGE;
            double y = getY() + getRandom().nextInt(3) - 1;
            if (Math.abs(x - getX()) + Math.abs(z - getZ()) > 1 && randomTeleport(x, y, z, true, state -> false)) return true;
        }
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return MobSounds.get(getType(), MobSounds.Kind.AMBIENT, super.getAmbientSound());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return MobSounds.get(getType(), MobSounds.Kind.HURT, super.getHurtSound(source));
    }

    @Override
    protected SoundEvent getDeathSound() {
        return MobSounds.get(getType(), MobSounds.Kind.DEATH, super.getDeathSound());
    }
}
