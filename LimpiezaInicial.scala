import cats.effect.{IO, IOApp}
import fs2.Stream
import fs2.io.file.{Files, Path}
import fs2.text
import fs2.data.csv._
import fs2.data.csv.lenient._
import models.{MovieRaw, MovieClean, CollectionJson}
import io.circe.parser.parse
import java.time.LocalDate
import scala.util.Try

// Objeto interno para reemplazar el package utilities
object ReglasSanitizacion {
  def limpiarTexto(s: String): String = {
    if (s == null) ""
    else s.trim.replaceAll("[\r\n]+", " ").replaceAll("\"", "'").trim
  }

  def validarBool(s: String): Option[Boolean] = s.trim.toLowerCase match {
    case "true" => Some(true)
    case "false" => Some(false)
    case _ => None
  }


  def validarDouble(s: String): Option[Double] = s.trim.toDoubleOption.filter(_ >= 0)

  def validarUrl(opt: Option[String]): Option[String] = opt.map(_.trim).filter(_.startsWith("http"))

  def validarEnteroId(s: String): Option[Int] = {
    val limpio = s.trim.takeWhile(_ != '.')
    limpio.toIntOption.filter(_ > 0)
  }

  def validarImdb(opt: Option[String]): Option[String] = opt.map(_.trim).filter(_.startsWith("tt"))

  def validarLenguaje(s: String): Option[String] = {
    val t = s.trim
    if (t.matches("^[a-zA-Z]{2}$")) Some(t) else None
  }

  def validarEstado(opt: Option[String]): Option[String] = {
    val ok = Set("In Production", "Post Production", "Released", "Rumored")
    opt.map(_.trim).flatMap { s =>
      if (ok.contains(s)) Some(s)
      else if (s == "In Produ") Some("In Production")
      else if (s == "Post Pro") Some("Post Production")
      else None
    }
  }

  def validarRuta(opt: Option[String]): Option[String] = opt.map(_.trim).filter(_.startsWith("/"))

  def validarFecha(opt: Option[String]): Option[String] = opt.map(_.trim).filter(_.matches("^\\d{4}-\\d{2}-\\d{2}$"))

  // Pequeño helper para JSON simple de colecciones aquí mismo
  def extraerIdColeccion(jsonRaw: String): Option[Int] = {
    val limpio = jsonRaw.replace("'", "\"").replaceAll("None", "null")
    parse(limpio).flatMap(_.as[CollectionJson]).map(_.id).toOption
  }
}

object ProcesamientoBase extends IOApp.Simple {


  val rutaEntrada = Path("C:\\Programacion Funcional y Reactiva\\matutexd\\src\\main\\resources\\data\\pi-movies-complete-2025-12-04.csv")
  val rutaSalida = Path("C:\\Programacion Funcional y Reactiva\\matutexd\\src\\main\\resources\\data\\entidad_peliculas.csv")
  def procesarFila(raw: MovieRaw): Option[MovieClean] = {
    val idCheck = ReglasSanitizacion.validarEnteroId(raw.id)

    idCheck.map { id =>
      val colId = raw.belongs_to_collection.flatMap(ReglasSanitizacion.extraerIdColeccion)

      MovieClean(
        adult             = ReglasSanitizacion.validarBool(raw.adult),
        budget            = ReglasSanitizacion.validarDouble(raw.budget),
        homepage          = ReglasSanitizacion.validarUrl(raw.homepage),
        id                = id,
        imdb_id           = ReglasSanitizacion.validarImdb(raw.imdb_id),
        original_language = ReglasSanitizacion.validarLenguaje(raw.original_language),
        original_title    = ReglasSanitizacion.limpiarTexto(if(raw.original_title.trim.nonEmpty) raw.original_title else "Sin Titulo"),
        overview          = raw.overview.map(ReglasSanitizacion.limpiarTexto).filter(_.nonEmpty),
        popularity        = ReglasSanitizacion.validarDouble(raw.popularity),
        poster_path       = ReglasSanitizacion.validarRuta(raw.poster_path),
        release_date      = ReglasSanitizacion.validarFecha(raw.release_date),
        revenue           = ReglasSanitizacion.validarDouble(raw.revenue),
        runtime           = raw.runtime.flatMap(ReglasSanitizacion.validarDouble),
        status            = ReglasSanitizacion.validarEstado(raw.status),
        tagline           = raw.tagline.map(ReglasSanitizacion.limpiarTexto).filter(_.nonEmpty),
        title             = ReglasSanitizacion.limpiarTexto(if(raw.title.trim.nonEmpty) raw.title else "Sin Titulo"),
        video             = ReglasSanitizacion.validarBool(raw.video),
        vote_average      = ReglasSanitizacion.validarDouble(raw.vote_average),
        vote_count        = ReglasSanitizacion.validarDouble(raw.vote_count),
        collection_id     = colId
      )
    }
  }

  val run: IO[Unit] = {
    IO.println("--- INICIANDO PROCESO DE DEPURACIÓN DE DATOS ---")

    val flujoLectura = Files[IO].readAll(rutaEntrada)
      .through(text.utf8.decode)
      .through(attemptDecodeUsingHeaders[MovieRaw](';'))
      .collect { case Right(p) => p }

    flujoLectura.compile.toList.flatMap { listaBruta =>
      println(s"-> Registros leídos: ${listaBruta.size}")

      val validas = listaBruta.flatMap(procesarFila)

      // Deduplicar
      val unicas = validas
        .groupBy(_.id)
        .map { case (_, items) => items.head }
        .toList
        .sortBy(_.id)

      println(s"-> Registros válidos y únicos: ${unicas.size}")

      if (unicas.nonEmpty) {
        Stream.emits(unicas)
          .covary[IO]
          .through(encodeUsingFirstHeaders(fullRows = true))
          .through(text.utf8.encode)
          .through(Files[IO].writeAll(rutaSalida))
          .compile.drain *> IO.println(s"-> Archivo generado exitosamente en: $rutaSalida")
      } else {
        IO.println("-> ALERTA: No se generaron datos.")
      }
    }
  }
}