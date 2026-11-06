package org.ecos.nexus

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class LinkLayer { CANONICAL, SEMANTIC }
data class KnowledgeLink(val id: String, val fromId: String, val toId: String, val predicate: String, val layer: LinkLayer, val provenance: String, val score: Float, val status: String)

/** Explicit canonical facts and inferred semantic candidates are persisted separately. */
class KnowledgeGraph(private val context: Context) {
    private val file = File(context.filesDir, "nexus-graph.json")
    @Synchronized fun links(): List<KnowledgeLink> = runCatching {
        val a = JSONArray(if (file.exists()) file.readText() else "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            KnowledgeLink(o.getString("id"), o.getString("from"), o.getString("to"), o.optString("predicate", "related_to"), LinkLayer.valueOf(o.getString("layer")), o.optString("provenance"), o.optDouble("score", 1.0).toFloat(), o.optString("status", "confirmed"))
        }
    }.getOrDefault(emptyList())
    @Synchronized fun addCanonical(from: String, to: String, predicate: String, source: String): KnowledgeLink {
        require(from.isNotBlank() && to.isNotBlank() && from != to)
        val link = KnowledgeLink(UUID.randomUUID().toString(), from, to, predicate, LinkLayer.CANONICAL, source, 1f, "confirmed")
        save(links() + link); return link
    }
    @Synchronized fun addSemanticCandidate(from: String, to: String, reason: String, score: Float): KnowledgeLink {
        require(from.isNotBlank() && to.isNotBlank() && from != to)
        val link = KnowledgeLink(UUID.randomUUID().toString(), from, to, "semantically_related", LinkLayer.SEMANTIC, reason, score.coerceIn(0f, 1f), "suggested")
        save(links() + link); return link
    }
    @Synchronized fun acceptSemantic(id: String) { save(links().map { if (it.id == id && it.layer == LinkLayer.SEMANTIC) it.copy(status = "accepted") else it }) }
    @Synchronized fun rejectSemantic(id: String) { save(links().filterNot { it.id == id && it.layer == LinkLayer.SEMANTIC }) }
    private fun save(items: List<KnowledgeLink>) {
        val a = JSONArray()
        items.forEach { l -> a.put(JSONObject().apply { put("id", l.id); put("from", l.fromId); put("to", l.toId); put("predicate", l.predicate); put("layer", l.layer.name); put("provenance", l.provenance); put("score", l.score); put("status", l.status) }) }
        val tmp = File(context.filesDir, "nexus-graph.tmp"); tmp.writeText(a.toString())
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }
}
