package com.example.screenmirror

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class HelpActivity : AppCompatActivity() {

    private var expandedSection: View? = null
    private var expandedArrow: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.nav_help)

        toolbar.setNavigationOnClickListener {
            finish()
        }

        setupAccordion()
    }

    private fun setupAccordion() {
        val sections = listOf(
            Triple(R.id.section1, R.id.content1, R.id.arrow1),
            Triple(R.id.section2, R.id.content2, R.id.arrow2),
            Triple(R.id.section3, R.id.content3, R.id.arrow3),
            Triple(R.id.section4, R.id.content4, R.id.arrow4),
            Triple(R.id.section5, R.id.content5, R.id.arrow5)
        )

        sections.forEach { (headerId, contentId, arrowId) ->
            val header = findViewById<LinearLayout>(headerId)
            val content = findViewById<View>(contentId)
            val arrow = findViewById<ImageView>(arrowId)

            header.setOnClickListener {
                if (expandedSection == content) {
                    collapseSection(content, arrow)
                    expandedSection = null
                    expandedArrow = null
                } else {
                    expandedSection?.let { prev ->
                        expandedArrow?.let { prevArrow ->
                            collapseSection(prev, prevArrow)
                        }
                    }
                    expandSection(content, arrow)
                    expandedSection = content
                    expandedArrow = arrow
                }
            }
        }
    }

    private fun expandSection(content: View, arrow: ImageView) {
        content.visibility = View.VISIBLE
        ObjectAnimator.ofFloat(arrow, "rotation", 90f).start()
    }

    private fun collapseSection(content: View, arrow: ImageView) {
        content.visibility = View.GONE
        ObjectAnimator.ofFloat(arrow, "rotation", 0f).start()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
