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

package me.fallenbreath.distributary.network;

import com.google.common.collect.Lists;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import me.fallenbreath.distributary.config.Address;
import me.fallenbreath.distributary.config.Config;
import me.fallenbreath.distributary.config.Route;
import me.fallenbreath.distributary.network.sniffer.LegacyHandshakeSniffer;
import me.fallenbreath.distributary.network.sniffer.ModernHandshakeSniffer;
import me.fallenbreath.distributary.network.sniffer.Sniffer;
import me.fallenbreath.distributary.network.sniffer.SniffingResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.SocketException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class DistributaryPacketHandler extends ChannelInboundHandlerAdapter
{
	private static final Logger LOGGER = LogManager.getLogger();

	private final Consumer<ChannelHandlerContext> fallbackToVanilla;
	private final List<Sniffer> sniffers;

	public DistributaryPacketHandler(Consumer<ChannelHandlerContext> fallbackToVanilla)
	{
		this.fallbackToVanilla = fallbackToVanilla;
		this.sniffers = Lists.newArrayList(
				new ModernHandshakeSniffer(),
				new LegacyHandshakeSniffer()
		);
	}

	@Override
	public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg)
	{
		ByteBuf byteBuf = (ByteBuf)msg;
		byteBuf.markReaderIndex();

		loopLabel:
		for (Iterator<Sniffer> iterator = this.sniffers.iterator(); iterator.hasNext(); )
		{
			Sniffer sniffer = iterator.next();

			SniffingResult result = sniffer.sniff(byteBuf);
			byteBuf.resetReaderIndex();

			LOGGER.debug("sniffer {} result: {}", sniffer.getName(), result);
			switch (result.state)
			{
				case ACCEPT:
					LOGGER.debug("sniffer {} accept, address: {}", sniffer.getName(), result.address);
					Optional<Address> target = Optional.ofNullable(result.address).map(this::routeFor);
					if (target.isPresent())
					{
						this.startForwarding(ctx, byteBuf, target.get());
						return;
					}
					break loopLabel;
				case REJECT:
					LOGGER.debug("sniffer {} rejects", sniffer.getName());
					iterator.remove();
					break;
			}
		}

		LOGGER.debug("no sniffer accept, switch to vanilla");
		this.fallbackToVanilla.accept(ctx);
		ctx.pipeline().fireChannelRead(msg);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
	{
		if (Config.shouldLog()) LOGGER.error("handler error: {}", cause.toString());
		ctx.channel().close();
	}

	@Nullable
	private Address routeFor(Address address)
	{
		Config config = Config.get();
		for (Route route : config.routes)
		{
			if ("minecraft".equals(route.type))
			{
				Address match = Address.of(route.match);
				if (match.hostname.equals(address.hostname) && (match.port == null || match.port.equals(address.port)))
				{
					return Address.of(route.target);
				}
			}
		}
		return null;
	}

	@SuppressWarnings("Convert2Diamond")
	private void startForwarding(ChannelHandlerContext ctx, ByteBuf initBuf, Address remote)
	{
		// TODO: mimic
		if (Config.shouldLog()) LOGGER.info("Starting forwarding to {} for client {}", remote, ctx.channel().remoteAddress());

		Channel inboundChannel = ctx.channel();
		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(ctx.channel().eventLoop()).
				channel(inboundChannel.getClass()).
				option(ChannelOption.TCP_NODELAY, true).
				handler(new ChannelInitializer<Channel>()
				{
					@Override
					protected void initChannel(@NotNull Channel channel)
					{
						channel.pipeline().addLast(new ForwardHandler("remote", inboundChannel));
					}
				});

		final long t = System.nanoTime();
		ChannelFuture f = bootstrap.connect(remote.hostname, remote.port);

		f.addListener((ChannelFutureListener)future -> {
			if (Config.shouldLog()) LOGGER.info("Connected to remote {}, cost {}ms, ok = {}", remote, String.format("%.1f", (System.nanoTime() - t) / 1e6), future.isSuccess());
			if (future.isSuccess())
			{
				ctx.channel().pipeline().addLast(new ForwardHandler("client", future.channel()));
				ctx.pipeline().fireChannelRead(initBuf);
				ctx.read();
			}
			else
			{
				inboundChannel.close();
			}
		});

		ctx.channel().pipeline().remove(this);
	}

	private static class ForwardHandler extends ChannelInboundHandlerAdapter
	{
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
}
