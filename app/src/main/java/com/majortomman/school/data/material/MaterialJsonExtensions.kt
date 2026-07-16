package com.majortomman.school.data.material

import org.json.JSONArray

internal fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) add(getString(index))
}

internal fun JSONArray?.toPathNodes(): List<CatalogPathNode> = buildList {
    val source = this@toPathNodes ?: return@buildList
    for (index in 0 until source.length()) {
        val node = CatalogPathNode.fromJson(source.getJSONObject(index))
        if (node.id.isNotBlank() && node.title.isNotBlank()) add(node)
    }
}
