package com.example.screenmirror

import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class HelpActivity : AppCompatActivity() {

    private data class SectionViews(
        val header: LinearLayout,
        val content: View,
        val arrow: ImageView,
        val text: TextView,
        val titleResId: Int,
        val contentResId: Int
    )

    private val sections = mutableListOf<SectionViews>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.nav_help)

        toolbar.setNavigationOnClickListener {
            HapticHelper.lightTap(this)
            finish()
        }

        setupSections()
        setupSearch()
        setupExpandAll()
        setupDeviceInfo()
        setupTroubleshootCards()
        animateSections()
    }

    private fun animateSections() {
        val container = findViewById<LinearLayout>(R.id.sectionsContainer)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            child.alpha = 0f
            child.translationY = 20f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((i * 40).toLong())
                .start()
        }
    }

    private fun setupSections() {
        data class SectionConfig(
            val headerId: Int,
            val contentId: Int,
            val arrowId: Int,
            val textId: Int,
            val titleResId: Int,
            val contentResId: Int
        )

        val configs = listOf(
            SectionConfig(R.id.section1, R.id.content1, R.id.arrow1, R.id.text1, R.string.help_how_title, R.string.help_how_desc),
            SectionConfig(R.id.section2, R.id.content2, R.id.arrow2, R.id.text2, R.string.help_start_title, R.string.help_start_desc),
            SectionConfig(R.id.section3, R.id.content3, R.id.arrow3, R.id.text3, R.string.help_watch_title, R.string.help_watch_desc),
            SectionConfig(R.id.section4, R.id.content4, R.id.arrow4, R.id.text4, R.string.help_troubleshoot_title, R.string.help_troubleshoot_desc),
            SectionConfig(R.id.section5, R.id.content5, R.id.arrow5, R.id.text5, R.string.help_privacy_title, R.string.help_privacy_desc),
            SectionConfig(R.id.section6, R.id.content6, R.id.arrow6, R.id.text6, R.string.help_faq_title, R.string.help_faq_desc)
        )

        configs.forEach { config ->
            val header = findViewById<LinearLayout>(config.headerId)
            val content = findViewById<View>(config.contentId)
            val arrow = findViewById<ImageView>(config.arrowId)
            val text = findViewById<TextView>(config.textId)

            text.text = getString(config.contentResId)

            val views = SectionViews(
                header = header,
                content = content,
                arrow = arrow,
                text = text,
                titleResId = config.titleResId,
                contentResId = config.contentResId
            )
            sections.add(views)

            header.setOnClickListener {
                HapticHelper.lightTap(this)
                toggleSection(views)
            }

            setupFeedbackButtons(content)
        }
    }

    private fun toggleSection(data: SectionViews) {
        if (data.content.visibility == View.VISIBLE) {
            data.content.animate()
                .alpha(0f)
                .translationY(-10f)
                .setDuration(150)
                .withEndAction {
                    data.content.visibility = View.GONE
                    data.content.alpha = 1f
                    data.content.translationY = 0f
                }
                .start()
            ObjectAnimator.ofFloat(data.arrow, "rotation", 0f).setDuration(200).start()
        } else {
            data.content.visibility = View.VISIBLE
            data.content.alpha = 0f
            data.content.translationY = 10f
            data.content.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .start()
            ObjectAnimator.ofFloat(data.arrow, "rotation", 90f).setDuration(200).start()
        }
    }

    private fun setupSearch() {
        val searchInput = findViewById<EditText>(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSections(s?.toString() ?: "")
            }
        })
    }

    private fun filterSections(query: String) {
        val normalizedQuery = query.lowercase().trim()

        sections.forEach { data ->
            val title = getString(data.titleResId).lowercase()
            val content = getString(data.contentResId).lowercase()

            val matches = normalizedQuery.isEmpty() ||
                    title.contains(normalizedQuery) ||
                    content.contains(normalizedQuery)

            data.header.visibility = if (matches) View.VISIBLE else View.GONE

            if (!matches && data.content.visibility == View.VISIBLE) {
                data.content.visibility = View.GONE
                data.arrow.rotation = 0f
            }
        }
    }

    private fun setupExpandAll() {
        val btnExpandAll = findViewById<TextView>(R.id.btnExpandAll)
        btnExpandAll.setOnClickListener {
            HapticHelper.lightTap(this)

            val allCurrentlyExpanded = sections.all { it.content.visibility == View.VISIBLE }

            sections.forEach { data ->
                if (allCurrentlyExpanded) {
                    data.content.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction {
                            data.content.visibility = View.GONE
                            data.content.alpha = 1f
                        }
                        .start()
                    data.arrow.animate().rotation(0f).setDuration(200).start()
                } else {
                    data.content.visibility = View.VISIBLE
                    data.content.alpha = 0f
                    data.content.animate().alpha(1f).setDuration(200).start()
                    data.arrow.animate().rotation(90f).setDuration(200).start()
                }
            }

            btnExpandAll.text = if (allCurrentlyExpanded) getString(R.string.help_expand_all) else getString(R.string.help_collapse_all)
        }
    }

    private fun setupFeedbackButtons(contentView: View) {
        val btnYes = contentView.findViewById<LinearLayout>(R.id.btnYes)
        val btnNo = contentView.findViewById<LinearLayout>(R.id.btnNo)
        val feedbackThanks = contentView.findViewById<TextView>(R.id.feedbackThanks)

        btnYes?.setOnClickListener {
            HapticHelper.lightTap(this)
            btnYes.visibility = View.GONE
            btnNo?.visibility = View.GONE
            feedbackThanks?.visibility = View.VISIBLE
            Toast.makeText(this, getString(R.string.help_feedback_thanks), Toast.LENGTH_SHORT).show()
        }

        btnNo?.setOnClickListener {
            HapticHelper.lightTap(this)
            btnYes?.visibility = View.GONE
            btnNo.visibility = View.GONE
            feedbackThanks?.visibility = View.VISIBLE
            feedbackThanks?.text = getString(R.string.help_feedback_sorry)
        }
    }

    private fun setupDeviceInfo() {
        findViewById<TextView>(R.id.deviceVersion).text = "1.0.0"
        findViewById<TextView>(R.id.deviceAndroid).text = "Android ${Build.VERSION.RELEASE}"
    }

    private fun setupTroubleshootCards() {
        val container = findViewById<LinearLayout>(R.id.troubleshootCards) ?: return

        data class TroubleshootItem(val iconRes: Int, val titleRes: Int, val descRes: Int)

        val items = listOf(
            TroubleshootItem(R.drawable.ic_signal, R.string.troubleshoot_network_title, R.string.troubleshoot_network_desc),
            TroubleshootItem(R.drawable.ic_frozen, R.string.troubleshoot_lag_title, R.string.troubleshoot_lag_desc),
            TroubleshootItem(R.drawable.ic_cast, R.string.troubleshoot_screen_title, R.string.troubleshoot_screen_desc),
            TroubleshootItem(R.drawable.ic_info_circle, R.string.troubleshoot_crash_title, R.string.troubleshoot_crash_desc)
        )

        items.forEach { item ->
            val cardView = layoutInflater.inflate(R.layout.help_troubleshoot_card, container, false)

            cardView.findViewById<ImageView>(R.id.cardIcon).setImageResource(item.iconRes)
            cardView.findViewById<TextView>(R.id.cardTitle).text = getString(item.titleRes)
            cardView.findViewById<TextView>(R.id.cardDesc).text = getString(item.descRes)

            val cardArrow = cardView.findViewById<ImageView>(R.id.cardArrow)
            val cardDesc = cardView.findViewById<TextView>(R.id.cardDesc)

            cardView.setOnClickListener {
                HapticHelper.lightTap(this)
                if (cardDesc.visibility == View.VISIBLE) {
                    cardDesc.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction {
                            cardDesc.visibility = View.GONE
                            cardDesc.alpha = 1f
                        }
                        .start()
                    cardArrow.animate().rotation(0f).setDuration(200).start()
                } else {
                    cardDesc.visibility = View.VISIBLE
                    cardDesc.alpha = 0f
                    cardDesc.animate().alpha(1f).setDuration(200).start()
                    cardArrow.animate().rotation(90f).setDuration(200).start()
                }
            }

            container.addView(cardView)
        }
    }
}
