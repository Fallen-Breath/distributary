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

import com.google.common.collect.Lists;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.haproxy.*;
import me.fallenbreath.distributary.config.Address;
import me.fallenbreath.distributary.config.Config;
import me.fallenbreath.distributary.config.Route;
import me.fallenbreath.distributary.network.sniffer.LegacyHandshakeSniffer;
import me.fallenbreath.distributary.network.sniffer.ModernHandshakeSniffer;
import me.fallenbreath.distributary.network.sniffer.Sniffer;
import me.fallenbreath.distributary.network.sniffer.SniffingResult;
import me.fallenbreath.distributary.utils.SrvResolver;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class DistributaryPacketHandler extends ByteToMessageDecoder
{
	private static final Logger LOGGER = LogManager.getLogger();

	private final Consumer<ChannelHandlerContext> restoreToVanilla;
	private final List<Sniffer> sniffers;
	public InetSocketAddress realClientAddress = null;

	public DistributaryPacketHandler(Consumer<ChannelHandlerContext> restoreToVanilla)
	{
		this.restoreToVanilla = restoreToVanilla;
		this.sniffers = Lists.newArrayList(
				new ModernHandshakeSniffer(),
				new LegacyHandshakeSniffer()
		);
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list)
	{
		byteBuf.markReaderIndex();

		boolean routeFailed = false;

		loopLabel:
		for (Iterator<Sniffer> iterator = this.sniffers.iterator(); iterator.hasNext(); )
		{
			Sniffer sniffer = iterator.next();

			SniffingResult result = sniffer.sniff(byteBuf);
			byteBuf.resetReaderIndex();

			if (Config.shouldLog()) LOGGER.info("sniffer {} result: {}", sniffer.getName(), result);
			switch (result.state)
			{
				case ACCEPT:
					if (Config.shouldLog()) LOGGER.info("sniffer {} accept, address: {}", sniffer.getName(), result.address);
					Optional<RouteResult> target = Optional.ofNullable(result.address).
							map(address -> {
								// forge client stuff
								return address.withHostname(StringUtils.substringBefore(address.hostname, "\0"));
							}).
							map(this::routeFor);
					if (target.isPresent())
					{
						this.startForwarding(ctx, byteBuf, target.get());
						return;
					}
					else
					{
						if (Config.shouldLog()) LOGGER.info("no valid route for address {}", result.address);
						iterator.remove();
						routeFailed = true;
						break loopLabel;
					}
				case REJECT:
					LOGGER.debug("sniffer {} rejects", sniffer.getName());
					iterator.remove();
					break;
			}
		}

		if (this.sniffers.isEmpty() || routeFailed)
		{
			if (Config.shouldLog()) LOGGER.info("{}, switch to vanilla", this.sniffers.isEmpty() ? "no available sniffer" : "route failed");
			this.restoreToVanilla.accept(ctx);
			ctx.pipeline().fireChannelRead(byteBuf.retain());
		}
		else
		{
			if (Config.shouldLog()) LOGGER.info("all remaining sniffers say packet incomplete, waiting for new messages");
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
	{
		if (Config.shouldLog()) LOGGER.error("handler error: {}", cause.toString());
		ctx.channel().close();
	}

	@Nullable
	private RouteResult routeFor(Address address)
	{
		String hostname = StringUtils.removeEnd(address.hostname, ".");
		Config config = Config.get();
		for (Route route : config.routes)
		{
			if ("minecraft".equals(route.type))
			{
				boolean found = false;
				for (String matchStr : route.allMatches())
				{
					Address match = Address.of(matchStr);
					boolean hostnameOk = StringUtils.removeEnd(match.hostname, ".").equals(hostname);
					boolean portOk = match.port == null || match.port.equals(address.port);
					if (hostnameOk && portOk)
					{
						found = true;
						break;
					}
				}
				if (found)
				{
					Address finalAddress = Address.of(route.target);
					if (finalAddress.port == null)
					{
						Address srv = SrvResolver.resolveSrv(finalAddress.hostname);
						finalAddress = srv != null ? srv : finalAddress.withPort(25565);
					}
					return new RouteResult(route, finalAddress);
				}
			}
		}
		return null;
	}

	@SuppressWarnings("Convert2Diamond")  // java8 needs it
	private void startForwarding(ChannelHandlerContext ctx, ByteBuf initBuf, RouteResult routeResult)
	{
		// TODO: mimic
		Address targetAddr = routeResult.address;
		Channel clientChannel = ctx.channel();
		if (Config.shouldLog()) LOGGER.info("Starting forwarding to {} for client {}", targetAddr, clientChannel.remoteAddress());

		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(clientChannel.eventLoop()).
				channel(clientChannel.getClass()).
				option(ChannelOption.TCP_NODELAY, true).
				handler(new ChannelInitializer<Channel>()
				{
					@Override
					protected void initChannel(@NotNull Channel channel)
					{
						channel.pipeline().addLast(new ForwardHandler("target", clientChannel));
					}
				});

		final long t = System.nanoTime();
		ChannelFuture f = bootstrap.connect(targetAddr.hostname, targetAddr.port);

		PacketHolder packetHolder = new PacketHolder(1024);  // Minecraft handshake should never longer than 1KiB

		f.addListener((ChannelFutureListener)future -> {
			Channel targetChannel = future.channel();
			ByteBuf heldClientBuf = packetHolder.export(ctx);
			if (Config.shouldLog())
			{
				LOGGER.info(
						"Connected to target {}, cost {}ms, ok = {}, held buf size = {}",
						targetAddr, String.format("%.1f", (System.nanoTime() - t) / 1e6),
						future.isSuccess(), heldClientBuf != null ? heldClientBuf.readableBytes() : "null"
				);
			}
			if (!clientChannel.isActive())
			{
				targetChannel.close();
				return;
			}

			//noinspection LoopStatementThatDoesntLoop
			while (future.isSuccess() && heldClientBuf != null)
			{
				if (routeResult.route.haproxy_protocol)
				{
					if (Config.shouldLog()) LOGGER.info("Sending HAProxy proxy protocol v{}", routeResult.route.haproxy_protocol_version);
					HAProxyMessage haProxyMessage = this.makeProxyProtocolHeader(clientChannel, targetChannel, routeResult);
					if (haProxyMessage == null)
					{
						if (Config.shouldLog()) LOGGER.warn("Failed to create a HAProxy message, disconnecting");
						break;
					}
					targetChannel.pipeline().addLast(HAProxyMessageEncoder.INSTANCE);
					targetChannel.writeAndFlush(haProxyMessage);
					targetChannel.pipeline().remove(HAProxyMessageEncoder.INSTANCE);
				}

				ctx.pipeline().remove(packetHolder);
				ctx.pipeline().addLast(new ForwardHandler("client", targetChannel));
				ctx.pipeline().fireChannelRead(heldClientBuf);

				return;
			}

			clientChannel.close();
			targetChannel.close();
		});

		ctx.pipeline().remove(this);
		ctx.pipeline().addLast(packetHolder);
		ctx.pipeline().fireChannelRead(initBuf);
	}

	@Nullable
	private HAProxyMessage makeProxyProtocolHeader(Channel clientChannel, Channel targetChannel, RouteResult routeResult)
	{
		InetSocketAddress clientAddr = (InetSocketAddress)clientChannel.remoteAddress();
		InetSocketAddress targetAddr = (InetSocketAddress)targetChannel.remoteAddress();
		if (this.realClientAddress != null)
		{
			clientAddr = this.realClientAddress;
		}

		HAProxyProxiedProtocol protocol;
		if (clientAddr.getAddress() instanceof Inet4Address && targetAddr.getAddress() instanceof Inet4Address)
		{
			protocol = HAProxyProxiedProtocol.TCP4;
		}
		else if (clientAddr.getAddress() instanceof Inet6Address && targetAddr.getAddress() instanceof Inet6Address)
		{
			protocol = HAProxyProxiedProtocol.TCP6;
		}
		else
		{
			if (Config.shouldLog()) LOGGER.warn("Mixed use of IPv4 and IPv6, cannot create a HAProxy protocol header. clientAddr: {}, targetAddr: {}", clientAddr, targetAddr);
			return null;
		}

		return new HAProxyMessage(
				routeResult.route.haproxy_protocol_version == 1 ? HAProxyProtocolVersion.V1 : HAProxyProtocolVersion.V2,
				HAProxyCommand.PROXY,
				protocol,
				clientAddr.getAddress().getHostAddress(),
				targetAddr.getAddress().getHostAddress(),
				clientAddr.getPort(),
				targetAddr.getPort()
		);
	}
}
