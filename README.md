# Kotlin kNowledge Management Service (KoNoMaS)

## Status

[![build](https://github.com/jvorhauer/konomas/actions/workflows/gradle.yml/badge.svg)](https://github.com/jvorhauer/noviblog/actions/workflows/gradle.yml)
[![coverage](https://codecov.io/gh/jvorhauer/noviblog/branch/main/graph/badge.svg?token=Nn5OmNCOEY)](https://codecov.io/gh/jvorhauer/noviblog)

An Event Sourced version of the backend for the FrontEnd solution.

## Vision

To prove that event sourcing works for a well-known project, [Noviaal](https://github.com/jvorhauer/noviaal), I have build this. 
Event sourcing fits really well, as the users of NoviBlog can update notes and can add comments, etc. to any existing note. 
Being able to understand the sequence of events that leads/led to a certain state is gold when something does not work as intended.

## Tech Stack

* Kotlin (tired of Java; Scala has no future as I far as I can see)
* Spring WebFlux (almost all annotations reduced away)
* Netty (async, best for webflux)
* Spring Actuator (free k8s health endpoints)
* Akka Persistence (the only viable event sourced option)
* JUnit 5
* Gradle
* Jackson (easier and most boring option)
* Cassandra (event store) or
* R2DBC in case of Replicated Event Sourcing
* TSID for unique, historically sortable ids

## Build

GitHub Actions FTW! See `.github/workflows/gradle.yml`.

## Operations

In order to use `kubectl` on your machine:

```shell
microk8s.kubectl config view --raw > $HOME/.kube/config
```

## Deploy

To Kubernetes cluster at MiruVor, see `deploy/deployment.yaml`. First deploy to my k8s cluster also needs `deploy/service.yaml`.
To allow traffic from outside the namespace in k8s, `deploy/ingress.yaml` should also be applied.

### re-deploy

```shell
kubectl rollout restart -n default deployment konomas
```

But changing the `deployment.yml` works as a redeployment trigger.

Connect to the new pod:

```shell
kubectl get pods
kubectl exec -i -t ||pod-name|| -- /bin/bash
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

## Roadmap

### Improve resilience

In order to provide more resilience for crashing k8s nodes and other anomalies, the use of Akka Cluster presents itself.

However, Akka Cluster is rather an extra layer of complication that I would like to postpone to when such resilience is really 
useful. With the introduction of [Replicated Event Sourcing](https://doc.akka.io/docs/akka/current/typed/replicated-eventsourcing.html)
the learning curve seems less objectable.

### Improve architecture

Get rid of:
1. Valiktor: not maintained, last change was 4 years ago -> switch to Hibernate Validator, but without using annotations
2. UUID: too much resource consumption, not sortable and no relevant information in the key -> TSID
