package win.sa4zet.ktor.config.server

import io.ktor.features.NotFoundException
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

val local_dir: Path = Paths.get(extraConfig.getString("git.local.dir"))
val local: File = local_dir.resolve(".git").toFile()
val git = getGitRepository()

private fun getGitRepository(): Git {
  if (local.exists()) {
    Git.open(local).pull().setRebase(true).call()
  } else {
    Git.cloneRepository()
      .setURI(extraConfig.getString("git.remote.url"))
      .setDirectory(Paths.get(extraConfig.getString("git.local.dir")).toFile())
      .call()
  }
  return Git(FileRepositoryBuilder.create(local))
}

fun gitGetConfigFile(name: String, createIfNotExist: Boolean): File {
  git.pull().setRebase(true).call()
  val file = git.repository.workTree.resolve("$name.conf").absoluteFile
  if (!file.exists()) if (createIfNotExist) file.createNewFile() else throw NotFoundException("$name config not found!")
  return file
}
