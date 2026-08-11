package com.example.screenmirror

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackActivity : AppCompatActivity() {

    private var selectedSubject: Int = -1
    private var selectedRating: Float = 0f

    private lateinit var subjectCards: List<LinearLayout>
    private lateinit var subjectIcons: List<ImageView>
    private lateinit var subjectLabels: List<TextView>
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var messageInput: TextInputEditText
    private lateinit var tilMessage: TextInputLayout
    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingLabel: TextView
    private lateinit var tvCharCount: TextView
    private lateinit var btnSend: Button
    private lateinit var tvAddScreenshot: TextView
    private lateinit var btnAddScreenshot: LinearLayout
    private lateinit var rvScreenshots: RecyclerView
    private lateinit var tvScreenshotCount: TextView
    private lateinit var screenshotAdapter: ScreenshotAdapter
    private lateinit var successOverlay: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var formContainer: LinearLayout

    private val maxScreenshots = 4
    private var keyboardListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private val ratingLabels by lazy {
        listOf(
            getString(R.string.feedback_rating_1),
            getString(R.string.feedback_rating_2),
            getString(R.string.feedback_rating_3),
            getString(R.string.feedback_rating_4),
            getString(R.string.feedback_rating_5)
        )
    }

    private val takeScreenshotLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val uri = saveBitmapToCache(bitmap)
            if (uri != null) addScreenshot(uri)
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) addScreenshot(uri)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) takeScreenshotLauncher.launch(null)
        else Toast.makeText(this, getString(R.string.feedback_permission_denied), Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)
        initViews()
        setupToolbar()
        setupSubjectCards()
        setupRatingBar()
        setupMessageCounter()
        setupScreenshotSection()
        setupSendButton()
        setupTextWatchers()
        setupSuccessOverlay()
        setupKeyboardScroll()
        applyBlurEffects()
    }

    override fun onDestroy() {
        super.onDestroy()
        keyboardListener?.let { formContainer.viewTreeObserver.removeOnGlobalLayoutListener(it) }
    }

    private fun initViews() {
        nameInput = findViewById(R.id.etName)
        emailInput = findViewById(R.id.etEmail)
        messageInput = findViewById(R.id.etMessage)
        tilMessage = findViewById(R.id.tilMessage)
        ratingBar = findViewById(R.id.ratingBar)
        tvRatingLabel = findViewById(R.id.tvRatingLabel)
        tvCharCount = findViewById(R.id.tvCharCount)
        btnSend = findViewById(R.id.btnSend)
        btnAddScreenshot = findViewById(R.id.btnAddScreenshot)
        tvAddScreenshot = findViewById(R.id.tvAddScreenshot)
        rvScreenshots = findViewById(R.id.rvScreenshots)
        tvScreenshotCount = findViewById(R.id.tvScreenshotCount)
        successOverlay = findViewById(R.id.successOverlay)
        scrollView = findViewById(R.id.scrollView)
        formContainer = findViewById(R.id.formContainer)

        subjectCards = listOf(findViewById(R.id.cardOneri), findViewById(R.id.cardHata), findViewById(R.id.cardGorus))
        subjectIcons = listOf(findViewById(R.id.iconOneri), findViewById(R.id.iconHata), findViewById(R.id.iconGorus))
        subjectLabels = listOf(findViewById(R.id.labelOneri), findViewById(R.id.labelHata), findViewById(R.id.labelGorus))

        screenshotAdapter = ScreenshotAdapter { pos ->
            HapticHelper.lightTap(this)
            screenshotAdapter.removeItem(pos)
            updateScreenshotCount()
        }
        rvScreenshots.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvScreenshots.adapter = screenshotAdapter
    }

    private fun applyBlurEffects() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                formContainer.setRenderEffect(
                    RenderEffect.createBlurEffect(2f, 2f, Shader.TileMode.CLAMP)
                )
            } catch (_: Exception) { }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.feedback_title)
        toolbar.setNavigationOnClickListener { HapticHelper.lightTap(this); confirmAndFinish() }
    }

    private fun setupSubjectCards() {
        subjectCards.forEachIndexed { index, card ->
            card.setOnClickListener {
                HapticHelper.lightTap(this)
                selectSubject(index)
                selectedSubject = index
                applySmartTemplate(index)
                validateForm()
            }
        }
    }

    private fun selectSubject(sel: Int) {
        val activeBg = R.drawable.bg_glass_card_active
        val inactiveBg = R.drawable.bg_glass_card_inactive
        val activeTint = android.R.color.white
        val inactiveTint = R.color.text_secondary

        subjectCards.forEachIndexed { i, card ->
            val active = i == sel
            card.setBackgroundResource(if (active) activeBg else inactiveBg)
            subjectIcons[i].setColorFilter(getColor(if (active) activeTint else inactiveTint))
            subjectLabels[i].setTextColor(getColor(if (active) activeTint else inactiveTint))
        }
    }

    private fun applySmartTemplate(idx: Int) {
        val template = when (idx) {
            0 -> getString(R.string.feedback_template_suggestion)
            1 -> {
                val info = buildDeviceInfo()
                getString(R.string.feedback_template_bug) + "\n\n---\n$info"
            }
            2 -> getString(R.string.feedback_template_opinion)
            else -> ""
        }
        messageInput.setText(template)
        messageInput.setSelection(messageInput.text?.length ?: 0)

        if (idx == 1) showScreenshotOptionsDialog()
    }

    private fun setupRatingBar() {
        ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) {
                HapticHelper.lightTap(this)
                selectedRating = rating
                val idx = rating.toInt().coerceIn(1, 5) - 1
                tvRatingLabel.text = ratingLabels[idx]
                tvRatingLabel.setTextColor(getColor(R.color.accent))
            }
        }
    }

    private fun setupMessageCounter() {
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val len = s?.length ?: 0
                tvCharCount.text = "$len / 2000"
                tvCharCount.setTextColor(getColor(if (len > 1800) R.color.status_warn else R.color.text_hint))
            }
        })
    }

    private fun setupScreenshotSection() {
        btnAddScreenshot.setOnClickListener {
            HapticHelper.lightTap(this)
            if (screenshotAdapter.itemCount >= maxScreenshots) {
                Toast.makeText(this, getString(R.string.feedback_screenshot_max), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showScreenshotOptionsDialog()
        }
    }

    private fun updateScreenshotCount() {
        val c = screenshotAdapter.itemCount
        tvScreenshotCount.text = "$c / $maxScreenshots"
        tvScreenshotCount.visibility = if (c > 0) View.VISIBLE else View.GONE
        rvScreenshots.visibility = if (c > 0) View.VISIBLE else View.GONE
        tvAddScreenshot.text = if (c >= maxScreenshots) getString(R.string.feedback_screenshot_max_reached)
        else getString(R.string.feedback_screenshot_add)
    }

    private fun showScreenshotOptionsDialog() {
        val opts = arrayOf(getString(R.string.feedback_screenshot_camera), getString(R.string.feedback_screenshot_gallery))
        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.feedback_screenshot_choose))
            .setItems(opts) { _, w -> if (w == 0) requestScreenshotPermission() else pickImageLauncher.launch("image/*") }
            .show()
    }

    private fun requestScreenshotPermission() {
        val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.CAMERA
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) takeScreenshotLauncher.launch(null)
        else cameraPermissionLauncher.launch(p)
    }

    private fun addScreenshot(uri: Uri) {
        if (screenshotAdapter.itemCount >= maxScreenshots) {
            Toast.makeText(this, getString(R.string.feedback_screenshot_max), Toast.LENGTH_SHORT).show(); return
        }
        screenshotAdapter.addItem(uri); updateScreenshotCount()
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? = try {
        val f = File(cacheDir, "fb_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg")
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
    } catch (_: Exception) { null }

    private fun setupSendButton() {
        btnSend.setOnClickListener { HapticHelper.mediumTap(this); if (validateForm()) sendFeedback() }
    }

    private fun setupTextWatchers() {
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { validateForm() }
        }
        nameInput.addTextChangedListener(w); emailInput.addTextChangedListener(w); messageInput.addTextChangedListener(w)
    }

    private fun setupSuccessOverlay() {
        findViewById<Button>(R.id.btnSuccessOk).setOnClickListener { HapticHelper.lightTap(this); successOverlay.visibility = View.GONE; finish() }
    }

    private fun setupKeyboardScroll() {
        val l = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val r = android.graphics.Rect(); formContainer.getWindowVisibleDisplayFrame(r)
                val kh = formContainer.rootView.height - r.bottom
                if (kh > formContainer.rootView.height * 0.15)
                    scrollView.postDelayed({ scrollView.fullScroll(View.FOCUS_DOWN); messageInput.requestFocus() }, 100)
            }
        }
        formContainer.viewTreeObserver.addOnGlobalLayoutListener(l); keyboardListener = l
    }

    private fun validateForm(): Boolean {
        val msg = messageInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val ok = selectedSubject >= 0 && msg.length >= 10 && (email.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())

        tilMessage.error = when { msg.isEmpty() -> null; msg.length < 10 -> getString(R.string.feedback_error_message_min); else -> null }
        findViewById<TextInputLayout>(R.id.tilEmail).error = if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) getString(R.string.feedback_error_email_invalid) else null

        btnSend.alpha = if (ok) 1f else 0.5f; btnSend.isEnabled = ok
        return ok
    }

    private fun isFormDirty() = selectedSubject >= 0 || selectedRating > 0 || nameInput.text?.isNotEmpty() == true || emailInput.text?.isNotEmpty() == true || messageInput.text?.isNotEmpty() == true || screenshotAdapter.itemCount > 0

    private fun confirmAndFinish() {
        if (isFormDirty()) AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.feedback_exit_title)).setMessage(getString(R.string.feedback_exit_message))
            .setPositiveButton(getString(R.string.feedback_exit_discard)) { _, _ -> finish() }
            .setNegativeButton(getString(R.string.feedback_exit_stay), null).show()
        else finish()
    }

    private fun sendFeedback() {
        val name = nameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val msg = messageInput.text.toString().trim()
        val rt = if (selectedRating > 0) "${selectedRating.toInt()}/5" else getString(R.string.feedback_email_not_set)
        val subjects = listOf(getString(R.string.feedback_subject_suggestion), getString(R.string.feedback_subject_bug), getString(R.string.feedback_subject_opinion))
        val subj = subjects.getOrElse(selectedSubject) { "" }

        val body = buildString {
            appendLine(getString(R.string.feedback_email_field_subject, subj))
            appendLine(getString(R.string.feedback_email_field_rating, rt))
            appendLine(getString(R.string.feedback_email_field_name, name.ifEmpty { getString(R.string.feedback_email_not_set) }))
            appendLine(getString(R.string.feedback_email_field_email, email.ifEmpty { getString(R.string.feedback_email_not_set) }))
            appendLine("---"); appendLine(msg); appendLine("---"); appendLine(buildDeviceInfo())
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf("akdemirferit608@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject, subj))
            putExtra(Intent.EXTRA_TEXT, body)
            val ss = screenshotAdapter.getItems()
            if (ss.isNotEmpty()) {
                if (ss.size == 1) putExtra(Intent.EXTRA_STREAM, ss[0])
                else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(ss))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        try { startActivity(Intent.createChooser(intent, getString(R.string.feedback_email_chooser))); showSuccessOverlay() }
        catch (_: Exception) { Toast.makeText(this, getString(R.string.feedback_email_not_found), Toast.LENGTH_SHORT).show() }
    }

    private fun buildDeviceInfo(): String {
        val dm = resources.displayMetrics
        val pkg = try { packageManager.getPackageInfo(packageName, 0) } catch (_: Exception) { null }
        val ver = pkg?.versionName ?: "?"
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg?.longVersionCode?.toString() ?: "0"
        else { @Suppress("DEPRECATION") pkg?.versionCode?.toString() ?: "0" }
        return buildString {
            appendLine("=== Cihaz Bilgileri ===")
            appendLine("Üretici: ${Build.MANUFACTURER}"); appendLine("Model: ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Uygulama: v$ver ($code)"); appendLine("Ekran: ${dm.widthPixels}x${dm.heightPixels}")
            appendLine("Dil: ${Locale.getDefault().displayLanguage}")
        }
    }

    private fun showSuccessOverlay() {
        nameInput.text?.clear(); emailInput.text?.clear(); messageInput.text?.clear()
        tvCharCount.text = "0 / 2000"; tvCharCount.setTextColor(getColor(R.color.text_hint))
        selectSubject(-1); selectedSubject = -1; selectedRating = 0f; ratingBar.rating = 0f
        tvRatingLabel.text = getString(R.string.feedback_rating_not_selected); tvRatingLabel.setTextColor(getColor(R.color.text_hint))
        screenshotAdapter.clear(); updateScreenshotCount()
        scrollView.visibility = View.GONE; successOverlay.visibility = View.VISIBLE
    }

    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() { confirmAndFinish() }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { confirmAndFinish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
