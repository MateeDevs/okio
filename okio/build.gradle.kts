import aQute.bnd.gradle.BundleTaskConvention
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSnifferExtension

plugins {
    kotlin("multiplatform")
    id("ru.vyarus.animalsniffer")
}

/*
 * Here's the main hierarchy of variants. Any `expect` functions in one level of the tree are
 * `actual` functions in a (potentially indirect) child node.
 *
 * ```
 *   common
 *   |-- jvm
 *   |-- js
 *   '-- native
 *       |- unix
 *       |   |-- apple
 *       |   |   |-- iosArm64
 *       |   |   |-- iosX64
 *       |   |   |-- macosX64
 *       |   |   |-- watchosArm32
 *       |   |   |-- watchosArm64
 *       |   |   '-- watchosX86
 *       |   '-- linux
 *       |       '-- linuxX64
 *       '-- mingw
 *           '-- mingwX64
 * ```
 *
 * Every child of `unix` also includes a source set that depends on the pointer size:
 *
 *  * `sizet32` for watchOS, including watchOS 64-bit architectures
 *  * `sizet64` for everything else
 *
 * The `nonJvm` and `nonJs` source sets exist and exclude a platform.
 *
 * The `hashFunctions` source set builds on all platforms. It ships as a main source set on non-JVM
 * platforms and as a test source set on the JVM platform.
 */
kotlin {
    jvm {
        withJava()
    }
    if (kmpJsEnabled) {
        js {
            compilations.all {
                kotlinOptions {
                    moduleKind = "umd"
                    sourceMap = true
                    metaInfo = true
                }
            }
            nodejs {
                testTask {
                    useMocha {
                        timeout = "30s"
                    }
                }
            }
        }
    }
    if (kmpNativeEnabled) {
        iosX64()
        iosArm64()
        watchosArm32()
        watchosArm64()
        watchosX86()
        tvosArm64()
        tvosX64()
        // Required to generate tests tasks: https://youtrack.jetbrains.com/issue/KT-26547
        linuxX64()
        macosX64()
        mingwX64()
    }
    sourceSets {
        all {
            languageSettings.apply {
                useExperimentalAnnotation("kotlin.RequiresOptIn")
            }
        }

        val commonMain by getting {
            dependencies {
                api(deps.kotlin.stdLib.common)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(deps.kotlin.test.common)
                implementation(deps.kotlin.test.annotations)
                implementation(deps.kotlin.time)

                implementation(project(":okio-fakefilesystem"))
            }
        }
        val nonJsMain by creating {
        }
        val nonJvmMain by creating {
            kotlin.srcDir("src/hashFunctions/kotlin")
        }

        val jvmMain by getting {
            dependsOn(nonJsMain)
            dependencies {
                api(deps.kotlin.stdLib.jdk8)
                compileOnly(deps.animalSniffer.annotations)
            }
        }
        val jvmTest by getting {
            kotlin.srcDir("src/hashFunctions/kotlin")
            dependencies {
                implementation(deps.test.junit)
                implementation(deps.test.assertj)
                implementation(deps.kotlin.test.jdk)
            }
        }

        if (kmpJsEnabled) {
            val jsMain by getting {
                dependsOn(nonJvmMain)
                dependencies {
                    api(deps.kotlin.stdLib.js)
                }
            }
            val jsTest by getting {
                dependencies {
                    implementation(deps.kotlin.test.js)
                    implementation(project(":okio-nodefilesystem"))
                }
            }
        }

        if (kmpNativeEnabled) {
            val nativeMain by creating {
                dependsOn(nonJsMain)
                dependsOn(nonJvmMain)
            }
            val nativeTest by creating {
                dependsOn(commonTest)
            }

            val sizet32Main by creating {
                dependsOn(nativeMain)
            }
            val sizet64Main by creating {
                dependsOn(nativeMain)
            }

            val mingwMain by creating {
                dependsOn(nativeMain)
            }
            val mingwX64Main by getting {
                dependsOn(mingwMain)
            }
            val mingwX64Test by getting {
                dependsOn(nativeTest)
            }

            val unixMain by creating {
                dependsOn(nativeMain)
            }

            val appleMain by creating {
                dependsOn(unixMain)
            }
            val appleTest by creating {
                dependsOn(nativeTest)
            }
            val iosX64Main by getting {}
            val iosArm64Main by getting {}
            val macosX64Main by getting {}
            for (it in listOf(iosX64Main, iosArm64Main, macosX64Main)) {
                it.dependsOn(sizet64Main)
                it.dependsOn(appleMain)
            }

            val iosX64Test by getting {}
            val iosArm64Test by getting {}
            val macosX64Test by getting {}
            for (it in listOf(iosX64Test, iosArm64Test, macosX64Test)) {
                it.dependsOn(appleTest)
            }

            val watchosArm32Main by getting {}
            val watchosArm64Main by getting {}
            val watchosX86Main by getting {}
            for (it in listOf(watchosArm32Main, watchosArm64Main, watchosX86Main)) {
                // Note that size_t is 32-bit on all watchOS versions (ie. pointers are always 32-bit).
                it.dependsOn(sizet32Main)
                it.dependsOn(appleMain)
            }

            val tvosArm64Main by getting {}
            val tvosX64Main by getting {}
            for (it in listOf(tvosArm64Main, tvosX64Main)) {
                it.dependsOn(sizet64Main)
                it.dependsOn(appleMain)
            }

            val watchosArm32Test by getting {}
            val watchosArm64Test by getting {}
            val watchosX86Test by getting {}
            for (it in listOf(watchosArm32Test, watchosArm64Test, watchosX86Test)) {
                it.dependsOn(appleTest)
            }

            val linuxMain by creating {
                dependsOn(unixMain)
                dependsOn(nativeMain)
            }
            val linuxX64Main by getting {
                dependsOn(sizet64Main)
                dependsOn(linuxMain)
            }
            val linuxX64Test by getting {
                dependsOn(nativeTest)
            }
        }
    }
}

tasks {
    val jvmJar by getting(Jar::class) {
        val bndConvention = BundleTaskConvention(this)
        bndConvention.setBnd(
            """
      Export-Package: okio
      Automatic-Module-Name: okio
      Bundle-SymbolicName: com.squareup.okio
      """
        )
        // Call the convention when the task has finished to modify the jar to contain OSGi metadata.
        doLast {
            bndConvention.buildBundle()
        }
    }
}

configure<AnimalSnifferExtension> {
    sourceSets = listOf(project.sourceSets.getByName("main"))
}

val signature by configurations.getting {
}

dependencies {
    signature(deps.animalSniffer.androidSignature)
    signature(deps.animalSniffer.javaSignature)
}

apply(from = "$rootDir/gradle/gradle-mvn-mpp-push.gradle")
