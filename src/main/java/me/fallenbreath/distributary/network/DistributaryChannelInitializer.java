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

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import me.fallenbreath.distributary.config.Config;
import me.fallenbreath.distributary.mixins.ServerNetworkIoChannelInitializerAccessor;
import me.fallenbreath.distributary.network.handler.DistributaryPacketHandler;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;

public class DistributaryChannelInitializer extends ChannelInitializer<Channel>
{
	private final ServerNetworkIoChannelInitializerAccessor vanillaInitializer;

	public DistributaryChannelInitializer(ChannelInitializer<Channel> vanillaInitializer)
	{
		this.vanillaInitializer = (ServerNetworkIoChannelInitializerAccessor)vanillaInitializer;
	}

	@Override
	protected void initChannel(@NotNull Channel channel)
	{
		DistributaryPacketHandler distributaryPacketHandler = new DistributaryPacketHandler(ctx -> {
			for (String name : new String[]{
					"distributary_timeout",
					"distributary_handler",
					"distributary_haproxy_decoder",
					"distributary_haproxy_handler",
			})
			{
				if (channel.pipeline().get(name) != null)
				{
					channel.pipeline().remove(name);
				}
			}

			this.vanillaInit(channel);
			// vanilla handlers need this to init something
			ctx.pipeline().fireChannelActive();
		});

		channel.pipeline().addLast("distributary_timeout", new ReadTimeoutHandler(30));
		channel.pipeline().addLast("distributary_handler", distributaryPacketHandler);

		if (Config.get().haproxy_protocol)
		{
			channel.pipeline().addAfter("distributary_timeout", "distributary_haproxy_decoder", new HAProxyMessageDecoder());
			channel.pipeline().addAfter("distributary_haproxy_decoder", "distributary_haproxy_handler", new ChannelInboundHandlerAdapter()
			{
				@Override
				public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
				{
					if (msg instanceof HAProxyMessage)
					{
						String readAddr = ((HAProxyMessage)msg).sourceAddress();
						int realPort = ((HAProxyMessage)msg).sourcePort();
						distributaryPacketHandler.realClientAddress = new InetSocketAddress(readAddr, realPort);
					}
					else
					{
						super.channelRead(ctx, msg);
					}
				}
			});
		}
	}

	private void vanillaInit(Channel channel)
	{
		this.vanillaInitializer.invokeInitChannel(channel);
	}
}
