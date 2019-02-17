package de.sicherheitskritisch.passbutler.ui

import android.widget.SeekBar

open class SimpleOnSeekBarChangeListener : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        // Implement if desired
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        // Implement if desired
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        // Implement if desired
    }
}