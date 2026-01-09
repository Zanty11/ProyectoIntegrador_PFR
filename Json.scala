package ProyectoIntegrador

import io.circe._
import io.circe.parser._
import io.circe.generic.auto._

object Json extends App {

  println("=== 1. PRUEBA DE CIRCE (JSON PEQUEÑO) ===")

  // 1. Definimos un JSON simple de prueba (String)
  val jsonString = """
    {
      "nombre": "Inception",
      "anio": 2010,
      "generos": ["Ciencia Ficción", "Acción"],
      "esExitosa": true
    }
  """

  // 2. Definimos la Case Class para recibir los datos
  case class PeliculaDemo(nombre: String, anio: Int, generos: List[String], esExitosa: Boolean)

  // 3. Decodificamos (Parsear)
  val resultado = decode[PeliculaDemo](jsonString)

  resultado match {
    case Right(peli) =>
      println(s"Película detectada: ${peli.nombre}")
      println(s"Año: ${peli.anio}")
      println(s"Géneros: ${peli.generos.mkString(", ")}")
      println(s"¿Fue exitosa?: ${peli.esExitosa}")
    case Left(error) =>
      println(s"Error al leer JSON: $error")
  }
}