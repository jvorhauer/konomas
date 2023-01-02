import net.researchgate.release.ReleaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.springframework.boot") version "2.7.5"
  id("io.spring.dependency-management") version "1.1.0"
  kotlin("jvm") version "1.8.0"
  kotlin("plugin.spring") version "1.7.21"
  id("com.google.cloud.tools.jib") version "3.3.1"
  id("net.researchgate.release") version "3.0.2"
  id("jacoco")
}

group = "nl.vorhauer"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {

  implementation(platform("com.typesafe.akka:akka-bom_2.13:2.7.0"))

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.0")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

  implementation("com.typesafe.akka:akka-serialization-jackson_2.13")
  implementation("com.typesafe.akka:akka-persistence-typed_2.13")
  implementation("com.typesafe.akka:akka-persistence-cassandra_2.13:1.1.0")
  implementation("com.typesafe.akka:akka-persistence-query_2.13")
  implementation("com.typesafe.akka:akka-cluster-typed_2.13")
  implementation("org.scala-lang:scala-library:2.13.10")

  implementation("io.netty:netty-all:4.1.85.Final")
  implementation("org.valiktor:valiktor-spring-boot-starter:0.12.0")
  implementation("org.valiktor:valiktor-core:0.12.0")
  implementation("org.valiktor:valiktor-javatime:0.12.0")
  implementation("org.owasp.encoder:encoder:1.2.3")
  implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")
  implementation("org.slf4j:slf4j-simple:2.0.3")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test:3.5.0")
  testImplementation("com.typesafe.akka:akka-persistence-testkit_2.13:2.7.0")
  testImplementation("junit:junit:4.13.2")
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = "11"
    languageVersion = "1.8"
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

jib {
  from {
    image = "openjdk:11"
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

tasks.jacocoTestReport {
  reports {
    xml.required.set(true)
    html.required.set(false)
    csv.required.set(false)
  }
}

tasks.named("check") { dependsOn("jacocoTestReport") }

configure<ReleaseExtension> {
  tagTemplate.set("v${version}")
  failOnUnversionedFiles.set(false)
}
