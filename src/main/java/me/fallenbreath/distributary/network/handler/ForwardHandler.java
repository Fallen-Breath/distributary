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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import me.fallenbreath.distributary.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.net.SocketException;

class ForwardHandler extends ChannelInboundHandlerAdapter
{
	private static final Logger LOGGER = LogManager.getLogger();

	private final String logName;
	private final Channel targetChannel;
	private long byteCount;

	public ForwardHandler(String logName, Channel targetChannel)
	{
		this.logName = logName;
		this.targetChannel = targetChannel;
		this.byteCount = 0;
	}

	private void flushAndClose()
	{
		if (this.targetChannel.isActive())
		{
			this.targetChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg)
	{
		if (Config.shouldLog()) LOGGER.info("[{}] read {} bytes, forwarding", this.logName, ((ByteBuf)msg).readableBytes());
		this.byteCount += ((ByteBuf)msg).readableBytes();
		this.targetChannel.writeAndFlush(msg).addListener((ChannelFutureListener)future -> {
			if (future.isSuccess())
			{
				ctx.read();
			}
			else
			{
				this.flushAndClose();
			}
		});
	}

	@Override
	public void channelInactive(@NotNull ChannelHandlerContext ctx)
	{
		this.flushAndClose();
		if (Config.shouldLog()) LOGGER.info("[{}] forwarder disconnected, forwarded {} bytes", this.logName, this.byteCount);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
	{
		if (!(cause instanceof SocketException && "Connection reset".equals(cause.getMessage())))
		{
			if (Config.shouldLog()) LOGGER.error("[{}] forwarder error: {}", this.logName, cause);
		}
		this.flushAndClose();
	}
}
