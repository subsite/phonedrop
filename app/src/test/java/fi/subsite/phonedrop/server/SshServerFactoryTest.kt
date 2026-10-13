package fi.subsite.phonedrop.server

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.server.SshServer
import org.apache.sshd.sftp.client.SftpClient
import org.apache.sshd.sftp.client.SftpClientFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.time.Duration

class SshServerFactoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var root: File
    private lateinit var keysFile: File
    private lateinit var server: SshServer
    private val client: SshClient = SshClient.setUpDefaultClient()
    @Volatile private var password: String? = Passwords.generate()

    @Before
    fun setUp() {
        root = tmp.newFolder("root")
        keysFile = File(tmp.root, "authorized_keys")
        val keys = AuthorizedKeys(keysFile)
        server = SshServerFactory.create(
            port = 0,
            root = root.toPath(),
            hostKeyFile = File(tmp.root, "hostkey.pem").toPath(),
            password = { password },
            authorizedKeys = { keys.publicKeys() },
            addAuthorizedKeys = keys::add,
        )
        server.start()
        client.start()
    }

    @After
    fun tearDown() {
        client.stop()
        server.stop(true)
    }

    private fun connect(): ClientSession =
        client.connect("anyone", "localhost", server.port).verify(TIMEOUT).session

    private fun passwordSession() = connect().apply {
        addPasswordIdentity(password!!)
        auth().verify(TIMEOUT)
    }

    private fun passwordAuth(pw: String): Boolean = connect().use { session ->
        session.addPasswordIdentity(pw)
        runCatching { session.auth().verify(TIMEOUT) }.isSuccess
    }

    @Test
    fun passwordLoginCanBeSwitchedWhileRunning() {
        val first = password!!
        assertTrue(passwordAuth(first))

        password = null
        assertFalse(passwordAuth(first))
        assertFalse(passwordAuth("anything"))

        // Re-enabling issues a new password; the old one stays dead.
        password = Passwords.generate()
        assertTrue(passwordAuth(password!!))
        if (password != first) assertFalse(passwordAuth(first))
    }

    @Test
    fun keyLoginWorksWithPasswordOff() {
        password = null
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        AuthorizedKeys(keysFile).add(PublicKeyEntry.toString(pair.public))
        assertTrue(keyAuth(pair))
    }

    @Test
    fun passwordAuthUploadListDownload() {
        passwordSession().use { session ->
            SftpClientFactory.instance().createSftpClient(session).use { sftp ->
                val data = ByteArray(1 shl 20) { it.toByte() }
                sftp.mkdir("/dir")
                sftp.write("/dir/file.bin").use { it.write(data) }

                assertArrayEquals(data, File(root, "dir/file.bin").readBytes())
                val names = sftp.readDir("/dir").map { it.filename }
                assertTrue("file.bin" in names)
                assertArrayEquals(data, sftp.read("/dir/file.bin").use { it.readBytes() })
            }
        }
    }

    @Test
    fun wrongPasswordRejected() {
        connect().use { session ->
            session.addPasswordIdentity("wrong")
            assertFalse(runCatching { session.auth().verify(TIMEOUT) }.isSuccess)
        }
    }

    @Test
    fun publicKeyAuth() {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        AuthorizedKeys(keysFile).add(PublicKeyEntry.toString(pair.public) + " me@laptop")

        assertTrue(keyAuth(pair))
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        assertFalse(keyAuth(other))
    }

    private fun keyAuth(pair: KeyPair): Boolean = connect().use { session ->
        session.keyIdentityProvider = KeyIdentityProvider.wrapKeyPairs(pair)
        runCatching { session.auth().verify(TIMEOUT) }.isSuccess
    }

    @Test
    fun cannotEscapeRoot() {
        File(tmp.root, "secret.txt").writeText("nope")
        passwordSession().use { session ->
            SftpClientFactory.instance().createSftpClient(session).use { sftp ->
                assertEquals("/", sftp.canonicalPath("/../.."))
                assertFalse(sftp.readDir("/..").any { it.filename == "secret.txt" })
            }
        }
    }

    @Test
    fun setAttributesDoesNotFail() {
        passwordSession().use { session ->
            SftpClientFactory.instance().createSftpClient(session).use { sftp ->
                sftp.write("/a.txt").use { it.write("hi".toByteArray()) }
                sftp.setStat("/a.txt", SftpClient.Attributes().perms(0b110_100_100).owner("nobody"))
            }
        }
    }

    @Test
    fun parsesPastedEd25519Key() {
        val line = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl user@host"
        val key = AuthorizedKeys.parse(line)
        assertEquals("ssh-ed25519", key.type)
        assertEquals("user@host", key.comment)
    }

    /** End-to-end with the real OpenSSH client and an ed25519 key, when available on this machine. */
    @Test
    fun openSshClientWithEd25519Key() {
        assumeTrue(File("/usr/bin/sftp").canExecute() && File("/usr/bin/ssh-keygen").canExecute())
        val key = File(tmp.root, "id_ed25519")
        run("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "test@pc", "-f", key.path)
        AuthorizedKeys(keysFile).add(File(key.path + ".pub").readText())

        val local = File(tmp.root, "upload.txt").apply { writeText("hello phone") }
        val batch = File(tmp.root, "batch").apply { writeText("put ${local.path} /up.txt\nls\n") }
        val opts = listOf(
            "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null",
            "-o", "BatchMode=yes", "-i", key.path, "-P", server.port.toString(),
        )
        run("sftp", *opts.toTypedArray(), "-b", batch.path, "pc@localhost")
        assertEquals("hello phone", File(root, "up.txt").readText())

        // scp (SFTP mode, the OpenSSH default) with -p, which tries to set times and modes.
        run("scp", "-p", *opts.toTypedArray(), "pc@localhost:/up.txt", File(tmp.root, "down.txt").path)
        assertEquals("hello phone", File(tmp.root, "down.txt").readText())
    }

    @Test
    fun sshCopyIdInstallsKey() {
        assumeTrue(File("/usr/bin/ssh-copy-id").canExecute() && File("/usr/bin/ssh-keygen").canExecute())
        val home = tmp.newFolder("home").also { File(it, ".ssh").mkdir() }
        val key = File(home, "id_ed25519")
        run("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "copied@pc", "-f", key.path)
        val askpass = File(home, "askpass.sh").apply {
            writeText("#!/bin/sh\necho '${password!!}'\n")
            setExecutable(true)
        }
        val env = mapOf(
            "HOME" to home.path,
            "SSH_ASKPASS" to askpass.path,
            "SSH_ASKPASS_REQUIRE" to "force",
            "DISPLAY" to ":0",
        )
        val copyId = arrayOf(
            "ssh-copy-id", "-i", key.path, "-p", server.port.toString(),
            "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null",
            "drop@localhost",
        )

        run(*copyId, env = env)
        assertEquals(listOf("copied@pc"), AuthorizedKeys(keysFile).load().map { it.comment })

        // Second run: ssh-copy-id's "exit" probe succeeds with the key, so nothing is added twice.
        assertTrue(run(*copyId, env = env).contains("already exist"))
        assertEquals(1, AuthorizedKeys(keysFile).load().size)

        // Key now works for SFTP without a password.
        val batch = File(home, "batch").apply { writeText("ls\n") }
        run(
            "sftp", "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null", "-o", "BatchMode=yes",
            "-i", key.path, "-P", server.port.toString(), "-b", batch.path, "drop@localhost",
        )
    }

    @Test
    fun otherCommandsAndShellRejected() {
        passwordSession().use { session ->
            val out = java.io.ByteArrayOutputStream()
            val result = runCatching { session.executeRemoteCommand("rm -rf /", out, Charsets.UTF_8) }
            assertTrue(result.isFailure)
            assertTrue(out.toString().contains("only supports SFTP"))
        }
        assertTrue(AuthorizedKeys(keysFile).load().isEmpty())
    }

    private fun run(vararg cmd: String, env: Map<String, String> = emptyMap()): String {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).apply { environment().putAll(env) }.start()
        p.outputStream.close()
        val out = p.inputStream.bufferedReader().readText()
        assertEquals("${cmd.joinToString(" ")}\n$out", 0, p.waitFor())
        return out
    }

    companion object {
        private val TIMEOUT = Duration.ofSeconds(10)
    }
}
