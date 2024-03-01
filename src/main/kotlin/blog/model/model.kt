package blog.model

import java.io.Serializable
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import io.hypersistence.tsid.TSID
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import blog.model.Constants.idNode
import blog.model.Constants.mdi
import blog.model.Constants.randm

interface Request : Serializable

interface Command : Serializable

interface Event   : Serializable {
  val received: ZonedDateTime
}

interface Entity  : Serializable {
  val id: String
}

interface Response : Serializable {
  val id: String
}

object Constants {
  val randm: SecureRandom = SecureRandom.getInstance("SHA1PRNG", "SUN")
  val idNode: Int = InetAddress.getLocalHost().address[3].toInt()and(0xFF)
  val mdi: MessageDigest = MessageDigest.getInstance("SHA-256")
}

object Hasher {
  private fun toHex(ba: ByteArray) = ba.joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
  fun hash(s: String): String = toHex(mdi.digest(s.toByteArray(StandardCharsets.UTF_8)))
}
val String.hashed: String get() = Hasher.hash(this)
val String.gravatar: String get() = this.trim().lowercase().hashed

val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
val ZonedDateTime.fmt: String get() = DTF.format(this)
val LocalDateTime.fmt: String get() = DTF.format(this)

val CET: ZoneId = ZoneId.of("CET")
val inow: Instant get() = Instant.now()
val znow: ZonedDateTime get() = inow.atZone(CET)
val now: LocalDateTime get() = LocalDateTime.ofInstant(inow, CET)

private val idFactory = TSID.Factory.builder().withRandom(randm).withNodeBits(8).withNode(idNode).build()
val nextTSID: TSID get() = idFactory.generate()
val nextId: String get() = nextTSID.toString()

fun slugify(s: String): String = s.trim().replace("  ", " ").lowercase().replace("[^ a-z0-9]".toRegex(), "").replace(' ', '-')
val String.slug: String get() = slugify(this)

fun doEncode(str: String): String = str.replace('<', '[').replace('>', ']')
fun doMEncode(str: String?): String? = if (str != null) doEncode(str) else null
val String.encode: String get() = doEncode(this)
val String?.mencode: String? get() = doMEncode(this)

fun equals(e: Entity, a: Any?): Boolean = e === a || (e.javaClass == a?.javaClass && e.id == (a as Entity).id)

val timeout: Duration = Duration.ofSeconds(10)
fun userIdFromJWT(call: ApplicationCall): String? = call.principal<JWTPrincipal>()?.payload?.getClaim("uid")?.asString()
