package de.passbutler.app.ui

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import de.passbutler.app.R

class VerticalSpaceItemDecoration(private val viewContext: Context) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val verticalSpace = viewContext.resources.getDimensionPixelSize(viewContext.resolveThemeAttributeId(R.attr.marginM))

        if (parent.getChildAdapterPosition(view) == 0) {
            outRect.top = verticalSpace;
        }

        outRect.bottom = verticalSpace
    }
}
