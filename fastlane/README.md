fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android publish_metadata

```sh
[bundle exec] fastlane android publish_metadata
```

Upload only bilingual store metadata and listing assets to the internal track

### android upload_mobile_internal

```sh
[bundle exec] fastlane android upload_mobile_internal
```

Upload the verified first mobile AAB and prepared listing to internal testing

### android upload_wear_internal

```sh
[bundle exec] fastlane android upload_wear_internal
```

Upload the verified Wear AAB to the confirmed Wear OS internal track

### android promote_mobile_production

```sh
[bundle exec] fastlane android promote_mobile_production
```

Promote the verified mobile release from internal testing to production

### android promote_wear_production

```sh
[bundle exec] fastlane android promote_wear_production
```

Promote the verified Wear OS release from internal testing to production

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
