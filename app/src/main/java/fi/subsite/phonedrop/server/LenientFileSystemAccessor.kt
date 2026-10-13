package fi.subsite.phonedrop.server

import org.apache.sshd.sftp.server.SftpFileSystemAccessor
import org.apache.sshd.sftp.server.SftpSubsystemProxy
import java.io.IOException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.PosixFilePermission
import java.security.Principal

/**
 * Android shared storage is FUSE-backed and rejects chmod/chown (and sometimes utimes).
 * Clients like Nautilus, rsync and scp -p set these after copying, so a failure here would
 * make a successful transfer look failed. Ignore attribute-setting errors instead.
 */
object LenientFileSystemAccessor : SftpFileSystemAccessor {
    private inline fun ignoreFailure(block: () -> Unit) {
        try {
            block()
        } catch (_: IOException) {
        } catch (_: UnsupportedOperationException) {
        }
    }

    override fun setFileAttribute(
        subsystem: SftpSubsystemProxy, file: Path, view: String, attribute: String,
        value: Any?, vararg options: LinkOption,
    ) = ignoreFailure { super.setFileAttribute(subsystem, file, view, attribute, value, *options) }

    override fun setFileOwner(
        subsystem: SftpSubsystemProxy, file: Path, value: Principal?, vararg options: LinkOption,
    ) = ignoreFailure { super.setFileOwner(subsystem, file, value, *options) }

    override fun setGroupOwner(
        subsystem: SftpSubsystemProxy, file: Path, value: Principal?, vararg options: LinkOption,
    ) = ignoreFailure { super.setGroupOwner(subsystem, file, value, *options) }

    override fun setFilePermissions(
        subsystem: SftpSubsystemProxy, file: Path, perms: Set<PosixFilePermission>?, vararg options: LinkOption,
    ) = ignoreFailure { super.setFilePermissions(subsystem, file, perms, *options) }

    override fun setFileAccessControl(
        subsystem: SftpSubsystemProxy, file: Path, acl: List<AclEntry>?, vararg options: LinkOption,
    ) = ignoreFailure { super.setFileAccessControl(subsystem, file, acl, *options) }
}
