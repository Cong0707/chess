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
import kotlin.random.Random

val boardSize = 15

data class Chess( val data: Array<IntArray> = Array(boardSize) { IntArray(boardSize) { 0 } } ){
    fun get(row: Int, col: Int) = data[row][col]
    fun set(row: Int, col: Int, value: Int) { data[row][col] = value }
}

fun serializeChess(chess: Chess) = chess.data.joinToString(";") { row ->
    row.joinToString("") { it.toString() }
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

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val inMsg = msg as io.netty.buffer.ByteBuf
        val received = inMsg.toString(CharsetUtil.UTF_8)
        println("Server received: $received")

        var response = "none"

        // 玩家登录
        if (received.startsWith("player: ")) {
            val playerName = received.removePrefix("player: ").trim()
            playerChannels[ctx] = Connection(playerName, null)
            response = "rooms: ${rooms.keys}"
        }

        // 创建房间
        if (received == "new") {
            val playerName = playerChannels.getValue(ctx).player
            val roomName = playerName + "#" + Random(System.currentTimeMillis()).nextInt().toHexString()
            rooms[roomName] = Room()
            println("New room: $roomName")
            response = "createSuccess: $roomName"
        }

        // 加入房间
        if (received.startsWith("join: ")) {
            val connection = playerChannels.getValue(ctx)
            val roomName = received.removePrefix("join: ").trim()
            println("${connection.player} join: $roomName")

            val room = rooms.getValue(roomName)
            connection.room = room
            room.players.add(connection.player)

            println("${connection.player} joined room $roomName")

            var value = room.players.indexOf(connection.player) + 1
            if (value > 2) {
                value = -1
            }

            response = value.toString()
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

            val playerName = playerChannels.getValue(ctx).player

            val value = room!!.players.indexOf(playerName) + 1

            var count2 = 0
            var count1 = 0
            room.chess.data.forEach { ints ->
                count2 += ints.filter {
                    it == 2
                }.size
                count1 += ints.filter {
                    it == 1
                }.size
            }

            val permit = if (value == 1){
                count2 == count1
            } else if (value == 2) {
                count2 < count1
            } else {
                false
            }

            if (permit) {
                println("${connection.player} place $value $row $col $playerName")

                room.chess.set(row, col, value)
                if (checkWin(room.chess, row, col, value)) {
                    val color = when (value) {
                        1 -> "黑"
                        2 -> "白"
                        else -> ""
                    }
                    room.players.forEach { player ->
                        playerChannels.filter { it.value.player == player }.keys.first().apply {
                            writeAndFlush(Unpooled.copiedBuffer("message: 游戏结束, $playerName($color)获胜\n", CharsetUtil.UTF_8))
                            disconnect()
                        }
                    }
                }
                else {
                    room.players.forEach { player ->
                        playerChannels.filter { it.value.player == player }.keys.first()
                            .writeAndFlush(Unpooled.copiedBuffer("sync\n", CharsetUtil.UTF_8))
                    }
                }
            }
            response = "done"
        }

        println("Response: $response")
        ctx.writeAndFlush(Unpooled.copiedBuffer("$response\n", CharsetUtil.UTF_8))
    }

    // 连接断开时清理
    override fun channelInactive(ctx: ChannelHandlerContext) {
        val playerName = playerChannels.getValue(ctx).player
        playerChannels.entries.removeIf { it.key == ctx } //大退 彻底删除
        rooms.values.forEach { room ->
            room.players.remove(playerName)
        }
        rooms.filter { it.value.players.isEmpty() }.forEach { rooms.remove(it.key) }
        println("Player disconnected: $playerName")
    }

    @Deprecated("Deprecated in Java")
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        val playerName = playerChannels.getValue(ctx).player
        println("Exception from ${playerName}: ${cause.message}")
        ctx.close() // 出错后关闭
    }

    private fun checkWin(chess: Chess, row: Int, col: Int, player: Int): Boolean {
        val dirs = arrayOf(
            intArrayOf(1, 0),  // 横
            intArrayOf(0, 1),  // 竖
            intArrayOf(1, 1),  // 右下
            intArrayOf(1, -1)  // 右上
        )
        for (dir in dirs) {
            var count = 1
            for (i in 1..4) {
                val r = row + i * dir[0]
                val c = col + i * dir[1]
                if (r !in 0 until boardSize || c !in 0 until boardSize || chess.get(r, c) != player) break
                count++
            }
            for (i in 1..4) {
                val r = row - i * dir[0]
                val c = col - i * dir[1]
                if (r !in 0 until boardSize || c !in 0 until boardSize || chess.get(r, c) != player) break
                count++
            }
            if (count >= 5) return true
        }
        return false
    }
}
