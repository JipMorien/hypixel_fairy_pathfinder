package com.gamemodejip.fairypathfinder;

import com.gamemodejip.fairypathfinder.data.FairySoulData;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point. Each feature class gets registered here as we build it.
 */
public class FairyPathfinderClient implements ClientModInitializer {
	public static final String MOD_ID = "fairypathfinder";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Fairy Pathfinder loaded");
		FairySoulData.load();
	}
}
