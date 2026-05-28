import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.9.24"
  id("org.jetbrains.intellij") version "1.17.4"
}

group = "dev.juhidamley"
version = "0.1.0"

repositories {
  mavenCentral()
}

dependencies {
  testImplementation(kotlin("test"))
}

intellij {
  version.set("2024.1.4")
  type.set("IC")
  plugins.set(listOf("org.jetbrains.plugins.terminal"))
  downloadSources.set(false)
}

tasks {
  withType<KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }

  patchPluginXml {
    sinceBuild.set("241")
  }

  test {
    useJUnitPlatform()
  }
}
