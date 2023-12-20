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
