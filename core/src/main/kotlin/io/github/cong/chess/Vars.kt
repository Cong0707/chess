package io.github.cong.chess

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.FitViewport

object Vars {
    lateinit var stage: Stage
    lateinit var skin: Skin
    lateinit var font: BitmapFont
    lateinit var camera: Camera

    fun init(){
        stage = Stage(FitViewport(960f, 540f))
        skin = Skin(Gdx.files.internal("ui/uiskin.json"))
        camera = OrthographicCamera()
        (camera as OrthographicCamera).setToOrtho(false, 960f, 540f)

        val generator = FreeTypeFontGenerator(Gdx.files.absolute("C:/Windows/Fonts/simhei.ttf"))
        val sb = StringBuilder()
        for (i in 0x4E00..0x9FA5) { // 常用汉字
            sb.append(i.toChar())
        }
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = 16
            borderWidth = 2f
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + sb.toString()
        }
        font = generator.generateFont(parameter)

        skin.add("simhei", font)

        skin.add("white", Color.WHITE)
        skin.add("black", Color.BLACK)
        skin.add("red", Color.RED)
        skin.add("green", Color.GREEN)
        skin.add("blue", Color.BLUE)

        val labelStyle = Label.LabelStyle()
        labelStyle.font = font
        labelStyle.fontColor = Color.WHITE
        skin.add("default", labelStyle)

        val buttonStyle = skin.get("default", TextButton.TextButtonStyle::class.java)
        buttonStyle.font = font
    }
}
