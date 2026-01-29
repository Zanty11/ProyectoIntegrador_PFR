import cats.effect.{IO, IOApp}
import fs2.Stream
import fs2.io.file.{Files, Path}
import fs2.text
import fs2.data.csv._
import fs2.data.csv.lenient._
import models._
import io.circe.parser._
import io.circe.Decoder

// Lógica de limpieza JSON integrada aquí para no usar 'utilities'
object ReparadorJson {
  def leerLista[T](raw: String)(implicit d: Decoder[List[T]]): List[T] = {
    val safe = arreglarFormato(raw, esLista = true)
    parse(safe).flatMap(_.as[List[T]]).getOrElse(Nil)
  }

  def leerObjeto[T](raw: String)(implicit d: Decoder[T]): Option[T] = {
    val safe = arreglarFormato(raw, esLista = false)
    parse(safe).flatMap(_.as[T]).toOption
  }

  private def arreglarFormato(raw: String, esLista: Boolean): String = {
    var txt = raw.trim
    if (txt.isEmpty || txt == "[]" || txt == "{}") return if (esLista) "[]" else "{}"

    txt = txt.replaceAll("None", "null")
      .replaceAll("True", "true")
      .replaceAll("False", "false")
      .replace("'", "\"")

    if (esLista && txt.startsWith("[") && !txt.endsWith("]")) {
      val cierre = txt.lastIndexOf("}")
      if (cierre != -1) txt = txt.substring(0, cierre + 1) + "]"
      else txt = "[]"
    }
    txt
  }
}

object ExtractorRelaciones extends IOApp.Simple {

  val origen = Path("C:\\Programacion Funcional y Reactiva\\matutexd\\src\\main\\resources\\data\\pi-movies-complete-2025-12-04.csv")
  // Guardamos directo en data
  val carpetaDestino = "C:\\Programacion Funcional y Reactiva\\matutexd\\src\\main\\resources\\data"

  def exportarCsv[T](lista: List[T], nombre: String)(implicit enc: CsvRowEncoder[T, String]): IO[Unit] = {
    if (lista.isEmpty) IO.println(s"Aviso: $nombre no tiene datos.")
    else {
      val p = Path(carpetaDestino) / nombre
      Stream.emits(lista).covary[IO]
        .through(encodeUsingFirstHeaders(fullRows = true))
        .through(text.utf8.encode)
        .through(Files[IO].writeAll(p))
        .compile.drain *> IO.println(s"  [OK] Exportado: $nombre (${lista.size} filas)")
    }
  }

  // Helpers de deduplicación
  def unicosPorInt[T](l: List[T])(fn: T => Int): List[T] = l.groupBy(fn).map(_._2.head).toList.sortBy(fn)
  def unicosPorStr[T](l: List[T])(fn: T => String): List[T] = l.groupBy(fn).map(_._2.head).toList.sortBy(fn)

