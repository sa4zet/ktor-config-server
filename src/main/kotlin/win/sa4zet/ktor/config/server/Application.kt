package win.sa4zet.ktor.config.server


import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.resource
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.http.content.staticBasePackage
import io.ktor.request.httpMethod
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import io.ktor.server.jetty.EngineMain
import io.ktor.util.InternalAPI
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.getDigestFunction
import java.io.File
import java.util.*
import kotlin.system.exitProcess
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.toDuration

data class CallInfo(
  val userName: String,
  val remoteHost: String,
  val config: String,
  val configPath: String,
  val method: String,
  val hocon: String,
  val inJson: Boolean,
  val prettyPrint: Boolean,
  val resolve: Boolean
)

class ForbiddenException(override val message: String) : kotlin.Exception(message)

val contentTypeHocon = ContentType("application", "hocon")
val salt: String = System.getenv("config_server_basic_auth_salt") ?: ""

@KtorExperimentalAPI
val digestFunction = getDigestFunction("SHA-512") { salt }
var extraConfig: Config = ConfigFactory.empty()

@KtorExperimentalAPI
fun main(args: Array<String>) {
  if (salt.isBlank()) {
    System.err.println("You have to set the 'config_server_basic_auth_salt' environment variable!")
    exitProcess(1)
  }
  if (args.isEmpty() || (args.first() != "digest" && !args.first().startsWith("-config="))) {
    System.err.println("You have to add a digest command or a -config=YOUR_CFG_FILE command line argument!")
    exitProcess(1)
  }
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
  extraConfig = ConfigFactory.parseFile(File(args.first().substring(8))).resolve()
  EngineMain.main(emptyArray())
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
    exception<ForbiddenException> {
      call.respond(HttpStatusCode.Forbidden, it.message)
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
    authenticate("basic-auth") {
      get("/cfg/{path...}") {
        val callInfo = getCallInfo(call)
        call.respondText(
          getValue(callInfo)
          , if (callInfo.inJson) ContentType.Application.Json else contentTypeHocon
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

fun getValue(callInfo: CallInfo): String {
  var cfg = parseConfigFile(callInfo.config, false)
  if (callInfo.resolve) cfg = cfg.resolve()
  if (callInfo.configPath.isNotEmpty()) {
    if (!cfg.hasPath(callInfo.configPath)) throw NotFoundException()
    val value = cfg.getAnyRef(callInfo.configPath)
    return if (value is HashMap<*, *>) {
      renderConfig(cfg.getObject(callInfo.configPath), callInfo.inJson, callInfo.prettyPrint)
    } else {
      value.toString()
    }
  }
  return renderConfig(cfg, callInfo.inJson, callInfo.prettyPrint)
}

fun parseConfigFile(fileName: String, createIfNotExist: Boolean): Config {
  return ConfigFactory.parseFile(gitGetConfigFile(fileName, createIfNotExist))
}

suspend fun getCallInfo(call: ApplicationCall): CallInfo {
  val principal: UserIdPrincipal? = call.authentication.principal()
  val path = call.parameters.getAll("path")!!
  if (path.isEmpty()) throw BadRequestException("")
  val callInfo = CallInfo(
    userName = principal!!.name,
    remoteHost = call.request.local.remoteHost,
    config = path.first(),
    configPath = path.drop(1).joinToString("."),
    method = call.request.httpMethod.value,
    hocon = call.receive(),
    inJson = call.request.queryParameters.contains("inJson"),
    prettyPrint = call.request.queryParameters.contains("prettyPrint"),
    resolve = call.request.queryParameters.contains("resolve")
  )
  call.application.log.info("${callInfo.userName} tried to ${callInfo.method} ${callInfo.config}{${callInfo.configPath}} from ${callInfo.remoteHost}")
  return callInfo
}