# Changelog

## [1.0.0] - UNRELEASED

### Added
- Missed "open URL" context menu entry
- Unmasking password icon for master password dialog when setup biometric unlock

### Fixed
- The case of items is ignored for sorting on item and item authorization screens
- Do not disable the unmasking password icon of password fields for readonly shared items

## [1.0.0-Preview1] - 2021-02-02

### Added
- Initial release

### Known issues
- If Android decides to destroy the `MainActivity` and restore it later, the item detail screen and item authorizations screen are not restored to previous state due to too late `ItemViewModel` recreation
