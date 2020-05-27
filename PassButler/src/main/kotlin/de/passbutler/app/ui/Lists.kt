package de.passbutler.app.ui

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

interface ListItemIdentifiable {
    val listItemId: String
}

class ListItemIdentifiableDiffCallback : DiffUtil.ItemCallback<ListItemIdentifiable>() {
    override fun areItemsTheSame(oldItem: ListItemIdentifiable, newItem: ListItemIdentifiable): Boolean {
        return oldItem.listItemId == newItem.listItemId
    }

    @SuppressLint("DiffUtilEquals")
    override fun areContentsTheSame(oldItem: ListItemIdentifiable, newItem: ListItemIdentifiable): Boolean {
        return oldItem == newItem
    }
}