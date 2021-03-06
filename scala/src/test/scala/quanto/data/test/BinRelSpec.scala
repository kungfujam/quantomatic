package quanto.data.test

import org.scalatest.FlatSpec
import quanto.data._

class BinRelSpec extends FlatSpec {
  var rel: BinRel[String,Int] = _
  var rel1: BinRel[String,Int] = _
  var f : PFun[String,Int] = _
  var f1 : PFun[String,Int] = _

  behavior of "A binary relation"

  it should "initialise" in {
    rel = BinRel()
  }

  it can "add elements" in {
    rel += "a" -> 2
    rel += "b" -> 3
    rel += "a" -> 4
    rel += "c" -> 2
  }

  it can "be constructed with arg list" in {
    rel1 = BinRel("a" -> 2, "a" -> 4, "b" -> 3, "c" -> 2)
  }

  it should "succeed when comparing two equal relations" in {
    assert(rel === rel1)
  }

  it can "evaluate direct images of elements" in {
    assert(rel.domf("a") === Set(2,4))
    assert(rel.domf("b") === Set(3))
    assert(rel.domf("c") === Set(2))
    assert(rel.domf("d") === Set())
  }

  it can "evaluate inverse images of elements" in {
    assert(rel.codf(1) === Set())
    assert(rel.codf(2) === Set("a", "c"))
    assert(rel.codf(3) === Set("b"))
    assert(rel.codf(4) === Set("a"))
  }

  it can "evaluate direct images of sets" in {
    assert(rel.directImage(Set("a", "b")) === Set(2,3,4))
    assert(rel.directImage(Set()) === Set())
  }

  it can "evaluate inverse images of sets" in {
    assert(rel.inverseImage(Set(1, 3, 4)) === Set("a","b"))
    assert(rel.inverseImage(Set()) === Set())
  }

  it should "iterate in the correct order (lexicographic [dom,cod])" in {
    val pairs = for((x,y) <- rel) yield (x,y)
    assert(pairs === List(("a", 2), ("a", 4), ("b", 3), ("c", 2)))
  }

  it can "remove elements" in {
    val rel2 = rel unmap ("a", 2) unmap ("c", 2)
    assert(rel2 === BinRel("a" -> 4, "b" -> 3))
  }

  it should "remain unchanged when removing pairs not in relation" in {
    val rel2 = rel unmap ("d", 4)
    val rel3 = rel unmap ("a", 7)
    assert(rel2 === rel)
    assert(rel3 === rel)
  }

  behavior of "A partial function"
  
  it should "initialise" in {
    f = PFun[String,Int]()
  }
  
  it should "add some elements" in {
    f = f + ("a" -> 2)
    f += "b" -> 1
    f += "c" -> 2
  }
  
  it should "evaluate inverse images" in {
    assert(f.codf(2).size === 2)
    assert(f.codf(2) === Set("a", "c"))
  }

  it can "be constructed with arg list" in  {
    f1 = PFun("a" -> 2, "b" -> 1, "c" -> 2)
  }

  it should "succeed when comparing two equal partial functions" in {
    assert(f === f1)
  }

  it should "succeed when comparing to the same relation" in {
    val rel2 = BinRel("a" -> 2, "b" -> 1, "c" -> 2)
    assert(f === rel2)
  }

  it should "behave differently from binary relations when mapping elements in domain" in {
    val f2 = PFun("a" -> 2, "b" -> 1, "c" -> 2)
    val rel2 = BinRel("a" -> 2, "b" -> 1, "c" -> 2)

    assert(f2 === rel2)

    val f3 = f2 + ("a" -> 3)      // remaps a to 3
    val rel3 = rel2 + ("a" -> 3)  // adds 3 to the image of a

    assert(f3 != rel3)
  }
  
}
