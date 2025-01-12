package ch.tutteli.spek.extensions

import org.spekframework.spek2.lifecycle.GroupScope
import org.spekframework.spek2.lifecycle.LifecycleListener
import org.spekframework.spek2.lifecycle.TestScope
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*

/**
 * A [LifecycleListener] which manages the creation of temporary files and folders.
 */
class TempFolder private constructor(private val scope: Scope) : LifecycleListener {

    private val tmpDirs = Stack<Path>()

    val tmpDir: Path get() = checkState("access tmpDir") { it }

    private fun <T> checkState(actDescription: String, act: (Path) -> T): T {
        check(tmpDirs.isNotEmpty()) {
            "You tried to $actDescription but you cannot use TempFolder outside of a ${scope.name} scope."
        }
        return act(tmpDirs.peek())
    }

    /**
     * Creates a new file with the given [name] in the current [tmpDir].
     */
    fun newFile(name: String): Path = checkState("call newFile") { Files.createFile(it.resolve(name)) }

    /**
     * Creates a new folder with the given [name] in the current [tmpDir].
     */
    fun newFolder(name: String): Path = checkState("call newFolder") { Files.createDirectory(it.resolve(name)) }

    override fun beforeExecuteTest(test: TestScope) = setUp(Scope.TEST)
    override fun beforeExecuteGroup(group: GroupScope) = setUp(Scope.GROUP)

    override fun afterExecuteTest(test: TestScope) = tearDown(Scope.TEST)
    override fun afterExecuteGroup(group: GroupScope) = tearDown(Scope.GROUP)


    private fun setUp(expectedScope: Scope) {
        if (scope == expectedScope) {
            tmpDirs.push(Files.createTempDirectory("spek"))
        }
    }

    private fun tearDown(expectedScope: Scope) {
        if (scope == expectedScope) {
            Files.walkFileTree(tmpDirs.pop(), object : SimpleFileVisitor<Path>() {

                override fun visitFile(file: Path, attrs: BasicFileAttributes) = deleteAndContinue(file)

                override fun postVisitDirectory(dir: Path, exc: IOException?) = deleteAndContinue(dir)

                private fun deleteAndContinue(path: Path): FileVisitResult {
                    Files.delete(path)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }

    companion object {
        /**
         * Sets up the [tmpDir] before each test and cleans it up after each test.
         */
        fun perTest() = TempFolder(Scope.TEST)

        /**
         * Sets up the [tmpDir] before each group and cleans it up after each group.
         */
        fun perGroup() = TempFolder(Scope.GROUP)
    }

    private enum class Scope {
        TEST,
        GROUP
    }
}
