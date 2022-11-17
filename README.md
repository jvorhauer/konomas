# Novi Blog

## Status

[![build](https://github.com/jvorhauer/noviblog/actions/workflows/gradle.yml/badge.svg)](https://github.com/jvorhauer/noviblog/actions/workflows/gradle.yml)
[![coverage](https://codecov.io/gh/jvorhauer/noviblog/branch/main/graph/badge.svg?token=Nn5OmNCOEY)](https://codecov.io/gh/jvorhauer/noviblog)

An Event Sourced version of the backend for the Novi Blog.

## Tech Stack

* Kotlin (tired of Java, still contemplating Scala though)
* Spring WebFlux (almost all annotations reduced away)
* Netty (async, best for webflux)
* Spring Actuator (free k8s health endpoints)
* Valiktor (explicit but nice validation)
* Akka Persistence (the only viable event sourced option)
* JUnit 5
* Gradle
* Jackson (easier and most boring option)
* Cassandra (event store)

## Build

GitHub Actions FTW! See `.github/workflows/gradle.yml`.

### Tekton?

In order to use `kubectl` on your machine:

```shell
microk8s.kubectl config view --raw > $HOME/.kube/config
```
--> [Tekton on k8s](https://earthly.dev/blog/building-k8s-tekton/)


## Unit Test Coverage

![sunburst](https://codecov.io/gh/jvorhauer/noviblog/branch/main/graphs/sunburst.svg?token=Nn5OmNCOEY)

## Deploy

To Kubernetes cluster at MiruVor, see `deploy/deployment.yaml`. First deploy to my k8s cluster also needs `deploy/service.yaml`.
To allow traffic from outside the namespace in k8s, `deploy/ingress.yaml` should also be applied.

### re-deploy

Should be a RollingUpdate, checking now!


```shell
kubectl rollout restart -n default deployment noviblog
```

### Docker Registry @ GitHub

```shell
echo -n "jvorhauer:{{microk8s docker registry token}}" | base64
kubectl create -f ~/Code/k8s/registryconfig.yaml
```

The image is stored in the registry of GitHub, ghcr.io. This registry requires authentication with a special JSON file, that is stored in a
k8s secret `dockerregistry`. The JSON file and the YAML file to deploy it are in my `~/Code/k8s` folder.

Username and password are stored in environment variables 

### nginx proxy

The noviblog api is proxied by an nginx running on enna. The configuration is in `/etc/nginx/sites-available/noviblog-https.conf`, which is 
soft-linked (ln -s) to `/etc/nginx/sites-enabled`. A redirector to the https site via `noviblog-http.conf`.

### Bonus: k8s dashboard

```shell
kubectl port-forward -n kube-system service/kubernetes-dashboard 8443:443
```

This way the dashboard is safely and only locally available via [dashboard](https://localhost:8443/#/workloads?namespace=default)
