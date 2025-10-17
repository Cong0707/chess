package io.github.cong.chess

import io.github.cong.chess.Net.needSync
import io.github.cong.chess.Vars.game
import io.github.cong.chess.screen.MultiplayerListScreen
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.DelimiterBasedFrameDecoder
import io.netty.handler.codec.string.StringEncoder
import io.netty.util.CharsetUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object Net {
    lateinit var group: EventLoopGroup
    lateinit var bootstrap: Bootstrap
    lateinit var channel: Channel
    var needSync = false

    // 当前等待响应的 future（每次调用 sendBlocking 会新建一个）
    @Volatile
    private var currentFuture: CompletableFuture<String>? = null

    fun connect() {
        group = NioEventLoopGroup()
        Vars.threadPool.execute {
            try {
                bootstrap = Bootstrap()
                bootstrap.group(group)
                    .channel(NioSocketChannel::class.java)
                    .handler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            ch.pipeline()
                                .addLast(
                                    DelimiterBasedFrameDecoder(1024 * 1024,
                                    Unpooled.wrappedBuffer("\n".toByteArray()))
                                )
                                .addLast(StringEncoder(CharsetUtil.UTF_8))
                                .addLast(GameClientHandler())
                        }
                    })

                //channel = bootstrap.connect("127.0.0.1", 6777).sync().channel()
                channel = bootstrap.connect("new.xem8k5.top", 6777).sync().channel()

                channel.closeFuture().sync()
            } finally {
                group.shutdownGracefully()
            }
        }
    }

    /** 阻塞发送：发送字符串后等待服务器返回 */
    fun sendBlocking(str: String, timeoutSeconds: Long = 10): String? {
        val future = CompletableFuture<String>()
        currentFuture = future

        // 发送数据
        channel.writeAndFlush(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8))

        // 阻塞等待返回
        return try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            currentFuture = null
        }
    }

    fun disconnect() {
        group.shutdownGracefully()
    }

    /** 被 Handler 调用，用于唤醒阻塞线程 */
    internal fun handleResponse(msg: String) {
        currentFuture?.complete(msg)
    }

    class GameClientHandler : SimpleChannelInboundHandler<ByteBuf>() {
        override fun channelActive(ctx: ChannelHandlerContext) {
            // 连接成功后，发送一个“登录”包
            val message = "player: ${Vars.preferences.getString("name")}"
            ctx.writeAndFlush(Unpooled.copiedBuffer(message, CharsetUtil.UTF_8))
        }

        override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
            val message = msg.toString(CharsetUtil.UTF_8)
            println("client received: $message")

            if (message.startsWith("rooms: ")) {
                val rooms = message
                    .removePrefix("rooms: ")
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                println(rooms)
                game.setScreen(MultiplayerListScreen(rooms))
            }else if (message.startsWith("sync")) {
                needSync = true
            }else {
                // 将收到的数据传给阻塞函数
                handleResponse(message)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            cause.printStackTrace()
            ctx.close()
        }
    }
}
