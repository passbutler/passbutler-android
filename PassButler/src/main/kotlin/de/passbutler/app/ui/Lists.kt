package de.passbutler.app.ui

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil
import de.passbutler.common.ui.ListItemIdentifiable

class ListItemIdentifiableDiffCallback : DiffUtil.ItemCallback<ListItemIdentifiable>() {
    override fun areItemsTheSame(oldItem: ListItemIdentifiable, newItem: ListItemIdentifiable): Boolean {
        return oldItem.listItemId == newItem.listItemId
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: ListItemIdentifiable, newItem: ListItemIdentifiable): Boolean {
        return oldItem == newItem
    }
}