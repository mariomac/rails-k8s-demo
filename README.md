# Barcelona Tapas Finder

A small demo app split into 4 pieces, deployable to a local Kubernetes cluster (kind/minikube/Docker Desktop):

- **`backend/`** — Ruby on Rails API (Rails 7, API-only) that talks to Postgres and exposes restaurants + reviews.
- **`frontend/`** — Node.js/Express + EJS app for searching restaurants, viewing details, and posting/reading opinions.
- **`loadgen/`** — Tiny Node script that continuously browses the frontend at ~2-3 requests/second.
- **`deployments/`** — Kubernetes manifests (Postgres with pre-seeded fake data, backend, frontend, load generator), all in the `tapas` namespace.

## Quick start (Kind)

If you're using [Kind](https://kind.sigs.k8s.io/), a `Makefile` handles building and loading images:

```bash
make deploy       # build images, load them into the kind cluster, kubectl apply -f deployments/
```

Defaults to a cluster named `kind`; override with `make deploy CLUSTER=my-cluster`. Other targets: `make build`, `make load`, `make down` (delete everything), `make restart`, `make logs-backend` / `logs-frontend` / `logs-loadgen`.

For other local clusters (minikube, Docker Desktop), follow the manual steps below.

## 1. Build the images

The manifests reference `tapas-backend:local`, `tapas-frontend:local`, and `tapas-loadgen:local` with `imagePullPolicy: IfNotPresent`, so build them locally and make them visible to your cluster.

```bash
docker build -t tapas-backend:local ./backend
docker build -t tapas-frontend:local ./frontend
docker build -t tapas-loadgen:local ./loadgen
```

Then load them into your cluster:

- **kind**: `kind load docker-image tapas-backend:local tapas-frontend:local tapas-loadgen:local`
- **minikube**: `minikube image load tapas-backend:local && minikube image load tapas-frontend:local && minikube image load tapas-loadgen:local`
- **Docker Desktop Kubernetes**: no extra step needed, it shares the local Docker daemon.

## 2. Deploy

```bash
kubectl apply -f deployments/
```

This creates the `tapas` namespace and, inside it:

- `tapas-db` — Postgres, initialized via a ConfigMap (`01-postgres-configmap.yaml`) that creates `restaurants`/`reviews` tables and seeds ~15 fake Barcelona tapas bars with reviews.
- `tapas-backend` — Rails API on port 3000, waits for Postgres to be ready before starting.
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
- The Rails app connects directly to the pre-seeded schema (no migrations are run); it's intentionally minimal for a local demo.
