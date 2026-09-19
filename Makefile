PYTHON_DATA_DIR = ../syllabreak-python/syllabreak/data
KOTLIN_RESOURCES_DIR = src/main/resources
KOTLIN_TEST_RESOURCES_DIR = src/test/resources
COMMENTCENSOR_VERSION ?= v0.3.2
COMMENTCENSOR_ENV = build/commentcensor
COMMENTCENSOR = $(COMMENTCENSOR_ENV)/bin/commentcensor

.DEFAULT_GOAL := build

.PHONY: install-tools comments lint lint-fix format test-build test docs build clean install sync-yaml

install-tools:
	python3 -m venv $(COMMENTCENSOR_ENV)
	$(COMMENTCENSOR_ENV)/bin/pip install --quiet --upgrade git+https://github.com/botforge-pro/commentcensor.git@$(COMMENTCENSOR_VERSION)

comments:
	$(COMMENTCENSOR) .

lint: comments
	./gradlew ktlintCheck

lint-fix:
	./gradlew ktlintFormat

format: lint-fix

test:
	./gradlew test

test-build:
	./gradlew compileTestKotlin

docs:
	./gradlew dokkaGeneratePublicationHtml

build: lint test-build test docs
	./gradlew build

clean:
	./gradlew clean

install:
	$(MAKE) install-tools
	./gradlew --version

sync-yaml:
	mkdir -p $(KOTLIN_RESOURCES_DIR) $(KOTLIN_TEST_RESOURCES_DIR)
	cp $(PYTHON_DATA_DIR)/rules.yaml $(KOTLIN_RESOURCES_DIR)/
	cp $(PYTHON_DATA_DIR)/syllabify_tests.yaml $(KOTLIN_TEST_RESOURCES_DIR)/
	cp $(PYTHON_DATA_DIR)/detect_language_tests.yaml $(KOTLIN_TEST_RESOURCES_DIR)/
	cp $(PYTHON_DATA_DIR)/tokenizer_tests.yaml $(KOTLIN_TEST_RESOURCES_DIR)/
	cp $(PYTHON_DATA_DIR)/language_rule_tests.yaml $(KOTLIN_TEST_RESOURCES_DIR)/
