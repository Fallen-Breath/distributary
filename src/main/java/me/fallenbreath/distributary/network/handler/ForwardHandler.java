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
