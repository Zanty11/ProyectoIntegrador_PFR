import cats.effect.{IO, IOApp}
import fs2.{Stream, text}
import fs2.io.file.{Files, Path}
import fs2.data.csv._
import fs2.data.csv.lowlevel._
import io.circe.{Decoder, HCursor}
import io.circe.parser.{parse => parseJson}

// ==========================================
// 1. MODELO (Simple)
// ==========================================
case class CrewMember(job: String, name: String)
object CrewMember {
  implicit val decoder: Decoder[CrewMember] = (c: HCursor) =>
    for {
      job <- c.downField("job").as[String]
      name <- c.downField("name").as[String]
    } yield CrewMember(job, name)
}

// ==========================================
// 2. PROGRAMA PRINCIPAL
// ==========================================
object MoviesETL extends IOApp.Simple {

  val inputFile = Path("src/main/resources/data/pi_movies_complete.csv")
  val outputFile = Path("src/main/resources/data/insert_movies_final.sql")

  // --- UTILITARIOS ---
  def repairJson(raw: String): String = {
    val trimmed = raw.trim
    if (trimmed.isEmpty || trimmed == "[]") return "[]"
    var fixed = trimmed
      .replace("None", "null")
      .replace("False", "false")
      .replace("True", "true")
      .replace("'", "\"")

    if (fixed.startsWith("[") && !fixed.endsWith("]")) {
      val lastClosing = fixed.lastIndexOf("}")
      if (lastClosing != -1) fixed = fixed.substring(0, lastClosing + 1) + "]"
      else fixed = "[]"
    }
    fixed
  }

  def extractDirector(jsonRaw: String): String = {
    val cleanJson = repairJson(jsonRaw)
    parseJson(cleanJson).flatMap(_.as[List[CrewMember]]) match {
      case Right(members) =>
        members.find(_.job == "Director").map(_.name).getOrElse("Unknown")
      case Left(_) => "Unknown"
    }
  }

  def sanitizeTitle(t: String): String = {
    t.trim.replaceAll("[\r\n]+", " ").replace("'", "''")
  }

  // --- EJECUCIÓN ---
  override def run: IO[Unit] = {
    println("⚡ Iniciando Pipeline ETL (Modo Mapeo Directo)...")

    val processingStream = Files[IO].readAll(inputFile)
      .through(text.utf8.decode)
      // 1. Parseamos CSV crudo (Maneja comillas y enters perfectamente)
      .through(rows[IO, String](';'))
      // 2. Leemos cabeceras (para poder pedir columnas por nombre)
      .through(headers[IO, String])
      // 3. MAPEO MANUAL
      .map { csvRow =>
        // Extraemos los datos por nombre de columna de forma segura
        val title = csvRow.toMap.getOrElse("title", "")
        val originalTitle = csvRow.toMap.getOrElse("original_title", "")
        val crew = csvRow.toMap.getOrElse("crew", "[]")

        (title, originalTitle, crew)
      }
      // 4. Transformación y Limpieza
      .map { case (title, _, crew) =>
        val cleanTitle = sanitizeTitle(title)
        val director = extractDirector(crew)
        (cleanTitle, director)
      }
      // 5. Filtrado
      .filter { case (title, director) =>
        title.nonEmpty && director != "Unknown"
      }
      // 6. Generación SQL
      .map { case (title, director) =>
        s"INSERT INTO movies (title, director) VALUES ('$title', '$director');"
      }

    val sqlHeader = Stream.emit(
      """CREATE TABLE IF NOT EXISTS movies (
        |    id INT AUTO_INCREMENT PRIMARY KEY,
        |    title VARCHAR(500),
        |    director VARCHAR(255)
        |);
        |""".stripMargin
    )

    (sqlHeader ++ processingStream.intersperse("\n"))
      .through(text.utf8.encode)
      .through(Files[IO].writeAll(outputFile))
      .compile
      .drain *> IO.println(s"✅ SQL generado en: $outputFile")
  }
}