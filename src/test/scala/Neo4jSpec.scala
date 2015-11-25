import java.io.File
import java.util

import org.neo4j.cypher.internal.compiler.v2_2.functions.Rels
import org.neo4j.graphdb.index.UniqueFactory
import org.neo4j.graphdb._
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.neo4j.graphdb.traversal.Uniqueness
import org.scalatest._
import scala.collection.JavaConversions._
import scala.util.Random

object DbServer {
  def runDb: GraphDatabaseService = {
    val neoStore: File = new File("src/test/neostore")
    if (!neoStore.exists)
      neoStore.mkdir
    new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(neoStore).newGraphDatabase
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
      implicit def conv(rt: RelTypes) = new RelationshipType() {def name = rt.toString}
    }

    var tx: Transaction = null
    try {
      tx = grapdDb.beginTx
      val firstNode = grapdDb.createNode();
      firstNode.setProperty("message", "Hello, ");
      val secondNode = grapdDb.createNode();
      secondNode.setProperty("message", "World!");
      val relationship = firstNode.createRelationshipTo(secondNode, RelTypes.KNOWS);
      relationship.setProperty("message", "brave Neo4j ");
      tx.success()
    } finally {
      tx.close()
    }

    grapdDb.shutdown()
  }

  "Neo4j-with-scala" should "be fun" in {
    val grapdDb = runDb

    object RelTypes extends Enumeration {
      type RelTypes = Value
      val NEIGHBOR = Value
      implicit def conv(rt: RelTypes) = new RelationshipType() {def name = rt.toString}
    }

    val text = """ Le petit juge blond est ivre """.toLowerCase

    var tx: Transaction = null
    try {
      tx = grapdDb.beginTx

      // Get or Create unique Char Nodes
      val uniqueCharNodeFactory = new UniqueFactory.UniqueNodeFactory(grapdDb, "chars") {
        override def initialize(n: Node, prop: util.Map[String, AnyRef]): Unit = {
          n.addLabel(DynamicLabel.label("Char"));
          n.setProperty("char", prop.get("char"));
        }
      };

      // Map Unique Char Nodes from input text
      text.zipWithIndex.foreach(
        c => {
          val ucn = uniqueCharNodeFactory.getOrCreate("char", c._1)
          val next = c._2 + 1
          if (text.length > next)
            ucn.createRelationshipTo(uniqueCharNodeFactory.getOrCreate("char", text(next)), RelTypes.NEIGHBOR)
        }
      )

      // Traverse Graph
      val neighborTraversal = grapdDb.traversalDescription()
        .depthFirst()
        .relationships(RelTypes.NEIGHBOR)
        .uniqueness(Uniqueness.NODE_GLOBAL);
      val randomStartNode = uniqueCharNodeFactory.getOrCreate("char", text(Random.nextInt(text.length)))
      neighborTraversal.traverse(randomStartNode).nodes().toList.foreach(
        n => {
          val char = n.getProperty("char")
          val degree = n.getDegree.toString
          println(s"$char: degree=$degree")
        }
      )

      tx.success()

    } finally {
      tx.close()
    }

    /*for ( Node currentNode : friendsTraversal
      .traverse( node )
      .nodes() )
    {
      output += currentNode.getProperty( "name" ) + "\n";
    }*/

    grapdDb.shutdown()
  }

}