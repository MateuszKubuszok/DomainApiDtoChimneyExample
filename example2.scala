package example2

import cats.implicits.*
import cats.effect.*, cats.effect.unsafe.implicits.*
import doobie.*, doobie.implicits.*, doobie.h2.*
import io.circe.*, io.circe.generic.auto.*, io.circe.parser.*, io.circe.syntax.*
import java.util.UUID, java.time.{Clock, Instant}

case class Invoice(
    id: UUID,
    seller: String,
    buyer: String,
    price: Long,
    `creation-date`: Option[Instant] // either that, or some ConfiguredCodec, but no circe-generic-extra for S3
)

final class InvoiceProgram private (transactor: H2Transactor[IO], clock: Clock) {

  extension [A](ca: ConnectionIO[A])
    def runToEither(msg: String): Either[String, A] =
      ca.transact(transactor).attempt.unsafeRunSync().left.map(e => s"$msg: ${e.getMessage()}")

  def saveInvoice(invoiceJson: String): Either[String, String] =
    for {
      invoice <- decode[Invoice](invoiceJson).left.map(_.getMessage)
      newInvoice = invoice.copy(
        `creation-date` = invoice.`creation-date`.orElse(Some(Instant.now(clock)))
      )
      _ <- Either.cond(newInvoice.price > 0, (), "Price cannot be negative")
      _ <- {
        import newInvoice.*
        // mapping of all values into types in DB
        sql"""
          |INSERT INTO invoices(id, seller, buyer, price, creation_date)
          |VALUES (${id.toString}, $seller, $buyer, $price, ${`creation-date`.map(_.toString)})
          |""".stripMargin.update.run.runToEither("Invoice couldn't be saved")
      }
    } yield newInvoice.asJson.spaces2

  def getInvoice(id: String): Either[String, String] = for {
    invoiceOpt <-
      sql"""
          |SELECT id, seller, buyer, price, creation_date
          |FROM invoices
          |WHERE id = $id
          |""".stripMargin.query[(String, String, String, Long, Option[String])].map {
            case (id, seller, buyer, price, creationDate) =>
              // mapping of all values from types in DB
              Invoice(UUID.fromString(id), seller, buyer, price, creationDate.map(Instant.parse))
          }.option.runToEither("Invoice couldn't be read")
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
        |  id            varchar(36) primary key,
        |  seller        varchar(20),
        |  buyer         varchar(20),
        |  price         int,
        |  creation_date varchar
        |)
        |""".stripMargin.update.run.transact(xa)
    )
  } yield new InvoiceProgram(xa, clock)
}

