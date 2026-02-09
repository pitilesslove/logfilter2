plugins {
    kotlin("jvm") version "1.9.22"
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

group = "com.logfilter"
version = "2.0.0"

repositories {
    mavenCentral()
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.swing")
}

dependencies {
    // TornadoFX
    implementation("no.tornado:tornadofx:1.7.20")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")

    // TestFX for UI testing
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")

    // Headless testing support (Monocle)
    testImplementation("org.testfx:openjfx-monocle:21.0.2")
}

application {
    mainClass.set("com.logfilter.K8sLogViewerAppKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()

    // Headless UI 테스트 설정
    jvmArgs = listOf(
        "-Dtestfx.robot=glass",
        "-Dtestfx.headless=true",
        "-Dprism.order=sw",
        "-Dprism.text=t2k",
        "-Djava.awt.headless=true",
        "-Dheadless.geometry=1280x720-32"
    )
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.logfilter.K8sLogViewerAppKt"
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// CLI 모드 실행 태스크
tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Run CLI mode for testing"
    mainClass.set("com.logfilter.cli.CliRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--test")
}

// 파일 파싱 테스트
tasks.register<JavaExec>("parseLog") {
    group = "application"
    description = "Parse a log file"
    mainClass.set("com.logfilter.cli.CliRunner")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--file", "samples/spring-boot-sample.log")
}

// UI 스크린샷 캡처 (디스플레이 필요)
tasks.register<JavaExec>("screenshot") {
    group = "application"
    description = "Capture UI screenshots (requires display)"
    mainClass.set("com.logfilter.cli.ScreenshotRunnerKt")
    classpath = sourceSets["main"].runtimeClasspath
}

// 웹 서버 실행 (Playwright 테스트용)
tasks.register<JavaExec>("runWeb") {
    group = "application"
    description = "Run web server for Playwright testing"
    mainClass.set("com.logfilter.web.WebServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}
