package com.example.screenmirror

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class FeedbackActivity : AppCompatActivity() {

    private var selectedSubject = ""
    private lateinit var btnOneri: TextView
    private lateinit var btnHata: TextView
    private lateinit var btnGorus: TextView
    private lateinit var etMessage: EditText

    private val templateOneri = """Öneriniz:

Ne öneriyorsunuz?

Nasıl uygulanmalı?

Faydası ne olur?"""

    private val templateHata = """Hata Bildirimi

Ne yapıyordunuz?

Hata mesajı (varsa):

Tekrarlanabilirlik: (Her seferinde / Bazen / İlk seferde)"""

    private val templateGorus = """Görüşünüz

Uygulama hakkında ne düşünüyorsunuz?

Beğendiğiniz özellikler:

İyileştirilebilecek alanlar:"""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        btnOneri = findViewById(R.id.btnOneri)
        btnHata = findViewById(R.id.btnHata)
        btnGorus = findViewById(R.id.btnGorus)
        etMessage = findViewById(R.id.etMessage)

        selectSubject("Öneri")

        btnOneri.setOnClickListener { selectSubject("Öneri") }
        btnHata.setOnClickListener { selectSubject("Hata") }
        btnGorus.setOnClickListener { selectSubject("Görüş") }

        val etName = findViewById<EditText>(R.id.etName)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val btnSend = findViewById<Button>(R.id.btnSend)

        btnSend.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val message = etMessage.text.toString().trim()

            if (name.isEmpty()) {
                etName.error = "Adınızı girin"
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                etEmail.error = "E-posta girin"
                return@setOnClickListener
            }
            if (message.isEmpty()) {
                etMessage.error = "Mesajınızı yazın"
                return@setOnClickListener
            }

            val body = "Ad: $name\nE-posta: $email\nKonu: $selectedSubject\n\n$message"
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("akdemirferit608@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Screen Mirror - $selectedSubject")
                putExtra(Intent.EXTRA_TEXT, body)
            }
            try {
                startActivity(Intent.createChooser(intent, "Geri Bildirim Gönder"))
                Toast.makeText(this, "Teşekkürler!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "E-posta uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selectSubject(subject: String) {
        selectedSubject = subject
        btnOneri.setBackgroundResource(if (subject == "Öneri") R.drawable.bg_button_primary else R.drawable.bg_input)
        btnOneri.setTextColor(if (subject == "Öneri") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#99FFFFFF"))
        btnHata.setBackgroundResource(if (subject == "Hata") R.drawable.bg_button_primary else R.drawable.bg_input)
        btnHata.setTextColor(if (subject == "Hata") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#99FFFFFF"))
        btnGorus.setBackgroundResource(if (subject == "Görüş") R.drawable.bg_button_primary else R.drawable.bg_input)
        btnGorus.setTextColor(if (subject == "Görüş") android.graphics.Color.WHITE else android.graphics.Color.parseColor("#99FFFFFF"))

        val template = when (subject) {
            "Öneri" -> templateOneri
            "Hata" -> templateHata
            "Görüş" -> templateGorus
            else -> ""
        }
        etMessage.setText(template)
        etMessage.setSelection(etMessage.text.length)
    }
}
