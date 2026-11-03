package dev.veyrath.mod.arena;

import dev.veyrath.combat.ai.Style;
import dev.veyrath.mod.combat.CombatServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The sparring Duelist. It has no vanilla AI at all: {@link CombatServer} gives it a fighter with
 * the player's own moveset and a {@code DuelistBrain} that presses its inputs, so everything it
 * does the player can do too, and everything it does can be read, blocked, parried or dodged.
 * Its style (apprentice, adept, master) is kept in an entity tag so it survives saving.
 */
public class DuelistEntity extends PathfinderMob {
    static final String STYLE_TAG = "veyrath.style.";

    public DuelistEntity(EntityType<? extends DuelistEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    public String styleId() {
        for (String t : entityTags()) if (t.startsWith(STYLE_TAG)) return t.substring(STYLE_TAG.length());
        return "adept";
    }

    /** Sets the style and the health that goes with it (full). */
    public void setStyle(Style s) {
        for (String t : List.copyOf(entityTags())) if (t.startsWith(STYLE_TAG)) removeTag(t);
        addTag(STYLE_TAG + s.id);
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(s.health);
        setHealth((float) s.health);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel) CombatServer.ensureDuelist(this);
    }
}
