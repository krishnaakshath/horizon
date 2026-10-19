package dev.tvlauncher.audit

/** JSON + one-line summaries for audit results. Hand-written JSON so the engine stays dependency-free. */
object AuditReport {
    fun toJson(findings: List<Finding>, meta: Map<String, Any?>): String =
        toJson(AuditResult(findings, findings.size,
            Severity.values().associateWith { s -> findings.count { it.severity == s } },
            Kind.values().associateWith { k -> findings.count { it.kind == k } }), meta)

    fun toJson(result: AuditResult, meta: Map<String, Any?>): String {
        val summary = linkedMapOf<String, Any?>(
            "total" to result.total,
            "shown" to result.findings.size,
            "truncated" to result.truncated,
            "high" to (result.bySeverity[Severity.HIGH] ?: 0),
            "medium" to (result.bySeverity[Severity.MEDIUM] ?: 0),
            "low" to (result.bySeverity[Severity.LOW] ?: 0),
            "byKind" to result.byKind.filterValues { it > 0 }.mapKeys { it.key.name },
            "text" to summary(result),
            "notes" to result.notes,
            "stats" to result.stats,
        )
        val list = result.findings.map {
            linkedMapOf<String, Any?>("kind" to it.kind.name, "severity" to it.severity.name, "a" to it.a, "b" to it.b,
                "message" to it.message, "rect" to it.rect)
        }
        return Json.write(linkedMapOf("meta" to meta, "summary" to summary, "findings" to list))
    }

    fun nodesToJson(nodes: List<Node>, meta: Map<String, Any?>): String {
        val list = nodes.sortedBy { it.id }.map {
            linkedMapOf<String, Any?>("id" to it.id, "parent" to it.parent, "cls" to it.cls, "name" to it.name,
                "rect" to listOf(it.left, it.top, it.right, it.bottom), "visible" to it.visible, "alpha" to it.alpha,
                "clipChildren" to it.clipChildren, "text" to it.text, "textSizePx" to it.textSizePx, "maxLines" to it.maxLines,
                "lineCount" to it.lineCount, "ellipsized" to it.ellipsized, "focusable" to it.focusable, "tag" to it.tag, "z" to it.z,
                "bgAlpha" to it.bgAlpha, "bgKnown" to it.bgKnown)
        }
        return Json.write(linkedMapOf("meta" to meta, "nodes" to list))
    }

    fun summary(findings: List<Finding>): String =
        line(findings.size, findings.size, findings.groupingBy { it.severity }.eachCount(), findings.groupingBy { it.kind }.eachCount())

    fun summary(result: AuditResult): String = line(result.total, result.findings.size, result.bySeverity, result.byKind)

    private fun line(total: Int, shown: Int, sev: Map<Severity, Int>, kinds: Map<Kind, Int>): String {
        if (total == 0) return "0 findings"
        val s = "$total findings (high=${sev[Severity.HIGH] ?: 0} medium=${sev[Severity.MEDIUM] ?: 0} low=${sev[Severity.LOW] ?: 0})"
        val k = Kind.values().filter { (kinds[it] ?: 0) > 0 }.joinToString(" ") { "${it.name}=${kinds[it]}" }
        val cap = if (shown < total) "; showing first $shown of $total" else ""
        return "$s: $k$cap"
    }
}

/** Minimal JSON writer: null, Boolean, Number, String, enums, Map (string keys), Iterable. */
internal object Json {
    fun write(v: Any?): String = StringBuilder().also { value(it, v) }.toString()

    private fun value(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long, is Short, is Byte -> sb.append(v)
            is Float -> if (v.isFinite()) sb.append(v.toString()) else sb.append("null")
            is Double -> if (v.isFinite()) sb.append(v.toString()) else sb.append("null")
            is Number -> sb.append(v.toString())
            is Enum<*> -> str(sb, v.name)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    str(sb, k.toString()); sb.append(':'); value(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; value(sb, x) }
                sb.append(']')
            }
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) when {
            ch == '"' -> sb.append("\\\"")
            ch == '\\' -> sb.append("\\\\")
            ch == '\n' -> sb.append("\\n")
            ch == '\r' -> sb.append("\\r")
            ch == '\t' -> sb.append("\\t")
            ch < ' ' || ch == ' ' || ch == ' ' -> sb.append(String.format("\\u%04x", ch.code))
            else -> sb.append(ch)
        }
        sb.append('"')
    }
}
