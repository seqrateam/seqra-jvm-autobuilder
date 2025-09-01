package org.seqra.project

import java.nio.file.Path
import kotlin.io.path.Path

sealed interface JavaToolchain {
    fun path(): Path

    data object DefaultJavaToolchain : JavaToolchain {
        override fun path(): Path = Path(System.getProperty("java.home"))
    }

    data class ConcreteJavaToolchain(val javaHome: String) : JavaToolchain {
        override fun path(): Path = Path(javaHome)
    }
}
