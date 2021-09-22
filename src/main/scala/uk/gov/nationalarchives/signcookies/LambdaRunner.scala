package uk.gov.nationalarchives.signcookies

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets

object LambdaRunner extends App {
  val lambda = new Lambda
  // This is a real token, but it has expired and it was for the integration environment, so it is safe to use as test
  // data.
  val accessToken = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJJQ3F4MUlJMEFkLThkRHA4U3Q4WDU4XzJsSW9MWEU0QmdmNThwUzl5WFo0In0.eyJleHAiOjE2MzA2NzY5NTAsImlhdCI6MTYzMDY3NjY1MCwiYXV0aF90aW1lIjoxNjMwNjczNzg1LCJqdGkiOiIxMmU3NWZiYS01NDI5LTQ2Y2ItYjU2Yy0zMzYwODg4NDZlODgiLCJpc3MiOiJodHRwczovL2F1dGgudGRyLWludGVncmF0aW9uLm5hdGlvbmFsYXJjaGl2ZXMuZ292LnVrL2F1dGgvcmVhbG1zL3RkciIsImF1ZCI6InRkci1mZSIsInN1YiI6IjNhMjUyZDczLTE4ZTEtNDMzZS1iY2FjLTdiNjVmN2VmMmZmNiIsInR5cCI6IkJlYXJlciIsImF6cCI6InRkci1mZSIsIm5vbmNlIjoiOWFlZDI4NDgtMmFhNy00ZTRhLTgxMWYtOGRjNzQ0NTI5OTUyIiwic2Vzc2lvbl9zdGF0ZSI6ImY5N2IwYmUwLTc4NGUtNDAyYS1hMDFhLTAyMGMwMjVkZTFhYSIsImFjciI6IjAiLCJhbGxvd2VkLW9yaWdpbnMiOlsiaHR0cHM6Ly90ZHItaW50ZWdyYXRpb24ubmF0aW9uYWxhcmNoaXZlcy5nb3YudWsiLCJodHRwOi8vbG9jYWxob3N0OjkwMDAiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbIm9mZmxpbmVfYWNjZXNzIiwidGRyX3VzZXIiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSBlbWFpbCIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwidXNlcl9pZCI6IjNhMjUyZDczLTE4ZTEtNDMzZS1iY2FjLTdiNjVmN2VmMmZmNiIsIm5hbWUiOiJTdXphbm5lICh0ZXN0KSBIYW1pbHRvbiIsInByZWZlcnJlZF91c2VybmFtZSI6InRlc3Qtc3V6YW5uZSIsImdpdmVuX25hbWUiOiJTdXphbm5lICh0ZXN0KSIsImJvZHkiOiJNT0NLMSIsImZhbWlseV9uYW1lIjoiSGFtaWx0b24ifQ.QQ3GPh-2TZ-1-uCNFl9v6lcQ7KVzNY6NKbwHVLClnHYrvKgwAwPAhqad-6TY4URvzS1VJF_x9ECPs4MgzZ4mzAKxvAHoU3S7UUKi7bvj6hDXsfSFseYw-02R_utVUYtpe744JINcSLxp07pBUR4CTlnrW043E6ObMbksasHnTGrm001L7TCZkXUHA2DMetmZFeleLfvtsOYhIOE9jMj4jwVgOD_mz68BlCmiaMOIIsDM68EzYze7qhknJAc4U3J626uf6pZgGBNCUON2lDm-Av5SX_Rq4btY_IrQ67e1A9xYcE0ZzyeBYT_jjn3rcUUKoUFVGaMKhALV1yL4sS2W-w"

  val input =
    s"""{
       |  "headers": {
       |    "Authorization": "Bearer $accessToken",
       |    "origin": "http://localhost:9000"
       |  }
       |}""".stripMargin
  val inputStream = new ByteArrayInputStream(input.getBytes)

  val outputStream = new ByteArrayOutputStream
  // Context is not used, so safe to set to null
  val context = null


  lambda.handleRequest(inputStream, outputStream, context)

  val output = new String(outputStream.toByteArray, StandardCharsets.UTF_8)

  println("Output:")
  println(output)
}
