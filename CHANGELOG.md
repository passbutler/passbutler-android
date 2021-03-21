# Changelog

## [1.0.0] - UNRELEASED

### Added
- Missed "open URL" context menu entry
- Unmasking password icon for master password dialog when setup biometric unlock
- Scroll to top of the list when clicking on toolbar

## Changed
- Allow to copy item details of read-only items (do not disable them anymore) - the delete button is hidden instead of disabled to avoid user confusion (like the save accept toolbar icon)

### Fixed
- The case of items is ignored for sorting on item and item authorization screens
- Do not disable the unmasking password icon of password fields for readonly shared items
- Missed ellipsize of too long text (especially titles on overview screen)

## [1.0.0-Preview1] - 2021-02-02

### Added
- Initial release

### Known issues
- If Android decides to destroy the `MainActivity` and restore it later, the item detail screen and item authorizations screen are not restored to previous state due to too late `ItemViewModel` recreation
