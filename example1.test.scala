package example1

import cats.effect.*, cats.effect.unsafe.implicits.*
import java.time.{Clock, Instant, ZoneId}

class InvoiceProgramSpec extends munit.FunSuite {

  test("invoices are saved and restored") {
    InvoiceProgram
      .resource(Clock.fixed(Instant.parse("2024-04-18T00:00:00Z"), ZoneId.of("UTC")))
      .use { invoiceProgram =>
        IO {
          // no invoice
          assertEquals(
            invoiceProgram.getInvoice("123456789"),
            Left(s"Invoice id = 123456789 not found")
          )
          // invoice created
          assertEquals(
            invoiceProgram.saveInvoice(
              """{
                |  "id" : "123456789",
                |  "seller" : "Jane Doe",
                |  "buyer" : "John Smith",
                |  "price" : 1500
                |}""".stripMargin
            ),
            Right(
              """{
                |  "id" : "123456789",
                |  "seller" : "Jane Doe",
                |  "buyer" : "John Smith",
                |  "price" : 1500,
                |  "creationDate" : "2024-04-18T00:00:00Z"
                |}""".stripMargin
            )
          )
          // invoice found
          assertEquals(
            invoiceProgram.getInvoice("123456789"),
            Right(
              """{
                |  "id" : "123456789",
                |  "seller" : "Jane Doe",
                |  "buyer" : "John Smith",
                |  "price" : 1500,
                |  "creationDate" : "2024-04-18T00:00:00Z"
                |}""".stripMargin
            )
          )
        }
      }
      .unsafeRunSync()
  }
}
