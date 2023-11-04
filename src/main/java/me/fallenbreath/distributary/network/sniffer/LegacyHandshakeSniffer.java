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

import java.nio.charset.StandardCharsets;

public class LegacyHandshakeSniffer implements Sniffer
{
	private static final int HEADER = 250;
	private static final String PING_HOST = "MC|PingHost";
	private static final int QUERY_PACKET_ID = 254;

	@Override
	public SniffingResult sniff(ByteBuf byteBuf)
	{
		try
		{
			// reference: net.minecraft.network.handler.LegacyQueryHandler#channelRead
			if (byteBuf.readUnsignedByte() != QUERY_PACKET_ID)
			{
				return SniffingResult.reject();
			}

			int magic = byteBuf.readableBytes();
			if (magic == 0)
			{
				DistributaryMod.LOGGER.debug("Legacy Ping (<1.3.x)");
			}
			else
			{
				if (byteBuf.readUnsignedByte() != 0x01)
				{
					return SniffingResult.reject();
				}

				if (byteBuf.isReadable())
				{
					// reference: net.minecraft.network.handler.LegacyQueryHandler.isLegacyQuery

					short s = byteBuf.readUnsignedByte();
					if (s != HEADER)
					{
						return SniffingResult.reject();
					}

					String string = readString(byteBuf);
					if (!PING_HOST.equals(string))
					{
						return SniffingResult.reject();
					}

					int i = byteBuf.readUnsignedShort();
					if (byteBuf.readableBytes() != i)
					{
						return SniffingResult.reject();
					}

					short t = byteBuf.readUnsignedByte();
					if (t < 73)
					{
						return SniffingResult.reject();
					}
					String hostname = readString(byteBuf);
					int port = byteBuf.readInt();
					if (port < 0 || port > 65535)
					{
						return SniffingResult.reject();
					}

					DistributaryMod.LOGGER.debug("Legacy Ping (1.6)");
					return SniffingResult.accept(new Address(hostname, port));
				}
				else
				{
					DistributaryMod.LOGGER.debug("Legacy Ping: (1.4-1.5.x)");
				}
			}
			return SniffingResult.acceptWithoutAddress();
		}
		catch (IndexOutOfBoundsException e)
		{
			return SniffingResult.incomplete();
		}
	}

	public static String readString(ByteBuf buf) 
	{
		int stringLen = buf.readShort();
		int bufLen = stringLen * 2;
		String string = buf.toString(buf.readerIndex(), bufLen, StandardCharsets.UTF_16BE);
		buf.skipBytes(bufLen);
		return string;
	}
}
