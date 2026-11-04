package com.jethsportable.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jethsportable.JethsPortable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Player settings, stored as JSON in config/jethsportable.json. Call {@link #save()} after changing anything. */
public final class PortableClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("jethsportable.json");
	public static final int SORT_COUNT = 0;
	public static final int SORT_NAME = 1;
	public static final int SORT_MOD = 2;
	private static PortableClientConfig instance;

	public boolean showInventoryButton = true;
	public boolean notifyBatchDone = true;
	public boolean notifyNoFuel = true;
	public boolean notifyOutputFull = true;
	public boolean notifyBrewingDone = true;
	public boolean notifyStorageFull = true;
	public boolean notifyDidntFit = true;
	public int sort = SORT_COUNT;
	public boolean sortReversed = false;

	public static PortableClientConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static PortableClientConfig load() {
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				PortableClientConfig config = GSON.fromJson(reader, PortableClientConfig.class);
				if (config != null) return config;
			} catch (Exception e) {
				JethsPortable.LOGGER.warn("Couldn't read {}, using defaults", PATH, e);
			}
		}
		return new PortableClientConfig();
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			JethsPortable.LOGGER.warn("Couldn't write {}", PATH, e);
		}
	}
}
