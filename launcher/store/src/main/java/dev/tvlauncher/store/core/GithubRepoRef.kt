package dev.tvlauncher.store.core

/** A validated GitHub "owner/repo". Accepts the bare slug or a pasted https://github.com/owner/repo link. */
data class GithubRepoRef(val owner: String, val repo: String) {

    val slug: String get() = "$owner/$repo"
    val latestReleaseApiUrl: String get() = "https://api.github.com/repos/$owner/$repo/releases/latest"

    companion object {
        private val SLUG = Regex("^[A-Za-z0-9_.-]{1,39}/[A-Za-z0-9_.-]{1,100}$")
        private val PREFIX = Regex("^(https://)?(www\\.)?github\\.com/", RegexOption.IGNORE_CASE)

        fun parse(input: String?): GithubRepoRef? {
            var s = input?.trim() ?: return null
            if (s.isEmpty()) return null
            if (s.contains("://") || s.lowercase().startsWith("github.com/") || s.lowercase().startsWith("www.github.com/")) {
                val m = PREFIX.find(s) ?: return null
                s = s.substring(m.range.last + 1).trimEnd('/')
                val parts = s.split('/')
                if (parts.size < 2) return null
                s = parts[0] + "/" + parts[1].removeSuffix(".git")
            }
            if (!SLUG.matches(s)) return null
            val owner = s.substringBefore('/')
            val repo = s.substringAfter('/')
            if (owner.all { it == '.' } || repo.all { it == '.' }) return null
            return GithubRepoRef(owner, repo)
        }
    }
}
