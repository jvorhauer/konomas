import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import net.researchgate.release.ReleaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.time.Instant

plugins {
  jacoco
  idea
  kotlin("jvm") version "1.9.22"
  id("com.google.cloud.tools.jib") version "3.4.0"
  id("net.researchgate.release") version "3.0.2"
  id("com.github.ben-manes.versions") version "0.51.0"
  id("io.ktor.plugin") version "2.3.8"
  id("io.sentry.jvm.gradle") version "4.3.0"
}

group = "nl.vorhauer"
java.sourceCompatibility = JavaVersion.VERSION_21

application {
  mainClass.set("blog.MainKt")
}

idea {
  module {
    isDownloadJavadoc = true
    isDownloadSources = true
  }
}

repositories {
  mavenLocal()
  mavenCentral()
  maven {
    url = uri("https://repo.akka.io/maven")
  }
  maven {
    url = uri("https://oss.sonatype.org/content/repositories/releases")
  }
}

dependencies {
  implementation(platform("com.typesafe.akka:akka-bom_2.13:2.9.1"))

  implementation("io.ktor:ktor-server-core-jvm")
  implementation("io.ktor:ktor-server-netty-jvm")
  implementation("io.ktor:ktor-server-content-negotiation-jvm")
  implementation("io.ktor:ktor-server-call-logging-jvm")
  implementation("io.ktor:ktor-server-request-validation")
  implementation("io.ktor:ktor-server-status-pages")
  implementation("io.ktor:ktor-serialization-jackson")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
  implementation("io.ktor:ktor-server-auth")
  implementation("io.ktor:ktor-server-auth-jwt")

  implementation("io.ktor:ktor-client-core")
  implementation("io.ktor:ktor-client-cio")
  implementation("io.ktor:ktor-client-content-negotiation")

  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("io.github.config4k:config4k:0.6.0")

  implementation("com.typesafe.akka:akka-persistence-typed_2.13")
  implementation("com.typesafe.akka:akka-persistence-cassandra_2.13:1.2.0")
  implementation("io.altoo:akka-kryo-serialization-typed_2.13:2.5.2")
  implementation("com.lightbend.akka:akka-diagnostics_2.13:2.1.0")
  implementation("org.scala-lang:scala-library:2.13.12")

  implementation("io.hypersistence:hypersistence-tsid:2.1.1")
  implementation("io.netty:netty-all:4.1.106.Final")
  implementation("org.owasp.encoder:encoder:1.2.3")
  implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
  implementation("ch.qos.logback:logback-classic:1.4.14")
  implementation("io.sentry:sentry:7.2.0")
  implementation("io.sentry:sentry-logback:7.2.0")

  testImplementation("com.typesafe.akka:akka-persistence-testkit_2.13:2.9.1")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
  testImplementation("org.assertj:assertj-core:3.11.1")
  testImplementation(platform("org.junit:junit-bom:5.10.2"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("io.ktor:ktor-server-tests-jvm")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = "21"
    languageVersion = "1.9"
    allWarningsAsErrors = true
  }
}
tasks.withType<JavaCompile> {
  options.compilerArgs.add("-parameters")
}

jacoco {
  toolVersion = "0.8.9"
}

tasks.jacocoTestReport {
  reports {
    xml.required = true
    csv.required = false
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy("jacocoTestReport")
}

tasks.withType<DependencyUpdatesTask> {
  gradleReleaseChannel = "current"
}

jib {
  from {
    image = "eclipse-temurin:21-jre-alpine"
  }
  to {
    image = "ghcr.io/jvorhauer/konomas:latest"
    tags = mutableSetOf("$version")
    auth {
      username = System.getenv("GITHUB_USERNAME")
      password = System.getenv("GITHUB_TOKEN")
    }
  }
  container {
    jvmFlags = listOf("-Xms512m", "-Xmx1024m")
    mainClass = "blog.MainKt"
    ports = listOf("8080/tcp")
    creationTime = Instant.now().toString()
  }
  setAllowInsecureRegistries(true)
}

tasks.named("jib") {
  dependsOn("test")
}

configure<ReleaseExtension> {
  tagTemplate.set("v${version}")
  failOnUnversionedFiles.set(false)
}

sentry {
  includeSourceContext = true
  org = "jdriven-5eda1177f"
  projectName = "konomas"
  authToken = System.getenv("KONOMAS_SENTRY_TOKEN")
}
