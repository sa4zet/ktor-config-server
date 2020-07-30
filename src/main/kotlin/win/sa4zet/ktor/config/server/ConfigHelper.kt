package win.sa4zet.ktor.config.server

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions


fun renderConfig(config: Config, inRaw: Boolean = false, prettyPrint: Boolean = true): String {
  return renderConfig(config.root(), inRaw, prettyPrint)
}

fun renderConfig(configObject: ConfigObject, inRaw: Boolean, prettyPrint: Boolean): String {
  return configObject.render(ConfigRenderOptions.concise().setJson(!inRaw).setFormatted(prettyPrint))
}
