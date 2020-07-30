package win.sa4zet.ktor.config.server


import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.jetty.*
import io.ktor.util.*
import java.io.File
import java.util.*
import kotlin.system.exitProcess
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

data class CallInfo(
  val userName: String,
  val remoteHost: String,
  val configFileName: String,
  val configSubPath: String,
  val method: String,
  val parentDir: String,
  val inRaw: Boolean,
  val prettyPrint: Boolean
)

val contentTypeHocon = ContentType("application", "hocon")
val salt: String = System.getenv("config_server_basic_auth_salt") ?: ""

@KtorExperimentalAPI
val digestFunction = getDigestFunction("SHA-512") { salt }
var extraConfig: Config = ConfigFactory.parseFile(File(System.getenv("config_server_config"))).resolve()

val git = getGitRepository()


@KtorExperimentalAPI
fun main(args: Array<String>) {
  if (salt.isBlank()) {
    System.err.println("You have to set the 'config_server_basic_auth_salt' environment variable!")
    exitProcess(1)
  }
  if (args.isNotEmpty()) {
    if (args.first() == "digest") {
      var pwd: String?
      while (true) {
        print("Enter a not empty password: ")
        pwd = readLine()
        if (pwd == null) {
          println("\nBye!")
          return
        }
        if (pwd.isBlank()) continue
        else println(String(Base64.getEncoder().encode(digestFunction.invoke(pwd))))
      }
    }
  }
  EngineMain.main(args)
}

@InternalAPI
@KtorExperimentalAPI
@ExperimentalTime
fun Application.module() {
  val hashedUserTable = UserHashedTableAuth(
    table = extraConfig.getConfigList("auth.basic.users")
      .map { it.getString("userName") to it.getString("userHash").decodeBase64Bytes() }
      .toMap(),
    digester = digestFunction
  )

  install(CallLogging)
  install(DefaultHeaders) {
    header(HttpHeaders.Accept, contentTypeHocon.toString())
    header(HttpHeaders.Server, "Configuration Server")
  }
  install(CORS) {
    method(HttpMethod.Options)
    anyHost()
    maxAgeDuration = 1.toDuration(DurationUnit.MINUTES)
  }
  install(Compression) {
    gzip {
      priority = 1.0
    }
  }
  install(StatusPages) {
    exception<NotFoundException> {
      call.respond(HttpStatusCode.NotFound)
    }
    exception<BadRequestException> {
      call.respond(HttpStatusCode.BadRequest)
    }
    exception<Throwable> {
      application.log.error("ouch :(", it)
      call.respond(HttpStatusCode.InternalServerError)
      throw it
    }
  }
  install(Authentication) {
    basic("basic-auth") {
      realm = "Config Server"
      validate { hashedUserTable.authenticate(it) }
    }
  }
  install(Routing) {
    trace { application.log.trace(it.buildText()) }
    get("/health_check") {
      call.respondText("ok", ContentType.Text.Plain)
    }
    authenticate("basic-auth") {
      get("/cfg/{path...}") {
        val callInfo = getCallInfo(call)
        call.respondText(
          getValue(callInfo), if (callInfo.inRaw) contentTypeHocon else ContentType.Application.Json
        )
      }
    }
    static("/") {
      resource("/favicon.ico", "favicon.svg", "static")
      staticBasePackage = "static"
      resources("/")
    }
  }
}

private fun getValue(callInfo: CallInfo): String {
  var cfg = parseConfigFile(callInfo)
  if (!callInfo.inRaw) cfg = cfg.resolve()
  if (callInfo.configSubPath.isNotEmpty()) {
    if (!cfg.hasPath(callInfo.configSubPath)) throw NotFoundException()
    val value = cfg.getAnyRef(callInfo.configSubPath)
    return if (value is HashMap<*, *>) {
      renderConfig(cfg.getObject(callInfo.configSubPath), callInfo.inRaw, callInfo.prettyPrint)
    } else {
      value.toString()
    }
  }
  return renderConfig(cfg, callInfo.inRaw, callInfo.prettyPrint)
}

private fun parseConfigFile(callInfo: CallInfo): Config {
  return ConfigFactory.parseFile(gitGetConfigFile(parentDir = callInfo.parentDir, fileName = callInfo.configFileName))
}

private fun getCallInfo(call: ApplicationCall): CallInfo {
  val principal: UserIdPrincipal? = call.authentication.principal()
  val path = call.parameters.getAll("path")!!
  if (path.isEmpty()) throw BadRequestException("")
  val callInfo = CallInfo(
    userName = principal!!.name,
    remoteHost = call.request.local.remoteHost,
    parentDir = path[0],
    configFileName = path[1],
    configSubPath = path.drop(2).joinToString("."),
    method = call.request.httpMethod.value,
    inRaw = call.request.queryParameters.contains("inRaw"),
    prettyPrint = call.request.queryParameters.contains("prettyPrint")
  )
  call.application.log.info("${callInfo.userName} tried to ${callInfo.method} ${callInfo.configFileName}{${callInfo.configSubPath}} from ${callInfo.remoteHost}")
  return callInfo
}

