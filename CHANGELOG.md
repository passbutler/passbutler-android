# Changelog

## [1.0.0] - UNRELEASED

### Fixed
- The case of items is ignored for sorting on item and item authorization screens

## [1.0.0-Preview1] - 2021-02-02

### Added
- Initial release

### Known issues
- If Android decides to destroy the `MainActivity` and restore it later, the item detail screen and item authorizations screen are not restored to previous state due to too late `ItemViewModel` recreation
