/*
 * Copyright (c) 2023. ForteScarlet.
 *
 * This file is part of simbot-component-miyoushe.
 *
 * simbot-component-miyoushe is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Lesser General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * simbot-component-miyoushe is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with simbot-component-miyoushe,
 * If not, see <https://www.gnu.org/licenses/>.
 */

package changelog

import org.gradle.api.Project
import simbotVersion
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.*

data class CommitLog(val message: String, val hash: MutableList<String>, val pre: String?)

fun Project.generateChangelog(tag: String) {
    println("Generate change log for $tag ...")
    // configurations.runtimeClasspath
    val changelogDir = rootProject.file(".changelog").also {
        it.mkdirs()
    }

    File(changelogDir, "$tag.md").also { file ->
        if (!file.exists()) {
            file.createNewFile()
        }

        val coreVersion = simbotVersion

        file.writeText("""
            > 对应核心版本: [**v$coreVersion**](https://github.com/simple-robot/simpler-robot/releases/tag/v$coreVersion)
                
            > [!warning]
            > 目前版本尚处于 **早期** 阶段，代表仍然可能存在大量已知问题或未知问题，
            以及未完善的内容和落后于官方更新的内容。**
            
            我们欢迎并期望着您的的[反馈](https://github.com/simple-robot/simbot-component-miyoushe/issues)或[协助](https://github.com/simple-robot/simbot-component-miyoushe/pulls)，
            感谢您的贡献与支持！
            
        """.trimIndent())
    }

    val rootChangelogFile = rootProject.file("CHANGELOG.md").also { file ->
        if (!file.exists()) {
            file.createNewFile()
        }
    }

    // 获取上一个tag
    var firstTag: String? = null
    var currentTag = false
    val lastTag = ByteArrayOutputStream().use { output ->
        rootProject.exec {
            commandLine("git", "tag", "--sort=-committerdate")
            standardOutput = output
        }

        var lastTag: String? = null

        for (lineTag in output.toString().lines()) {
            if (!lineTag.startsWith('v')) {
                continue
            }

            if (lineTag == tag) {
                currentTag = true
                continue
            }

            if (firstTag == null) {
                firstTag = lineTag
            }

            if (currentTag) {
                // first after current tag
                lastTag = lineTag
                break
            }

        }

        if (lastTag == null) {
            lastTag = firstTag
        }

        lastTag
    }



    ByteArrayOutputStream().use { output ->
        rootProject.exec {
            val commandList = mutableListOf("git", "log", "--no-merges", "--oneline").apply {
                if (lastTag != null) {
                    if (currentTag) add("$lastTag..$tag") else add("$lastTag..HEAD")
                }
            }
            commandLine(commandList)
            standardOutput = output
        }


        val lines = LinkedList<CommitLog>()
        // 不要:
        val excludes = listOf("release", "submodule", "ci", "chore", "doc")
        val match = Regex("((?!(${excludes.joinToString("|")}))[a-zA-Z0-9-_]+)(\\(.+\\))?: *.+")

        output.toString()
            .lines()
            .asReversed()
            .asSequence()
            .filter { line ->
                line.isNotEmpty()
            }.mapNotNull { line ->
                val split = line.trim().split(" ", limit = 2)
                val hash = split[0]
                val message = split.getOrNull(1)?.trim() ?: return@mapNotNull null

                hash to message
            }.filter { (_, message) ->
                if (message.startsWith("release")) {
                    return@filter false
                }

                match.matches(message)
            }.forEach { (hash, message) ->
                fun add(pre: String?) {
                    lines.addLast(CommitLog(message, mutableListOf(hash), pre))
                }

                if (lines.isEmpty()) {
                    add(null)
                } else {
                    val last = lines.last
                    if (last.message == message) {
                        last.hash.add(0, hash)
                    } else {
                        add(last.hash.last())
                    }
                }
            }

        val tmpDir = rootProject.buildDir.resolve("tmp/changelog").apply { mkdirs() }

        val tmpFile =
            Files.createTempFile(tmpDir.toPath(), "changelog", "tmp").toFile()

        // copy source file to tmp file
        RandomAccessFile(rootChangelogFile, "r").channel.use { srcChannel ->
            RandomAccessFile(tmpFile, "rw").channel.use { destChannel ->
                srcChannel.transferTo(0, srcChannel.size(), destChannel)
            }
        }

        FileWriter(rootChangelogFile).buffered().use { writer ->
            writer.appendLine("# $tag")
            writer.appendLine(
                """
                
                > Release & Pull Notes: [$tag](https://github.com/simple-robot/simpler-robot/releases/tag/$tag) 
                
            """.trimIndent()
            )

            lines.asReversed()
                .forEach { (message, hashList, preHash) ->
                    if (hashList.size == 1) {
                        writer.appendLine("- $message ([`${hashList[0]}`](https://github.com/simple-robot/simpler-robot/commit/${hashList[0]}))")
                    } else {
                        val pre = hashList[0]
                        val post: String = preHash ?: lastTag ?: "HEAD"
                        writer.appendLine("- $message ([`$pre..${hashList.last()}`](https://github.com/simple-robot/simpler-robot/compare/$pre..$post))")
                        writer.newLine()
                        writer.appendLine("    <details><summary><code>$pre..${hashList.last()}</code></summary>")
                        writer.newLine()
                        hashList.forEach { hash ->
                            writer.appendLine("    - [`$hash`](https://github.com/simple-robot/simpler-robot/commit/$hash)")
                        }
                        writer.newLine()
                        writer.appendLine("    </details>\n")


                        /*
                        <details><summary>ec1e591a..cd1a6df5</summary>

                                - 1

                        - 2
                        - 3

                        </details>
                         */


                    }
                }

            writer.newLine()
        }

        tmpFile.inputStream().use { input ->
            FileWriter(rootChangelogFile, true).use { writer ->
                var skip = false
                input.bufferedReader().use { r ->
                    r.lineSequence().forEach { oldLine ->
                        when {
                            skip && oldLine.startsWith('#') && oldLine != "# $tag" -> {
                                skip = false
                            }

                            oldLine.trim() == "# $tag" -> {
                                skip = true
                            }

                            !skip -> {
                                writer.appendLine(oldLine)
                            }
                        }
                    }
                }
            }
        }
    }

}
