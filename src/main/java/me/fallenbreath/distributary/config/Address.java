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

@SuppressWarnings("ClassCanBeRecord")
public final class Address
{
	public final String hostname;
	public final Integer port;

	public Address(String hostname, Integer port)
	{
		this.hostname = hostname;
		this.port = port;
	}

	public static Address of(String address)
	{
		int i = address.indexOf(':');
		if (i != -1)
		{
			String hostname = address.substring(0, i);
			int port = Integer.parseInt(address.substring(i + 1));
			return new Address(hostname, port);
		}
		else
		{
			return new Address(address, null);
		}
	}

	@Override
	public String toString()
	{
		String s = this.hostname;
		if (this.port != null)
		{
			s += ":" + this.port;
		}
		return s;
	}
}
