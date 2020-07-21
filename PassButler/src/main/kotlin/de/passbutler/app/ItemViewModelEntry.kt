package de.passbutler.app

import de.passbutler.app.ui.ListItemIdentifiable

class ItemViewModelEntry(val itemViewModel: ItemViewModel): ListItemIdentifiable {
    override val listItemId: String
        get() = itemViewModel.id
}