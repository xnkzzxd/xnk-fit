package app.xnkfit.domain

import java.io.File

/** Finds files in the repository from a JVM unit test (the working directory is the `app` module). */
object RepoFiles {
    val root: File by lazy {
        var dir: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (dir != null && !File(dir, "docs/Rules.md").isFile) dir = dir.parentFile
        checkNotNull(dir) { "docs/Rules.md not found above ${System.getProperty("user.dir")}" }
    }

    fun file(path: String): File = File(root, path)

    fun text(path: String): String = file(path).readText()
}
