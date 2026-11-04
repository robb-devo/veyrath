package dev.veyrath.mod.dragon;

import dev.veyrath.mod.combat.CombatSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Storm Horn: blow it and Vaelith comes. If she is nowhere near, she arrives from the sky far
 * off, flies in and lands beside you; if she is near, she comes to you. Sneak and blow to send her
 * away. One dragon per player: the horn always calls the same one.
 */
public class StormHorn extends Item {
    public StormHorn(Properties p) { super(p); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        sp.getCooldowns().addCooldown(sp.getItemInHand(hand), 40);
        sl.playSound(null, sp.getX(), sp.getY() + 1.5, sp.getZ(), CombatSounds.event("boss_awaken"), SoundSource.PLAYERS, 1.6f, 1.25f);
        DragonEntity mine = null;
        for (DragonEntity d : sl.getEntitiesOfClass(DragonEntity.class, sp.getBoundingBox().inflate(256), d -> sp.getUUID().equals(d.owner()))) {
            mine = d;
            break;
        }
        if (sp.isShiftKeyDown()) {
            if (mine != null) {
                mine.dismiss();
                sp.sendSystemMessage(Component.translatable("veyrath.dragon.dismissed"));
            }
            return InteractionResult.SUCCESS;
        }
        if (mine == null) {
            // She comes from far off: high, behind the horizon of the player's view.
            double yr = Math.toRadians(sp.getYRot() + 180 + (sl.getRandom().nextDouble() - 0.5) * 60);
            // Within the simulation distance, so she flies the whole way in.
            double x = sp.getX() - Math.sin(yr) * 64, z = sp.getZ() + Math.cos(yr) * 64;
            int ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            DragonEntity d = DragonEntities.VAELITH.create(sl, EntitySpawnReason.MOB_SUMMONED);
            if (d == null) return InteractionResult.FAIL;
            d.snapTo(x, Math.max(ground, (int) sp.getY()) + 34, z, (float) Math.toDegrees(yr) + 180, 0);
            d.setOwner(sp.getUUID());
            d.setFlying(true);
            sl.addFreshEntity(d);
            mine = d;
            sp.sendSystemMessage(Component.translatable("veyrath.dragon.answers"));
        }
        mine.call();
        return InteractionResult.SUCCESS;
    }
}
