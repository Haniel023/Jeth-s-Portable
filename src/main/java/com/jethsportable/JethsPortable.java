package com.jethsportable;

import com.jethsportable.config.PortableServerConfig;
import com.jethsportable.link.LinkHandler;
import com.jethsportable.machine.MachineWatcher;
import com.jethsportable.module.VirtualStations;
import com.jethsportable.network.PortablePayloads;
import com.jethsportable.network.ServerActions;
import com.jethsportable.registry.PortableRegistry;
import com.jethsportable.remote.RemoteAccess;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JethsPortable implements ModInitializer {
	public static final String MOD_ID = "jethsportable";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PortableServerConfig.get();
		PortableRegistry.register();
		PortablePayloads.register();
		ServerActions.register();
		LinkHandler.register();
		MachineWatcher.register();
		RemoteAccess.register();
		VirtualStations.register();
	}
}
