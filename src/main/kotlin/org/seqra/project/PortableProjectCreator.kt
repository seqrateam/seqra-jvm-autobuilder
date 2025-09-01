package org.seqra.project

import mu.KLogging
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.OnErrorResult
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

class PortableProjectCreator(
    private val portableProjectPath: Path,
    private val rootProject: Project
) {
    sealed interface PortAction {
        data class Copy(val dst: Path) : PortAction
        data class Ported(val path: Path) : PortAction
    }

    private inner class ProjectPortContext(
        val sources: Path,
        val classes: Path,
        val dependencies: Path,
        val toolchain: Path,
    ) {
        private var classesCounter = 0
        private var duplicateDependencyCounter = 0
        private var duplicateToolchainCounter = 0

        private val portedDependencies = hashMapOf<String, Path>()
        private val portedToolchain = hashMapOf<String, Path>()

        fun nextClassesPath(): Path = classes.resolve("c${classesCounter++}")

        fun nextDependencyPath(dependency: Path): PortAction =
            copyWithoutDuplicates(dependency, portedDependencies, dependencies) {
                "d${duplicateDependencyCounter++}"
            }

        fun nextToolchainPath(tc: Path): PortAction =
            copyWithoutDuplicates(tc, portedToolchain, toolchain) {
                "tc${duplicateToolchainCounter++}"
            }

        private inline fun copyWithoutDuplicates(
            path: Path,
            cache: MutableMap<String, Path>,
            newBaseLocation: Path,
            nextDuplicateName: () -> String
        ): PortAction {
            val name = path.name
            val portedPath = cache.putIfAbsent(name, path)

            return when (portedPath) {
                null -> PortAction.Copy(newBaseLocation.resolve(name))
                path -> PortAction.Ported(newBaseLocation.resolve(name))
                else -> PortAction.Copy(
                    newBaseLocation.resolve(nextDuplicateName()).resolve(name)
                )
            }
        }
    }

    fun create() {
        if (portableProjectPath.exists()) {
            if (!portableProjectPath.isDirectory() || portableProjectPath.isNotEmpty()) {
                logger.error { "Portable project path exists" }
                return
            }
        }

        portableProjectPath.createDirectories()

        val ctx = ProjectPortContext(
            sources = portableProjectPath.resolve("sources").createDirectories(),
            classes = portableProjectPath.resolve("classes").createDirectories(),
            dependencies = portableProjectPath.resolve("dependencies").createDirectories(),
            toolchain = portableProjectPath.resolve("toolchain").createDirectories()
        )

        copyDirectory(rootProject.sourceRoot, ctx.sources)

        val portableProject = create(ctx, rootProject)
        val relativeProject = portableProject.relativeTo(portableProjectPath)

        relativeProject.dump(portableProjectPath.resolve("project.yaml"))
    }

    private fun create(ctx: ProjectPortContext, project: Project): Project = Project(
        sourceRoot = copySources(ctx, project.sourceRoot),
        javaToolchain = project.javaToolchain?.let { copyToolchain(ctx, it) },
        modules = project.modules.map { create(ctx, it) },
        dependencies = project.dependencies.map { copyDependency(ctx, it) },
        subProjects = project.subProjects.map { create(ctx, it) }
    )

    private fun create(ctx: ProjectPortContext, module: ProjectModuleClasses) = ProjectModuleClasses(
        moduleSourceRoot = copySources(ctx, module.moduleSourceRoot),
        moduleClasses = module.moduleClasses.map { copyClasses(ctx, it) }
    )

    private fun copySources(ctx: ProjectPortContext, source: Path): Path {
        val relativeOriginal = source.relativeTo(rootProject.sourceRoot)
        return ctx.sources.resolve(relativeOriginal)
    }

    private fun copyClasses(ctx: ProjectPortContext, classes: Path): Path {
        val portedClasses = ctx.nextClassesPath()
        copy(classes, portedClasses)
        return portedClasses
    }

    private fun copyDependency(ctx: ProjectPortContext, dependency: Path): Path {
        val portedDependency = ctx.nextDependencyPath(dependency)
        return portedDependency.port(dependency)
    }

    private fun copyToolchain(ctx: ProjectPortContext, toolchain: Path): Path {
        val portedToolchain = ctx.nextToolchainPath(toolchain)
        return portedToolchain.port(toolchain)
    }

    private fun PortAction.port(original: Path): Path = when (this) {
        is PortAction.Ported -> path
        is PortAction.Copy -> {
            copy(original, dst)
            dst
        }
    }

    private fun copy(from: Path, dst: Path) {
        if (from.isDirectory()) {
            copyDirectory(from, dst)
        } else {
            dst.parent?.createDirectories()
            from.copyTo(dst)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyDirectory(from: Path, dst: Path) {
        dst.createDirectories()
        from.copyToRecursively(
            dst,
            onError = { src, _, ex ->
                logger.error(ex) { "Failed to create portable project for $src" }
                OnErrorResult.SKIP_SUBTREE
            },
            followLinks = false,
            overwrite = false
        )
    }

    companion object {
        private val logger = object : KLogging() {}.logger

        private fun Path.isNotEmpty(): Boolean {
            forEachDirectoryEntry { return true }
            return false
        }
    }
}
