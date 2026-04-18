SHELL := /bin/bash
.DEFAULT_GOAL := help

APP_ID := com.jakemccrary.gravitygainsassist
MAIN_ACTIVITY := $(APP_ID)/.MainActivity
GRADLEW := ./gradlew
ADB := adb
SERIAL_ARG := $(if $(SERIAL),-s $(SERIAL),)
DEBUG_APK := app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK := app/build/outputs/apk/release/app-release.apk
rwildcard = $(foreach d,$(wildcard $1*),$(call rwildcard,$d/,$2) $(filter $(subst *,%,$2),$d))

GRADLE_INPUTS := \
	$(GRADLEW) \
	settings.gradle.kts \
	build.gradle.kts \
	gradle.properties \
	app/build.gradle.kts \
	app/proguard-rules.pro \
	$(call rwildcard,gradle/,*)

RELEASE_INPUTS := \
	$(GRADLE_INPUTS) \
	$(call rwildcard,app/src/main/,*)

.PHONY: help test assemble bundle-release verify install install-release run clean

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*## "}; {printf "\033[36m%-12s\033[0m %s\n", $$1, $$2}'

test: ## Run unit tests
	$(GRADLEW) test

assemble: ## Build the debug APK
	$(GRADLEW) assembleDebug

release: $(RELEASE_APK) ## Build the release APK

$(RELEASE_APK): $(RELEASE_INPUTS)
	$(GRADLEW) assembleRelease

bundle-release: ## Build the release app bundle
	$(GRADLEW) bundleRelease

verify: ## Run the standard verification command for this project
	$(GRADLEW) test assembleDebug

install: ## Build and install the debug app; set SERIAL=<device-id> when needed
	$(GRADLEW) assembleDebug
	$(ADB) $(SERIAL_ARG) install -r $(DEBUG_APK)

install-release: release ## Build and install the release app; set SERIAL=<device-id> when needed
	$(ADB) $(SERIAL_ARG) install -r $(RELEASE_APK)

run: ## Launch the app; set SERIAL=<device-id> when needed
	$(ADB) $(SERIAL_ARG) shell am start -n $(MAIN_ACTIVITY)

clean: ## Remove Gradle build outputs
	$(GRADLEW) clean
