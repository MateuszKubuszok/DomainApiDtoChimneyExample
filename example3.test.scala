package example3

import cats.effect.*, cats.effect.unsafe.implicits.*
import java.time.{Clock, Instant, ZoneId}

class InvoiceProgramSpec extends munit.FunSuite {

  test(s"invoices are saved and restored ${java.util.UUID.randomUUID()}") {
    InvoiceProgram
      .resource(Clock.fixed(Instant.parse("2024-04-18T00:00:00Z"), ZoneId.of("UTC")))
      .use { invoiceProgram =>
        IO {
          // no invoice
          assertEquals(
            invoiceProgram.getInvoice("4d069420-d45d-453a-887f-8565b1b1170d"),
            Left(s"Invoice id = 4d069420-d45d-453a-887f-8565b1b1170d not found")
          )
          // invoice created
          assertEquals(
            invoiceProgram.saveInvoice(
              """{
                |  "id" : "4d069420-d45d-453a-887f-8565b1b1170d",
                |  "seller" : "Jane Doe",
                |  "buyer" : "John Smith",
                |  "price" : 1500
                |}""".stripMargin
            ),
            Right(
              """{
                |  "id" : "4d069420-d45d-453a-887f-8565b1b1170d",
                |  "seller" : "Jane Doe",
                |  "buyer" : "John Smith",
                |  "price" : 1500,
                |  "creation-date" : "2024-04-18T00:00:00Z"
                |}""".stripMargin
            )
          )
          // invoice found
          assertEquals(
            invoiceProgram.getInvoice("4d069420-d45d-453a-887f-8565b1b1170d"),
            Right(
              """{
                |  "id" : "4d069420-d45d-453a-887f-8565b1b1170d",
                |  "seller" : "Jane Doe",
                |  "buyer" : "John Smith",
                |  "price" : 1500,
                |  "creation-date" : "2024-04-18T00:00:00Z"
                |}""".stripMargin
            )
          )
        }
      }
      .unsafeRunSync()
  }
}
