package example4

import cats.implicits.*
import cats.effect.*, cats.effect.unsafe.implicits.*
import doobie.*, doobie.implicits.*, doobie.h2.*
import io.circe.*, io.circe.generic.auto.*, io.circe.parser.*, io.circe.syntax.*
import io.scalaland.chimney.{PartialTransformer, Transformer}, io.scalaland.chimney.dsl.*, io.scalaland.chimney.partial.syntax.*
import java.util.UUID, java.time.{Clock, Instant}

given Transformer[Instant, String] = _.toString
given PartialTransformer[String, Instant] = PartialTransformer.fromFunction(Instant.parse)

given Transformer[UUID, String] = _.toString
given PartialTransformer[String, UUID] = PartialTransformer.fromFunction(UUID.fromString)

// domain model

case class Invoice private (
    id: UUID,
    seller: String,
    buyer: String,
    price: Long,
    creationDate: Instant
)
object Invoice {

  def parse(
      id: UUID,
      seller: String,
      buyer: String,
      price: Long,
      creationDate: Instant
  ): Either[String, Invoice] = for {
    _ <- Either.cond(price > 0, (), "Price cannot be negative")
  } yield Invoice(id, seller, buyer, price, creationDate)
}

// API (JSONs)

case class ApiInvoice(
    id: UUID,
    seller: String,
    buyer: String,
    price: Long,
    `creation-date`: Option[Instant]
) {

  def toDomain(creationDate: => Instant): Either[String, Invoice] =
    this
      .intoPartial[Invoice]
      .withConstructorEither(Invoice.parse)
      .withFieldConst(_.creationDate, `creation-date`.getOrElse(creationDate))
      .transform.asEitherErrorPathMessageStrings.leftMap(_.map(_._2).mkString("\n"))
}
object ApiInvoice {

  def fromDomain(domain: Invoice): ApiInvoice =
    domain.into[ApiInvoice].withFieldRenamed(_.creationDate, _.`creation-date`).transform
}

// Database

case class DbInvoice(
    id: String,
    seller: String,
    buyer: String,
    price: Long,
    creationDate: String
) {

  def toDomain: Either[String, Invoice] = 
    this
      .intoPartial[Invoice]
      .withConstructorEither(Invoice.parse)
      .transform.asEitherErrorPathMessageStrings.leftMap(_.map(_._2).mkString("\n"))
}
object DbInvoice {

  def fromDomain(domain: Invoice): DbInvoice =
    DbInvoice(domain.id.toString, domain.seller, domain.buyer, domain.price, domain.creationDate.toString)
}

// program

final class InvoiceProgram private (transactor: H2Transactor[IO], clock: Clock) {

  extension [A](ca: ConnectionIO[A])
    def runToEither(msg: String): Either[String, A] =
      ca.transact(transactor).attempt.unsafeRunSync().left.map(e => s"$msg: ${e.getMessage()}")

  def saveInvoice(invoiceJson: String): Either[String, String] =
    for {
      apiInvoice <- decode[ApiInvoice](invoiceJson).left.map(_.getMessage)
      invoice <- apiInvoice.toDomain(Instant.now(clock))
      _ <- {
        val dbInvoice = DbInvoice.fromDomain(invoice)
        import dbInvoice.*
        sql"""
          |INSERT INTO invoices(id, seller, buyer, price, creation_date)
          |VALUES ($id, $seller, $buyer, $price, $creationDate)
          |""".stripMargin.update.run.runToEither("Invoice couldn't be saved")
      }
    } yield ApiInvoice.fromDomain(invoice).asJson.spaces2

  def getInvoice(id: String): Either[String, String] = for {
    dbInvoiceOpt <-
      sql"""
          |SELECT id, seller, buyer, price, creation_date
          |FROM invoices
          |WHERE id = $id
          |""".stripMargin
        .query[DbInvoice]
        .option
        .runToEither("Invoice couldn't be read")
    dbInvoice <- dbInvoiceOpt.toRight(s"Invoice id = $id not found")
    invoice <- dbInvoice.toDomain
  } yield ApiInvoice.fromDomain(invoice).asJson.spaces2
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
