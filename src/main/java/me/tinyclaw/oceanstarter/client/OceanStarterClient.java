package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

/**
 * Client entrypoint for Ocean Overhaul. Registers the Megalodon's model layer and
 * its renderer (client-only — these classes are never touched on a dedicated server).
 */
public class OceanStarterClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(
				MegalodonModel.LAYER, MegalodonModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanStarter.MEGALODON, MegalodonRenderer::new);
		EntityRendererRegistry.register(OceanStarter.MEGALODON_SEGMENT, NoopEntityRenderer::new);

		// Reef Life passive mobs.
		EntityModelLayerRegistry.registerModelLayer(
				ReefFishModel.LAYER, ReefFishModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanStarter.REEF_FISH, ReefFishRenderer::new);

		EntityModelLayerRegistry.registerModelLayer(
				JellyfishModel.LAYER, JellyfishModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanStarter.JELLYFISH, JellyfishRenderer::new);

		// The Depths: hostile Abyssal Lurker.
		EntityModelLayerRegistry.registerModelLayer(
				AbyssalLurkerModel.LAYER, AbyssalLurkerModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanStarter.ABYSSAL_LURKER, AbyssalLurkerRenderer::new);

		// Feature 3: Harpoon projectile — stock billboard renderer (draws the harpoon item model
		// via FlyingItemEntity.getStack()). No custom model/texture/render code, no model layer.
		// FlyingItemEntityRenderer(Context, scale, lit) — javap-verified 3-arg ctor.
		EntityRendererRegistry.register(OceanStarter.HARPOON_ENTITY,
				ctx -> new FlyingItemEntityRenderer<>(ctx, 1.0f, true));

		// Feature 4: Aquarium BER — draws the captured creature swimming inside the tank by
		// reusing the reef/jelly models above (already registered, so ctx.getLayerModelPart
		// resolves). Uses the Fabric BlockEntityRendererRegistry wrapper — the vanilla
		// BlockEntityRendererFactories.register is private (not access-widened by Fabric API),
		// so the Fabric API helper is the public registration path, matching the EntityRenderer
		// registrations above.
		BlockEntityRendererRegistry.register(OceanStarter.AQUARIUM_BLOCK_ENTITY,
				AquariumBlockEntityRenderer::new);

		// Feature 4: render the Aquarium on the TRANSLUCENT layer so its semi-transparent glass
		// texture actually blends (the default SOLID layer ignores alpha and would draw the tank
		// as an opaque box, hiding the creature the BER draws inside). Vanilla stained glass uses
		// the same translucent layer.
		BlockRenderLayerMap.INSTANCE.putBlock(OceanStarter.AQUARIUM, RenderLayer.getTranslucent());
	}
}
