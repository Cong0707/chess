package io.github.cong.chess.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.CharsetUtil


data class Chess( val data: Array<IntArray> = Array(15) { IntArray(15) { 0 } } ){
    fun get(row: Int, col: Int) = data[row][col]
    fun set(row: Int, col: Int, value: Int) { data[row][col] = value }
}

fun serializeChess(chess: Chess) = chess.data.joinToString(";") { row ->
    row.joinToString("") { it.toString() } // 去掉逗号
}

fun deserializeChess(str: String): Chess {
    return Chess(str.split(";").map { row ->
        row.map { it.toString().toInt() }.toIntArray() // 每个字符单独转 Int
    }.toTypedArray())
}

data class Room(val players: MutableList<String> = mutableListOf(), val chess: Chess = Chess()) //房间人员 棋谱

data class Connection(val player: String, var room: Room?)

val rooms = mutableMapOf<String, Room>() //房间号 房间
val playerChannels = mutableMapOf<ChannelHandlerContext, Connection>() // 玩家 socket

/** Launches the server application.  */
object ServerLauncher {

    @JvmStatic
    fun main(args: Array<String>) {

        val bossGroup = NioEventLoopGroup(1)
        val workerGroup = NioEventLoopGroup()

        try {
            val bootstrap = ServerBootstrap()
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel::class.java)
                .childHandler(object : ChannelInitializer<SocketChannel>() {
                    override fun initChannel(ch: SocketChannel) {
                        ch.pipeline().addLast(GameServerHandler())
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true)

            val channelFuture = bootstrap.bind(6777).sync()
            println("Server started at 6777")
            channelFuture.channel().closeFuture().sync()
        } finally {
            bossGroup.shutdownGracefully()
            workerGroup.shutdownGracefully()
        }
    }
}

class GameServerHandler : ChannelInboundHandlerAdapter() {

    private lateinit var playerName: String // 保存当前连接对应的玩家名

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val inMsg = msg as io.netty.buffer.ByteBuf
        val received = inMsg.toString(CharsetUtil.UTF_8)
        println("Server received: $received")

        var response = "none"

        // 玩家登录
        if (received.startsWith("player: ")) {
            playerName = received.removePrefix("player: ").trim()
            playerChannels[ctx] = Connection(playerName, null)
            response = "rooms: ${rooms.keys}"
        }

        // 创建房间
        if (received == "new") {
            val playerName = playerChannels.getValue(ctx).player
            rooms[playerName] = Room()
            println("New room: $playerName")
            response = "createSuccess"
        }

        // 加入房间
        if (received.startsWith("join: ")) {
            val connection = playerChannels.getValue(ctx)
            val roomName = received.removePrefix("join: ").trim()
            val room = rooms[roomName]!!

            connection.room = room

            room.players.add(playerName)

            println("${connection.player} join room $roomName")
            if (room.players.size >= 2) response = "-1"
            else response = (room.players.size).toString()
        }

        // 请求棋盘状态
        if (received == "update") {
            val connection = playerChannels.getValue(ctx)
            val room = connection.room
            response = serializeChess(room!!.chess)
        }

        // 落子
        if (received.startsWith("place: ")) {
            val place = received.removePrefix("place: ").trim()
            val data = place.split(" ")

            val connection = playerChannels.getValue(ctx)
            val room = connection.room

            val row = data[0].toInt()
            val col = data[1].toInt()

            val value = room!!.players.indexOf(playerName) + 1

            room.chess.set(row, col, value)
            response = "success"
        }

        println("Response: $response")
        ctx.writeAndFlush(Unpooled.copiedBuffer("$response\n", CharsetUtil.UTF_8))
    }

    // 连接断开时清理
    override fun channelInactive(ctx: ChannelHandlerContext) {
        playerChannels.entries.removeIf { it.key == ctx }
        println("Player disconnected: $playerName")
    }

    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        cause.printStackTrace()
        ctx.close()
    }
}
