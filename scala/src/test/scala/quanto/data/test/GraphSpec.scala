package quanto.data.test

import org.scalatest._
import quanto.data._
import quanto.data.Names._
import quanto.util.json._



class GraphSpec extends FlatSpec with GivenWhenThen {
  behavior of "A graph"
  
  var g : Graph = _
  
  it can "initialize" in {
    g = new Graph()
  }

  var v0 : VName = _
  var v1 : VName = _
  var e0 : EName = _
  var e1 : EName = _
  var bb0 : BBName = _
  var bb1 : BBName = _

  it should "get fresh names for newVertex" in {
    g.newVertex(NodeV()) match {case (g1,v) => g = g1; v0 = v}
    assert(v0 === VName("v0"))

    g.newVertex(NodeV()) match {case (g1,v) => g = g1; v1 = v}
    assert(v1 === VName("v1"))
  }

  it should "get fresh names for newEdge" in {
    g.newEdge(DirEdge(), v0 -> v1) match {case (g1,e) => g = g1; e0 = e}
    assert(e0 === EName("e0"))

    g.newEdge(DirEdge(), v1 -> v1) match {case (g1,e) => g = g1; e1 = e}
    assert(e1 === EName("e1"))
  }

  it should "get fresh names for newBBox" in {
    g.newBBox(BBData()) match {case (g1,b) => g = g1; bb0 = b}
    assert(bb0 === BBName("bb0"))

    g.newBBox(BBData(), Set(v0), Some(bb0)) match {case (g1,b) => g = g1; bb1 = b}
    assert(bb1 === BBName("bb1"))
  }

  it should "contain 2 vertices, edges, and bboxes" in {
    assert(g.vdata.size === 2)
    assert(g.edata.size === 2)
    assert(g.bbdata.size === 2)
  }

  it should "throw dulicate name exceptions" in {
    intercept[DuplicateVertexNameException] {
      g.addVertex("v0", NodeV())
    }

    intercept[DuplicateEdgeNameException] {
      g.addEdge("e0", DirEdge(), "v0" -> "v1")
    }

    intercept[DuplicateBBoxNameException] {
      g.addBBox("bb0", BBData())
    }
  }

  it should "be equal to its copy" in {
    val g1 = g.copy()
    assert(g1 === g)
  }

  behavior of "Another graph"

  var otherG : Graph = _

  it can "be constructed in block form" in {
    // implicit conversions are used to make strings into names, where
    // necessary.
    otherG = (new Graph()
      addVertex ("v0", NodeV())
      addVertex ("v1", WireV())
      addVertex ("v2", NodeV())
      addEdge   ("e0", DirEdge(), "v0" -> "v0")
      addEdge   ("e1", UndirEdge(), "v0" -> "v1")
      addEdge   ("e2", DirEdge(), "v1" -> "v2")
      newEdge   (DirEdge(), "v0" -> "v1") // fresh name is returned, but dropped by implicit conversion
      addBBox   ("bb0", BBData(), Set("v0", "v1"))
      addBBox   ("bb1", BBData(), Set("v2"), parent = Some("bb0"))
    )

    println(otherG.toString)
  }

  it should "not be equal to the first graph" in {
    assert(g != otherG)
  }

  val jsonString =
    """
      |{
      |  "wire_vertices": ["w0", "w1", "w2"],
      |  "node_vertices": {
      |    "n0":{"annotation": {"coord": [1.0,2.0]}},
      |    "n1":{}
      |  },
      |  "dir_edges": {
      |    "e0": {"src": "w0", "tgt": "w1"},
      |    "e1": {"src": "w1", "tgt": "w2"}
      |  },
      |  "undir_edges": {
      |    "e2": {"src": "n0", "tgt": "n1"}
      |  },
      |  "bang_boxes": {
      |    "bb0": {"contains": ["n0", "n1", "w0"]},
      |    "bb1": {"contains": ["n0", "n1"], "parent": "bb0"},
      |    "bb2": {}
      |  }
      |}
    """.stripMargin

