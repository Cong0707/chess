package io.github.cong.chess

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.ScreenUtils
import io.github.cong.chess.Vars.camera
import io.github.cong.chess.Vars.skin
import io.github.cong.chess.Vars.stage
import io.github.cong.chess.screen.MainMenuScreen


/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms.  */
class ChessGame : Game() {

    private lateinit var fpsLabel: Label

    override fun create() {
        Vars.init()
        Vars.game = this

        fpsLabel = Label("0 FPS", skin)
        fpsLabel.setPosition(10f, 20f, Align.topLeft)

        setScreen(MainMenuScreen())

        Gdx.input.inputProcessor = stage
    }

    override fun render() {
        ScreenUtils.clear(0.95f, 0.9f, 0.8f, 1f)
        camera.update()
        stage.batch.projectionMatrix = camera.combined

        super.render()

        stage.batch.begin()
        fpsLabel.text.clear()
        fpsLabel.text.append(Gdx.graphics.framesPerSecond).append(" FPS")
        // Allows the FPS label to be drawn with the correct width.
        fpsLabel.invalidate()
        fpsLabel.draw(stage.batch, 1f)
        stage.batch.end()

        stage.act(Gdx.graphics.deltaTime)
        stage.draw()
    }

    override fun resize(width: Int, height: Int) {
        // If the window is minimized on a desktop (LWJGL3) platform, width and height are 0, which causes problems.
        // In that case, we don't resize anything, and wait for the window to be a normal size before updating.
        if (width <= 0 || height <= 0) return

        stage.viewport.update(width, height)
    }

    override fun dispose() {
        super.dispose()
        stage.dispose()
        skin.dispose()
    }
}
