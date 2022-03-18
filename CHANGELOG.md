# Change Log
All notable changes to this project will be documented in this file. This change
log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.2.22] - 2022-03-18
* Updated to clj-statecharts 0.1.3.

## [0.2.14] - 2021-12-12

### Changed
* Changed namespace of re-frame events, to avoid conflicts with app namespaces.

  To upgrade, change `:state/initialize` to `:mainej.re-stated/initialize`. Or,
  if you were using the recommended require `[mainej.re-stated :as state]`,
  change `:state/initialize` to `::state/initialize`.

  Make similar changes for `:state/transition`.

## [0.1.9] - 2021-12-12

Initial release.

### Added
* Tools to initialize and transition a state-map and store it in the re-frame
  app-db.
* Pre-defined re-frame events that initialize and transition a state-map.
* State machine actions that dispatch re-frame events, i.e. when a state-map
  enters/exits/transitions between states.

[Unreleased]: https://github.com/mainej/re-stated/compare/v0.2.22...HEAD
[0.2.22]: https://github.com/mainej/re-stated/compare/v0.2.14...v0.2.22
[0.2.14]: https://github.com/mainej/re-stated/compare/v0.1.9...v0.2.14
[0.1.9]: https://github.com/mainej/re-stated/tree/v0.1.9
