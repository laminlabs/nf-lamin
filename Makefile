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
validate:
	BRANCH=$$(git rev-parse --abbrev-ref HEAD); \
	VERSION=$$(awk -F"'" '/^version =/{print $$2}' build.gradle); \
	nextflow run laminlabs/nf-lamin -r $$BRANCH -latest -main-script validation/main.nf -config validation/nextflow.config -plugins "nf-lamin@$$VERSION" -output-dir results
