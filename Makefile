# Build the plugin
assemble:
	./gradlew assemble

clean:
	rm -rf .nextflow*
	rm -rf work
	rm -rf build
	./gradlew clean

# Run plugin unit tests
test:
	./gradlew test

# Install the plugin into local nextflow plugins dir
install:
	./gradlew install

# Publish the plugin
release:
	./gradlew releasePlugin

# Run all validation workflows
# Usage: make validate [BRANCH=branch-name] [VERSION=x.y.z] [ARGS="extra args"]
validate: validate-legacy validate-run

# Instance and key prefix used by the publish validation workflows
INSTANCE ?= laminlabs/lamin-dev
PREFIX ?= nf-lamin-test

# Run the legacy validation workflow (Nextflow < 26.04, legacy DSL2 syntax parser)
# Uses publishDir directive; output directory is set via --output-dir param.
# Usage: make validate-legacy [BRANCH=branch-name] [VERSION=x.y.z] [ARGS="extra args"]
validate-legacy:
	BRANCH=$${BRANCH:-$$(git symbolic-ref --short HEAD 2>/dev/null || echo "main")}; \
	VERSION=$${VERSION:-$$(awk -F"'" '/^version =/{print $$2}' build.gradle)}; \
	echo "Running legacy validation workflow with branch: $$BRANCH, version: $$VERSION"; \
	nextflow -trace ai.lamin \
		run laminlabs/nf-lamin \
		-r $$BRANCH \
		-latest \
		-main-script validation/legacy_syntax_parser/main.nf \
		-config configs/ci.config \
		-plugins "nf-lamin@$$VERSION" \
		--output-dir results \
		$(ARGS)

# Run the validation workflow using Nextflow 26.04+ features
# Uses typed params/processes/workflows and the workflow output block.
# Output directory is set via the -output-dir CLI option.
# Usage: make validate-run [BRANCH=branch-name] [VERSION=x.y.z] [ARGS="extra args"]
validate-run:
	BRANCH=$${BRANCH:-$$(git symbolic-ref --short HEAD 2>/dev/null || echo "main")}; \
	VERSION=$${VERSION:-$$(awk -F"'" '/^version =/{print $$2}' build.gradle)}; \
	echo "Running validation workflow with branch: $$BRANCH, version: $$VERSION"; \
	nextflow -trace ai.lamin \
		run laminlabs/nf-lamin \
		-r $$BRANCH \
		-latest \
		-main-script validation/run/main.nf \
		-config configs/ci.config \
		-plugins "nf-lamin@$$VERSION" \
		-output-dir results \
		$(ARGS)

# Publish workflow outputs into a Lamin storage location (Nextflow 26.04+).
# Usage: make validate-publish-run [INSTANCE=owner/instance] [PREFIX=key-prefix] [ARGS="extra args"]
validate-publish-run:
	BRANCH=$${BRANCH:-$$(git symbolic-ref --short HEAD 2>/dev/null || echo "main")}; \
	VERSION=$${VERSION:-$$(awk -F"'" '/^version =/{print $$2}' build.gradle)}; \
	echo "Publishing to lamin://$(INSTANCE)?prefix=$(PREFIX) with branch: $$BRANCH, version: $$VERSION"; \
	nextflow -trace ai.lamin \
		run laminlabs/nf-lamin \
		-r $$BRANCH \
		-latest \
		-main-script validation/publish_run/main.nf \
		-config configs/ci.config \
		-plugins "nf-lamin@$$VERSION" \
		-output-dir "lamin://$(INSTANCE)?prefix=$(PREFIX)" \
		$(ARGS)

# Publish workflow outputs via publishDir (Nextflow < 26.04).
# Usage: make validate-publish-legacy [INSTANCE=owner/instance] [PREFIX=key-prefix] [ARGS="extra args"]
validate-publish-legacy:
	BRANCH=$${BRANCH:-$$(git symbolic-ref --short HEAD 2>/dev/null || echo "main")}; \
	VERSION=$${VERSION:-$$(awk -F"'" '/^version =/{print $$2}' build.gradle)}; \
	echo "Publishing to lamin://$(INSTANCE)?prefix=$(PREFIX) with branch: $$BRANCH, version: $$VERSION"; \
	nextflow -trace ai.lamin \
		run laminlabs/nf-lamin \
		-r $$BRANCH \
		-latest \
		-main-script validation/publish_legacy/main.nf \
		-config configs/ci.config \
		-plugins "nf-lamin@$$VERSION" \
		--output-dir "lamin://$(INSTANCE)?prefix=$(PREFIX)" \
		$(ARGS)

# Run the publish workflow once per accepted URI grammar, so the artifacts they produce can
# be compared before we settle on one (see https://github.com/laminlabs/nf-lamin/issues/152).
# Usage: make validate-publish-grammars [INSTANCE=owner/instance] [SPACE=uid] [STORAGE=uid]
validate-publish-grammars:
	$(MAKE) validate-publish-run PREFIX="$(PREFIX)/query-form"
	@if [ -n "$(SPACE)" ]; then \
		$(MAKE) validate-publish-run INSTANCE="$(INSTANCE)/space/$(SPACE)" PREFIX="$(PREFIX)/space-form"; \
	else \
		echo "Skipping the space form: set SPACE=<space-uid> to run it"; \
	fi
	@if [ -n "$(STORAGE)" ]; then \
		$(MAKE) validate-publish-run INSTANCE="$(INSTANCE)/storage/$(STORAGE)" PREFIX="$(PREFIX)/storage-form"; \
	else \
		echo "Skipping the storage form: set STORAGE=<storage-uid> to run it"; \
	fi
