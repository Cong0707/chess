package io.github.cong.chess.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import io.github.cong.chess.Net
import io.github.cong.chess.Vars
import io.github.cong.chess.Vars.game
import io.github.cong.chess.Vars.skin
import io.github.cong.chess.Vars.stage

class MultiplayerListScreen(val rooms: List<String>) : Screen {

    private lateinit var table: Table

    override fun show() {
        println("MultiplayerListScreen")
        // 设置舞台输入处理
        Gdx.input.inputProcessor = stage

        Gdx.app.postRunnable {
            table = Table()

            // 居中布局
            table.setFillParent(true)
            table.align(Align.center)
            stage.addActor(table)

            // 标题
            val titleLabel = Label("多人游戏", skin)
            table.add(titleLabel).padBottom(80f)

            table.row()

            var i = 0

            if (rooms.size >= 1) {
                for (room in rooms) {
                    // 开始按钮
                    val startButton = TextButton("房间:$room", skin)
                    startButton.addListener(object : ClickListener() {
                        override fun clicked(event: InputEvent?, x: Float, y: Float) {
                            game.setScreen(MutliplayerGameScreen(room))
                        }
                    })
                    table.add(startButton).width(200f).height(50f).padBottom(20f)
                    i++
                    if (i >= 3) {
                        table.row()
                        i = 0
                    }
                }

                if(i != 0) table.row()
            }

            // 新建房间
            val newButton = TextButton("新建房间", skin)
            newButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val message = Net.sendBlocking("new")
                    Thread.sleep(20)
                    println(message)
                    if (message!!.startsWith("createSuccess: ")) {
                        val roomName = message.removePrefix("createSuccess: ").trim()
                        println(roomName)
                        game.setScreen(MutliplayerGameScreen(roomName))
                    }
                }
            })
            table.add(newButton).width(200f).height(50f).padBottom(5f).padRight(20f)

            // 返回
            val multiplayerButton = TextButton("返回", skin)
            multiplayerButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    game.setScreen(MainMenuScreen())
                }
            })
            table.add(multiplayerButton).width(200f).height(50f).padBottom(5f)

            // 退出按钮
            val exitButton = TextButton("退出游戏", skin)
            exitButton.addListener(object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    Net.disconnect()
                    Vars.threadPool.shutdownNow()
                    Gdx.app.exit()
                }
            })
            table.add(exitButton).width(200f).height(50f).padBottom(5f).padLeft(20f)
        }
    }

    override fun render(delta: Float) {}
    override fun resize(width: Int, height: Int) {}

    override fun pause() {}
    override fun resume() {}
    override fun hide() {
        println("MainMenuScreen hide")
        table.remove()
    }

    override fun dispose() {}
}
