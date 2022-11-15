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

### Docker Registry @ GitHub

```shell
echo -n "jvorhauer:{{microk8s docker registry token}}" | base64
kubectl create -f ~/Code/k8s/registryconfig.yaml
```

The image is stored in the registry of GitHub, ghcr.io. This registry requires authentication with a special JSON file, that is stored in a
k8s secret `dockerregistry`. The JSON file and the YAML file to deploy it are in my `~/Code/k8s` folder.

### nginx proxy

The noviblog api is proxied by an nginx running on enna. The configuration is in `/etc/nginx/sites-available/noviblog-https.conf`, which is 
soft-linked (ln -s) to 
