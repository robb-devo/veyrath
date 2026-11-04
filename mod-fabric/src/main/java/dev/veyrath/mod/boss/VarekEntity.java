package dev.veyrath.mod.boss;

import dev.veyrath.combat.ai.BossBrain;
import dev.veyrath.combat.content.Varek;
import dev.veyrath.combat.fighter.Rules;
import dev.veyrath.combat.move.MoveSet;
import dev.veyrath.mod.combat.CombatServer;
import dev.veyrath.mod.combat.CombatSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Varek, the Oathbound (combat content: dev.veyrath.combat.content.Varek). Tells: a cold glint at
 * the visor for strikes, an ember flare on the blade for heavies, the oath-brand's red blaze for
 * the unblockable strike, and a burning mark on the ground where a leap or wave will land.
 */
public class VarekEntity extends BossEntity {
    private static final MoveSet MOVES = Varek.moveSet();

    public VarekEntity(EntityType<? extends VarekEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Varek.HEALTH)
                .add(Attributes.SCALE, Varek.SCALE)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.FOLLOW_RANGE, 64)
                .add(Attributes.STEP_HEIGHT, 1.2);
    }

    @Override public Component bossName() { return Component.translatable("veyrath.boss.varek"); }

    @Override public Component epithet() { return Component.translatable("veyrath.boss.varek.epithet"); }

    @Override protected BossEvent.BossBarColor barColor() { return BossEvent.BossBarColor.RED; }

    @Override public MoveSet combatMoves() { return MOVES; }

    @Override public Rules combatRules() { return Varek.rules(); }

    @Override protected BossBrain createBrain(long seed) { return Varek.brain(seed); }

    @Override public String idleClip() { return "vk_idle"; }

    /** The brand burns from phase 2 on (clients show embers on the blade). */
    public boolean branded() {
        return CombatServer.brainOf(this) instanceof BossBrain b && b.phase() >= 1;
    }

    @Override
    protected void tell(BossBrain.Attack a) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 eyes = at(0.92, 0.25), blade = at(0.6, 1.2);
        switch (a.tell()) {
            case STRIKE -> {
                CombatServer.vfx(this, "tell_strike", eyes.x, eyes.y, eyes.z);
                level.playSound(null, eyes.x, eyes.y, eyes.z, CombatSounds.event("tell_strike"), SoundSource.HOSTILE, 1.4f, 0.8f);
            }
            case HEAVY -> {
                CombatServer.vfx(this, "vk_tell_heavy", blade.x, blade.y, blade.z);
                level.playSound(null, blade.x, blade.y, blade.z, CombatSounds.event("tell_heavy"), SoundSource.HOSTILE, 1.6f, 0.8f);
            }
            case UNBLOCKABLE -> {
                CombatServer.vfx(this, "vk_tell_oath", eyes.x, eyes.y, eyes.z);
                level.playSound(null, eyes.x, eyes.y, eyes.z, CombatSounds.event("tell_unblockable"), SoundSource.HOSTILE, 2.0f, 0.7f);
            }
            case AREA -> {
                // Where it lands: the player's feet now (the leap tracks; the wave runs along the facing).
                ServerPlayer target = level.getNearestPlayer(this, 40) instanceof ServerPlayer sp ? sp : null;
                if (target != null) {
                    if (a.move().equals("vk_wave")) {
                        Vec3 to = target.position().subtract(position());
                        double len = Math.min(22, to.horizontalDistance());
                        Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
                        for (int i = 1; i <= 5; i++) {
                            Vec3 p = position().add(dir.scale(len * i / 5.0));
                            CombatServer.vfx(this, "tell_area_line", p.x, p.y + 0.05, p.z);
                        }
                    } else {
                        CombatServer.vfx(this, "tell_area", target.getX(), target.getY() + 0.05, target.getZ());
                    }
                }
                level.playSound(null, getX(), getY() + 2, getZ(), CombatSounds.event("tell_heavy"), SoundSource.HOSTILE, 1.8f, 0.6f);
            }
        }
    }

    @Override
    protected void phaseStarted(int phase) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = at(0.5, 0);
        CombatServer.vfx(this, phase == 1 ? "vk_brand_ignite" : "vk_desperation", c.x, c.y, c.z);
        level.playSound(null, c.x, c.y, c.z, CombatSounds.event("boss_roar"), SoundSource.HOSTILE, 2.5f, phase == 1 ? 0.85f : 0.7f);
    }
}
