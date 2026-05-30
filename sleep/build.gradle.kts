import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.git.properties)
    alias(libs.plugins.openapi.generator)
}

group = "com.noom.interview.fullstack"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.logging)
    runtimeOnly(libs.postgresql)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_25
    }
}

tasks.named<BootBuildImage>("bootBuildImage") {
    builder = "paketobuildpacks/builder-jammy-base:latest"
}

openApiGenerate {
    generatorName = "kotlin-spring"
    inputSpec = "$projectDir/src/main/resources/openapi/sleep-api.yaml"
    outputDir =
        layout.buildDirectory
            .dir("generated/openapi")
            .get()
            .asFile
            .toString()
    cleanupOutput = true
    apiPackage = "com.noom.interview.fullstack.sleep.api"
    modelPackage = "com.noom.interview.fullstack.sleep.api.model"
    configOptions =
        mapOf(
            "interfaceOnly" to "true",
            "useTags" to "true",
            "documentationProvider" to "none",
            "useSpringBoot3" to "true",
            "exceptionHandler" to "false",
            "enumPropertyNaming" to "UPPERCASE",
            "serviceInterface" to "false",
            "skipDefaultInterface" to "false",
        )
    typeMappings =
        mapOf(
            "time" to "java.time.LocalTime",
        )
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(tasks.named("openApiGenerate"))
}

tasks.matching { it.name.startsWith("runKtlint") }.configureEach {
    dependsOn(tasks.named("openApiGenerate"))
}

ktlint {
    reporters {
        reporter(ReporterType.HTML)
    }
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
        val integrationTest by registering(JvmTestSuite::class) {
            sources {
                kotlin {
                    setSrcDirs(listOf("src/it/kotlin"))
                }
            }
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
            dependencies {
                implementation(project())
                implementation(libs.spring.boot.starter.test)
                implementation(libs.testcontainers)
                implementation(libs.testcontainers.junit)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.springmockk)
            }
        }
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.implementation.get(), configurations.runtimeOnly.get())
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

kover {
    currentProject {
        sources {
            excludedSourceSets.addAll("integrationTest")
        }
    }
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
            log {
                onCheck = true
            }
        }
        filters {
            excludes {
                classes(
                    "com.noom.interview.fullstack.sleep.SleepApplicationKt",
                    "com.noom.interview.fullstack.sleep.api.*",
                    "com.noom.interview.fullstack.sleep.model.*",
                    "com.noom.interview.fullstack.sleep.exception.*",
                )
            }
        }
    }
}

sonar {
    properties {
        property("sonar.projectKey", "philipmvitale_sleep-logger")
        property("sonar.organization", "philipmvitale")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory
                .file("reports/kover/report.xml")
                .get()
                .asFile
                .toString(),
        )
    }
}
