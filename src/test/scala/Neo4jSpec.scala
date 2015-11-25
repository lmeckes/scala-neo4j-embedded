import java.io.File
import java.util

import org.neo4j.graphdb.index.UniqueFactory
import org.neo4j.graphdb._
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.index.UniqueFactory.UniqueNodeFactory
import org.neo4j.graphdb.traversal.Uniqueness
import org.scalatest._
import scala.collection.JavaConversions._
import scala.util.{Try, Random}
import scalax.file.Path

object DbServer {
  def runDb: GraphDatabaseService = {
    val neoStore: Path = Path.fromString("src/test/neostore")
    Try(neoStore.deleteRecursively(continueOnFailure = true))
    neoStore.createDirectory()
    new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(neoStore.fileOption.getOrElse(new File("/tmp/neostore"))).newGraphDatabase
  }
}

import DbServer._

class Neo4jSpec extends FlatSpec with Matchers {

  "Neo4j-embedded" should "start" in {
    val grapdDb = runDb
    grapdDb.shutdown()
  }

  "Neo4j-transaction" should "work nicely" in {
    val grapdDb = runDb
    var tx: Transaction = null
    try {
      tx = grapdDb.beginTx
      tx.success()
    } finally {
      tx.close()
    }
    grapdDb.shutdown()
  }

  "Neo4j-relationships" should "work" in {
    val grapdDb = runDb

    object RelTypes extends Enumeration {
      type RelTypes = Value
      val KNOWS = Value

      implicit def conv(rt: RelTypes): RelationshipType = new RelationshipType() {
        def name = rt.toString
      }
    }

    var tx: Transaction = null
    try {
      tx = grapdDb.beginTx
      val firstNode = grapdDb.createNode()
      firstNode.setProperty("message", "Hello, ")
      val secondNode = grapdDb.createNode()
      secondNode.setProperty("message", "World!")
      val relationship = firstNode.createRelationshipTo(secondNode, RelTypes.KNOWS)
      relationship.setProperty("message", "brave Neo4j ")
      tx.success()
    } finally {
      tx.close()
    }

    grapdDb.shutdown()
  }

  "Neo4j-with-scala" should "be fun" in {
    val graphDb = runDb

    object RelTypes extends Enumeration {
      type RelTypes = Value
      val NEIGHBOR = Value

      implicit def conv(rt: RelTypes) = new RelationshipType() {
        def name = rt.toString
      }
    }

    val text = scala.io.Source.fromFile("src/test/resources/testInput.txt").mkString
      .replaceAll("\\p{Punct}+", " ")
      .replaceAll("\\s+", "+")
      .toLowerCase

    val inputText = text.split("\\+")

    var tx: Transaction = null
    var ucnf: UniqueNodeFactory = null

    /** **************************/
    /** CREATE GRAPH FROM TEXT **/
    try {
      tx = graphDb.beginTx

      ucnf = new UniqueFactory.UniqueNodeFactory(graphDb, "chars") {
        override def initialize(n: Node, prop: util.Map[String, AnyRef]): Unit = {
          n.addLabel(DynamicLabel.label("Node"))
          n.setProperty("char", prop.get("char"))
        }
      }

      // Map Unique Char Nodes from input text
      inputText.zipWithIndex.foreach(
        c => {
          val ucn = ucnf.getOrCreate("char", c._1)
          val next = c._2 + 1
          if (inputText.length > next)
            ucn.createRelationshipTo(ucnf.getOrCreate("char", inputText(next)), RelTypes.NEIGHBOR)
        }
      )

      tx.success()

    } finally {
      tx.close()
    }

    /** **************************/
    /** TRAVERSE GRAPH **/
    try {
      tx = graphDb.beginTx

      val neighborTraversal = graphDb.traversalDescription()
        .breadthFirst()
        .relationships(RelTypes.NEIGHBOR)
        .uniqueness(Uniqueness.NODE_GLOBAL)

      val randomStartNode = ucnf.getOrCreate("char", inputText(Random.nextInt(inputText.length)))

      val traversed = neighborTraversal.traverse(randomStartNode).nodes()

      traversed.toList.sortWith(_.getDegree > _.getDegree).foreach(
        n => {
          val char = n.getProperty("char")
          val rls = n.getRelationships(Direction.BOTH, RelTypes.NEIGHBOR)
            .map(_.getEndNode)
            .map(_.getProperty("char"))
            .toSet.mkString("[", ",", "]")
          println(s"'$char': rel=$rls")
        }
      )
      tx.success()

    } finally {
      tx.close()
    }

    graphDb.shutdown()
  }

}