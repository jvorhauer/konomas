package blog

import org.springframework.test.web.reactive.server.WebTestClient

fun WebTestClient.get(uri: String): WebTestClient.RequestHeadersSpec<*> = this.get().uri(uri)
fun WebTestClient.post(uri: String) = this.post().uri(uri)
