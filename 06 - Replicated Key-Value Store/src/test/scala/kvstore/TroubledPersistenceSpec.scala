package kvstore

import kvstore.Arbiter.{Join, JoinedPrimary, JoinedSecondary, Replicas}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import org.scalatest.OptionValues._
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers

@RunWith(classOf[JUnitRunner])
class TroubledPersistenceSpec extends TestKit(ActorSystem("TroubledPersistenceSpec"))
  with FunSuite
  with BeforeAndAfterAll
  with ShouldMatchers
  with ImplicitSender
  with Tools {

  test("a broken persistence layer won't stop a Primary") {
    val arbiter = TestProbe()
    val primary = system.actorOf(Replica.props(arbiter.ref, Persistence.flaky), "case1-primary")
    val client = session(primary)

    arbiter.expectMsg(Join)
    arbiter.send(primary, JoinedPrimary)

    for (i <- 1 to 20) {
      client.setAcked(s"key-$i", s"Value $i")
    }

    for (i <- 1 to 20) {
      client.get(s"key-$i").value should be (s"Value $i")
    }
  }

  test("a broken persistence layer won't stop a Primary with Secondaries") {
    val arbiter = TestProbe()
    val primary = system.actorOf(Replica.props(arbiter.ref, Persistence.flaky), "case2-primary")
    val secondaries = (1 to 1).map(i => system.actorOf(Replica.props(arbiter.ref, Persistence.flaky), s"case2-secondary-$i")).toSet
    val replicas = Seq(primary) ++ secondaries
    val primaryClient = session(primary)
    val secondaryClients = secondaries.map(secondary => session(secondary))
    val clients = Seq(primaryClient) ++ secondaryClients

    replicas foreach { _ => arbiter.expectMsg(Join) }
    arbiter.send(primary, JoinedPrimary)
    secondaries foreach { secondary => arbiter.send(secondary, JoinedSecondary) }
    arbiter.send(primary, Replicas(Set(primary) ++ secondaries))

    for (i <- 1 to 20) {
      primaryClient.setAcked(s"key-$i", s"Value $i")
    }

    for (i <- 1 to 20) {
      for (client <- clients) {
        client.get(s"key-$i").value should be (s"Value $i")
      }
    }
  }
}
