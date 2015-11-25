import java.io.File
import java.util

import org.neo4j.graphdb.index.UniqueFactory
import org.neo4j.graphdb._
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.index.UniqueFactory.UniqueNodeFactory
import org.neo4j.graphdb.traversal.Uniqueness
import org.scalatest._
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.util.{Failure, Success, Try, Random}
import scalax.file.Path

object DbServer {
  def runDb: GraphDatabaseService = {
    val neoStore: Path = Path.fromString("src/test/neostore")
    Try(neoStore.deleteRecursively(continueOnFailure = true))
    Try(neoStore.createDirectory())

    new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(
      neoStore.fileOption.getOrElse(new File("/tmp/neostore")))
      .newGraphDatabase
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
      .split("\\+")

    var tx: Transaction = null
    var ucnf: UniqueNodeFactory = null

    /** CREATE GRAPH FROM TEXT (words with their neighbor) **/
    try {
      tx = graphDb.beginTx

      ucnf = new UniqueFactory.UniqueNodeFactory(graphDb, "words") {
        override def initialize(n: Node, prop: util.Map[String, AnyRef]): Unit = {
          n.addLabel(DynamicLabel.label("Word Node"))
          n.setProperty("word", prop.get("word"))
        }
      }

      text.zipWithIndex.foreach(
        c => {
          val ucn = ucnf.getOrCreate("word", c._1)
          val next = c._2 + 1
          if (text.length > next) {
            val neighbor = ucnf.getOrCreate("word", text(next))
            ucn.createRelationshipTo(neighbor, RelTypes.NEIGHBOR)
            neighbor.createRelationshipTo(ucn, RelTypes.NEIGHBOR)
          }
        }
      )

      tx.success()

    } finally {
      tx.close()
    }

    /** TRAVERSE GRAPH **/
    try {
      tx = graphDb.beginTx

      val neighborTraversal = graphDb.traversalDescription()
        .breadthFirst()
        .relationships(RelTypes.NEIGHBOR)
        .uniqueness(Uniqueness.NODE_GLOBAL)

      val randomStartNode = ucnf.getOrCreate("word", text(Random.nextInt(text.length)))

      val traversed = neighborTraversal.traverse(randomStartNode).nodes

      traversed.toList.sortWith(_.getDegree > _.getDegree).foreach(
        n => {
          val word = n.getProperty("word")
          val rls = n.getRelationships(Direction.BOTH, RelTypes.NEIGHBOR)
            .map(_.getEndNode.getProperty("word").toString)
            .filter(_ != word)
            .groupBy(_.toString).toList
            .sortWith(_._2.size > _._2.size)
            .map(m => {
              val count = m._2.size
              val word = m._2.head
              if (count > 1)
                s"$word($count)"
              else
                word
            })
            .mkString(",")
          println(s"'$word': $rls")
        }
      )

      tx.success()
    } finally {
      tx.close()
    }

    graphDb.shutdown()
  }

}