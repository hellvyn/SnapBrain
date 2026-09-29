package com.snapbrain.core

data class ShareList(val title: String, val steps: Boolean, val items: List<Pair<String, Boolean>>)

/** Plain text for WhatsApp and friends: the title, info lines, then each list with ☐/☑. */
fun shareText(title: String, info: Map<String, String>, lists: List<ShareList>): String = buildString {
    append(title)
    info.forEach { (k, v) -> append('\n').append(k).append(": ").append(v) }
    lists.forEach { l ->
        append("\n\n").append(l.title)
        l.items.forEachIndexed { i, (text, done) ->
            append('\n').append(if (done) "☑ " else "☐ ")
            if (l.steps) append(i + 1).append(". ")
            append(text)
        }
    }
}
