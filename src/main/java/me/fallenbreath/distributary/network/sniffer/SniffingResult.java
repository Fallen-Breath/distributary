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

package me.fallenbreath.distributary.network.sniffer;

import me.fallenbreath.distributary.config.Address;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("ClassCanBeRecord")
public final class SniffingResult
{
	public final State state;
	@Nullable public final Address address;

	public SniffingResult(State state, @Nullable Address address)
	{
		this.state = state;
		this.address = address;
	}

	public static SniffingResult accept(@NotNull Address address)
	{
		return new SniffingResult(State.ACCEPT, address);
	}

	public static SniffingResult acceptWithoutAddress()
	{
		return new SniffingResult(State.ACCEPT, null);
	}

	public static SniffingResult incomplete()
	{
		return new SniffingResult(State.INCOMPLETE, null);
	}

	public static SniffingResult reject()
	{
		return new SniffingResult(State.REJECT, null);
	}

	public enum State
	{
		ACCEPT, INCOMPLETE, REJECT;
	}
}