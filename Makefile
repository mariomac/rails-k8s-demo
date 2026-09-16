CLUSTER ?= kind
NAMESPACE ?= tapas
BACKEND ?= ruby
FRONTEND_IMAGE ?= tapas-frontend:local
LOADGEN_IMAGE ?= tapas-loadgen:local
BACKEND_RUBY_IMAGE ?= tapas-backend-ruby:local
BACKEND_WILDFLY_IMAGE ?= tapas-backend-wildfly:local

ifeq ($(BACKEND),wildfly)
BACKEND_IMAGE := $(BACKEND_WILDFLY_IMAGE)
BACKEND_DIR := backend-wildfly
else ifeq ($(BACKEND),ruby)
BACKEND_IMAGE := $(BACKEND_RUBY_IMAGE)
BACKEND_DIR := backend-ruby
else
$(error Unknown BACKEND "$(BACKEND)", expected "ruby" or "wildfly")
endif

.PHONY: build load deploy up down restart logs-backend logs-frontend logs-loadgen

build:
	docker build -t $(BACKEND_IMAGE) ./$(BACKEND_DIR)
	docker build -t $(FRONTEND_IMAGE) ./frontend
	docker build -t $(LOADGEN_IMAGE) ./loadgen

load: build
	kind load docker-image $(BACKEND_IMAGE) $(FRONTEND_IMAGE) $(LOADGEN_IMAGE) --name $(CLUSTER)

# Applies the shared manifests (namespace, db, frontend, loadgen), then swaps in
# the chosen backend variant, removing the other one if it was deployed before.
deploy: load
	kubectl apply -f deployments/
	kubectl delete -f deployments/backend-ruby/ --ignore-not-found
	kubectl delete -f deployments/backend-wildfly/ --ignore-not-found
	kubectl apply -f deployments/backend-$(BACKEND)/

up: deploy

down:
	kubectl delete -f deployments/backend-ruby/ --ignore-not-found
	kubectl delete -f deployments/backend-wildfly/ --ignore-not-found
	kubectl delete -f deployments/ --ignore-not-found

# Adding here some changes in the base branch, right after other
# PRs already rely on this commit
restart: down deploy

logs-backend:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-backend

logs-frontend:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-frontend

logs-loadgen:
	kubectl logs -n $(NAMESPACE) -f deployment/tapas-loadgen
