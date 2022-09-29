package blog

import org.owasp.encoder.Encode
import org.springframework.data.cassandra.core.InsertOptions
import org.springframework.data.cassandra.core.ReactiveCassandraOperations
import org.springframework.data.cassandra.core.mapping.PrimaryKey
import org.springframework.data.cassandra.core.mapping.Table
import org.springframework.data.cassandra.core.query.Query
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.valiktor.functions.doesNotContain
import org.valiktor.functions.isEmail
import org.valiktor.functions.isNotBlank
import org.valiktor.validate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Table("users")
data class User(
    @PrimaryKey
    val email: String,
    val name: String,
    val joined: LocalDate = LocalDate.now(),
    val born: LocalDate,
    val password: String
) {
    fun toResponse(): UserResponse = UserResponse(
        Encode.forHtml(email), Encode.forHtml(name), LDF.format(joined), listOf()
    )
}

val LDF: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")


data class CreateUser(
    val email: String,
    val name: String,
    val password: String,
    val born: String
) {
    init {
        validate(this) {
            validate(CreateUser::email).isNotBlank().isEmail()
            validate(CreateUser::name).isNotBlank()
            validate(CreateUser::password).isNotBlank()
                  .doesNotContain(name)
            validate(CreateUser::born).isNotBlank().isLocalDate()
        }
    }

    fun toUser(): User = User(
        email, name, born = LocalDate.parse(born, LDF), password = hash(password)
    )
}

data class UserResponse(
    val email: String,
    val name: String,
    val joined: String,
    val posts: List<PostResponse>
)

class UserRepo(private val ops: ReactiveCassandraOperations) {
    fun findById(id: String): Mono<User> = ops.selectOneById(id, userC)
    fun save(u: User): Mono<User> = ops.insert(u)
    fun findAll(): Flux<User> = ops.select(Query.empty(), userC)
}


@Table("tokens")
data class Token(
    @PrimaryKey
    val token: String,
    val email: String,
    val created: LocalDateTime = ZonedDateTime.now(ZoneId.of("CET")).toLocalDateTime()
) {
    companion object {
        val klass = Token::class.java
    }
}

internal data class UserCredentials(
    val email: String,
    val password: String
)

private val userC = User::class.java

class TokenRepo(private val ops: ReactiveCassandraOperations) {
    fun findById(id: String): Mono<Token> = ops.selectOneById(id, Token::class.java)
    fun save(t: Token, ttl: Int): Mono<Token> = ops.insert<Token>(t, ttl(ttl)).map { it.entity }
    fun deleteById(id: String): Mono<Boolean> = ops.deleteById(id, Token::class.java)

    private fun ttl(i: Int): InsertOptions = InsertOptions.builder().ttl(i).build()
}


class UserHandler(
    private val userRepo: UserRepo,
    private val tokenRepo: TokenRepo
) {
    fun register(req: ServerRequest): Mono<ServerResponse> = req
          .bodyToMono(CreateUser::class.java)
          .map(CreateUser::toUser)
          .flatMap { userRepo.findById(it.email).switchIfEmpty(userRepo.save(it)) }
          .map(User::toResponse)
          .flatMap(ok()::bodyValue)

    fun login(req: ServerRequest): Mono<ServerResponse> = req
          .bodyToMono(UserCredentials::class.java)
          .flatMap {
              userRepo.findById(it.email)
                    .filter { res -> hash(it.password) == res.password }
          }
          .flatMap {
              tokenRepo.save(Token(hash(UUID.randomUUID().toString()), Encode.forHtml(it.email)), 3600)
          }
          .flatMap(ok()::bodyValue)
          .switchIfEmpty(badRequest().build())

    fun authenticate(req: ServerRequest): Mono<Tuple2<User, ServerRequest>> =
        Mono.just(req.headers().header("Authorization"))
              .filter { it.size > 0 }
              .flatMap { tokenRepo.findById(it[0]) }
              .flatMap { userRepo.findById(it.email) }
              .flatMap { Mono.zip(Mono.just(it), Mono.just(req)) }

    fun logout(req: ServerRequest): Mono<ServerResponse> =
        authenticate(req)
              .flatMap { tokenRepo.deleteById(it.t1.email) }
              .flatMap { ServerResponse.status(HttpStatus.NO_CONTENT).build() }
              .switchIfEmpty(ServerResponse.status(HttpStatus.NOT_FOUND).build())


    fun all(req: ServerRequest): Mono<ServerResponse> =
        ok().body(userRepo.findAll().map(User::toResponse), UserResponse::class.java)

    fun one(req: ServerRequest): Mono<ServerResponse> =
        req.monoPathVar("id")
              .filter { it.isEmail() }
              .flatMap { userRepo.findById(it).map(User::toResponse) }
              .flatMap { ok().bodyValue(it) }
              .switchIfEmpty(ServerResponse.status(HttpStatus.BAD_REQUEST).build())
}

private val md = MessageDigest.getInstance("SHA-256")
private fun toHex(ba: ByteArray) = ba.joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
fun hash(s: String): String = toHex(md.digest(s.toByteArray(StandardCharsets.UTF_8)))
