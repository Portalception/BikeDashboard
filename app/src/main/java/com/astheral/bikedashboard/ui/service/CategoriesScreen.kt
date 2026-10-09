package com.astheral.bikedashboard.ui.service

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astheral.bikedashboard.data.AppDatabase
import com.astheral.bikedashboard.data.DefaultCategory
import com.astheral.bikedashboard.data.ServiceCategory
import com.astheral.bikedashboard.data.newCategory
import com.astheral.bikedashboard.ui.components.GroupCard
import com.astheral.bikedashboard.ui.components.GroupDivider
import com.astheral.bikedashboard.ui.components.SettingRow
import com.astheral.bikedashboard.ui.count
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A category and how many records (on every bike) are in it. */
data class CategoryRow(val category: ServiceCategory, val records: Int)

class CategoriesViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).categoryDao()

    val rows: StateFlow<List<CategoryRow>?> = combine(dao.observe(), dao.observeCounts()) { cats, counts ->
        cats.map { c -> CategoryRow(c, counts.firstOrNull { it.id == c.id }?.count ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(name: String) {
        val existing = rows.value?.map { it.category } ?: return
        viewModelScope.launch { dao.insertAll(listOf(newCategory(name, existing))) }
    }

    fun rename(category: ServiceCategory, name: String) {
        viewModelScope.launch { dao.rename(category.id, name) }
    }

    /** Removes it; its records move to "Other" and its intervals go. */
    fun delete(category: ServiceCategory) {
        viewModelScope.launch { dao.delete(category.id) }
    }
}

/**
 * The kinds of work records can be filed under, shared by all bikes. Tap one to rename it; removing
 * one moves its records to "Other", which can't be removed itself.
 */
@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = viewModel()) {
    val rows by viewModel.rows.collectAsState()
    val list = rows ?: return
    var renaming by remember { mutableStateOf<ServiceCategory?>(null) }
    var removing by remember { mutableStateOf<CategoryRow?>(null) }
    var adding by remember { mutableStateOf(false) }
    val names = list.map { it.category.name }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Shared by all your bikes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        Text(
            "The kinds of work your service records are filed under. Tap one to rename it.",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GroupCard {
            list.forEachIndexed { i, row ->
                if (i > 0) GroupDivider()
                val c = row.category
                Row(
                    Modifier.fillMaxWidth().clickable { renaming = c }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(categoryColor(c.id), CircleShape))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                if (row.records == 0) "No records" else count(row.records, "record"),
                                "on the intervals page".takeIf { c.hasInterval },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (c.id != DefaultCategory.OTHER) {
                        IconButton(onClick = { removing = row }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${c.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        GroupCard {
            SettingRow(Icons.Filled.Add, "Add a Category", subtitle = "Like tyres, chain or accessories", onClick = { adding = true })
        }
    }

    renaming?.let { c ->
        CategoryNameDialog(
            title = "Rename",
            initial = c.name,
            taken = names - c.name,
            note = "Its records, on every bike, keep it under the new name.",
            onDismiss = { renaming = null },
            onSave = { name -> viewModel.rename(c, name); renaming = null },
        )
    }
    if (adding) {
        CategoryNameDialog(
            title = "Add a Category",
            initial = "",
            taken = names,
            onDismiss = { adding = false },
            onSave = { name -> viewModel.add(name); adding = false },
        )
    }
    removing?.let { row ->
        val other = list.firstOrNull { it.category.id == DefaultCategory.OTHER }?.category?.name ?: "Other"
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${row.category.name}?") },
            text = {
                Text(
                    buildString {
                        append(
                            if (row.records == 0) "No records are in it."
                            else "${count(row.records, "record")} (on all your bikes) will move to $other.",
                        )
                        if (row.category.hasInterval) append(" Its service interval and reminders are removed too.")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(row.category); removing = null }) {
                    Text(if (row.records == 0) "Remove" else "Move and Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}
