package fi.subsite.phonedrop.server

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import java.io.File
import java.security.PublicKey

/** A pasted OpenSSH public key line, e.g. "ssh-ed25519 AAAA... user@host". */
data class AuthorizedKey(val line: String, val key: PublicKey) {
    val type: String get() = KeyUtils.getKeyType(key) ?: key.algorithm
    val comment: String get() = line.trim().split(Regex("\\s+")).drop(2).joinToString(" ")
    val fingerprint: String get() = KeyUtils.getFingerPrint(key)
}

/** Persists authorized keys in OpenSSH authorized_keys format. */
class AuthorizedKeys(private val file: File) {

    @Synchronized
    fun load(): List<AuthorizedKey> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line -> runCatching { parse(line) }.getOrNull() }
    }

    fun publicKeys(): List<PublicKey> = load().map { it.key }

    /** Adds every valid key line in [text]. Throws [IllegalArgumentException] if none are valid. */
    @Synchronized
    fun add(text: String): Int {
        val existing = load()
        val parsed = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line -> runCatching { parse(line) }.getOrElse { throw IllegalArgumentException("Not a valid public key: ${line.take(40)}…") } }
        require(parsed.isNotEmpty()) { "Paste a public key, e.g. the contents of ~/.ssh/id_ed25519.pub" }
        val fresh = parsed.filter { p -> existing.none { it.key == p.key } }
        save(existing + fresh)
        return fresh.size
    }

    @Synchronized
    fun remove(key: AuthorizedKey) = save(load().filterNot { it.key == key.key })

    private fun save(keys: List<AuthorizedKey>) {
        file.parentFile?.mkdirs()
        file.writeText(keys.joinToString("") { it.line.trim() + "\n" })
    }

    companion object {
        fun parse(line: String): AuthorizedKey {
            val entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(line.trim())
                ?: throw IllegalArgumentException("Empty key")
            val key = entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING)
                ?: throw IllegalArgumentException("Unsupported key type")
            return AuthorizedKey(line.trim(), key)
        }
    }
}
