package com.example.screenmirror

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class FeedbackActivity : AppCompatActivity() {

    private var selectedSubject: String = ""
    private lateinit var subjectButtons: List<LinearLayout>
    private lateinit var nameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var messageInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.feedback_title)

        toolbar.setNavigationOnClickListener { finish() }

        nameInput = findViewById(R.id.etName)
        emailInput = findViewById(R.id.etEmail)
        messageInput = findViewById(R.id.etMessage)
        val sendBtn = findViewById<MaterialButton>(R.id.btnSend)

        subjectButtons = listOf(
            findViewById(R.id.btnOneri),
            findViewById(R.id.btnHata),
            findViewById(R.id.btnGorus)
        )

        subjectButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectSubject(index)
                selectedSubject = when (index) {
                    0 -> getString(R.string.feedback_subject_suggestion)
                    1 -> getString(R.string.feedback_subject_bug)
                    2 -> getString(R.string.feedback_subject_opinion)
                    else -> ""
                }
            }
        }

        sendBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val message = messageInput.text.toString().trim()

            if (selectedSubject.isEmpty()) {
                Toast.makeText(this, getString(R.string.feedback_error_subject), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (message.isEmpty()) {
                messageInput.error = getString(R.string.feedback_error_message)
                return@setOnClickListener
            }

            val subject = getString(R.string.feedback_email_subject, selectedSubject)
            val body = buildString {
                appendLine(getString(R.string.feedback_email_field_subject, selectedSubject))
                appendLine(getString(R.string.feedback_email_field_name, name.ifEmpty { getString(R.string.feedback_email_not_set) }))
                appendLine(getString(R.string.feedback_email_field_email, email.ifEmpty { getString(R.string.feedback_email_not_set) }))
                appendLine("---")
                appendLine(message)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("akdemirferit608@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            try {
                startActivity(Intent.createChooser(intent, getString(R.string.feedback_email_chooser)))
                nameInput.text.clear()
                emailInput.text.clear()
                messageInput.text.clear()
                subjectButtons.forEach { it.isSelected = false }
                selectedSubject = ""
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.feedback_email_not_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectSubject(index: Int) {
        subjectButtons.forEach { it.isSelected = false }
        subjectButtons[index].isSelected = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
