package org.ecos.nexus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class KnowledgeNode(val id: String, val title: String, val kind: String, val assetId: String? = null, val summary: String = "", val body: String = "", val createdAt: Long = System.currentTimeMillis())
data class KnowledgeEdge(val id: String, val fromId: String, val toId: String, val relation: String, val layer: String, val status: String, val provenance: String, val score: Double = 1.0)

/** Local offline graph. Canonical facts and semantic suggestions are persisted separately. */
class KnowledgeGraphStore(context: Context) {
    private val file = File(context.filesDir, "nexus-knowledge.json")

    @Synchronized fun nodes(): List<KnowledgeNode> = read().getJSONArray("nodes").let { a ->
        (0 until a.length()).map { a.getJSONObject(it).toNode() }
    }
    @Synchronized fun edges(layer: String? = null): List<KnowledgeEdge> = read().getJSONArray("edges").let { a ->
        (0 until a.length()).map { a.getJSONObject(it).toEdge() }.filter { layer == null || it.layer == layer }
    }
    @Synchronized fun ensureAssetNode(asset: LibraryAsset): KnowledgeNode {
        nodes().firstOrNull { it.assetId == asset.id }?.let { return it }
        return KnowledgeNode(UUID.randomUUID().toString(), asset.name, "asset", asset.id).also(::writeNode)
    }
    @Synchronized fun removeAssetNode(assetId: String) {
        val root = read()
        val nodes = root.getJSONArray("nodes")
        val removedIds = mutableSetOf<String>()
        for (i in nodes.length() - 1 downTo 0) {
            val node = nodes.getJSONObject(i)
            if (node.optString("assetId") == assetId) {
                removedIds += node.optString("id")
                nodes.remove(i)
            }
        }
        val edges = root.getJSONArray("edges")
        for (i in edges.length() - 1 downTo 0) {
            val edge = edges.getJSONObject(i)
            if (edge.optString("fromId") in removedIds || edge.optString("toId") in removedIds) edges.remove(i)
        }
        persist(root)
    }
    @Synchronized fun createArticle(title: String, body: String, summary: String = ""): KnowledgeNode {
        require(title.isNotBlank()) { "Article title is required" }
        return KnowledgeNode(UUID.randomUUID().toString(), title.trim(), "article", summary = summary.trim(), body = body).also(::writeNode)
    }
    @Synchronized fun addCanonical(fromId: String, toId: String, relation: String, provenance: String = "user-confirmed"): KnowledgeEdge {
        require(fromId != toId) { "A node cannot link to itself" }
        val ids = nodes().map { it.id }.toSet()
        require(fromId in ids && toId in ids) { "Both nodes must exist" }
        return KnowledgeEdge(UUID.randomUUID().toString(), fromId, toId, relation.ifBlank { "related_to" }, "canonical", "accepted", provenance).also(::writeEdge)
    }
    @Synchronized fun reviewSemantic(edgeId: String, accept: Boolean) {
        val root = read()
        val a = root.getJSONArray("edges")
        for (i in 0 until a.length()) {
            val item = a.getJSONObject(i)
            if (item.optString("id") == edgeId && item.optString("layer") == "semantic") {
                item.put("status", if (accept) "accepted" else "rejected")
                break
            }
        }
        persist(root)
    }
    /** Local, explainable title-token similarity. Suggestions never become canonical automatically. */
    @Synchronized fun refreshSuggestions(assets: List<LibraryAsset>, contentFor: (LibraryAsset) -> String = { "" }) {
        val graphNodes = assets.map(::ensureAssetNode)
        val contentById = graphNodes.associate { node -> node.id to (assets.firstOrNull { it.id == node.assetId }?.let(contentFor).orEmpty()) }\n        val existing = edges("semantic").map { setOf(it.fromId, it.toId) }.toMutableSet()
        val additions = mutableListOf<KnowledgeEdge>()
        for (i in graphNodes.indices) for (j in i + 1 until graphNodes.size) {
            val a = graphNodes[i]; val b = graphNodes[j]
            val left = tokens(a.title + " " + contentById[a.id].orEmpty()); val right = tokens(b.title + " " + contentById[b.id].orEmpty())
            if (left.isEmpty() || right.isEmpty()) continue
            val overlap = left.intersect(right).size.toDouble()
            val score = overlap / (left.size + right.size - overlap)
            if (score >= 0.20 && setOf(a.id, b.id) !in existing) {
                additions += KnowledgeEdge(UUID.randomUUID().toString(), a.id, b.id, "similar_title_terms", "semantic", "suggested", "local-content-token-overlap", score)
                existing += setOf(a.id, b.id)
            }
        }
        if (additions.isNotEmpty()) {
            val root = read()
            additions.forEach { root.getJSONArray("edges").put(it.toJson()) }
            persist(root)
        }
    }
    private fun tokens(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 3 && it !in setOf("the", "and", "for", "with", "from", "yang", "dan", "untuk", "dari") }.toSet()
    private fun writeNode(n: KnowledgeNode) { val r = read(); r.getJSONArray("nodes").put(n.toJson()); persist(r) }
    private fun writeEdge(e: KnowledgeEdge) { val r = read(); r.getJSONArray("edges").put(e.toJson()); persist(r) }
    private fun read(): JSONObject = runCatching { if (file.exists()) JSONObject(file.readText()) else JSONObject() }.getOrElse { JSONObject() }.apply {
        if (!has("nodes")) put("nodes", JSONArray())
        if (!has("edges")) put("edges", JSONArray())
        if (!has("schemaVersion")) put("schemaVersion", 1)
    }
    private fun persist(root: JSONObject) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }
    private fun KnowledgeNode.toJson() = JSONObject().apply {
        put("id", id); put("title", title); put("kind", kind); put("assetId", assetId); put("summary", summary); put("body", body); put("createdAt", createdAt)
    }
    private fun JSONObject.toNode() = KnowledgeNode(getString("id"), getString("title"), optString("kind", "article"), optString("assetId").takeIf { it.isNotBlank() && it != "null" }, optString("summary"), optString("body"), optLong("createdAt"))
    private fun KnowledgeEdge.toJson() = JSONObject().apply {
        put("id", id); put("fromId", fromId); put("toId", toId); put("relation", relation); put("layer", layer); put("status", status); put("provenance", provenance); put("score", score)
    }
    private fun JSONObject.toEdge() = KnowledgeEdge(getString("id"), getString("fromId"), getString("toId"), optString("relation"), optString("layer"), optString("status"), optString("provenance"), optDouble("score", 1.0))
}
