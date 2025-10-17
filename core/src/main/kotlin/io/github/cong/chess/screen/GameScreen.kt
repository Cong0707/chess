package io.github.cong.chess.screen

import com.badlogic.gdx.Game
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
import io.github.cong.chess.Vars.camera
import io.github.cong.chess.Vars.game
import io.github.cong.chess.Vars.skin
import io.github.cong.chess.Vars.stage
import kotlin.math.floor
import kotlin.math.min


class GameScreen: Screen {

    private lateinit var shapeRenderer: ShapeRenderer

    private val boardSize = 15
    // 以下为运行时计算：cellSize, offsetX, offsetY 会在 create/resize 时计算
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    // 棋盘状态：0=空，1=黑，2=白
    private val board = Array(boardSize) { IntArray(boardSize) }

    private var currentPlayer = 1 // 玩家执黑棋

    private var gameOver = false

    init {
        recalcLayout()
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
                when (board[r][c]) {
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

            if (row in 0 until boardSize && col in 0 until boardSize && board[row][col] == 0) {
                board[row][col] = currentPlayer
                if (checkWin(row, col, currentPlayer)) {
                    val msg = if (currentPlayer == 1) "玩家(黑棋)胜利!" else "白棋胜利!"
                    showEndDialog(msg)
                    gameOver = true
                }
                currentPlayer = if (currentPlayer == 1) 2 else 1
            }
        }
    }

    private fun showEndDialog(message: String) {
        Gdx.input.inputProcessor = stage
        val dialog = Dialog("游戏结束", skin)
        dialog.text(message).contentTable.pad(20f).align(Align.center)

        val againButton = TextButton("再来一局", skin)
        againButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                game.setScreen(GameScreen())
                dialog.hide()
            }
        })
        dialog.add(againButton).width(200f).height(50f)

        val exitButton = TextButton("退出游戏", skin)
        exitButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                Net.disconnect()
                game.setScreen(MainMenuScreen())
                dialog.hide()
            }
        })
        dialog.add(exitButton).width(200f).height(50f)

        dialog.show(stage)
        stage.addActor(dialog)
    }

    private fun checkWin(row: Int, col: Int, player: Int): Boolean {
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
                if (r !in 0 until boardSize || c !in 0 until boardSize || board[r][c] != player) break
                count++
            }
            for (i in 1..4) {
                val r = row - i * dir[0]
                val c = col - i * dir[1]
                if (r !in 0 until boardSize || c !in 0 until boardSize || board[r][c] != player) break
                count++
            }
            if (count >= 5) return true
        }
        return false
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
