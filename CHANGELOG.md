# Changelog

## [1.0.0] - UNRELEASED

### Added
- Missed "open URL" context menu entry
- Unmasking password icon for master password dialog when setup biometric unlock
- Scroll to top of the list when clicking on toolbar
- Thin item dividers for all lists
- Confirmation dialogs for destructive actions (logout and item deletion)
- Confirmation dialog for discard item and item authorization changes action
- Password generator dialog on item screen

## Changed
- Allow to copy item details of read-only items (do not disable them anymore) - the delete button is hidden instead of disabled to avoid user confusion (like the save accept toolbar icon)
- Improved contrast on navigation view in dark mode
- Show dedicated error for entering wrong invitation code when try to register user
- Automatically show biometric prompt if enabled
- Introduction flow to be more understandable / user friendly
- New wording for "register local user" button and moved "logout" to deeper level in settings and have different wording for local/remote user to avoid accidental data loss
- The search on overview is closed if the user clicks backpress

### Fixed
- The case of items is ignored for sorting on item and item authorization screens
- Do not disable the unmasking password icon of password fields for readonly shared items
- Missed ellipsize of too long text (especially titles on overview screen)
- Broken fragment presentation on autofill screen after configuration change
- Missed transition animation after configuration change
- Broken state when locked screen in autofill mode is rebuild after configuration change
- Keyboard was not hidden on overview screen if an item was searched and the item detail screen was shown
- Non-updated filtered list on overview / recycle bin screen if a searched item was deleted

## [1.0.0-Preview1] - 2021-02-02

### Added
- Initial release

### Known issues
- If Android decides to destroy the `MainActivity` and restore it later, the item detail screen and item authorizations screen are not restored to previous state due to too late `ItemViewModel` recreation
- If locked screen in autofill mode is shown while a configuration change is done, the unlock does not work
