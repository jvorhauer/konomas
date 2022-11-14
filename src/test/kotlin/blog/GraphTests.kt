package blog

import blog.Constants.rnote
import blog.Constants.ruser
import org.assertj.core.api.Assertions.assertThat
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DefaultWeightedEdge
import org.jgrapht.graph.DirectedWeightedPseudograph
import org.jgrapht.graph.SimpleGraph
import org.junit.jupiter.api.Test
import java.net.URI


class GraphTests {

  @Test
  fun `create graph`() {
    val g = VGraph<String>()
    g.addVertex("first")
    g.addVertex("second")
    g.addVertex("third")

    g.addEdge("first", "second", true)
    g.addEdge("second", "third", false)
    g.addEdge("third", "first", false)
    g.addEdge("second", "fourth", false)

    assertThat(g.map.size).isEqualTo(4)
    assertThat(g.vertexCount()).isEqualTo(4)
    assertThat(g.edgeCount()).isEqualTo(5)

    assertThat(g.containsVertex("first")).isTrue
    assertThat(g.containsVertex("oink")).isFalse
    assertThat(g.containsEdge("first", "second")).isTrue
    assertThat(g.containsEdge("second", "first")).isTrue
    assertThat(g.containsEdge("third", "second")).isFalse
  }

  @Test
  fun `jgrapht simplegraph`() {
    val g = SimpleGraph<String, DefaultEdge>(DefaultEdge::class.java)
    val v1 = "v1"
    val v2 = "v2"
    val v3 = "v3"
    val v4 = "v4"
    g.addVertex(v1)
    g.addVertex(v2)
    g.addVertex(v3)
    g.addVertex(v4)
    g.addEdge(v1, v2)
    g.addEdge(v2, v3)
    g.addEdge(v3, v4)
    g.addEdge(v4, v1)
    assertThat(g.toString()).isEqualTo("([v1, v2, v3, v4], [{v1,v2}, {v2,v3}, {v3,v4}, {v4,v1}])")
  }

  @Test
  fun `jgrapht directed graph`() {
    val g = DefaultDirectedGraph<URI, DefaultEdge>(DefaultEdge::class.java)
    val v1 = URI("https://www.google.com")
    val v2 = URI("https://www.wikipedia.org")
    val v3 = URI("https://www.jgrapht.org")
    g.addVertex(v1)
    g.addVertex(v2)
    g.addVertex(v3)
    g.addEdge(v3, v2)
    g.addEdge(v1, v3)
    g.addEdge(v1, v2)
    g.addEdge(v2, v1)
    assertThat(g.toString()).isEqualTo("([https://www.google.com, https://www.wikipedia.org, https://www.jgrapht.org], [(https://www.jgrapht.org,https://www.wikipedia.org), (https://www.google.com,https://www.jgrapht.org), (https://www.google.com,https://www.wikipedia.org), (https://www.wikipedia.org,https://www.google.com)])")
  }

  @Test
  fun `directed weighted pseudo graph`() {
    // Preferred Graph type: has self-loops, multiple edges and weigted edges
    val g = DirectedWeightedPseudograph<String, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
    val v1 = "v1"
    val v2 = "v2"
    val v3 = "v3"
    val v4 = "v4"
    g.addVertex(v1)
    g.addVertex(v2)
    g.addVertex(v3)
    g.addVertex(v4)
    g.addEdge(v1, v2)
    g.addEdge(v2, v3)
    g.addEdge(v3, v4)
    g.addEdge(v4, v1)
//    println(g.toString())
    assertThat(g.toString()).isEqualTo("([v1, v2, v3, v4], [(v1,v2), (v2,v3), (v3,v4), (v4,v1)])")
  }

  @Test
  fun `graph of events`() {
    val g = DirectedWeightedPseudograph<Event, DefaultWeightedEdge>(DefaultWeightedEdge::class.java)
    val v1 = ruser("v1")
    val v2 = ruser("v2")
    val v3 = ruser("v3")
    val v4 = ruser("v4")
    g.addVertex(v1)
    g.addVertex(v2)
    g.addVertex(v3)
    g.addVertex(v4)

    val n1 = rnote(v1.id, "n1")
    val n2 = rnote(v1.id, "n2")
    g.addVertex(n1)
    g.addVertex(n2)
    g.addEdge(v1, n1)
    g.addEdge(v1, n2)


  }
}
