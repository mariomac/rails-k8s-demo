CLUSTER ?= kind
NAMESPACE ?= tapas
BACKEND ?= ruby
LOADGEN ?= node
FRONTEND_IMAGE ?= tapas-frontend:local
BACKEND_RUBY_IMAGE ?= tapas-backend-ruby:local
BACKEND_WILDFLY_IMAGE ?= tapas-backend-wildfly:local
LOADGEN_NODE_IMAGE ?= tapas-loadgen-node:local
LOADGEN_GO_IMAGE ?= tapas-loadgen-go:local

ifeq ($(BACKEND),wildfly)
BACKEND_IMAGE := $(BACKEND_WILDFLY_IMAGE)
BACKEND_DIR := backend-wildfly
else ifeq ($(BACKEND),ruby)
BACKEND_IMAGE := $(BACKEND_RUBY_IMAGE)
BACKEND_DIR := backend-ruby
else
$(error Unknown BACKEND "$(BACKEND)", expected "ruby" or "wildfly")
endif

ifeq ($(LOADGEN),go)
LOADGEN_IMAGE := $(LOADGEN_GO_IMAGE)
LOADGEN_DIR := loadgen-go
else ifeq ($(LOADGEN),node)
LOADGEN_IMAGE := $(LOADGEN_NODE_IMAGE)
LOADGEN_DIR := loadgen-node
else
$(error Unknown LOADGEN "$(LOADGEN)", expected "node" or "go")
endif

.PHONY: build load deploy up down restart logs-backend logs-frontend logs-loadgen

build:
	docker build -t $(BACKEND_IMAGE) ./$(BACKEND_DIR)
	docker build -t $(FRONTEND_IMAGE) ./frontend
	docker build -t $(LOADGEN_IMAGE) ./$(LOADGEN_DIR)

load: build
	kind load docker-image $(BACKEND_IMAGE) $(FRONTEND_IMAGE) $(LOADGEN_IMAGE) --name $(CLUSTER)

# Applies the shared manifests (namespace, db, frontend), then swaps in the
# chosen backend and load generator variants, removing the other ones first.
deploy: load
	kubectl apply -f deployments/
	kubectl delete -f deployments/backend-ruby/ --ignore-not-found
	kubectl delete -f deployments/backend-wildfly/ --ignore-not-found
	kubectl apply -f deployments/backend-$(BACKEND)/
	kubectl delete -f deployments/loadgen-node/ --ignore-not-found
	kubectl delete -f deployments/loadgen-go/ --ignore-not-found
	kubectl apply -f deployments/loadgen-$(LOADGEN)/

up: deploy

down:
	kubectl delete -f deployments/backend-ruby/ --ignore-not-found
	kubectl delete -f deployments/backend-wildfly/ --ignore-not-found
	kubectl delete -f deployments/loadgen-node/ --ignore-not-found
	kubectl delete -f deployments/loadgen-go/ --ignore-not-found
	kubectl delete -f deployments/ --ignore-not-found

restart: down deploy

# Blablablabla
logs-backend:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-backend

logs-frontend:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-frontend

logs-loadgen:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-loadgen
