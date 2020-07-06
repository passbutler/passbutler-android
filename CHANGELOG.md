# Changelog

## 1.0.0 (unreleased)

### Added
- Screen to manage item authorizations for users (#7)

### Known issues
- If Android decides to destroy the `MainActivity` and restore it later, the item detail screen and item authorizations screen are not restored to previous state due to too late `ItemViewModel` recreation
