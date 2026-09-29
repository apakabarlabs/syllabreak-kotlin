PYTHON_DATA_DIR = ../syllabreak-python/syllabreak/data
KOTLIN_RESOURCES_DIR = src/main/resources/fm/apakabar/syllabreak
KOTLIN_TEST_RESOURCES_DIR = src/test/resources

.DEFAULT_GOAL := build

.PHONY: install-tools comments lint lint-fix format test-build test docs build clean install sync-yaml publish publish-local publish-check

install-tools:
	python3 -m pip install --quiet --upgrade git+https://github.com/botforge-pro/commentcensor.git

comments:
	commentcensor .

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
