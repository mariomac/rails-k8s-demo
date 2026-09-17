# Barcelona Tapas Finder

A small demo app split into 4 pieces, deployable to a local Kubernetes cluster (kind/minikube/Docker Desktop):

- **`backend-ruby/`** — Ruby on Rails API (Rails 7, API-only) that talks to Postgres and exposes restaurants + reviews.
- **`backend-wildfly/`** — The same API re-implemented in Java (JAX-RS + plain JDBC) deployed to WildFly 41 (Jakarta EE 11, JDK 21). Same routes, same JSON shapes, same env vars — a drop-in swap for the Rails backend.
- **`frontend/`** — Node.js/Express + EJS app for searching restaurants, viewing details, and posting/reading opinions.
- **`loadgen-node/`** — Node script that continuously browses the frontend at ~2-3 requests/second.
- **`loadgen-go/`** — The same load generator re-implemented in Go. Same behavior, same env vars — a drop-in swap for the Node one.
- **`deployments/`** — Kubernetes manifests (Postgres with pre-seeded fake data, frontend, and a backend + load generator variant), all in the `tapas` namespace.

## Choosing a backend / load generator

Both backends expose the identical API (`GET /restaurants`, `GET /restaurants/:id`, `GET`/`POST /restaurants/:id/reviews`, `GET /healthz`) on port 3000, so the frontend works unmodified against either one. Both load generators browse the frontend the same way. The shared manifests in `deployments/` don't include a backend or load generator — pick one of `deployments/backend-ruby/` / `deployments/backend-wildfly/` and one of `deployments/loadgen-node/` / `deployments/loadgen-go/` (each pair defines the same Deployment + Service name, so only one of each should be applied at a time).

## Quick start (Kind)

If you're using [Kind](https://kind.sigs.k8s.io/), a `Makefile` handles building and loading images:

```bash
make deploy                              # backend=ruby, loadgen=node (defaults)
make deploy BACKEND=wildfly LOADGEN=go   # or mix and match
```

Defaults to a cluster named `kind`; override with `make deploy CLUSTER=my-cluster`. Switching variants (e.g. `BACKEND=wildfly` after a `ruby` deploy, or `LOADGEN=go` after `node`) automatically removes the other one first. Other targets: `make build`, `make load`, `make down` (delete everything), `make restart`, `make logs-backend` / `logs-frontend` / `logs-loadgen` (all respect `BACKEND=`/`LOADGEN=`/`NAMESPACE=`).

For other local clusters (minikube, Docker Desktop), follow the manual steps below.

## 1. Build the images

The manifests reference `tapas-backend-ruby:local` / `tapas-backend-wildfly:local`, `tapas-frontend:local`, and `tapas-loadgen-node:local` / `tapas-loadgen-go:local` with `imagePullPolicy: IfNotPresent`, so build them locally and make them visible to your cluster.

```bash
docker build -t tapas-backend-ruby:local ./backend-ruby       # if using the Ruby backend
docker build -t tapas-backend-wildfly:local ./backend-wildfly # if using the WildFly backend
docker build -t tapas-frontend:local ./frontend
docker build -t tapas-loadgen-node:local ./loadgen-node       # if using the Node load generator
docker build -t tapas-loadgen-go:local ./loadgen-go           # if using the Go load generator
```

Then load them into your cluster:

- **kind**: `kind load docker-image <backend-image> tapas-frontend:local <loadgen-image>`
- **minikube**: `minikube image load <backend-image> && minikube image load tapas-frontend:local && minikube image load <loadgen-image>`
- **Docker Desktop Kubernetes**: no extra step needed, it shares the local Docker daemon.

## 2. Deploy

```bash
kubectl apply -f deployments/
kubectl apply -f deployments/backend-ruby/      # or deployments/backend-wildfly/
kubectl apply -f deployments/loadgen-node/      # or deployments/loadgen-go/
```

This creates the `tapas` namespace and, inside it:

- `tapas-db` — Postgres, initialized via a ConfigMap (`01-postgres-configmap.yaml`) that creates `restaurants`/`reviews` tables and seeds ~15 fake Barcelona tapas bars with reviews.
- `tapas-backend` — either the Rails API or the WildFly 41/JAX-RS API on port 3000, waits for Postgres to be ready before starting.
- `tapas-frontend` — Node/Express UI on port 8080, exposed via NodePort `30080`.
- `tapas-loadgen` — either the Node or Go implementation, hitting the frontend every ~350-500ms.

## 3. Access the app

```bash
kubectl get pods -n tapas
```

Once everything is `Running`/`Ready`:

- **minikube**: `minikube service tapas-frontend -n tapas --url`
- **kind / Docker Desktop**: open `http://localhost:30080` (or `kubectl port-forward -n tapas svc/tapas-frontend 8080:8080` and use `http://localhost:8080`)

Search by name, neighborhood (e.g. `El Born`, `Poble Sec`, `Eixample`) or food type (e.g. `Seafood Tapas`, `Modern Tapas`), open a restaurant to see its details, read opinions, and post your own.

## Notes

- Postgres data lives in an `emptyDir` volume for simplicity — it resets if the pod restarts. Swap in a `PersistentVolumeClaim` for anything longer-lived.
- The DB password is stored in a plain `Secret` (`02-postgres-secret.yaml`) with a demo value — replace it before using this anywhere real.
- Both backends connect directly to the pre-seeded schema (no migrations, no JPA/ORM datasource setup) via plain SQL/JDBC; it's intentionally minimal for a local demo.
- The WildFly backend's readiness probe allows up to ~100s before being marked unhealthy, as a conservative margin for slower environments (JVM + WAR deployment vs. Puma boot); in practice WildFly 41 comes up in a few seconds.
- WildFly's community releases aren't traditionally supported long-term (a new major version ships every 2-3 months); WildFly 41 (July 2026) was the latest at the time this was written, targeting Jakarta EE 11 on JDK 21.
