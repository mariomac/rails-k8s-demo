# Barcelona Tapas Finder

A small demo app split into 4 pieces, deployable to a local Kubernetes cluster (kind/minikube/Docker Desktop):

- **`backend-ruby/`** — Ruby on Rails API (Rails 7, API-only) that talks to Postgres and exposes restaurants + reviews.
- **`backend-wildfly/`** — The same API re-implemented in Java (JAX-RS + plain JDBC) deployed to WildFly 26. Same routes, same JSON shapes, same env vars — a drop-in swap for the Rails backend.
- **`frontend/`** — Node.js/Express + EJS app for searching restaurants, viewing details, and posting/reading opinions.
- **`loadgen/`** — Tiny Node script that continuously browses the frontend at ~2-3 requests/second.
- **`deployments/`** — Kubernetes manifests (Postgres with pre-seeded fake data, frontend, load generator, and a backend variant), all in the `tapas` namespace.

## Choosing a backend

Both backends expose the identical API (`GET /restaurants`, `GET /restaurants/:id`, `GET`/`POST /restaurants/:id/reviews`, `GET /healthz`) on port 3000, so the frontend works unmodified against either one. The shared manifests in `deployments/` don't include a backend — pick one from `deployments/backend-ruby/` or `deployments/backend-wildfly/` (both define a `tapas-backend` Deployment + Service, so only one should be applied at a time).

## Quick start (Kind)

If you're using [Kind](https://kind.sigs.k8s.io/), a `Makefile` handles building and loading images:

```bash
make deploy                  # backend defaults to ruby
make deploy BACKEND=wildfly  # or explicitly pick the WildFly backend
```

Defaults to a cluster named `kind`; override with `make deploy CLUSTER=my-cluster`. Switching backends (`make deploy BACKEND=wildfly` after a `ruby` deploy, or vice versa) automatically removes the other variant first. Other targets: `make build`, `make load`, `make down` (delete everything), `make restart`, `make logs-backend` / `logs-frontend` / `logs-loadgen` (all respect `BACKEND=`/`NAMESPACE=`).

For other local clusters (minikube, Docker Desktop), follow the manual steps below.

## 1. Build the images

The manifests reference `tapas-backend-ruby:local` / `tapas-backend-wildfly:local`, `tapas-frontend:local`, and `tapas-loadgen:local` with `imagePullPolicy: IfNotPresent`, so build them locally and make them visible to your cluster.

```bash
docker build -t tapas-backend-ruby:local ./backend-ruby       # if using the Ruby backend
docker build -t tapas-backend-wildfly:local ./backend-wildfly # if using the WildFly backend
docker build -t tapas-frontend:local ./frontend
docker build -t tapas-loadgen:local ./loadgen
```

Then load them into your cluster:

- **kind**: `kind load docker-image <backend-image> tapas-frontend:local tapas-loadgen:local`
- **minikube**: `minikube image load <backend-image> && minikube image load tapas-frontend:local && minikube image load tapas-loadgen:local`
- **Docker Desktop Kubernetes**: no extra step needed, it shares the local Docker daemon.

## 2. Deploy

```bash
kubectl apply -f deployments/
kubectl apply -f deployments/backend-ruby/      # or deployments/backend-wildfly/
```

This creates the `tapas` namespace and, inside it:

- `tapas-db` — Postgres, initialized via a ConfigMap (`01-postgres-configmap.yaml`) that creates `restaurants`/`reviews` tables and seeds ~15 fake Barcelona tapas bars with reviews.
- `tapas-backend` — either the Rails API or the WildFly/JAX-RS API on port 3000, waits for Postgres to be ready before starting.
- `tapas-frontend` — Node/Express UI on port 8080, exposed via NodePort `30080`.
- `tapas-loadgen` — background job hitting the frontend every ~350-500ms.

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
- The WildFly backend takes noticeably longer to become ready than Rails (JVM + WAR deployment vs. Puma boot) — its readiness probe allows up to ~100s before being marked unhealthy.
