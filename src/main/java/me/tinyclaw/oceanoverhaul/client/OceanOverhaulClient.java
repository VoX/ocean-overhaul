package me.tinyclaw.oceanoverhaul.client;

import me.tinyclaw.oceanoverhaul.OceanOverhaul;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

/**
 * Client entrypoint for Ocean Overhaul. Registers every model layer + renderer: the
 * Megalodon (and its no-op segment renderer), Reef Fish, Jellyfish and Abyssal
 * Lurker, the Kraken mantle + its tentacles, the Harpoon's flying-item renderer,
 * the Aquarium's block-entity renderer, the non-SOLID block render layers (the
 * Aquarium + sea-glass family on translucent, the driftwood door/trapdoor on
 * cutout), and the plankton bloom particle factories + their ambient spawner /
 * wake-scan tick hook — client-only, these classes are never touched on a
 * dedicated server.
 */
public class OceanOverhaulClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(
				MegalodonModel.LAYER, MegalodonModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanOverhaul.MEGALODON, MegalodonRenderer::new);
		EntityRendererRegistry.register(OceanOverhaul.MEGALODON_SEGMENT, NoopEntityRenderer::new);

		// Reef Life passive mobs.
		EntityModelLayerRegistry.registerModelLayer(
				ReefFishModel.LAYER, ReefFishModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanOverhaul.REEF_FISH, ReefFishRenderer::new);

		EntityModelLayerRegistry.registerModelLayer(
				JellyfishModel.LAYER, JellyfishModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanOverhaul.JELLYFISH, JellyfishRenderer::new);

		// The Depths: hostile Abyssal Lurker.
		EntityModelLayerRegistry.registerModelLayer(
				AbyssalLurkerModel.LAYER, AbyssalLurkerModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanOverhaul.ABYSSAL_LURKER, AbyssalLurkerRenderer::new);

		// Feature 5: Kraken trench boss — the stationary mantle (with its emissive-eyes
		// feature) plus its ring of tentacles. Each tentacle is a real LivingEntity with
		// its own model + renderer, so hurt flash and death keel-over come from vanilla.
		EntityModelLayerRegistry.registerModelLayer(
				KrakenModel.LAYER, KrakenModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanOverhaul.KRAKEN, KrakenRenderer::new);
		EntityModelLayerRegistry.registerModelLayer(
				KrakenTentacleModel.LAYER, KrakenTentacleModel::getTexturedModelData);
		EntityRendererRegistry.register(OceanOverhaul.KRAKEN_TENTACLE, KrakenTentacleRenderer::new);

		// Feature 3: Harpoon projectile — stock billboard renderer (draws the harpoon item model
		// via FlyingItemEntity.getStack()). No custom model/texture/render code, no model layer.
		// FlyingItemEntityRenderer(Context, scale, lit) — javap-verified 3-arg ctor.
		EntityRendererRegistry.register(OceanOverhaul.HARPOON_ENTITY,
				ctx -> new FlyingItemEntityRenderer<>(ctx, 1.0f, true));

		// Feature 4: Aquarium BER — draws the captured creature swimming inside the tank by
		// reusing the reef/jelly models above (already registered, so ctx.getLayerModelPart
		// resolves). Uses the Fabric BlockEntityRendererRegistry wrapper — the vanilla
		// BlockEntityRendererFactories.register is private (not access-widened by Fabric API),
		// so the Fabric API helper is the public registration path, matching the EntityRenderer
		// registrations above.
		BlockEntityRendererRegistry.register(OceanOverhaul.AQUARIUM_BLOCK_ENTITY,
				AquariumBlockEntityRenderer::new);

		// Feature 4: render the Aquarium on the TRANSLUCENT layer so its semi-transparent glass
		// texture actually blends (the default SOLID layer ignores alpha and would draw the tank
		// as an opaque box, hiding the creature the BER draws inside). Vanilla stained glass uses
		// the same translucent layer.
		BlockRenderLayerMap.INSTANCE.putBlock(OceanOverhaul.AQUARIUM, RenderLayer.getTranslucent());

		// Audit L40: the rest of the glass/door-shaped blocks defaulted to SOLID, which silently
		// renders any future alpha texture opaque. Sea glass (frosted glass) goes TRANSLUCENT like
		// the stained/tinted-glass precedent and the Aquarium above; the driftwood door/trapdoor
		// go CUTOUT like their vanilla oak counterparts (binary-alpha window holes). All five
		// textures are 100%-opaque today, so this is a visual no-op until a texture gains alpha.
		BlockRenderLayerMap.INSTANCE.putBlock(OceanOverhaul.SEA_GLASS, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(OceanOverhaul.SEA_GLASS_STAIRS, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(OceanOverhaul.SEA_GLASS_SLAB, RenderLayer.getTranslucent());
		BlockRenderLayerMap.INSTANCE.putBlock(OceanOverhaul.DRIFTWOOD_DOOR, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(OceanOverhaul.DRIFTWOOD_TRAPDOOR, RenderLayer.getCutout());

		// Feature A: plankton bloom particles — sprite-backed factories (the Fabric
		// PendingParticleFactory route hands each factory its SpriteProvider), then the
		// client-side ambient spawner/wake scanner tick hook.
		ParticleFactoryRegistry.getInstance().register(
				OceanOverhaul.PLANKTON_GLOW, PlanktonGlowParticle.Factory::new);
		ParticleFactoryRegistry.getInstance().register(
				OceanOverhaul.PLANKTON_WAKE, PlanktonWakeParticle.Factory::new);
		PlanktonBloomClient.init();
	}
}
