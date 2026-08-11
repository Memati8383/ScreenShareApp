package com.example.screenmirror

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

class SpinnerPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    private var entries: Array<CharSequence> = emptyArray()
    private var entryValues: Array<CharSequence> = emptyArray()
    private var currentValue: String = ""
    private var spinner: Spinner? = null
    private var onValueChanged: ((String) -> Unit)? = null

    init {
        layoutResource = R.layout.preference_glass_spinner
    }

    fun setEntries(entries: Array<CharSequence>) {
        this.entries = entries
    }

    fun setEntryValues(values: Array<CharSequence>) {
        this.entryValues = values
    }

    fun setCurrentValue(value: String) {
        this.currentValue = value
        spinner?.let { updateSpinnerPosition(it) }
    }

    fun setOnValueChangedListener(listener: (String) -> Unit) {
        this.onValueChanged = listener
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        spinner = holder.itemView.findViewById(R.id.preference_spinner)
        spinner?.let { setupSpinner(it) }
    }

    private fun setupSpinner(spinner: Spinner) {
        val adapter = ArrayAdapter(
            context,
            R.layout.spinner_glass_item,
            entries
        )
        adapter.setDropDownViewResource(R.layout.spinner_glass_dropdown)
        spinner.adapter = adapter
        spinner.background = null

        updateSpinnerPosition(spinner)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = entryValues[position].toString()
                if (selected != currentValue) {
                    currentValue = selected
                    onValueChanged?.invoke(selected)
                    callChangeListener(selected)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateSpinnerPosition(spinner: Spinner) {
        val index = entryValues.indexOfFirst { it.toString() == currentValue }
        if (index >= 0 && spinner.selectedItemPosition != index) {
            spinner.setSelection(index)
        }
    }

    fun getValue(): String = currentValue
}
