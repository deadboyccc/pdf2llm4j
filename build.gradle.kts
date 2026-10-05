import com.sun.tools.javac.code.Lint.LintCategory.options
import org.gradle.kotlin.dsl.application
import sun.jvmstat.monitor.MonitoredVmUtil.mainClass

plugins {
    application
    alias(libs.plugins.shadow)
}

version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(libs.pdfbox)
    runtimeOnly(libs.jbig2.imageio)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

application {
    mainClass = "pdf2llm.Main"
    applicationName = "pdf2llm"
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true", "-Dfile.encoding=UTF-8")
}

tasks.processResources {
    val tokens = mapOf(
        "version" to project.version.toString(),
        "pdfboxVersion" to libs.versions.pdfbox.get(),
    )
    inputs.properties(tokens)
    filesMatching("pdf2llm.properties") { expand(tokens) }
}

tasks.shadowJar {
    // ImageIO plugins (JBIG2) register through META-INF/services; those files must be merged, not overwritten.
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
    testLogging {
        events("passed", "skipped", "failed")
    }
}
