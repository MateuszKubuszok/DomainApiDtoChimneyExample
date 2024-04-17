package example1

import cats.effect.*, cats.effect.unsafe.implicits.*

class ExampleProgramSpec extends munit.FunSuite {

  test("our service works") {
    ExampleProgram
      .resource(
        java.time.Clock.fixed(java.time.Instant.parse("2024-04-18T00:00:00Z"), java.time.ZoneId.of("UTC"))
      )
      .use { exampleProgram =>
        IO {
          assertEquals(
            exampleProgram.exampleService(
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

        }
      }
      .unsafeRunSync()
  }
}
