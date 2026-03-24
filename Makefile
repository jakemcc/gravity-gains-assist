SHELL := /bin/bash
.DEFAULT_GOAL := help

APP_ID := com.jakemccrary.gravitygainsassist
MAIN_ACTIVITY := $(APP_ID)/.MainActivity
GRADLEW := ./gradlew
ADB := adb
SERIAL_ARG := $(if $(SERIAL),-s $(SERIAL),)
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk

.PHONY: help test assemble release bundle-release verify install run clean

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*## "}; {printf "\033[36m%-12s\033[0m %s\n", $$1, $$2}'

test: ## Run unit tests
	$(GRADLEW) test

assemble: ## Build the debug APK
	$(GRADLEW) assembleDebug

release: ## Build the release APK
	$(GRADLEW) assembleRelease

bundle-release: ## Build the release app bundle
	$(GRADLEW) bundleRelease

verify: ## Run the standard verification command for this project
	$(GRADLEW) test assembleDebug

install: ## Build and install the debug app; set SERIAL=<device-id> when needed
	$(GRADLEW) assembleDebug
	$(ADB) $(SERIAL_ARG) install -r $(DEBUG_APK)

run: ## Launch the app; set SERIAL=<device-id> when needed
	$(ADB) $(SERIAL_ARG) shell am start -n $(MAIN_ACTIVITY)

clean: ## Remove Gradle build outputs
	$(GRADLEW) clean
