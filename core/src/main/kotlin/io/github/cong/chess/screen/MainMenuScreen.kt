package io.github.cong.chess.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import io.github.cong.chess.Net
import io.github.cong.chess.Vars.game
import io.github.cong.chess.Vars.preferences
import io.github.cong.chess.Vars.skin
import io.github.cong.chess.Vars.stage
import kotlin.random.Random

class MainMenuScreen : Screen {

    private lateinit var table: Table

    override fun show() {
        println("MainMenuScreen")
        // 设置舞台输入处理
        Gdx.input.inputProcessor = stage

        table = Table()

        // 居中布局
        table.setFillParent(true)
        table.align(Align.center)
        stage.addActor(table)

        // 标题
        val titleLabel = Label("五子棋", skin)
        table.add(titleLabel).padBottom(40f)
        table.row()

        // 开始按钮
        val startButton = TextButton("开始游戏", skin)
        startButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                game.setScreen(GameScreen())
            }
        })
        table.add(startButton).width(200f).height(50f).padBottom(20f).padRight(10f)
        table.row()

        // 新建一个水平Table，用来放输入框和多人游戏按钮
        val multiplayerTable = Table()

        val ramdomName = Random(System.currentTimeMillis()).nextInt().toHexString()

        // 输入框
        val nameField = TextField(
            preferences.getString(
                "name", ramdomName
            ),
            skin
        )
        multiplayerTable.add(nameField).width(150f).height(50f).padRight(10f)

        // 多人按钮
        val multiplayerButton = TextButton("多人游戏", skin)
        multiplayerButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                // 这里可以获取输入框内容
                val playerName = nameField.text.ifEmpty { ramdomName }
                preferences.putString("name", playerName)
                preferences.flush()

                Net.connect()
            }
        })
        multiplayerTable.add(multiplayerButton).width(200f).height(50f).padRight(10f)

        table.add(multiplayerTable).padBottom(20f)
        table.row()

        // 退出按钮
        val exitButton = TextButton("退出游戏", skin)
        exitButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                Net.disconnect()
                Gdx.app.exit()
            }
        })
        table.add(exitButton).width(200f).height(50f).padRight(10f)
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
