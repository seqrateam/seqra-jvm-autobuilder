import SeqraProjectDependency.seqraProject
import SeqraUtilDependency.seqraUtilCli
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.seqra.common.KotlinDependency

plugins {
    id("kotlin-conventions")
    kotlinSerialization()
    shadowPlugin().apply(false)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }
}

dependencies {
    implementation(seqraProject)
    implementation(seqraUtilCli)

    implementation(KotlinDependency.Libs.kotlinx_serialization_json)
    implementation(KotlinDependency.Libs.kotlin_logging)

    implementation(Libs.zt_exec)
    implementation(Libs.slf4j_api)
    implementation(Libs.logback)
}

val projectAutoBuilderJar = tasks.register<ShadowJar>("projectAutoBuilderJar") {
    jarWithDependencies("seqra-project-auto-builder", "org.seqra.project.ProjectAutoBuilder")
}

tasks.register<JavaExec>("runAutoBuilder") {
    mainClass.set("org.seqra.project.ProjectAutoBuilder")
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs = listOf("-Xmx8g")
}

tasks.register("buildProjectAutoBuilderDocker") {
    dependsOn(projectAutoBuilderJar)
    autoBuilderDockerImage(nameSuffix = "private") {
        projectAutoBuilderJar.get().outputs.files.singleFile
    }
}

fun Task.autoBuilderDockerImage(
    nameSuffix: String,
    autoBuilderJarProvider: () -> File,
) = doLast {
    val autoBuilderJar = autoBuilderJarProvider()

    buildDockerImage(
        imageName = "autobuilder",
        nameSuffix = nameSuffix,
        imageContentFiles = listOf(autoBuilderJar),
        entryPointVars = mapOf(
            "AUTOBUILDER_JAR_NAME" to autoBuilderJar.name
        )
    )
}

fun ShadowJar.jarWithDependencies(name: String, mainClass: String) {
    duplicatesStrategy = DuplicatesStrategy.WARN
    archiveBaseName.set(name)

    manifest {
        attributes(mapOf("Main-Class" to mainClass))
    }

    configurations = listOf(project.configurations.runtimeClasspath.get())
    mergeServiceFiles()

    with(tasks.jar.get() as CopySpec)
}
