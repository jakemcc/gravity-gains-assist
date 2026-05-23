SHELL := /bin/bash
.DEFAULT_GOAL := help

APP_ID := com.jakemccrary.gravitygainsassist
MAIN_ACTIVITY := $(APP_ID)/.MainActivity
GRADLEW := ./gradlew
ADB := adb
VERSION_FILE := app/build.gradle.kts
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

DEBUG_INPUTS := \
	$(GRADLE_INPUTS) \
	$(call rwildcard,app/src/main/,*)

.PHONY: help test bump-version bundle-release verify install install-release run clean

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*## ' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*## "}; {printf "\033[36m%-12s\033[0m %s\n", $$1, $$2}'

test: ## Run unit tests
	$(GRADLEW) test

bump-version: ## Bump app versionName by 0.1 and versionCode by 1
	perl -0pi -e 's/(versionCode\s*=\s*)(\d+)/$$1 . ($$2 + 1)/e; s/(versionName\s*=\s*")(\d+)\.(\d+)(")/$$1 . ($$2 + int(($$3 + 1) \/ 10)) . "." . (($$3 + 1) % 10) . $$4/e' $(VERSION_FILE)

assemble: $(DEBUG_APK) ## Build the debug APK

$(DEBUG_APK): $(DEBUG_INPUTS)
	$(GRADLEW) assembleDebug

release: $(RELEASE_APK) ## Build the release APK

$(RELEASE_APK): $(RELEASE_INPUTS)
	$(GRADLEW) assembleRelease

bundle-release: ## Build the release app bundle
	$(GRADLEW) bundleRelease

verify: ## Run the standard verification command for this project
	$(GRADLEW) test assembleDebug

install: assemble ## Build and install the debug app; set SERIAL=<device-id> when needed
	$(ADB) $(SERIAL_ARG) install -r $(DEBUG_APK)

install-release: release ## Build and install the release app; set SERIAL=<device-id> when needed
	$(ADB) $(SERIAL_ARG) install -r $(RELEASE_APK)

run: ## Launch the app; set SERIAL=<device-id> when needed
	$(ADB) $(SERIAL_ARG) shell am start -n $(MAIN_ACTIVITY)

clean: ## Remove Gradle build outputs
	$(GRADLEW) clean
