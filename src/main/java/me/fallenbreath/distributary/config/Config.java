/*
 * This file is part of the Distributary project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2023  Fallen_Breath and contributors
 *
 * Distributary is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Distributary is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Distributary.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.fallenbreath.distributary.config;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import me.fallenbreath.distributary.DistributaryMod;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class Config
{
	@NotNull
	private static Config INSTANCE = new Config();
	public boolean enabled = false;
	public boolean silent = false;
	public boolean haproxy_protocol = false;
	public final List<Route> routes = Lists.newArrayList();

	public static void load()
	{
		try
		{
			doLoad();
			validate();
		}
		catch (Exception e)
		{
			DistributaryMod.LOGGER.error("Failed to load config, distributary will be disabled", e);
		}
		if (Config.shouldLog())
		{
			DistributaryMod.LOGGER.info("Notes: logging is enabled");
			DistributaryMod.LOGGER.info("Enabled: {}, Route counts: {}", get().enabled, get().routes.size());
			for (Route route : get().routes)
			{
				DistributaryMod.LOGGER.info("- {}", route);
			}
		}
	}

	private static void doLoad() throws IOException
	{
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(DistributaryMod.MOD_ID);
		if (!configDir.toFile().isDirectory() && !configDir.toFile().mkdirs())
		{
			return;
		}

		Path configFile = configDir.resolve("config.json");
		if (!configFile.toFile().isFile())
		{
			DistributaryMod.LOGGER.info("config file not found, generating default config");
			try (InputStream inputStream = Config.class.getClassLoader().getResourceAsStream("default_config.json"))
			{
				String defaultConfig = IOUtils.toString(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8);
				Files.write(configFile, defaultConfig.getBytes(StandardCharsets.UTF_8));
			}
		}

		String configContent = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
		INSTANCE = Objects.requireNonNull(new Gson().fromJson(configContent, Config.class));
	}

	private static void validate()
	{
		for (Route route : get().routes)
		{
			List<String> allMatches = route.allMatches();
			if (allMatches.isEmpty())
			{
				throw new RuntimeException("match and matches missing");
			}
			for (String match : allMatches)
			{
				Address.of(Objects.requireNonNull(match, "match missing"));
			}

			Address.of(Objects.requireNonNull(route.target, "target missing"));
			if (route.mimic != null)
			{
				Address.of(route.mimic);
			}
			int hpv = route.haproxy_protocol_version;
			if (hpv != 1 && hpv != 2)
			{
				throw new IllegalArgumentException(String.format("bad haproxy_protocol_version %d, should be 1 or 2", hpv));
			}
		}
	}

	public static Config get()
	{
		return Objects.requireNonNull(INSTANCE);
	}

	public static boolean shouldLog()
	{
		return !get().silent;
	}
}
