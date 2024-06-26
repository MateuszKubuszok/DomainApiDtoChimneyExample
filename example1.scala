package example1

import cats.implicits.*
import cats.effect.*, cats.effect.unsafe.implicits.*
import doobie.*, doobie.implicits.*, doobie.h2.*
import io.circe.*, io.circe.generic.auto.*, io.circe.parser.*, io.circe.syntax.*
import java.time.{Clock, Instant}

case class Invoice(
    id: String,
    seller: String,
    buyer: String,
    price: Long,
    creationDate: Option[String]
)

final class InvoiceProgram private (transactor: H2Transactor[IO], clock: Clock) {

  extension [A](ca: ConnectionIO[A])
    def runToEither(msg: String): Either[String, A] =
      ca.transact(transactor).attempt.unsafeRunSync().left.map(e => s"$msg: ${e.getMessage()}")

  def saveInvoice(invoiceJson: String): Either[String, String] =
    for {
      invoice <- decode[Invoice](invoiceJson).left.map(_.getMessage)
      newInvoice = invoice.copy(
        creationDate = invoice.creationDate.orElse(Some(Instant.now(clock).toString))
      )
      _ <- {
        import newInvoice.*
        sql"""
          |INSERT INTO invoices(id, seller, buyer, price, creation_date)
          |VALUES ($id, $seller, $buyer, $price, $creationDate)
          |""".stripMargin.update.run.runToEither("Invoice couldn't be saved")
      }
    } yield newInvoice.asJson.spaces2

  def getInvoice(id: String): Either[String, String] = for {
    invoiceOpt <-
      sql"""
          |SELECT id, seller, buyer, price, creation_date
          |FROM invoices
          |WHERE id = $id
          |""".stripMargin.query[Invoice].option.runToEither("Invoice couldn't be read")
    invoice <- invoiceOpt.toRight(s"Invoice id = $id not found")
  } yield invoice.asJson.spaces2
}
object InvoiceProgram {

  def resource(clock: Clock = Clock.systemUTC()): Resource[IO, InvoiceProgram] = for {
    ce <- ExecutionContexts.fixedThreadPool[IO](32)
    xa <- H2Transactor.newH2Transactor[IO]("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "", ce)
    _ <- Resource.eval(
      sql"""
        |CREATE TABLE invoices(
        |  id            varchar(20) primary key,
        |  seller        varchar(20),
        |  buyer         varchar(20),
        |  price         int,
        |  creation_date varchar
        |)
        |""".stripMargin.update.run.transact(xa)
    )
  } yield new InvoiceProgram(xa, clock)
}
