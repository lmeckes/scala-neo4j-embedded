import java.io.File

import org.neo4j.graphdb.RelationshipType
import org.neo4j.graphdb.{GraphDatabaseService, Transaction}
import org.neo4j.graphdb.factory.GraphDatabaseFactory
import org.scalatest._

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

  "Neo4j-transation" should "work nicely" in {
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

  object RelTypes extends Enumeration {
    type RelTypes = Value
    val KNOWS = Value
    implicit def conv(rt: RelTypes) = new RelationshipType() {def name = rt.toString}
  }

  "Neo4j-relationships" should "work" in {
    val grapdDb = runDb

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

}