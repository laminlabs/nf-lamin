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

# Run the validation Nextflow workflow
# Usage: make validate [BRANCH=branch-name] [VERSION=x.y.z]
validate:
	BRANCH=$${BRANCH:-$$(git symbolic-ref --short HEAD 2>/dev/null || echo "main")}; \
	VERSION=$${VERSION:-$$(awk -F"'" '/^version =/{print $$2}' build.gradle)}; \
	echo "Running validation workflow with branch: $$BRANCH, version: $$VERSION"; \
	nextflow -trace ai.lamin \
		run laminlabs/nf-lamin \
		-r $$BRANCH \
		-latest \
		-main-script validation/main.nf \
		-config validation/nextflow.config \
		-plugins "nf-lamin@$$VERSION" \
		-output-dir results
