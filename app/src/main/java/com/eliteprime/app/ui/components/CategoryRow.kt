package com.eliteprime.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.eliteprime.app.data.local.entity.Category

@Composable
fun CategoryRow(
    categories: List<Category>,
    selectedCategory: String?,
    onCategoryClick: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onCategoryClick(null) },
                label = { Text("All") }
            )
        }
        items(categories) { category ->
            FilterChip(
                selected = selectedCategory == category.categoryId,
                onClick = { onCategoryClick(category.categoryId) },
                label = { Text(category.categoryName) }
            )
        }
    }
}
