package de.passbutler.app.ui

import android.annotation.SuppressLint
import android.widget.Filter
import android.widget.Filterable
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.common.ui.ListItemIdentifiable

abstract class FilterableListAdapter<ItemType : ListItemIdentifiable, ViewHolderType : RecyclerView.ViewHolder>(
    diffCallback: DiffUtil.ItemCallback<ItemType>,
    private val filterPredicate: (filterString: String, item: ItemType) -> Boolean,
) : ListAdapter<ItemType, ViewHolderType>(diffCallback), Filterable {

    private var unfilteredItems: List<ItemType>? = null

    override fun submitList(list: List<ItemType>?) {
        unfilteredItems = list
        super.submitList(list)
    }

    private fun submitFilteredList(list: List<ItemType>?) {
        super.submitList(list)
    }

    override fun getFilter(): Filter {
        return ItemFilter()
    }

    @SuppressLint("SyntheticAccessor")
    private inner class ItemFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filterString = constraint?.toString()
            val filteredItems = if (filterString.isNullOrEmpty()) {
                unfilteredItems
            } else {
                unfilteredItems?.filter { filterPredicate(filterString, it) }
            }

            return FilterResults().apply {
                values = filteredItems
                count = filteredItems?.size ?: 0
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            val filteredList = results.values as? List<ItemType>
            submitFilteredList(filteredList)
        }
    }
}

class ListItemIdentifiableDiffCallback<ItemType : ListItemIdentifiable> : DiffUtil.ItemCallback<ItemType>() {
    override fun areItemsTheSame(oldItem: ItemType, newItem: ItemType): Boolean {
        return oldItem.listItemId == newItem.listItemId
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: ItemType, newItem: ItemType): Boolean {
        return oldItem == newItem
    }
}
