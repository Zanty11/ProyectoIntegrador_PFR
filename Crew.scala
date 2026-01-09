package ProyectoIntegrador


import scala.io.Source
import scala.util.Using
import scala.collection.mutable.ArrayBuffer

// 1. MODELO
case class Movie(
                  title: String,
                  director: String
                )

// 2. UTILITARIO INTELIGENTE
object MovieLoader {

  def fromCsvLine(line: String): Option[Movie] = {
    val cols = parseCsvLine(line)

    if (cols.length >= 5) {
      // 1. OBTENER TÍTULO (Intentamos reconstruirlo si se rompió)
      val titleRaw = if (cols.length > 8) cols(8) else cols(0)
      val title = titleRaw.replaceAll("\"", "")

      // 2. OBTENER DIRECTOR (Lógica de Rastreo)
      val director = findDirectorInFragments(cols)

      Some(Movie(title, director))
    } else {
      None
    }
  }

  def parseCsvLine(line: String): Array[String] = {
    val result = ArrayBuffer[String]()
    val currentField = new StringBuilder
    var inQuotes = false

    for (char <- line) {
      if (char == '\"') {
        inQuotes = !inQuotes
        currentField.append(char)
      } else if (char == ',' && !inQuotes) {
        result += currentField.toString()
        currentField.clear()
      } else {
        currentField.append(char)
      }
    }
    result += currentField.toString()
    result.toArray
  }

  // --- NUEVA FUNCIÓN: RASTREADOR DE DIRECTOR ---
  def findDirectorInFragments(cols: Array[String]): String = {
    val index = cols.indexWhere(c => c.contains("'job': 'Director'") || c.contains("\"job\": \"Director\""))

    if (index != -1 && index + 1 < cols.length) {
      val nameFragment = cols(index + 1)
      extractName(nameFragment)
    } else {
      "Desconocido"
    }
  }

  def extractName(fragment: String): String = {
    var nameTag = "'name': '"
    var quoteEnd = "'"

    if (!fragment.contains(nameTag)) {
      nameTag = "\"name\": \""
      quoteEnd = "\""
    }

    val start = fragment.indexOf(nameTag)
    if (start != -1) {
      val nameStart = start + nameTag.length
      val end = fragment.indexOf(quoteEnd, nameStart)

      if (end != -1) {
        return fragment.substring(nameStart, end)
      }
    }
    "Desconocido"
  }
}

// 3. EJECUTABLE FINAL
object Crew {
  def main(args: Array[String]): Unit = {
    val filePath = "src/main/resources/data/pi_movies_small.csv"

    println("--- BUSCANDO DIRECTORES (MODO RASTREO) ---")
    println(f"${"TITULO (Aprox)"}%-30s | DIRECTOR")
    println("-" * 60)

    val movies: List[Movie] = Using(Source.fromFile(filePath)) { source =>
      source.getLines()
        .drop(1)
        .flatMap(MovieLoader.fromCsvLine)
        .toList
    }.getOrElse(List.empty)

    val found = movies.filter(_.director != "Desconocido")

    found.take(20).foreach { movie =>
      val printTitle = if (movie.title.length > 25) movie.title.take(25) + "..." else movie.title
      println(f"$printTitle%-30s | ${movie.director}")
    }

    println("-" * 60)
    println(f"Total procesado: ${movies.size}")
    println(f"Directores encontrados: ${found.size}")
  }
}