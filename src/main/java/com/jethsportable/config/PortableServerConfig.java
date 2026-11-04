package com.jethsportable.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jethsportable.JethsPortable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server rules, stored as JSON in config/jethsportable-server.json. Levels are 1 to 6; a range or
 * link limit of -1 means no limit (and for range, any dimension).
 */
public final class PortableServerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("jethsportable-server.json");
	public static final int MAX_LEVEL = 6;
	private static PortableServerConfig instance;

	public int[] rangeByLevel = {24, 48, 96, 192, 512, -1};
	public int[] linksByLevel = {16, 32, 64, 128, 256, -1};
	/** Lowest Core level that keeps a linked machine's chunk loaded while it has work. 0 turns this off. */
	public int keepMachinesLoadedFromLevel = 3;
	/** Whether players can open linked chests and machines from afar. */
	public boolean allowRemoteOpening = true;
	/** Storage actions one player can make per second, to protect the server from a spamming client. */
	public int maxActionsPerSecond = 60;

	public static PortableServerConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public int range(int level) {
		return this.rangeByLevel[clampLevel(level) - 1];
	}

	public int maxLinks(int level) {
		return this.linksByLevel[clampLevel(level) - 1];
	}

	private static int clampLevel(int level) {
		return Math.max(1, Math.min(MAX_LEVEL, level));
	}

	private static PortableServerConfig load() {
		PortableServerConfig config = null;
		if (Files.exists(PATH)) {
			try (Reader reader = Files.newBufferedReader(PATH)) {
				config = GSON.fromJson(reader, PortableServerConfig.class);
			} catch (Exception e) {
				JethsPortable.LOGGER.warn("Couldn't read {}, using defaults", PATH, e);
			}
		}
		if (config == null || config.rangeByLevel == null || config.rangeByLevel.length != MAX_LEVEL
			|| config.linksByLevel == null || config.linksByLevel.length != MAX_LEVEL) {
			config = new PortableServerConfig();
		}
		config.save();
		return config;
	}

	private void save() {
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
