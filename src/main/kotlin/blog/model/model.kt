package blog.model

import io.hypersistence.tsid.TSID
import java.io.Serializable
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.format.DateTimeFormatter
import java.util.Locale

interface CborSerializable
interface Command : CborSerializable
interface Event   : CborSerializable
interface Entity  : CborSerializable {
  val id: TSID
}
interface Response : Serializable {
  val id: String
}

object Hasher {
  private val md = MessageDigest.getInstance("SHA-256")
  private fun toHex(ba: ByteArray) = ba.joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
  fun hash(s: String): String = toHex(md.digest(s.toByteArray(StandardCharsets.UTF_8)))
}

val DTF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private val idFactory = TSID.Factory.builder()
  .withRandom(SecureRandom.getInstance("SHA1PRNG", "SUN"))
  .withNodeBits(8)
  .withNode(InetAddress.getLocalHost().address[3].toInt()and(0xFF)).build()
fun nextId(): TSID = idFactory.generate()

fun slugify(s: String): String = s.trim().replace("  ", " ").lowercase().replace("[^ a-z0-9]".toRegex(), "").replace(' ', '-')

fun String.toTSID(): TSID = TSID.from(this)
