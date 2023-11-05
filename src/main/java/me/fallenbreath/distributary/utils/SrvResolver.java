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

package me.fallenbreath.distributary.utils;

import me.fallenbreath.distributary.config.Address;
import org.apache.commons.lang3.StringUtils;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

public class SrvResolver
{
	public static Address resolveSrv(String address)
	{
		try
		{
			Class.forName("com.sun.jndi.dns.DnsContextFactory");

			Hashtable<String, String> hashtable = new Hashtable<>();
			hashtable.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
			hashtable.put("java.naming.provider.url", "dns:");
			hashtable.put("com.sun.jndi.dns.timeout.retries", "1");
			DirContext dirContext = new InitialDirContext(hashtable);

			Attributes attributes = dirContext.getAttributes("_minecraft._tcp." + address, new String[]{"SRV"});
			String[] parts = attributes.get("srv").get().toString().split(" ", 4);

			return new Address(StringUtils.removeEnd(parts[3], "."), Integer.parseInt(parts[2]));
		}
		catch (Throwable var6)
		{
			return null;
		}
	}
}