  val jsonGraphShouldBe = (Graph()
    addVertex("w0", WireV()) addVertex("w1", WireV()) addVertex("w2", WireV())
    addVertex("n0", NodeV(coord=(1.0,2.0)))
    addVertex("n1", NodeV())
    addEdge("e0", DirEdge(), "w0" -> "w1")
    addEdge("e1", DirEdge(), "w1" -> "w2")
    addEdge("e2", UndirEdge(), "n0" -> "n1")
    addBBox("bb0", BBData(), Set[VName]("w0", "n0", "n1"))
    addBBox("bb1", BBData(), Set[VName]("n0", "n1"), parent=Some(BBName("bb0")))
    addBBox("bb2", BBData())
  )

  var jsonGraph: Graph = _

  it can "be constructed from JSON" in {
    jsonGraph = Graph.fromJson(jsonString)
  }

  it should "be equal to a graph from the same JSON" in {
    val jsonGraph1 = Graph.fromJson(jsonString)
    assert(jsonGraph === jsonGraph1)
  }

  it should "be the expected graph" in {
    assert(jsonGraphShouldBe === jsonGraph)
  }

  // note this test is sensitive to having "normalised" JSON input, e.g. arrays that represent name sets are
  // alphabetised.
  it should "save to the correct json" in {
    val json = Graph.toJson(jsonGraph)
    assert(json === Json.parse(jsonString))
  }

  it should "save then load to the same graph" in {
    assert(jsonGraphShouldBe === Graph.fromJson(Graph.toJson(jsonGraphShouldBe)))
  }

  behavior of "Depth-first traversal"

  val dftGraph = Graph.fromJson(
    """
      |{
      |  "node_vertices": ["n0", "n1", "n2", "n3", "n4", "n5"],
      |  "dir_edges": {
      |    "e0": {"src": "n0", "tgt": "n1"},
      |    "e1": {"src": "n2", "tgt": "n0"},
      |    "e2": {"src": "n1", "tgt": "n2"},
      |    "e3": {"src": "n0", "tgt": "n3"},
      |    "e4": {"src": "n4", "tgt": "n5"},
      |    "e5": {"src": "n5", "tgt": "n5"}
      |  }
      |}
    """.stripMargin)

  it should "traverse all edges" in {
    val eSet = dftGraph.dft(Set[EName]()) { (es, e, _) => es + e }
    assert(eSet === Set[EName]("e0", "e1", "e2", "e3", "e4", "e5"))
  }

//  it should "traverse edges in the correct order" in {
//    val eList = dftGraph.dft(List[EName]()) { (es, e, _) => e :: es }.reverse
//    assert(eList === List[EName]("e0", "e2", "e1", "e3", "e4", "e5"))
//  }

  behavior of "Dag copy"

  val dagGraph = Graph.fromJson(
    """
      |{
      |  "node_vertices": ["n0", "n1", "n2", "n3", "n4", "n5"],
      |  "dir_edges": {
      |    "e0": {"src": "n0", "tgt": "n1"},
      |    "e1": {"tgt": "n2", "src": "n0"},
      |    "e2": {"src": "n1", "tgt": "n2"},
      |    "e3": {"src": "n0", "tgt": "n3"},
      |    "e4": {"src": "n4", "tgt": "n5"}
      |  }
      |}
    """.stripMargin)

  it should "translate into a dag correctly" in {
    assert(dftGraph.dagCopy === dagGraph)
  }

  it should "leave dags unchanged" in {
    val dag = Graph.randomDag(50,50)
    assert(dag.dagCopy === dag)
  }

  def traverseFrom(graph: Graph, v: VName, seen: Set[VName]) {
    if (seen contains v) fail("directed cycle detected")
    for (e <- graph.outEdges(v)) traverseFrom(graph, graph.target(e), seen + v)
  }

  it should "contain no directed cycles" in {
    val graph = Graph.random(100,100)
    val dag = graph.dagCopy

    for ((v,_) <- graph.vdata) traverseFrom(dag, v, Set[VName]())
  }
}
