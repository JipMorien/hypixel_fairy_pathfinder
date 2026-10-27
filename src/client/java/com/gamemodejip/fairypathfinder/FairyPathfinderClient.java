package com.gamemodejip.fairypathfinder;

import com.gamemodejip.fairypathfinder.data.FairySoulData;
import com.gamemodejip.fairypathfinder.tracking.FoundSoulTracker;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.gamemodejip.fairypathfinder.location.IslandDetector;

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
		IslandDetector.register();
		FoundSoulTracker.register();
	}

}
