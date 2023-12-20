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

package me.fallenbreath.distributary.network.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import me.fallenbreath.distributary.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PacketHolder extends ChannelInboundHandlerAdapter
{
	private static final Logger LOGGER = LogManager.getLogger();

	private final int maxSize;
	private CompositeByteBuf compositeByteBuf;

	public PacketHolder(int maxSize)
	{
		this.maxSize = maxSize;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx)
	{
		this.compositeByteBuf = ctx.alloc().compositeBuffer();
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx)
	{
		this.compositeByteBuf.release();
		this.compositeByteBuf = null;
	}

	@Override
	public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg)
	{
		int sizeToBe = this.compositeByteBuf.readableBytes() + ((ByteBuf)msg).readableBytes();
		if (sizeToBe > this.maxSize)
		{
			if (Config.shouldLog()) LOGGER.error("Too many bytes to hold ({} / {}) bytes, disconnect now", sizeToBe, this.maxSize);
			ctx.channel().close();
		}
		else
		{
			if (Config.shouldLog()) LOGGER.info("[holder] read {} bytes, holding", ((ByteBuf)msg).readableBytes());
			this.compositeByteBuf.addComponent(true, ((ByteBuf)msg).retain());
		}
	}

	@Nullable
	public ByteBuf export(ChannelHandlerContext ctx)
	{
		if (this.compositeByteBuf == null)
		{
			return null;
		}
		ByteBuf output = ctx.alloc().buffer(this.compositeByteBuf.readableBytes());
		output.writeBytes(this.compositeByteBuf);
		this.compositeByteBuf.clear();
		return output;
	}
}
