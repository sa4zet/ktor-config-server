package win.sa4zet.ktor.config.server

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigRenderOptions


fun renderConfig(config: Config, inJson: Boolean = false, prettyPrint: Boolean = true): String {
  return renderConfig(config.root(), inJson, prettyPrint)
}

fun renderConfig(configObject: ConfigObject, inJson: Boolean, prettyPrint: Boolean): String {
  return configObject.render(ConfigRenderOptions.concise().setJson(inJson).setFormatted(prettyPrint))
}
