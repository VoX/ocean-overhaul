package me.tinyclaw.oceanstarter.client;

import me.tinyclaw.oceanstarter.OceanStarter;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

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
	}
}
