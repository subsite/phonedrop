package fi.subsite.phonedrop.server

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.shell.ShellFactory
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import java.nio.file.Path
import java.security.MessageDigest
import java.security.PublicKey

/**
 * Builds the SFTP-only SSH server. Kept free of Android APIs so it can be exercised in JVM unit tests.
 */
object SshServerFactory {
    const val DEFAULT_PORT = 2222

    fun create(
        port: Int,
        root: Path,
        hostKeyFile: Path,
        /** Current session password, or null while password login is switched off. Read on every attempt. */
        password: () -> String?,
        authorizedKeys: () -> Collection<PublicKey>,
        addAuthorizedKeys: (String) -> Int,
    ): SshServer {
        return SshServer.setUpDefaultServer().apply {
            this.port = port
            keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile).apply {
                algorithm = "EC"
                keySize = 256
            }
            // Any username is accepted; the per-session password or a registered key is what matters.
            setPasswordAuthenticator { _, pw, _ ->
                val expected = password() ?: return@setPasswordAuthenticator false
                MessageDigest.isEqual(expected.toByteArray(), pw.toByteArray())
            }
            setPublickeyAuthenticator { _, key, _ ->
                authorizedKeys().any { it == key }
            }
            // No shell or exec: only SFTP, plus the commands ssh-copy-id uses to install keys.
            commandFactory = CommandFactory { _, command -> CommandHandler.forCommand(command, addAuthorizedKeys) }
            shellFactory = ShellFactory { CommandHandler.forShell() }
            subsystemFactories = listOf(
                SftpSubsystemFactory.Builder()
                    .withFileSystemAccessor(LenientFileSystemAccessor)
                    .build()
            )
            fileSystemFactory = VirtualFileSystemFactory(root)
        }
    }
}
