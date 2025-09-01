package org.seqra.project

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.groups.defaultByName
import com.github.ajalt.clikt.parameters.groups.groupChoice
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import mu.KLogging
import org.seqra.util.CliWithLogger
import org.seqra.util.directory
import org.seqra.util.newDirectory
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class ProjectAutoBuilder : CliWithLogger() {
    private val buildDir by option(help = "Project resolver (builder) working directory")
        .newDirectory()

    private val projectRootDir: Path by option(help = "Project root dir")
        .directory()
        .required()

    private val build by option().groupChoice(
        "portable" to PortableProjectBuild(),
        "simple" to SimpleProjectBuild(),
    ).defaultByName("simple")

    override fun main() {
        logger.info { "Run auto build resolver at $projectRootDir" }

        val resolverWorkDir = buildDir ?: createTempDirectory("resolver")

        val resolvedProject = ProjectResolver.resolveProject(projectRootDir, resolverWorkDir)
            ?: return

        when (val b = build) {
            is SimpleProjectBuild -> {
                resolvedProject.dump(b.result)
            }

            is PortableProjectBuild -> {
                val portableProjectCreator = PortableProjectCreator(b.resultDir, resolvedProject)
                portableProjectCreator.create()
            }
        }
    }

    companion object {
        private val logger = object : KLogging() {}.logger

        @JvmStatic
        fun main(args: Array<String>) = ProjectAutoBuilder().main(args)
    }
}
