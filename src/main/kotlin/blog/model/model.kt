package blog.model

import io.hypersistence.tsid.TSID
import java.io.Serializable
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

interface Request : Serializable
interface Command : Serializable
interface Event   : Serializable
interface Entity  : Serializable {
  val id: Long
}
interface Response : Serializable {
  val id: Long
}

object Hasher {
  private val md = MessageDigest.getInstance("SHA-256")
  private fun toHex(ba: ByteArray) = ba.joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
  fun hash(s: String): String = toHex(md.digest(s.toByteArray(StandardCharsets.UTF_8)))
}
fun gravatarize(s: String): String = s.trim().lowercase().hashed()

val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
fun now(): LocalDateTime = LocalDateTime.now()

private val idFactory = TSID.Factory.builder()
  .withRandom(SecureRandom.getInstance("SHA1PRNG", "SUN"))
  .withNodeBits(8)
  .withNode(InetAddress.getLocalHost().address[3].toInt()and(0xFF)).build()
fun nextId(): Long = idFactory.generate().toLong()

fun slugify(s: String): String = s.trim().replace("  ", " ").lowercase().replace("[^ a-z0-9]".toRegex(), "").replace(' ', '-')

fun Long.toTSID(): TSID = TSID.from(this)
fun String.hashed() = Hasher.hash(this)

data class Counts(
  val users: Int,
  val notes: Int,
  val tasks: Int
)

data class Konfig(
  val secret: String,
  val audience: String,
  val realm: String,
  val issuer: String,
  val expiresIn: Long = 1000L * 60L * 60L * 24L
)

val timeout: Duration = Duration.ofSeconds(10)
fun user(call: ApplicationCall): Long? = call.principal<JWTPrincipal>()?.payload?.getClaim("uid")?.asLong()
