import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "2.1.0"
  id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "ru.maybetm"
version = "1.0.0"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
      compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
  }
}

intellijPlatform {
  pluginConfiguration {
    ideaVersion {
      sinceBuild = "251"
    }

    changeNotes = """
      First release version
    """.trimIndent()
  }
}

tasks.withType<Zip>().named("buildPlugin") {
  archiveBaseName.set("spring-boot-ce-launcher")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
  intellijPlatform {
    create("IC", "2025.1")
    testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Starter)
    // Add necessary plugin dependencies for compilation here, example:
    bundledPlugin("com.intellij.java")
  }

  sourceSets {
    create("integrationTest") {
      compileClasspath += sourceSets.main.get().output
      runtimeClasspath += sourceSets.main.get().output
    }
  }

  val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
  }

  dependencies {
    integrationTestImplementation("org.kodein.di:kodein-di-jvm:7.30.0")
    integrationTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    integrationTestImplementation("org.junit.jupiter:junit-jupiter:6.0.1")
    integrationTestImplementation("org.junit.platform:junit-platform-launcher:6.0.1")
    integrationTestImplementation("io.ktor:ktor-client:3.3.3")
  }

  tasks.register<Test>("integrationTest") {
    val integrationTestSourceSet = sourceSets.getByName("integrationTest")
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    systemProperty("path.to.build.plugin", tasks.prepareSandbox.get().pluginDirectory.get().asFile)
    useJUnitPlatform()
    dependsOn(tasks.prepareSandbox)
  }
}

