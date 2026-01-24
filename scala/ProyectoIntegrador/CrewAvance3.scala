package ProyectoIntegrador

import cats.effect.{IO, IOApp}
import fs2.{Stream, text}
import fs2.io.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer

// 1. MODELO INMUTABLE
case class Movie(title: String, director: String)

// 2. LÓGICA PURA DE NEGOCIO
object DomainLogic {

  def parseMovie(line: String): Option[Movie] = {
    val cols = parseCsvLineManual(line)

    if (cols.length >= 5) {
      var title = "Desconocido"
      val foundIndex = cols.indexWhere(t => isValidTitle(t.replaceAll("\"", "").trim))

      if (foundIndex != -1) {
        title = cols(foundIndex).replaceAll("\"", "").trim
      }

      val director = findDirectorInFragments(cols)

      if (title != "Desconocido" && director != "Desconocido") {
        Some(Movie(title, director))
      } else None
    } else None
  }

  def isValidTitle(t: String): Boolean = {
    if (t.isEmpty) return false
    if (t.contains("{") || t.contains("}") || t.contains("False")) return false
    if (t.contains("'name':") || t.contains("id':")) return false
    if (t.head.isLetter && t.head.isLower) return false
    if (t.length > 80) return false
    if (t.startsWith("and ") || t.startsWith("with ")) return false
    true
  }

  def parseCsvLineManual(line: String): Array[String] = {
    val result = ArrayBuffer[String]()
    val currentField = new StringBuilder
    var inQuotes = false
    for (char <- line) {
      if (char == '\"') { inQuotes = !inQuotes; currentField.append(char) }
      else if (char == ',' && !inQuotes) { result += currentField.toString(); currentField.clear() }
      else currentField.append(char)
    }
    result += currentField.toString()
    result.toArray
  }

  def findDirectorInFragments(cols: Array[String]): String = {
    val index = cols.indexWhere(c => c.contains("'job': 'Director'") || c.contains("\"job\": \"Director\""))
    if (index != -1 && index + 1 < cols.length) extractName(cols(index + 1)) else "Desconocido"
  }

  def extractName(fragment: String): String = {
    var nameTag = "'name': '"; var quoteEnd = "'"
    if (!fragment.contains(nameTag)) { nameTag = "\"name\": \""; quoteEnd = "\"" }
    val start = fragment.indexOf(nameTag)
    if (start != -1) {
      val nameStart = start + nameTag.length
      val end = fragment.indexOf(quoteEnd, nameStart)
      if (end != -1) return fragment.substring(nameStart, end)
    }
    "Desconocido"
  }

  def toSqlInsert(movie: Movie): String = {
    val safeTitle = movie.title.replace("'", "''")
    val safeDirector = movie.director.replace("'", "''")
    s"INSERT INTO movies (title, director) VALUES ('$safeTitle', '$safeDirector');"
  }
}


object Crew extends IOApp.Simple {

  val inputPath = Path("src/main/resources/data/pi_movies_small.csv")
  val outputPath = Path("src/main/resources/data/insert_movies.sql")

  val sqlHeader: Stream[IO, String] = Stream("CREATE TABLE IF NOT EXISTS movies (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), director VARCHAR(255));\n\n")

  val conversionStream: Stream[IO, Unit] = {
    val dataStream = Files[IO].readAll(inputPath)
      .through(text.utf8.decode)
      .through(text.lines)
      .drop(1)
      .map(line => DomainLogic.parseMovie(line))
      .unNone
      .map(movie => DomainLogic.toSqlInsert(movie))

    (sqlHeader ++ dataStream.intersperse("\n"))
      .through(text.utf8.encode)
      .through(Files[IO].writeAll(outputPath))
  }

  override def run: IO[Unit] = {
    IO.println("--- INICIANDO PROCESO (FS2) ---") *>
      conversionStream.compile.drain *>
      IO.println(s"✅ Archivo generado exitosamente en: $outputPath")
  }
}