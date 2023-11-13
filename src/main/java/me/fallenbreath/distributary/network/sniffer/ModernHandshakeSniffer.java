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

import io.netty.buffer.ByteBuf;
import me.fallenbreath.distributary.DistributaryMod;
import me.fallenbreath.distributary.config.Address;
import me.fallenbreath.distributary.config.Config;
import net.minecraft.network.PacketByteBuf;

public class ModernHandshakeSniffer implements Sniffer
{
	@Override
	public SniffingResult sniff(ByteBuf byteBuf)
	{
		try
		{
			PacketByteBuf buf = new PacketByteBuf(byteBuf);
			int packetSize = buf.readVarInt();
			PacketByteBuf bodyBuf = new PacketByteBuf(buf.readBytes(packetSize));
			int packetId = bodyBuf.readVarInt();
			if (packetId != 0x00)  // HandshakeC2SPacket
			{
				if (Config.shouldLog()) DistributaryMod.LOGGER.warn("bad packet id {}", packetId);
				return SniffingResult.reject();
			}

			// ref: net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket
			int protocol = bodyBuf.readVarInt();
			String hostname = bodyBuf.readString(255);
			int port = bodyBuf.readUnsignedShort();
			int nextState = bodyBuf.readVarInt();

			DistributaryMod.LOGGER.debug("HandshakeC2SPacket protocol={} address={}:{} nextState={}", protocol, hostname, port, nextState);
			return SniffingResult.accept(new Address(hostname, port));
		}
		catch (IndexOutOfBoundsException e)
		{
			return SniffingResult.incomplete();
		}
	}
}
