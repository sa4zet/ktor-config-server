package win.sa4zet.ktor.config.server

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.JSch
import io.ktor.features.*
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.util.Base64
import org.eclipse.jgit.util.FS
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

fun getGitRepository(): Git {
  SshSessionFactory.setInstance(object : JschConfigSessionFactory() {
    override fun createDefaultJSch(fs: FS?): JSch {
      val jsch = super.createDefaultJSch(fs)
      jsch.removeAllIdentity()
      jsch.addIdentity("private-key", extraConfig.getString("git.remote.privateKey").trimIndent().toByteArray(), null, null)
      extraConfig.getStringList("git.remote.hostKeys").forEach {
        jsch.hostKeyRepository.add(HostKey(extraConfig.getString("git.remote.host"), Base64.decode(it.trimIndent())), null)
      }
      return jsch
    }
  })
  val localDir: Path = Paths.get(extraConfig.getString("git.local.dir"))
  val local: File = localDir.resolve(".git").toFile()
  if (local.exists()) {
    Git.open(local).pull()
      .setTransportConfigCallback {
        val sshTransport: SshTransport = it as SshTransport
        sshTransport.sshSessionFactory = SshSessionFactory.getInstance()
      }
      .setRebase(true)
      .call()
  } else {
    Git.cloneRepository()
      .setURI(extraConfig.getString("git.remote.url"))
      .setDirectory(localDir.toFile())
      .setTransportConfigCallback {
        val sshTransport: SshTransport = it as SshTransport
        sshTransport.sshSessionFactory = SshSessionFactory.getInstance()
      }
      .call()
  }
  return Git(FileRepositoryBuilder.create(local))
}

fun gitGetConfigFile(parentDir: String, fileName: String): File {
  git
    .pull()
    .setTransportConfigCallback {
      val sshTransport: SshTransport = it as SshTransport
      sshTransport.sshSessionFactory = SshSessionFactory.getInstance()
    }
    .setRebase(true)
    .call()
  val file = git.repository.workTree.resolve(parentDir).resolve("$fileName.conf").absoluteFile
  if (!file.exists()) throw NotFoundException("$fileName config not found!")
  return file
}
