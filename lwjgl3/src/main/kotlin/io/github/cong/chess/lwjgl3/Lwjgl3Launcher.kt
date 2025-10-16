package io.github.cong.chess.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import io.github.cong.chess.Chess
import io.github.cong.chess.lwjgl3.StartupHelper.Companion.startNewJvmIfRequired

/** Launches the desktop (LWJGL3) application.  */
object Lwjgl3Launcher {
    @JvmStatic
    fun main(args: Array<String>) {
        if (startNewJvmIfRequired()) return  // This handles macOS support and helps on Windows.

        createApplication()
    }

    private fun createApplication(): Lwjgl3Application {
        return Lwjgl3Application(Chess(), defaultConfiguration)
    }

    private val defaultConfiguration: Lwjgl3ApplicationConfiguration
        get() {
            val configuration = Lwjgl3ApplicationConfiguration()
            configuration.setTitle("Chess")
            // Vsync（垂直同步）会将每秒帧数限制在显示器可显示的范围内，有助于消除画面撕裂。
            // 但该设置在 Linux 上有时无效，因此下面这一行代码作为备用措施。
            configuration.useVsync(false)
            // 将帧率限制为当前显示器刷新率加 1，以尽量匹配非整数刷新率的情况。
            // 上面的 Vsync 设置应会让实际帧率与显示器刷新率匹配。
            configuration.setForegroundFPS(16500)

            // 如果移除上面的代码并将 Vsync 设为 false，就能获得无限帧率，
            // 这对性能测试有用，但可能会让某些硬件承受较大压力。
            // 你可能还需要在 GPU 驱动中关闭 Vsync，这样可能会导致画面撕裂。

            configuration.setWindowedMode(1920, 1080)
            // 这些文件位于lwjgl3/src/main/resources/目录下，您可以进行修改。
            // 同时，它们也能从assets/根目录加载。
            configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")
            // 此举旨在提升与存在OpenGL驱动缺陷的Windows设备、以及必须通过模拟实现OpenGL兼容性的Apple Silicon芯片Mac等设备的兼容性。
            // 该功能需依赖`com.badlogicgames.gdx:gdx-lwjgl3-angle`组件实现。
            // 若您开发的是基于GL30（即兼容OpenGL ES 3.0规范）的游戏项目，可选择性移除以下配置行及对应依赖项，因其并非该类型项目的必要配置。
            //configuration.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20, 0, 0)

            return configuration
        }
}
