package blog.config

data class Jwt(
  val secret: String,
  val audience: String,
  val realm: String,
  val issuer: String,
  val expiresIn: Long = 1000L * 60L * 60L * 24L
)

data class Server(
  val port: Int,
  val host: String
)

data class Konfig(
  val jwt: Jwt,
  val server: Server
)
