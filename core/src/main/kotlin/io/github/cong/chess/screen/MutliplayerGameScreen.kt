package io.github.cong.chess.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Dialog
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import io.github.cong.chess.Net
import io.github.cong.chess.Net.needSync
import io.github.cong.chess.Vars.camera
import io.github.cong.chess.Vars.game
import io.github.cong.chess.Vars.skin
import io.github.cong.chess.Vars.stage
import io.github.cong.chess.server.Chess
import io.github.cong.chess.server.boardSize
import io.github.cong.chess.server.deserializeChess
import kotlin.math.floor
import kotlin.math.min


class MutliplayerGameScreen(roomName: String) : Screen {

    private lateinit var shapeRenderer: ShapeRenderer

    // 以下为运行时计算：cellSize, offsetX, offsetY 会在 create/resize 时计算
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    // 棋盘状态：0=空，1=黑，2=白
    private var chess: Chess

    private var currentPlayer: Int

    private var gameOver = false

    init {
        recalcLayout()
        currentPlayer = Net.sendBlocking("join: $roomName")!!.toInt()
        chess = deserializeChess(Net.sendBlocking("update")!!)
    }

    private fun recalcLayout() {
        // 留一点边距
        val margin = 20f
        val vw = camera.viewportWidth
        val vh = camera.viewportHeight

        // cell 数是 boardSize - 1 个格间距（15 点有 14 个间隔）
        cellSize = min((vw - 2 * margin) / (boardSize - 1), (vh - 2 * margin) / (boardSize - 1))
        // 让棋盘在视口中居中
        val boardWidth = (boardSize - 1) * cellSize
        val boardHeight = (boardSize - 1) * cellSize
        offsetX = (vw - boardWidth) / 2f
        offsetY = (vh - boardHeight) / 2f
    }

    override fun show() {
        println("GameScreen")
        // 不占用 stage 输入，直接使用相机坐标转换
        Gdx.input.inputProcessor = null

        shapeRenderer = ShapeRenderer()
    }

    override fun render(delta: Float) {
        ScreenUtils.clear(0.95f, 0.9f, 0.8f, 1f)

        camera.update()
        shapeRenderer.projectionMatrix = camera.combined

        drawBoard()
        handleInput()
        if (needSync) {
            chess = deserializeChess(Net.sendBlocking("update")!!)
            needSync = false
        }
    }

    private fun drawBoard() {
        // 画网格线
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.DARK_GRAY
        val lastIndex = boardSize - 1
        for (i in 0..lastIndex) {
            // 横线
            shapeRenderer.line(offsetX, offsetY + i * cellSize, offsetX + lastIndex * cellSize, offsetY + i * cellSize)
            // 竖线
            shapeRenderer.line(offsetX + i * cellSize, offsetY, offsetX + i * cellSize, offsetY + lastIndex * cellSize)
        }
        shapeRenderer.end()

        // 画棋子：注意 circle 的位置使用格点中心
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                when (chess.get(r, c)) {
                    1 -> {
                        shapeRenderer.color = Color.BLACK
                        val cx = offsetX + c * cellSize
                        val cy = offsetY + r * cellSize
                        shapeRenderer.circle(cx, cy, cellSize * 0.45f, 32)
                    }
                    2 -> {
                        shapeRenderer.color = Color.WHITE
                        val cx = offsetX + c * cellSize
                        val cy = offsetY + r * cellSize
                        shapeRenderer.circle(cx, cy, cellSize * 0.45f, 32)
                    }
                }
            }
        }
        shapeRenderer.end()
    }

    private fun handleInput() {
        if (Gdx.input.justTouched() && !gameOver) {
            // 把屏幕坐标转换为世界坐标（相机坐标系）
            val v = Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)
            camera.unproject(v)
            val worldX = v.x
            val worldY = v.y

            // 计算最近的格点（四舍五入到最近的点）
            val col = floor((worldX - offsetX + cellSize / 2f) / cellSize).toInt()
            val row = floor((worldY - offsetY + cellSize / 2f) / cellSize).toInt()

            if (row in 0 until boardSize && col in 0 until boardSize && chess.get(row, col) == 0) {
                val result = Net.sendBlocking("place: $row $col")
                if (result == "done") {
                    //will wait for sync
                } else if (result!!.startsWith("message: ")) {
                    val msg = result.removePrefix("message: ")
                    showEndDialog(msg)
                    gameOver = true
                }
            }
        }
    }

    private fun showEndDialog(message: String) {
        Gdx.input.inputProcessor = stage
        val dialog = Dialog("游戏结束", skin)
        dialog.text(message).contentTable.pad(20f).align(Align.center)
/*
        val againButton = TextButton("再来一局", skin)
        againButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                game.setScreen(GameScreen())
                dialog.hide()
            }
        })
        dialog.add(againButton).width(200f).height(50f)*/

        val exitButton = TextButton("回到多人游戏列表", skin)
        exitButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                Net.disconnect()
                Net.connect()
                dialog.hide()
            }
        })
        dialog.add(exitButton).width(200f).height(50f)

        dialog.show(stage)
        stage.addActor(dialog)
    }

    override fun resize(width: Int, height: Int) {
        recalcLayout()
    }

    override fun pause() {}
    override fun resume() {}
    override fun hide() {
        println("GameScreen hide")
        shapeRenderer.dispose()
        this.dispose()
    }
    override fun dispose() {}
}
