import love.forte.gradle.common.core.project.setup
import love.forte.gradle.common.kotlin.multiplatform.NativeTargets

plugins {
    kotlin("multiplatform")
//    `miyoushe-multiplatform-maven-publish`
    kotlin("plugin.serialization")
//    `miyoushe-dokka-partial-configure`
}

setup(P)
if (isSnapshot()) {
    version = P.snapshotVersion.toString()
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()

    sourceSets.configureEach {
        languageSettings {
//            optIn("love.forte.simbot.qguild.InternalApi")
        }
    }

    jvm {
        withJava()
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
                javaParameters = true
                freeCompilerArgs = freeCompilerArgs + listOf("-Xjvm-default=all")
            }
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js(IR) {
        nodejs()
        binaries.library()
    }


    val mainPresets = mutableSetOf<org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet>()
    val testPresets = mutableSetOf<org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet>()

    // see https://kotlinlang.org/docs/native-target-supporsupportTargets = setOf(
    ////        // Tier 1
    ////        "linuxX64",
    ////        "macosX64",
    ////        "macosArm64",
    ////        "iosSimulatorArm64",
    ////        "iosX64",
    ////
    ////        // Tier 2
    //////        "linuxArm64",
    ////        "watchosSimulatorArm64",
    ////        "watchosX64",
    ////        "watchosArm32",
    ////        "watchosArm64",
    ////        "tvosSimulatorArm64",
    ////        "tvosX64",
    ////        "tvosArm64",
    ////        "iosArm64",
    ////
    ////        // Tier 3
    //////        "androidNativeArm32",
    //////        "androidNativeArm64",
    //////        "androidNativeX86",
    //////        "androidNativeX64",
    ////        "mingwX64",
    //////        "watchosDeviceArm64",
    ////    )t.html
//    val

    val targets = NativeTargets.Official.all.intersect(NativeTargets.KtorClient.all)


    targets {
        presets.filterIsInstance<org.jetbrains.kotlin.gradle.plugin.mpp.AbstractKotlinNativeTargetPreset<*>>()
            .filter { it.name in targets }
            .forEach { presets ->
                val target = fromPreset(presets, presets.name)
                val mainSourceSet = target.compilations["main"].kotlinSourceSets.first()
                val testSourceSet = target.compilations["test"].kotlinSourceSets.first()

                val tn = target.name
                when {
                    // just for test
                    // main中只使用HttpClient但用不到引擎，没必要指定

                    // win
                    tn.startsWith("mingw") -> {
                        testSourceSet.dependencies {
                            implementation(libs.ktor.client.winhttp)
                        }
                    }
                    // linux: CIO..?
                    tn.startsWith("linux") -> {
                        testSourceSet.dependencies {
                            implementation(libs.ktor.client.cio)
                        }
                    }

                    // darwin based
                    tn.startsWith("macos")
                            || tn.startsWith("ios")
                            || tn.startsWith("watchos")
                            || tn.startsWith("tvos") -> {
                        testSourceSet.dependencies {
                            implementation(libs.ktor.client.darwin)
                        }
                    }
                }

                mainPresets.add(mainSourceSet)
                testPresets.add(testSourceSet)
            }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly(simbotAnnotations)
                api(simbotRequestorCore)
                api(libs.ktor.client.core)
                api(libs.ktor.client.contentNegotiation)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.kotlinx.serialization.json)
                api(simbotLogger)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        getByName("jvmMain") {
            dependencies {
                compileOnly(simbotApi) // use @Api4J annotation
                compileOnly(simbotAnnotations) // use @Api4J annotation
            }
        }

        getByName("jvmTest") {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(simbotApi) // use @Api4J annotation
                implementation(libs.log4j.api)
                implementation(libs.log4j.core)
                implementation(libs.log4j.slf4j2Impl)
            }
        }

        getByName("jsMain") {
            dependencies {
                api(libs.ktor.client.js)
            }
        }
        getByName("jsTest") {
            dependencies {
                api(libs.ktor.client.js)
            }
        }

        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        configure(mainPresets) { dependsOn(nativeMain) }
        configure(testPresets) { dependsOn(nativeTest) }

    }

}

// suppress all?
//tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
//    dokkaSourceSets.configureEach {
//        suppress.set(true)
//        perPackageOption {
//            suppress.set(true)
//        }
//    }
//}


