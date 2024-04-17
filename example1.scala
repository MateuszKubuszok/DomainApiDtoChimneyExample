package example1

import cats.implicits.*
import cats.effect.*, cats.effect.unsafe.implicits.*
import doobie.*, doobie.implicits.*, doobie.h2.*
import io.circe.*, io.circe.generic.auto.*, io.circe.parser.*, io.circe.syntax.*

case class Invoice(
    id: String,
    seller: String,
    buyer: String,
    price: Long,
    creationDate: Option[String]
)

final class ExampleProgram private (transactor: H2Transactor[IO], clock: java.time.Clock) {

  def exampleService(json: String): Either[String, String] =
    for {
      invoice <- decode[Invoice](json).left.map(_.getMessage)
      newInvoice = invoice.copy(
        creationDate = invoice.creationDate.orElse(Some(java.time.Instant.now(clock).toString))
      )
      _ <- {
        import newInvoice.*
        sql"""
          |INSERT INTO invoices(id, seller, buyer, price, creation_date)
          |VALUES ($id, $seller, $buyer, $price, $creationDate)
          |""".stripMargin.update.run.transact(transactor).attempt.unsafeRunSync().left.map(_.getMessage)
      }
    } yield newInvoice.asJson.spaces2
}
object ExampleProgram {

  def resource(clock: java.time.Clock = java.time.Clock.systemUTC()): Resource[IO, ExampleProgram] = for {
    ce <- ExecutionContexts.fixedThreadPool[IO](32)
    xa <- H2Transactor.newH2Transactor[IO]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "", ce)
    _ <- Resource.eval(
      sql"""
        |CREATE TABLE invoices(
        |  id            varchar(20) primary key,
        |  seller        varchar(20),
        |  buyer         varchar(20),
        |  price         int,
        |  creation_date timestamp
        |)
        |""".stripMargin.update.run.transact(xa)
    )
  } yield new ExampleProgram(xa, clock)
}