  val run: IO[Unit] = {
    println("--- COMIENZO DE EXTRACCIÓN Y NORMALIZACIÓN DE ENTIDADES ---")

    val streamLectura = Files[IO].readAll(origen)
      .through(text.utf8.decode)
      .through(attemptDecodeUsingHeaders[MovieRaw](';'))
      .collect { case Right(x) => x }

    streamLectura.compile.toList.flatMap { crudos =>

      // Filtro inicial de IDs
      val validos = crudos.filter(_.id.trim.toIntOption.exists(_ > 0))
      val peliculas = validos.groupBy(_.id.trim.toInt).map(_._2.head).toList.sortBy(_.id.trim.toInt)

      println(s"Procesando ${peliculas.size} películas únicas...")

      // 1. GENEROS
      val dataGeneros = peliculas.flatMap { p =>
        val pid = p.id.trim.toInt
        ReparadorJson.leerLista[GenreJson](p.genres).map { g =>
          (DatoGenero(g.id, g.name), RelPeliGenero(pid, g.id))
        }
      }
      val generosUnicos = unicosPorInt(dataGeneros.map(_._1))(_.genre_id)
      val linksGeneros = dataGeneros.map(_._2)

      // 2. COMPAÑIAS
      val dataComps = peliculas.flatMap { p =>
        val pid = p.id.trim.toInt
        ReparadorJson.leerLista[CompanyJson](p.production_companies).map { c =>
          (DatoEmpresa(c.id, c.name), RelMovieCompany(pid, c.id))
        }
      }
      val compUnicas = unicosPorInt(dataComps.map(_._1))(_.company_id)
      val linksComps = dataComps.map(_._2)

      // 3. PAISES
      val dataPaises = peliculas.flatMap { p =>
        val pid = p.id.trim.toInt
        ReparadorJson.leerLista[CountryJson](p.production_countries).map { c =>
          (DatoPais(c.iso_3166_1, c.name), RelMovieCountry(pid, c.iso_3166_1))
        }
      }
      val paisesUnicos = unicosPorStr(dataPaises.map(_._1))(_.iso_3166_1)
      val linksPaises = dataPaises.map(_._2)

      // 4. IDIOMAS
      val dataIdiomas = peliculas.flatMap { p =>
        val pid = p.id.trim.toInt
        ReparadorJson.leerLista[LanguageJson](p.spoken_languages).map { l =>
          (DatoLenguaje(l.iso_639_1, l.name), RelMovieLenguage(pid, l.iso_639_1))
        }
      }
      val idiomasUnicos = unicosPorStr(dataIdiomas.map(_._1))(_.iso_639_1)
      val linksIdiomas = dataIdiomas.map(_._2)

      // 5. KEYWORDS
      val dataKeys = peliculas.flatMap { p =>
        val pid = p.id.trim.toInt
        ReparadorJson.leerLista[KeywordJson](p.keywords).map { k =>
          (DatoKeyword(k.id, k.name), RelKeyword(pid, k.id))
        }
      }
      val keysUnicas = unicosPorInt(dataKeys.map(_._1))(_.keyword_id)
      val linksKeys = dataKeys.map(_._2)

      // 6. COLECCIONES
      val dataCol = peliculas.flatMap { p =>
        p.belongs_to_collection.flatMap(r => ReparadorJson.leerObjeto[CollectionJson](r)).map { c =>
          DatoCollection(c.id, c.name, c.poster_path.getOrElse(""), c.backdrop_path.getOrElse(""))
        }
      }
      val colUnicas = unicosPorInt(dataCol)(_.collection_id)

      // 7. PERSONAS (CAST & CREW)
      val dataCast = peliculas.flatMap { p =>
        val pid = p.id.trim.toInt
        ReparadorJson.leerLista[CastJson](p.cast).map { c =>
          (DatoPersona(c.id, c.name, c.gender.getOrElse(0), c.profile_path.getOrElse("")),
            RelReparto(pid, c.id, c.character, c.order, c.credit_id, c.cast_id))
        }
      }
      val dataCrew = peliculas.flatMap { p =>
        val pid = p.id.trim.toInt
        ReparadorJson.leerLista[CrewJson](p.crew).map { c =>
          (DatoPersona(c.id, c.name, c.gender.getOrElse(0), c.profile_path.getOrElse("")),
            RelStaff(pid, c.id, c.department, c.job, c.credit_id))
        }
      }
      val personasUnicas = unicosPorInt((dataCast.map(_._1) ++ dataCrew.map(_._1)))(_.person_id)
      val linksCast = dataCast.map(_._2)
      val linksCrew = dataCrew.map(_._2)

      // 8. USUARIOS Y RATINGS
      val dataRatings = peliculas.flatMap { p =>
        val pid = p.id.trim.toInt
        ReparadorJson.leerLista[RatingJson](p.ratings).map { r =>
          (DatoUsuario(r.userId), RelVoto(pid, r.userId, r.rating, r.timestamp))
        }
      }
      val usuariosUnicos = dataRatings.map(_._1).distinct
      val linksRatings = dataRatings.map(_._2)

      println(s"Guardando archivos CSV en: $carpetaDestino")

      for {
        // === ENTIDADES (entidad_nombre.csv) ===
        _ <- exportarCsv(colUnicas,   "entidad_colecciones.csv")
        _ <- exportarCsv(compUnicas,  "entidad_companias.csv")
        _ <- exportarCsv(paisesUnicos,"entidad_paises.csv")
        _ <- exportarCsv(generosUnicos, "entidad_generos.csv")
        _ <- exportarCsv(keysUnicas,    "entidad_keywords.csv")
        _ <- exportarCsv(idiomasUnicos, "entidad_idiomas.csv")
        _ <- exportarCsv(personasUnicas,"entidad_personas.csv")
        _ <- exportarCsv(usuariosUnicos,"entidad_usuarios.csv")

        // === RELACIONES (relacion_nombre1_nombre2.csv) ===
        _ <- exportarCsv(linksCast,    "relacion_pelicula_elenco.csv")
        _ <- exportarCsv(linksComps,   "relacion_pelicula_compania.csv")
        _ <- exportarCsv(linksPaises,  "relacion_pelicula_pais.csv")
        _ <- exportarCsv(linksCrew,    "relacion_pelicula_staff.csv")
        _ <- exportarCsv(linksGeneros, "relacion_pelicula_genero.csv")
        _ <- exportarCsv(linksKeys,    "relacion_pelicula_keyword.csv")
        _ <- exportarCsv(linksIdiomas, "relacion_pelicula_idioma.csv")
        _ <- exportarCsv(linksRatings, "relacion_pelicula_rating.csv")

        _ <- IO.println("\n*** PROCESO TERMINADO CORRECTAMENTE ***")
      } yield ()
    }
  }
}