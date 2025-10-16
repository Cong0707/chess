/*
 * Copyright 2020 damios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//Note, the above license and copyright applies to this file only.
package io.github.cong.chess.lwjgl3

import com.badlogic.gdx.Version
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3NativesLoader
import org.lwjgl.system.JNI
import org.lwjgl.system.macosx.LibC
import org.lwjgl.system.macosx.ObjCRuntime
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.util.*

/**
 * Adds some utilities to ensure that the JVM was started with the
 * `-XstartOnFirstThread` argument, which is required on macOS for LWJGL 3
 * to function. Also helps on Windows when users have names with characters from
 * outside the Latin alphabet, a common cause of startup crashes.
 * <br></br>
 * [Based on this java-gaming.org post by kappa](https://jvm-gaming.org/t/starting-jvm-on-mac-with-xstartonfirstthread-programmatically/57547)
 * @author damios
 */
class StartupHelper private constructor() {
    init {
        throw UnsupportedOperationException()
    }

    companion object {
        private const val JVM_RESTARTED_ARG = "jvmIsRestarted"

        /**
         * Starts a new JVM if the application was started on macOS without the
         * `-XstartOnFirstThread` argument. This also includes some code for
         * Windows, for the case where the user's home directory includes certain
         * non-Latin-alphabet characters (without this code, most LWJGL3 apps fail
         * immediately for those users). Returns whether a new JVM was started and
         * thus no code should be executed.
         *
         *
         * <u>Usage:</u>
         *
         * <pre>`
         * public static void main(String... args) {
         * if (StartupHelper.startNewJvmIfRequired(true)) return; // This handles macOS support and helps on Windows.
         * // after this is the actual main method code
         * }
        `</pre> *
         *
         * @param redirectOutput
         * whether the output of the new JVM should be rerouted to the
         * old JVM, so it can be accessed in the same place; keeps the
         * old JVM running if enabled
         * @return whether a new JVM was started and thus no code should be executed
         * in this one
         */
        /**
         * Starts a new JVM if the application was started on macOS without the
         * `-XstartOnFirstThread` argument. Returns whether a new JVM was
         * started and thus no code should be executed. Redirects the output of the
         * new JVM to the old one.
         *
         *
         * <u>Usage:</u>
         *
         * <pre>
         * public static void main(String... args) {
         * if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
         * // the actual main method code
         * }
        </pre> *
         *
         * @return whether a new JVM was started and thus no code should be executed
         * in this one
         */
        @JvmOverloads
        fun startNewJvmIfRequired(redirectOutput: Boolean = true): Boolean {
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
            if (!osName.contains("mac")) {
                if (osName.contains("windows")) {
                    // 此处我们试图解决LWJGL3加载其提取的.dll文件时遇到的一个问题。
                    // 默认情况下，LWJGL3会提取到由"java.io.tmpdir"指定的目录，这通常是用户主目录。
                    // 若用户名包含非ASCII（或某些非字母数字）字符，此操作将会失败。
                    // 通过将文件提取到相关的"ProgramData"文件夹（通常为"C:\ProgramData"），我们避免了这一问题。
                    // 同时，我们临时将"user.name"属性更改为不包含任何无效字符的用户名。
                    // 在加载LWJGL3本地库之后，我们立即恢复这些更改。
                    var programData = System.getenv("ProgramData")
                    if (programData == null) programData = "C:\\Temp\\" // if ProgramData isn't set, try some fallback.

                    val prevTmpDir = System.getProperty("java.io.tmpdir", programData)
                    val prevUser = System.getProperty("user.name", "libGDX_User")
                    System.setProperty("java.io.tmpdir", "$programData/libGDX-temp")
                    System.setProperty(
                        "user.name",
                        ("User_" + prevUser.hashCode() + "_GDX" + Version.VERSION).replace('.', '_')
                    )
                    Lwjgl3NativesLoader.load()
                    System.setProperty("java.io.tmpdir", prevTmpDir)
                    System.setProperty("user.name", prevUser)
                }
                return false
            }

            // There is no need for -XstartOnFirstThread on Graal native image
            if (!System.getProperty("org.graalvm.nativeimage.imagecode", "").isEmpty()) {
                return false
            }

            // Checks if we are already on the main thread, such as from running via Construo.
            val objc_msgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend")
            val NSThread = ObjCRuntime.objc_getClass("NSThread")
            val currentThread = JNI.invokePPP(NSThread, ObjCRuntime.sel_getUid("currentThread"), objc_msgSend)
            val isMainThread = JNI.invokePPZ(currentThread, ObjCRuntime.sel_getUid("isMainThread"), objc_msgSend)
            if (isMainThread) return false

            val pid = LibC.getpid()

            // check whether -XstartOnFirstThread is enabled
            if ("1" == System.getenv("JAVA_STARTED_ON_FIRST_THREAD_$pid")) {
                return false
            }

            // check whether the JVM was previously restarted
            // avoids looping, but most certainly leads to a crash
            if ("true" == System.getProperty(JVM_RESTARTED_ARG)) {
                System.err.println(
                    "There was a problem evaluating whether the JVM was started with the -XstartOnFirstThread argument."
                )
                return false
            }

            // Restart the JVM with -XstartOnFirstThread
            val jvmArgs = ArrayList<String?>()
            val separator = System.getProperty("file.separator", "/")
            // The following line is used assuming you target Java 8, the minimum for LWJGL3.
            val javaExecPath = System.getProperty("java.home") + separator + "bin" + separator + "java"

            // If targeting Java 9 or higher, you could use the following instead of the above line:
            //String javaExecPath = ProcessHandle.current().info().command().orElseThrow();
            if (!(File(javaExecPath)).exists()) {
                System.err.println(
                    "A Java installation could not be found. If you are distributing this app with a bundled JRE, be sure to set the -XstartOnFirstThread argument manually!"
                )
                return false
            }

            jvmArgs.add(javaExecPath)
            jvmArgs.add("-XstartOnFirstThread")
            jvmArgs.add("-D" + JVM_RESTARTED_ARG + "=true")
            jvmArgs.addAll(ManagementFactory.getRuntimeMXBean().inputArguments)
            jvmArgs.add("-cp")
            jvmArgs.add(System.getProperty("java.class.path"))
            var mainClass = System.getenv("JAVA_MAIN_CLASS_$pid")
            if (mainClass == null) {
                val trace = Thread.currentThread().stackTrace
                if (trace.size > 0) {
                    mainClass = trace[trace.size - 1].className
                } else {
                    System.err.println("The main class could not be determined.")
                    return false
                }
            }
            jvmArgs.add(mainClass)

            try {
                if (!redirectOutput) {
                    val processBuilder = ProcessBuilder(jvmArgs)
                    processBuilder.start()
                } else {
                    val process = (ProcessBuilder(jvmArgs))
                        .redirectErrorStream(true).start()
                    val processOutput = BufferedReader(
                        InputStreamReader(process.inputStream)
                    )
                    var line: String?

                    while ((processOutput.readLine().also { line = it }) != null) {
                        println(line)
                    }

                    process.waitFor()
                }
            } catch (e: Exception) {
                System.err.println("There was a problem restarting the JVM")
                e.printStackTrace()
            }

            return true
        }
    }
}
