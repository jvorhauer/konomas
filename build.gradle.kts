import net.researchgate.release.ReleaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.springframework.boot") version "3.2.1"
  id("io.spring.dependency-management") version "1.1.4"
  kotlin("jvm") version "1.9.22"
  kotlin("plugin.spring") version "1.9.22"
  id("com.google.cloud.tools.jib") version "3.4.0"
  id("net.researchgate.release") version "3.0.2"
}

group = "nl.vorhauer"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {

  implementation(platform("com.typesafe.akka:akka-bom_2.13:2.8.5"))

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

  implementation("com.typesafe.akka:akka-serialization-jackson_2.13")
  implementation("com.typesafe.akka:akka-persistence-typed_2.13")
  implementation("com.typesafe.akka:akka-persistence-cassandra_2.13:1.1.1")
  implementation("com.typesafe.akka:akka-persistence-query_2.13")
  implementation("org.scala-lang:scala-library:2.13.12")

  implementation("io.netty:netty-all:4.1.105.Final")
  implementation("org.valiktor:valiktor-spring-boot-starter:0.12.0")
  implementation("org.valiktor:valiktor-core:0.12.0")
  implementation("org.valiktor:valiktor-javatime:0.12.0")
  implementation("org.owasp.encoder:encoder:1.2.3")
  implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
  implementation("ch.qos.logback:logback-classic:1.4.14")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test:3.6.2")
  testImplementation("com.typesafe.akka:akka-persistence-testkit_2.13:2.8.5")
  testImplementation("junit:junit:4.13.2")
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = "21"
    languageVersion = "2.0"
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

jib {
  from {
    image = "eclipse-temurin:21-jre-alpine"
  }
  to {
    image = "ghcr.io/jvorhauer/noviblog:latest"
    tags = mutableSetOf("$version")
    auth {
      username = System.getenv("GITHUB_USER")
      password = System.getenv("GITHUB_TOKEN")
    }
  }
  container {
    jvmFlags = listOf("-Xms512m", "-Xmx1024m")
    mainClass = "blog.ApplicationKt"
    ports = listOf("8080/tcp")
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
