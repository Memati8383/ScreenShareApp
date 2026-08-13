package com.example.screenmirror

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var profileView: ScrollView
    private lateinit var createForm: ScrollView

    private lateinit var nicknameDisplay: LinearLayout
    private lateinit var nicknameEditContainer: LinearLayout
    private lateinit var nicknameEditInput: EditText
    private lateinit var profileName: TextView
    private lateinit var avatarBg: View
    private lateinit var avatarLetter: TextView
    private lateinit var greetingText: TextView
    private lateinit var profilePhoto: ShapeableImageView

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<String>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    private val avatarColors = intArrayOf(
        R.drawable.bg_avatar_color1,
        R.drawable.bg_avatar_color2,
        R.drawable.bg_avatar_color3,
        R.drawable.bg_avatar_color4,
        R.drawable.bg_avatar_color5,
        R.drawable.bg_avatar_color6
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        prefs = getSharedPreferences("user_profile", Context.MODE_PRIVATE)

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val bitmap = if (Build.VERSION.SDK_INT >= 33) {
                    val uri = result.data?.data
                    uri?.let { loadBitmapFromUri(it) }
                } else {
                    val extras = result.data?.extras
                    extras?.get("data") as? Bitmap
                }
                bitmap?.let { saveProfilePhoto(it) }
            }
        }

        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                val bitmap = loadBitmapFromUri(it)
                bitmap?.let { bmp -> saveProfilePhoto(bmp) }
            }
        }

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] == true
            if (cameraGranted) {
                openCamera()
                return@registerForActivityResult
            }
            
            // Android 13+ için READ_MEDIA_IMAGES izni
            val readMediaImagesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
            } else {
                permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            }
            
            if (readMediaImagesGranted) {
                galleryLauncher.launch("image/*")
            } else {
                Toast.makeText(this, getString(R.string.feedback_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.title = getString(R.string.profile_title)

        toolbar.setNavigationOnClickListener {
            HapticHelper.lightTap(this)
            finish()
        }

        profileView = findViewById(R.id.profileView)
        createForm = findViewById(R.id.createForm)
        nicknameDisplay = findViewById(R.id.nicknameDisplay)
        nicknameEditContainer = findViewById(R.id.nicknameEditContainer)
        nicknameEditInput = findViewById(R.id.nicknameEditInput)
        profileName = findViewById(R.id.profileName)
        avatarBg = findViewById(R.id.avatarBg)
        avatarLetter = findViewById(R.id.avatarLetter)
        greetingText = findViewById(R.id.greetingText)
        profilePhoto = findViewById(R.id.profilePhoto)

        findViewById<TextView>(R.id.deviceName).text = getDeviceName()

        if (hasProfile()) {
            showProfile()
        } else {
            showCreateForm()
        }

        setupCreateForm()
        setupProfileView()
        setupQuickStart()
        animateElements()
    }

    private fun setupCreateForm() {
        val saveBtn = findViewById<com.google.android.material.button.MaterialButton>(R.id.saveBtn)
        val nicknameInput = findViewById<EditText>(R.id.nicknameInput)

        saveBtn.setOnClickListener {
            HapticHelper.mediumTap(this)
            val nickname = nicknameInput.text.toString().trim()
            if (nickname.isEmpty()) {
                nicknameInput.error = getString(R.string.profile_error_nickname)
                return@setOnClickListener
            }
            saveNickname(nickname)
            HapticHelper.successTap(this)
            Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show()
            showProfile()
        }
    }

    private fun setupProfileView() {
        nicknameDisplay.setOnClickListener {
            HapticHelper.lightTap(this)
            startNicknameEdit()
        }

        findViewById<TextView>(R.id.nicknameDoneBtn).setOnClickListener {
            HapticHelper.lightTap(this)
            finishNicknameEdit()
        }

        nicknameEditInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                finishNicknameEdit()
                true
            } else false
        }

        findViewById<View>(R.id.avatarBg).setOnClickListener {
            HapticHelper.lightTap(this)
            showModernProfileOptions()
        }

        findViewById<TextView>(R.id.avatarLetter).setOnClickListener {
            HapticHelper.lightTap(this)
            showModernProfileOptions()
        }

        profilePhoto.setOnClickListener {
            HapticHelper.lightTap(this)
            showModernProfileOptions()
        }

        findViewById<LinearLayout>(R.id.btnLogout).setOnClickListener {
            HapticHelper.heavyTap(this)
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_delete))
                .setMessage(getString(R.string.profile_deleted))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    clearProfile()
                    Toast.makeText(this, getString(R.string.profile_deleted), Toast.LENGTH_SHORT).show()
                    showCreateForm()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        findViewById<LinearLayout>(R.id.menuSettings).setOnClickListener {
            HapticHelper.lightTap(this)
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuHelp).setOnClickListener {
            HapticHelper.lightTap(this)
            startActivity(Intent(this, HelpActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.menuAbout).setOnClickListener {
            HapticHelper.lightTap(this)
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun setupQuickStart() {
        val resSpinner = findViewById<Spinner>(R.id.quickResSpinner)
        val fpsSpinner = findViewById<Spinner>(R.id.quickFpsSpinner)
        val autoReconnect = findViewById<SwitchMaterial>(R.id.quickAutoReconnect)

        val resolutions = arrayOf("480p", "720p", "1080p", "1440p")
        val resolutionWidths = intArrayOf(854, 1280, 1920, 2560)
        val resolutionHeights = intArrayOf(480, 720, 1080, 1440)

        val resAdapter = ArrayAdapter(this, R.layout.spinner_item, resolutions)
        resAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        resSpinner.adapter = resAdapter
        resSpinner.setBackgroundResource(android.R.color.transparent)

        val currentWidth = prefs.getInt("pref_resolution_width", 1280)
        val currentResIndex = resolutionWidths.indexOf(currentWidth).coerceAtLeast(1)
        resSpinner.setSelection(currentResIndex)

        resSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.edit()
                    .putInt("pref_resolution_width", resolutionWidths[pos])
                    .putInt("pref_resolution_height", resolutionHeights[pos])
                    .apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val fpsOptions = arrayOf("15 FPS", "24 FPS", "30 FPS", "60 FPS")
        val fpsValues = intArrayOf(15, 24, 30, 60)

        val fpsAdapter = ArrayAdapter(this, R.layout.spinner_item, fpsOptions)
        fpsAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        fpsSpinner.adapter = fpsAdapter
        fpsSpinner.setBackgroundResource(android.R.color.transparent)

        val currentFps = prefs.getInt("pref_fps", 30)
        val currentFpsIndex = fpsValues.indexOf(currentFps).coerceAtLeast(2)
        fpsSpinner.setSelection(currentFpsIndex)

        fpsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.edit().putInt("pref_fps", fpsValues[pos]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        autoReconnect.isChecked = prefs.getBoolean("pref_auto_reconnect", true)
        autoReconnect.setOnCheckedChangeListener { _, isChecked ->
            HapticHelper.lightTap(this)
            prefs.edit().putBoolean("pref_auto_reconnect", isChecked).apply()
        }
    }

    private fun showModernProfileOptions() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Dialog arkaplan şeffaf yap
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val hasPhoto = prefs.getString("photo_path", null) != null
        val removeOption = dialogView.findViewById<LinearLayout>(R.id.optionRemove)
        
        // Fotoğraf varsa "Kaldır" seçeneğini göster
        if (hasPhoto) {
            removeOption.visibility = View.VISIBLE
        }
        
        // Galeri seçeneği
        dialogView.findViewById<LinearLayout>(R.id.optionGallery).setOnClickListener {
            HapticHelper.lightTap(this)
            dialog.dismiss()
            checkGalleryPermissionAndOpen()
        }
        
        // Kamera seçeneği
        dialogView.findViewById<LinearLayout>(R.id.optionCamera).setOnClickListener {
            HapticHelper.lightTap(this)
            dialog.dismiss()
            checkCameraPermissionAndOpen()
        }
        
        // Avatar seçeneği
        dialogView.findViewById<LinearLayout>(R.id.optionAvatar).setOnClickListener {
            HapticHelper.lightTap(this)
            dialog.dismiss()
            showAvatarPicker()
        }
        
        // Kaldır seçeneği
        if (hasPhoto) {
            removeOption.setOnClickListener {
                HapticHelper.lightTap(this)
                dialog.dismiss()
                removeProfilePhoto()
            }
        }
        
        dialog.show()
    }

    private fun showAvatarOrPhotoOptions() {
        val hasPhoto = prefs.getString("photo_path", null) != null
        val items = if (hasPhoto) {
            arrayOf(
                getString(R.string.profile_photo_gallery),
                getString(R.string.profile_photo_camera),
                getString(R.string.profile_avatar_title),
                getString(R.string.profile_photo_remove)
            )
        } else {
            arrayOf(
                getString(R.string.profile_photo_gallery),
                getString(R.string.profile_photo_camera),
                getString(R.string.profile_avatar_title)
            )
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_change_profile))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> checkGalleryPermissionAndOpen()
                    1 -> checkCameraPermissionAndOpen()
                    2 -> showAvatarPicker()
                    3 -> if (hasPhoto) removeProfilePhoto()
                }
            }
            .show()
    }

    private fun showPhotoPicker() {
        val items = arrayOf(
            getString(R.string.profile_photo_gallery),
            getString(R.string.profile_photo_camera),
            getString(R.string.profile_photo_remove)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_photo_title))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> checkGalleryPermissionAndOpen()
                    1 -> checkCameraPermissionAndOpen()
                    2 -> removeProfilePhoto()
                }
            }
            .show()
    }

    private fun checkGalleryPermissionAndOpen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ için READ_MEDIA_IMAGES izni
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*")
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-12 için READ_EXTERNAL_STORAGE izni
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                galleryLauncher.launch("image/*")
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        } else {
            // Android 6'dan önce izin gerekmiyor
            galleryLauncher.launch("image/*")
        }
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(cameraIntent)
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveProfilePhoto(bitmap: Bitmap) {
        try {
            val file = File(filesDir, "profile_photo.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            prefs.edit().putString("photo_path", file.absolutePath).apply()
            runOnUiThread {
                loadProfilePhoto()
                Toast.makeText(this, getString(R.string.profile_photo_cropped), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            // silently fail
        }
    }

    private fun loadProfilePhoto() {
        val photoPath = prefs.getString("photo_path", null)
        if (photoPath != null) {
            val file = File(photoPath)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    profilePhoto.setImageBitmap(bitmap)
                    profilePhoto.visibility = View.VISIBLE
                    avatarBg.visibility = View.GONE
                    avatarLetter.visibility = View.GONE
                    return
                }
            }
        }
        profilePhoto.visibility = View.GONE
        avatarBg.visibility = View.VISIBLE
        avatarLetter.visibility = View.VISIBLE
    }

    private fun removeProfilePhoto() {
        val file = File(filesDir, "profile_photo.jpg")
        if (file.exists()) file.delete()
        prefs.edit().remove("photo_path").apply()
        profilePhoto.visibility = View.GONE
        avatarBg.visibility = View.VISIBLE
        avatarLetter.visibility = View.VISIBLE
    }

    private fun startNicknameEdit() {
        val nickname = prefs.getString("nickname", "") ?: ""
        nicknameDisplay.visibility = View.GONE
        nicknameEditContainer.visibility = View.VISIBLE
        nicknameEditInput.setText(nickname)
        nicknameEditInput.requestFocus()
        nicknameEditInput.setSelection(nicknameEditInput.text.length)
    }

    private fun finishNicknameEdit() {
        val nickname = nicknameEditInput.text.toString().trim()
        if (nickname.isEmpty()) {
            nicknameEditInput.error = getString(R.string.profile_error_nickname)
            return
        }
        saveNickname(nickname)
        nicknameEditContainer.visibility = View.GONE
        nicknameDisplay.visibility = View.VISIBLE
        profileName.text = nickname
        updateAvatar(nickname)
        updateGreeting()
        Toast.makeText(this, getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
    }

    private fun showAvatarPicker() {
        val nickname = prefs.getString("nickname", "") ?: "U"
        val labels = arrayOf("Mavi", "Açık Mavi", "Yeşil", "Sarı", "Turuncu", "Kırmızı")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_avatar_title))
            .setItems(labels) { _, which ->
                prefs.edit().putInt("avatar_color", which).apply()
                updateAvatar(nickname)
            }
            .show()
    }

    private fun updateAvatar(nickname: String) {
        val firstLetter = nickname.firstOrNull()?.uppercase() ?: "U"
        avatarLetter.text = firstLetter

        val colorIndex = prefs.getInt("avatar_color", nickname.hashCode().mod(avatarColors.size).let {
            if (it < 0) it + avatarColors.size else it
        } % avatarColors.size)
        avatarBg.setBackgroundResource(avatarColors[colorIndex])
    }

    private fun updateGreeting() {
        val nickname = prefs.getString("nickname", "") ?: ""
        if (nickname.isEmpty()) {
            greetingText.visibility = View.GONE
            return
        }
        greetingText.visibility = View.VISIBLE
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greetingRes = when (hour) {
            in 6..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_afternoon
            in 18..23 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
        greetingText.text = getString(greetingRes, nickname)
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    private fun hasProfile(): Boolean {
        return prefs.contains("nickname")
    }

    private fun showProfile() {
        profileView.visibility = View.VISIBLE
        createForm.visibility = View.GONE

        val nickname = prefs.getString("nickname", "") ?: ""

        profileName.text = nickname
        findViewById<TextView>(R.id.profileDevice).text = getDeviceName()
        updateAvatar(nickname)
        updateGreeting()
        loadProfilePhoto()

        loadStats()
        loadBadgesWithProgress()
        loadWeeklyActivity()
        loadTimeline()
        loadDeviceInfo()
    }

    private fun showCreateForm() {
        profileView.visibility = View.GONE
        createForm.visibility = View.VISIBLE
        findViewById<EditText>(R.id.nicknameInput).text.clear()
    }

    private fun loadStats() {
        val roomHistoryManager = (application as ScreenMirrorApp).roomHistoryManager
        lifecycleScope.launch {
            val allRooms: List<com.example.screenmirror.data.RoomHistory>
            withContext(Dispatchers.IO) {
                allRooms = roomHistoryManager.getAll()
            }

            val roomsHosted = allRooms.count { it.role == "sender" }
            val totalHostMs = allRooms.filter { it.role == "sender" }.sumOf { it.duration }
            val totalViewMs = allRooms.filter { it.role == "viewer" }.sumOf { it.duration }
            val hoursHosted = (totalHostMs / 3600000).toInt()
            val hoursViewed = (totalViewMs / 3600000).toInt()

            val totalSessions = allRooms.size
            val successRate = if (totalSessions > 0) {
                val completedSessions = allRooms.count { it.duration > 10000 }
                (completedSessions * 100) / totalSessions
            } else 0

            val lastRoom = allRooms.maxByOrNull { it.startTime }
            val lastActiveText = if (lastRoom != null) {
                val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                sdf.format(Date(lastRoom.startTime))
            } else {
                getString(R.string.profile_stat_never)
            }

            val roomCounts = allRooms.groupBy { it.roomName }.mapValues { it.value.size }
            val topRoom = roomCounts.maxByOrNull { it.value }?.key ?: "-"

            findViewById<TextView>(R.id.statRooms).text = roomsHosted.toString()
            findViewById<TextView>(R.id.statHours).text = hoursHosted.toString()
            findViewById<TextView>(R.id.statViewHours).text = hoursViewed.toString()
            findViewById<TextView>(R.id.statSuccess).text = "$successRate%"
            findViewById<TextView>(R.id.statLastActive).text = lastActiveText
            findViewById<TextView>(R.id.statTopRoom).text = topRoom
        }
    }

    private fun loadBadgesWithProgress() {
        val roomHistoryManager = (application as ScreenMirrorApp).roomHistoryManager
        lifecycleScope.launch {
            val allRooms: List<com.example.screenmirror.data.RoomHistory>
            withContext(Dispatchers.IO) {
                allRooms = roomHistoryManager.getAll()
            }

            val roomsHosted = allRooms.count { it.role == "sender" }
            val roomsViewed = allRooms.count { it.role == "viewer" }
            val totalHours = (allRooms.sumOf { it.duration } / 3600000).toInt()

            val hasNightSession = allRooms.any {
                val cal = Calendar.getInstance().apply { timeInMillis = it.startTime }
                cal.get(Calendar.HOUR_OF_DAY) in 0..5
            }

            val hasStreak = checkWeeklyStreak(allRooms)

            val badges = findViewById<LinearLayout>(R.id.badgesContainer)
            badges.removeAllViews()

            data class BadgeInfo(val iconRes: Int, val label: String, val unlocked: Boolean, val current: Int, val target: Int)

            val badgeList = listOf(
                BadgeInfo(R.drawable.ic_badge_star, getString(R.string.badge_first_session), allRooms.isNotEmpty(), allRooms.size.coerceAtMost(1), 1),
                BadgeInfo(R.drawable.ic_badge_star, getString(R.string.badge_broadcaster_10), roomsHosted >= 10, roomsHosted.coerceAtMost(10), 10),
                BadgeInfo(R.drawable.ic_badge_star, getString(R.string.badge_viewer_20), roomsViewed >= 20, roomsViewed.coerceAtMost(20), 20),
                BadgeInfo(R.drawable.ic_badge_star, getString(R.string.badge_night_owl), hasNightSession, if (hasNightSession) 1 else 0, 1),
                BadgeInfo(R.drawable.ic_badge_star, getString(R.string.badge_week_streak), hasStreak, if (hasStreak) 7 else countStreakDays(allRooms), 7),
                BadgeInfo(R.drawable.ic_badge_star, getString(R.string.badge_master_50), roomsHosted >= 50, roomsHosted.coerceAtMost(50), 50),
                BadgeInfo(R.drawable.ic_badge_star, getString(R.string.badge_hours_100), totalHours >= 100, totalHours.coerceAtMost(100), 100)
            )

            for (badge in badgeList) {
                val badgeView = createBadgeWithProgress(badge.iconRes, badge.label, badge.unlocked, badge.current, badge.target)
                badges.addView(badgeView)
            }
        }
    }

    private fun countStreakDays(rooms: List<com.example.screenmirror.data.RoomHistory>): Int {
        if (rooms.isEmpty()) return 0
        val calendar = Calendar.getInstance()
        val today = calendar.clone() as Calendar
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        var streak = 0
        for (i in 0 until 7) {
            val dayStart = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
            val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val hasActivity = rooms.any { it.startTime >= dayStart.timeInMillis && it.startTime < dayEnd.timeInMillis }
            if (hasActivity) streak++ else if (i > 0) break
        }
        return streak
    }

    private fun checkWeeklyStreak(rooms: List<com.example.screenmirror.data.RoomHistory>): Boolean {
        return countStreakDays(rooms) >= 7
    }

    private fun createBadgeWithProgress(iconRes: Int, label: String, unlocked: Boolean, current: Int, target: Int): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12, 12, 12, 12)
            background = getDrawable(if (unlocked) R.drawable.bg_badge_unlocked else R.drawable.bg_badge)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.marginEnd = 6
            params.marginStart = 6
            layoutParams = params
        }

        val icon = android.widget.ImageView(this).apply {
            setImageResource(if (unlocked) iconRes else R.drawable.ic_badge_star_locked)
            val size = (resources.displayMetrics.density * 36).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            alpha = if (unlocked) 1f else 0.3f
        }

        val textView = TextView(this).apply {
            text = label
            textSize = 10f
            setTextColor(if (unlocked) getColor(R.color.accent) else getColor(R.color.text_secondary))
            gravity = Gravity.CENTER
            maxLines = 1
        }

        val progressText = TextView(this).apply {
            text = getString(R.string.progress_format, current, target)
            textSize = 9f
            setTextColor(if (unlocked) getColor(R.color.accent) else getColor(R.color.text_secondary))
            gravity = Gravity.CENTER
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = target
            this.progress = current
            progressDrawable = getDrawable(R.drawable.bg_progress_bar)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.density * 6).toInt()
            ).apply {
                topMargin = (resources.displayMetrics.density * 4).toInt()
            }
        }

        container.addView(icon)
        container.addView(textView)
        container.addView(progressText)
        container.addView(progressBar)
        return container
    }

    private fun loadWeeklyActivity() {
        val roomHistoryManager = (application as ScreenMirrorApp).roomHistoryManager
        val weeklyView = findViewById<WeeklyActivityView>(R.id.weeklyActivityView)
        val totalText = findViewById<TextView>(R.id.activityTotalText)

        lifecycleScope.launch {
            val allRooms: List<com.example.screenmirror.data.RoomHistory>
            withContext(Dispatchers.IO) {
                allRooms = roomHistoryManager.getAll()
            }

            val calendar = Calendar.getInstance()
            val dailyMinutes = IntArray(7) { 0 }

            for (room in allRooms) {
                val diffDays = ((calendar.timeInMillis - room.startTime) / (24 * 60 * 60 * 1000)).toInt()
                if (diffDays in 0..6) {
                    val dayIndex = 6 - diffDays
                    dailyMinutes[dayIndex] += (room.duration / 60000).toInt()
                }
            }

            weeklyView.setData(dailyMinutes)
            val totalMinutes = dailyMinutes.sum()
            totalText.text = getString(R.string.activity_total_format, totalMinutes)
        }
    }

    private fun loadTimeline() {
        val roomHistoryManager = (application as ScreenMirrorApp).roomHistoryManager
        val timelineView = findViewById<ConnectionTimelineView>(R.id.connectionTimelineView)

        lifecycleScope.launch {
            val allRooms: List<com.example.screenmirror.data.RoomHistory>
            withContext(Dispatchers.IO) {
                allRooms = roomHistoryManager.getAll()
            }

            val calendar = Calendar.getInstance()
            val dailyData = Array(30) { Pair(0, 0) }

            for (room in allRooms) {
                val diffDays = ((calendar.timeInMillis - room.startTime) / (24 * 60 * 60 * 1000)).toInt()
                if (diffDays in 0..29) {
                    val dayIndex = 29 - diffDays
                    val minutes = (room.duration / 60000).toInt()
                    if (room.role == "sender") {
                        dailyData[dayIndex] = dailyData[dayIndex].copy(first = dailyData[dayIndex].first + minutes)
                    } else {
                        dailyData[dayIndex] = dailyData[dayIndex].copy(second = dailyData[dayIndex].second + minutes)
                    }
                }
            }

            timelineView.setData(dailyData)
        }
    }

    private fun loadDeviceInfo() {
        findViewById<TextView>(R.id.infoAndroid).text = "Android ${Build.VERSION.RELEASE}"

        val wm = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        findViewById<TextView>(R.id.infoResolution).text = "${metrics.widthPixels}x${metrics.heightPixels}"

        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val networkText = when {
            caps == null -> getString(R.string.profile_network_none)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> getString(R.string.profile_network_wifi)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> getString(R.string.profile_network_mobile)
            else -> getString(R.string.profile_network_none)
        }
        findViewById<TextView>(R.id.infoNetwork).text = networkText

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        findViewById<TextView>(R.id.infoBattery).text = getString(R.string.profile_battery_format, batteryLevel)
    }

    private fun saveNickname(nickname: String) {
        prefs.edit().putString("nickname", nickname).apply()
    }

    private fun clearProfile() {
        prefs.edit().clear().apply()
        removeProfilePhoto()
    }

    private fun animateElements() {
        val header = findViewById<LinearLayout>(R.id.profileHeader)
        val greeting = greetingText
        val stats = findViewById<LinearLayout>(R.id.statsCard)
        val badges = findViewById<LinearLayout>(R.id.badgesCard)
        val weekly = findViewById<LinearLayout>(R.id.weeklyCard)
        val timeline = findViewById<LinearLayout>(R.id.timelineCard)
        val deviceInfo = findViewById<LinearLayout>(R.id.deviceInfoCard)
        val quickStart = findViewById<LinearLayout>(R.id.quickStartCard)
        val menu = findViewById<LinearLayout>(R.id.menuCard)

        listOf(header, greeting, stats, badges, weekly, timeline, deviceInfo, quickStart, menu).forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 30f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((index * 70).toLong())
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }
}
