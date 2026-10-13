package fi.subsite.phonedrop.server

import java.security.SecureRandom

object Passwords {
    // No look-alikes (0/O, 1/l/I) since the password is read off the phone screen.
    private const val ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(length: Int = 8): String =
        String(CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] })
}
