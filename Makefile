CLUSTER ?= kind
NAMESPACE ?= tapas
BACKEND_IMAGE ?= tapas-backend:local
FRONTEND_IMAGE ?= tapas-frontend:local
LOADGEN_IMAGE ?= tapas-loadgen:local

.PHONY: build load deploy up down restart logs-backend logs-frontend logs-loadgen

build:
	docker build -t $(BACKEND_IMAGE) ./backend
	docker build -t $(FRONTEND_IMAGE) ./frontend
	docker build -t $(LOADGEN_IMAGE) ./loadgen

load: build
	kind load docker-image $(BACKEND_IMAGE) $(FRONTEND_IMAGE) $(LOADGEN_IMAGE) --name $(CLUSTER)

deploy: load
	kubectl apply -f deployments/

up: deploy

down:
	kubectl delete -f deployments/ --ignore-not-found

restart: down deploy

logs-backend:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-backend

logs-frontend:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-frontend

logs-loadgen:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-loadgen
