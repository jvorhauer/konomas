# Novi Blog

An Event Sourced version of the backend for the Novi Blog.

## Tech Stack

* Kotlin (sick and tired of Java, still contemplating Scala though)
* Spring WebFlux (almost all annotations reduced away)
* Netty (async, best for webflux)
* Spring Actuator (free k8s health endpoints)
* Valiktor (explicit but nice validation)
* Akka Persistence (the only viable event sourced option)
* JUnit 5
* Gradle
* Jackson (easier and most boring option)
* Cassandra (event store)

## Deploy

To Kubernetes cluster at MiruVor.

This needs some more work, mainly on how to get the authentication ZIP archive working in k8s...
