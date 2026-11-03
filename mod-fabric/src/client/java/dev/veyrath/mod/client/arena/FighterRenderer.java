package dev.veyrath.mod.client.arena;

import dev.veyrath.mod.client.combat.CombatAnimations;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;

/**
 * A humanoid that fights like the player: the combat poses come from the shared render-state hook
 * (CombatAnimations), and the body always faces where the fighter faces. Vanilla would turn the
 * body toward the direction of travel, which looks wrong for a fighter circling its opponent.
 */
public class FighterRenderer<T extends PathfinderMob> extends HumanoidMobRenderer<T, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;

    public FighterRenderer(EntityRendererProvider.Context ctx, ModelLayerLocation layer, Identifier texture) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(layer)), 0.5f);
        this.texture = texture;
    }

    @Override
    public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) { return texture; }

    @Override
    public void extractRenderState(T entity, HumanoidRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (!CombatAnimations.facingOverridden(entity.getId())) {
            state.bodyRot = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
            state.yRot = 0;
        }
    }
}
