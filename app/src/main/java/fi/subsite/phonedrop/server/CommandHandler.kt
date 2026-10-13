package fi.subsite.phonedrop.server

import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import java.io.InputStream
import java.io.OutputStream

/**
 * There is no shell. The only commands understood are the two that `ssh-copy-id` sends, so keys
 * can be installed from the PC with `ssh-copy-id -p 2222 drop@<ip>`:
 *  - `exit`: probe used to skip keys that already work (reaching it means the key is installed).
 *  - `exec sh -c '... cat >> .ssh/authorized_keys ...'`: keys arrive on stdin.
 * Anything else, including an interactive shell, gets a short explanation and exit status 1.
 */
class CommandHandler private constructor(
    private val action: Action,
    private val addKeys: (String) -> Int,
) : Command {
    private enum class Action { EXIT, INSTALL_KEYS, REJECT }

    private lateinit var input: InputStream
    private lateinit var output: OutputStream
    private lateinit var error: OutputStream
    private lateinit var exit: ExitCallback

    override fun setInputStream(input: InputStream) { this.input = input }
    override fun setOutputStream(output: OutputStream) { this.output = output }
    override fun setErrorStream(error: OutputStream) { this.error = error }
    override fun setExitCallback(callback: ExitCallback) { exit = callback }

    override fun start(channel: ChannelSession, env: Environment) {
        when (action) {
            Action.EXIT -> exit.onExit(0)
            Action.REJECT -> fail(REJECT_MESSAGE)
            // Reading stdin blocks until the client closes it, so do it off the I/O thread.
            Action.INSTALL_KEYS -> Thread({ installKeys() }, "PhoneDrop-copy-id").start()
        }
    }

    private fun installKeys() {
        val text = input.readBytes().toString(Charsets.UTF_8)
        runCatching { addKeys(text) }
            .onSuccess { exit.onExit(0) }
            .onFailure { fail(it.message ?: "Could not add key") }
    }

    private fun fail(message: String) {
        runCatching {
            error.write("$message\n".toByteArray())
            error.flush()
        }
        exit.onExit(1)
    }

    override fun destroy(channel: ChannelSession) {}

    companion object {
        private const val REJECT_MESSAGE =
            "PhoneDrop only supports SFTP. Connect with sftp, scp, rsync, sshfs or a file manager (sftp://)."

        fun forCommand(command: String, addKeys: (String) -> Int): Command {
            val trimmed = command.trim()
            val action = when {
                trimmed == "exit" -> Action.EXIT
                trimmed.startsWith("exec sh -c ") && "authorized_keys" in trimmed -> Action.INSTALL_KEYS
                else -> Action.REJECT
            }
            return CommandHandler(action, addKeys)
        }

        fun forShell(): Command = CommandHandler(Action.REJECT) { 0 }
    }
}
