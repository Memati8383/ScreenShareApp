package com.example.screenmirror.presentation.recent

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.screenmirror.HapticHelper
import com.example.screenmirror.R
import com.example.screenmirror.data.local.RoomHistoryEntity
import com.example.screenmirror.model.RoomRole
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecentActivity : AppCompatActivity() {

    private val viewModel: RecentViewModel by viewModels()
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var statsCard: View
    private lateinit var filterChipGroup: ChipGroup
    private lateinit var adapter: RoomHistoryAdapter
    
    private var searchView: SearchView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recent_new)

        initViews()
        setupToolbar()
        setupRecyclerView()
        setupFilters()
        observeUiState()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        statsCard = findViewById(R.id.statsCard)
        filterChipGroup = findViewById(R.id.filterChipGroup)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeButtonEnabled(true)
            title = getString(R.string.nav_recent)
        }
        
        toolbar.setNavigationOnClickListener {
            HapticHelper.lightTap(this)
            finish()
        }
        
        // Toolbar icon colors
        toolbar.overflowIcon?.setTint(resources.getColor(R.color.text_primary, theme))
    }

    private fun setupRecyclerView() {
        adapter = RoomHistoryAdapter(
            onItemClick = { room ->
                showRoomDetails(room)
            },
            onFavoriteClick = { room ->
                HapticHelper.lightTap(this)
                viewModel.onEvent(RecentEvent.ToggleFavorite(room.id, !room.isFavorite))
            },
            onDeleteClick = { room ->
                showDeleteDialog(room)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Swipe to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val room = adapter.getItemAt(position)
                    deleteRoom(room)
                }
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun setupFilters() {
        // Hepsi
        findViewById<Chip>(R.id.chipAll).setOnClickListener {
            HapticHelper.lightTap(this)
            viewModel.onEvent(RecentEvent.ApplyFilter(FilterType.All))
        }

        // Favoriler
        findViewById<Chip>(R.id.chipFavorites).setOnClickListener {
            HapticHelper.lightTap(this)
            viewModel.onEvent(RecentEvent.ApplyFilter(FilterType.Favorites))
        }

        // Gönderici
        findViewById<Chip>(R.id.chipSender).setOnClickListener {
            HapticHelper.lightTap(this)
            viewModel.onEvent(RecentEvent.ApplyFilter(FilterType.ByRole(RoomRole.SENDER)))
        }

        // İzleyici
        findViewById<Chip>(R.id.chipViewer).setOnClickListener {
            HapticHelper.lightTap(this)
            viewModel.onEvent(RecentEvent.ApplyFilter(FilterType.ByRole(RoomRole.VIEWER)))
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is RecentUiState.Loading -> showLoading()
                        is RecentUiState.Success -> showSuccess(state)
                        is RecentUiState.Error -> showError(state.message)
                    }
                }
            }
        }
    }

    private fun showLoading() {
        recyclerView.isVisible = false
        emptyView.isVisible = false
        statsCard.isVisible = false
    }

    private fun showSuccess(state: RecentUiState.Success) {
        val isEmpty = state.rooms.isEmpty()
        
        recyclerView.isVisible = !isEmpty
        emptyView.isVisible = isEmpty
        statsCard.isVisible = !isEmpty && state.statistics != null

        adapter.submitList(state.rooms)

        // İstatistikleri güncelle
        state.statistics?.let { stats ->
            updateStatistics(stats)
        }

        // Aktif filtreyi güncelle
        updateFilterSelection(state.filterType)
    }

    private fun showError(message: String) {
        Snackbar.make(recyclerView, message, Snackbar.LENGTH_LONG).show()
    }

    private fun updateStatistics(stats: com.example.screenmirror.domain.usecase.RoomStatistics) {
        findViewById<android.widget.TextView>(R.id.tvTotalSessions)?.text = stats.totalSessions.toString()
        findViewById<android.widget.TextView>(R.id.tvTotalDuration)?.text = formatDuration(stats.totalDuration)
        findViewById<android.widget.TextView>(R.id.tvSenderCount)?.text = stats.senderCount.toString()
        findViewById<android.widget.TextView>(R.id.tvViewerCount)?.text = stats.viewerCount.toString()
    }

    private fun updateFilterSelection(filterType: FilterType) {
        filterChipGroup.clearCheck()
        when (filterType) {
            is FilterType.All -> findViewById<Chip>(R.id.chipAll)?.isChecked = true
            is FilterType.Favorites -> findViewById<Chip>(R.id.chipFavorites)?.isChecked = true
            is FilterType.ByRole -> {
                if (filterType.role == RoomRole.SENDER) {
                    findViewById<Chip>(R.id.chipSender)?.isChecked = true
                } else {
                    findViewById<Chip>(R.id.chipViewer)?.isChecked = true
                }
            }
            else -> {}
        }
    }

    private fun deleteRoom(room: RoomHistoryEntity) {
        viewModel.onEvent(RecentEvent.DeleteRoom(room))
        Snackbar.make(
            recyclerView,
            getString(R.string.recent_deleted, room.roomName),
            Snackbar.LENGTH_LONG
        ).setAction(getString(R.string.recent_undo)) {
            viewModel.onEvent(RecentEvent.UndoDelete(room))
        }.show()
    }

    private fun showDeleteDialog(room: RoomHistoryEntity) {
        HapticHelper.mediumTap(this)
        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.recent_delete_title))
            .setMessage(getString(R.string.recent_delete_msg, room.roomName))
            .setPositiveButton(getString(R.string.recent_delete_confirm)) { _, _ ->
                deleteRoom(room)
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun showRoomDetails(room: RoomHistoryEntity) {
        HapticHelper.lightTap(this)
        RoomDetailsBottomSheet.newInstance(room).show(supportFragmentManager, "room_details")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_recent_new, menu)
        
        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem?.actionView as? SearchView
        
        searchView?.apply {
            queryHint = getString(R.string.action_search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    query?.let { viewModel.onEvent(RecentEvent.Search(it)) }
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    newText?.let { viewModel.onEvent(RecentEvent.Search(it)) }
                    return true
                }
            })
        }
        
        // Menü item iconlarına tint uygula
        menu.findItem(R.id.action_export)?.icon?.setTint(resources.getColor(R.color.text_primary, theme))
        menu.findItem(R.id.action_delete_all)?.icon?.setTint(resources.getColor(R.color.dark_status_error, theme))
        menu.findItem(R.id.action_search)?.icon?.setTint(resources.getColor(R.color.text_primary, theme))
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_delete_all -> {
                HapticHelper.mediumTap(this)
                showDeleteAllDialog()
                true
            }
            R.id.action_export -> {
                HapticHelper.lightTap(this)
                exportRoomHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportRoomHistory() {
        val currentState = viewModel.uiState.value
        if (currentState is RecentUiState.Success && currentState.rooms.isNotEmpty()) {
            // TODO: CSV/JSON export implementasyonu
            Snackbar.make(
                recyclerView, 
                "Dışa aktarma özelliği: ${currentState.rooms.size} oda kaydı hazır", 
                Snackbar.LENGTH_LONG
            ).setAction("Tamam", null).show()
        } else {
            Snackbar.make(
                recyclerView, 
                "Dışa aktarılacak oda geçmişi bulunamadı", 
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun showDeleteAllDialog() {
        HapticHelper.mediumTap(this)
        AlertDialog.Builder(this, R.style.Theme_ScreenShare_Dialog)
            .setTitle(getString(R.string.recent_delete_title))
            .setMessage(getString(R.string.recent_delete_all_msg))
            .setPositiveButton(getString(R.string.recent_delete_confirm)) { _, _ ->
                HapticHelper.mediumTap(this)
                viewModel.onEvent(RecentEvent.DeleteAll)
                Snackbar.make(recyclerView, getString(R.string.recent_cleared), Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                HapticHelper.lightTap(this)
            }
            .show()
    }

    private fun formatDuration(millis: Long): String {
        val hours = millis / 3600000
        val minutes = (millis % 3600000) / 60000
        val seconds = (millis % 60000) / 1000

        return when {
            hours > 0 -> "${hours}sa ${minutes}dk"
            minutes > 0 -> "${minutes}dk ${seconds}sn"
            else -> "${seconds}sn"
        }
    }
}
