package evented

import java.util.UUID

interface Command

// create a new user when the specified email address is not yet in the State
data class RegisterUser(
    val email: String,
    val password: String,
    val born: String
): Command

// from within login process:
data class CreateToken(
    val email: String,
    val token: String
): Command

data class Logout(
    val token: String
): Command

data class CreatePost(
    val title: String,
    val content: String
): Command

data class UpdatePost(
    val id: UUID,
    val title: String,
    val body: String
): Command
