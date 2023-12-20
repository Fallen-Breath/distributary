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

import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.Nullable;

public final class Route
{
	public String type;
	public String match;
	public String target;
	@Nullable public String mimic;

	public boolean haproxy_protocol = false;
	public int haproxy_protocol_version = 2;

	@Nullable
	public String mimic()
	{
		return mimic;
	}

	@SuppressWarnings("SwitchStatementWithTooFewBranches")
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("(").append(this.type).append(") ");
		switch (this.type)
		{
			case "minecraft":
				sb.append(this.match).append(" -> ").append(this.target);
				if (!Strings.isEmpty(this.mimic()))
				{
					sb.append(" [mimic=").append(this.mimic()).append("]");
				}
				break;
			default:
				sb.append("<unknown>");
				break;
		}
		if (this.haproxy_protocol)
		{
			sb.append("[ha=").append(this.haproxy_protocol_version).append("]");
		}
		return sb.toString();
	}
}
