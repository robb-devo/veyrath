package dev.veyrath.mod.client.arena;

import dev.veyrath.mod.VeyrathMod;
import dev.veyrath.mod.arena.ArenaEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/** Renderers and model layers of the Proving Grounds humanoids (vanilla humanoid mesh, own textures). */
public final class ArenaRenderers {
    private ArenaRenderers() {}

    public static final ModelLayerLocation DUELIST = new ModelLayerLocation(VeyrathMod.id("duelist"), "main");
    public static final ModelLayerLocation DUMMY = new ModelLayerLocation(VeyrathMod.id("training_dummy"), "main");

    public static void init() {
        ModelLayerRegistry.registerModelLayer(DUELIST, ArenaRenderers::humanoid);
        ModelLayerRegistry.registerModelLayer(DUMMY, ArenaRenderers::humanoid);
        EntityRendererRegistry.register(ArenaEntities.DUELIST,
                ctx -> new FighterRenderer<>(ctx, DUELIST, VeyrathMod.id("textures/entity/duelist.png")));
        EntityRendererRegistry.register(ArenaEntities.TRAINING_DUMMY,
                ctx -> new FighterRenderer<>(ctx, DUMMY, VeyrathMod.id("textures/entity/training_dummy.png")));
    }

    private static LayerDefinition humanoid() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64);
    }
}
