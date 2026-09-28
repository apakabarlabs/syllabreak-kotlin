PYTHON_DATA_DIR = ../syllabreak-python/syllabreak/data
KOTLIN_RESOURCES_DIR = src/main/resources
KOTLIN_TEST_RESOURCES_DIR = src/test/resources
COMMENTCENSOR_REF ?= 48d702a6ba4ace9af0bf996fad2fff9a012f25f9
COMMENTCENSOR_ENV = build/commentcensor
COMMENTCENSOR = $(COMMENTCENSOR_ENV)/bin/commentcensor

.DEFAULT_GOAL := build

.PHONY: install-tools comments lint lint-fix format test-build test docs build clean install sync-yaml publish publish-local publish-check

install-tools:
	python3 -m venv $(COMMENTCENSOR_ENV)
	$(COMMENTCENSOR_ENV)/bin/pip install --quiet --upgrade git+https://github.com/botforge-pro/commentcensor.git@$(COMMENTCENSOR_REF)

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
	./gradlew assemble

publish:
	@test -n "$(CI)" || { echo "publish runs in the release workflow, not locally" >&2; exit 1; }
	./gradlew publishAndReleaseToMavenCentral

publish-local:
	./gradlew publishToMavenLocal -PunsignedLocalPublish

publish-check:
	./gradlew publishToMavenLocal

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
