package io.github.cong.chess.screen

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import io.github.cong.chess.Vars.skin
import io.github.cong.chess.Vars.stage

class MainMenuScreen(val game: Game) : Screen {

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
                game.setScreen(GameScreen(game))
            }
        })
        table.add(startButton).width(200f).height(50f).padBottom(20f)
        table.row()

        // 退出按钮
        val exitButton = TextButton("退出游戏", skin)
        exitButton.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                Gdx.app.exit()
            }
        })
        table.add(exitButton).width(200f).height(50f)
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
