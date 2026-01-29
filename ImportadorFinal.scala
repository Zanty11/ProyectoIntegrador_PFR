import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import fs2.text
import fs2.Stream
import fs2.io.file.{Files, Path}
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*
import doobie.*
import doobie.implicits.*
import models._

object ImportadorFinal extends IOApp.Simple {

  // ===========================================
  // 1. CONFIGURACIÓN DE CONEXIÓN
  // ===========================================

  // Conexión Root para crear el Schema
  private val conRaiz = Transactor.fromDriverManager[IO](
    driver = "com.mysql.cj.jdbc.Driver",
    url = "jdbc:mysql://localhost:3306/",
    user = "root",
    password = "1234",
    logHandler = None
  )

  // Conexión a la Base de Datos específica
  val conApp = Transactor.fromDriverManager[IO](
    driver = "com.mysql.cj.jdbc.Driver",
    url = "jdbc:mysql://localhost:3306/MoviesDB",
    user = "root",
    password = "1234",
    logHandler = None
  )

  // ===========================================
  // 2. DEFINICIÓN DE ESQUEMA (DDL)
  // ===========================================
  private def inicializarTablas(): ConnectionIO[Unit] = {
    val sentencias = List(
      sql"""SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0""".update.run,
      sql"""SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0""".update.run,
      sql"""SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'""".update.run,

      // Crear Base de Datos
      sql"""CREATE SCHEMA IF NOT EXISTS MoviesDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci""".update.run,
      sql"""USE MoviesDB""".update.run,

      // Tabla: Collection
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Collection (
        collection_id INT NOT NULL,
        name VARCHAR(255) NOT NULL,
        poster_path VARCHAR(255) NOT NULL,
        backdrop_path VARCHAR(255) NOT NULL,
        PRIMARY KEY (collection_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,

      // Tabla: Movie
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Movie (
        movie_id INT NOT NULL,
        title VARCHAR(255) NULL,
        original_title VARCHAR(255) NOT NULL,
        original_language VARCHAR(10) NULL DEFAULT NULL,
        overview TEXT NULL DEFAULT NULL,
        release_date DATE NULL DEFAULT NULL,
        runtime INT NOT NULL,
        budget DECIMAL(15,2) NULL DEFAULT 0,
        revenue DECIMAL(15,2) NULL DEFAULT 0,
        popularity DECIMAL(10,2) NULL DEFAULT 0.0,
        vote_average DECIMAL(3,1) NULL DEFAULT 0,
        vote_count INT NULL DEFAULT NULL,
        adult TINYINT(1) NOT NULL,
        video TINYINT(1) NULL DEFAULT NULL,
        status VARCHAR(50) NULL DEFAULT NULL,
        homepage VARCHAR(255) NULL DEFAULT NULL,
        imdb_id VARCHAR(20) NULL,
        poster_path VARCHAR(255) NULL DEFAULT NULL,
        tagline VARCHAR(255) NULL DEFAULT NULL,
        collection_id INT NULL,
        PRIMARY KEY (movie_id),
        CONSTRAINT fk_movie_collection1
          FOREIGN KEY (collection_id)
          REFERENCES MoviesDB.Collection (collection_id)
          ON DELETE NO ACTION
          ON UPDATE NO ACTION
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX fk_movie_collection1_idx ON MoviesDB.Movie (collection_id ASC) VISIBLE""".update.run,

      // Tabla: Person
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Person (
        person_id INT NOT NULL,
        name VARCHAR(255) NOT NULL,
        gender INT NOT NULL,
        profile_path VARCHAR(255) NULL DEFAULT NULL,
        PRIMARY KEY (person_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,

      // Tabla: Cast
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Cast (
        movie_id INT NOT NULL,
        person_id INT NOT NULL,
        character_name VARCHAR(255) NOT NULL,
        cast_order INT NOT NULL,
        credit_id VARCHAR(50) NOT NULL,
        cast_id INT NOT NULL,
        PRIMARY KEY (movie_id, person_id, cast_id),
        CONSTRAINT cast_ibfk_1
          FOREIGN KEY (movie_id)
          REFERENCES MoviesDB.Movie (movie_id),
        CONSTRAINT cast_ibfk_2
          FOREIGN KEY (person_id)
          REFERENCES MoviesDB.Person (person_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX person_id ON MoviesDB.Cast (person_id ASC) VISIBLE""".update.run,

      // Tabla: Crew
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Crew (
        movie_id INT NOT NULL,
        person_id INT NOT NULL,
        department VARCHAR(100) NOT NULL,
        job VARCHAR(100) NOT NULL,
        credit_id VARCHAR(50) NOT NULL,
        PRIMARY KEY (movie_id, person_id, job),
        CONSTRAINT crew_ibfk_1
          FOREIGN KEY (movie_id)
          REFERENCES MoviesDB.Movie (movie_id),
        CONSTRAINT crew_ibfk_2
          FOREIGN KEY (person_id)
          REFERENCES MoviesDB.Person (person_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX person_id ON MoviesDB.Crew (person_id ASC) VISIBLE""".update.run,

      // Tabla: Genre
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Genre (
        genre_id INT NOT NULL,
        name VARCHAR(50) NOT NULL,
        PRIMARY KEY (genre_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,

      // Tabla: Keyword
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Keyword (
        keyword_id INT NOT NULL,
        name VARCHAR(100) NOT NULL,
        PRIMARY KEY (keyword_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,

      // Tabla: SpokenLanguage
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.SpokenLanguage (
        iso_639_1 VARCHAR(10) NOT NULL,
        name VARCHAR(100) NOT NULL,
        PRIMARY KEY (iso_639_1)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,

      // Tabla: Movie_Genre
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Movie_Genre (
        movie_id INT NOT NULL,
        genre_id INT NOT NULL,
        PRIMARY KEY (movie_id, genre_id),
        CONSTRAINT movie_genre_ibfk_1
          FOREIGN KEY (movie_id)
          REFERENCES MoviesDB.Movie (movie_id),
        CONSTRAINT movie_genre_ibfk_2
          FOREIGN KEY (genre_id)
          REFERENCES MoviesDB.Genre (genre_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX genre_id ON MoviesDB.Movie_Genre (genre_id ASC) VISIBLE""".update.run,

      // Tabla: Movie_Keyword
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Movie_Keyword (
        movie_id INT NOT NULL,
        keyword_id INT NOT NULL,
        PRIMARY KEY (movie_id, keyword_id),
        CONSTRAINT movie_keyword_ibfk_1
          FOREIGN KEY (movie_id)
          REFERENCES MoviesDB.Movie (movie_id),
        CONSTRAINT movie_keyword_ibfk_2
          FOREIGN KEY (keyword_id)
          REFERENCES MoviesDB.Keyword (keyword_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX keyword_id ON MoviesDB.Movie_Keyword (keyword_id ASC) VISIBLE""".update.run,

      // Tabla: Movie_SpokenLanguage
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Movie_SpokenLanguage (
        movie_id INT NOT NULL,
        language_code VARCHAR(10) NOT NULL,
        PRIMARY KEY (movie_id, language_code),
        CONSTRAINT movie_language_ibfk_1
          FOREIGN KEY (movie_id)
          REFERENCES MoviesDB.Movie (movie_id),
        CONSTRAINT movie_language_ibfk_2
          FOREIGN KEY (language_code)
          REFERENCES MoviesDB.SpokenLanguage (iso_639_1)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX language_code ON MoviesDB.Movie_SpokenLanguage (language_code ASC) VISIBLE""".update.run,

      // Tabla: ProductionCompany
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.ProductionCompany (
        company_id INT NOT NULL,
        name VARCHAR(255) NOT NULL,
        PRIMARY KEY (company_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,

      // Tabla: Movie_ProductionCompany
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Movie_ProductionCompany (
        movie_id INT NOT NULL,
        company_id INT NOT NULL,
        PRIMARY KEY (movie_id, company_id),
        CONSTRAINT movie_productioncompany_ibfk_1
          FOREIGN KEY (movie_id)
          REFERENCES MoviesDB.Movie (movie_id),
        CONSTRAINT movie_productioncompany_ibfk_2
          FOREIGN KEY (company_id)
          REFERENCES MoviesDB.ProductionCompany (company_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX company_id ON MoviesDB.Movie_ProductionCompany (company_id ASC) VISIBLE""".update.run,

      // Tabla: ProductionCountry
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.ProductionCountry (
        iso_3166_1 CHAR(2) NOT NULL,
        name VARCHAR(100) NOT NULL,
        PRIMARY KEY (iso_3166_1)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,

      // Tabla: Movie_ProductionCountry
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Movie_ProductionCountry (
        movie_id INT NOT NULL,
        country_code CHAR(2) NOT NULL,
        PRIMARY KEY (movie_id, country_code),
        CONSTRAINT movie_productioncountry_ibfk_1
          FOREIGN KEY (movie_id)
          REFERENCES MoviesDB.Movie (movie_id),
        CONSTRAINT movie_productioncountry_ibfk_2
          FOREIGN KEY (country_code)
          REFERENCES MoviesDB.ProductionCountry (iso_3166_1)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX country_code ON MoviesDB.Movie_ProductionCountry (country_code ASC) VISIBLE""".update.run,

      // Tabla: User
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.User (
        user_id INT NOT NULL,
        PRIMARY KEY (user_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,

      // Tabla: Rating
      sql"""
      CREATE TABLE IF NOT EXISTS MoviesDB.Rating (
        user_id INT NOT NULL,
        movie_id INT NOT NULL,
        rating DECIMAL(2,1) NOT NULL,
        timestamp BIGINT NOT NULL,
        PRIMARY KEY (user_id, movie_id),
        CONSTRAINT rating_ibfk_1
          FOREIGN KEY (user_id)
          REFERENCES MoviesDB.User (user_id),
        CONSTRAINT rating_ibfk_2
          FOREIGN KEY (movie_id)
          REFERENCES MoviesDB.Movie (movie_id)
      ) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
      """.update.run,
      sql"""CREATE INDEX movie_id ON MoviesDB.Rating (movie_id ASC) VISIBLE""".update.run,

      sql"""SET SQL_MODE=@OLD_SQL_MODE""".update.run,
      sql"""SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS""".update.run,
      sql"""SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS""".update.run
    )

    sentencias.traverse_(identity)
  }

  // ===========================================
  // 3. FUNCIONES DE INSERCIÓN INDIVIDUAL
  // ===========================================

  // Película (Usando el nuevo modelo PeliculaLimpia)
  private def insertarPelicula(p: MovieClean): ConnectionIO[Int] =
    sql"""
      INSERT INTO Movie (
        movie_id, title, original_title, original_language, overview, release_date,
        runtime, budget, revenue, popularity, vote_average, vote_count,
        adult, video, status, homepage, imdb_id, poster_path, tagline, collection_id
      )
      VALUES (
        ${p.id}, 
        ${p.title}, 
        ${p.original_title}, 
        ${p.original_language.filter(_.nonEmpty)}, -- <-- AQUÍ ESTABA EL ERROR
        ${p.overview}, 
        ${p.release_date},
        ${p.runtime.map(_.toInt).getOrElse(0)}, 
        ${p.budget}, 
        ${p.revenue}, 
        ${p.popularity},
        ${p.vote_average}, 
        ${p.vote_count}, 
        ${p.adult}, 
        ${p.video}, 
        ${p.status},
        ${p.homepage}, 
        ${p.imdb_id}, 
        ${p.poster_path}, 
        ${p.tagline}, 
        ${p.collection_id}
      )
    """.update.run

  // Entidades Principales
  private def insertarGenero(g: DatoGenero): ConnectionIO[Int] =
    sql"INSERT INTO Genre (genre_id, name) VALUES (${g.genre_id}, ${g.name})".update.run

  private def insertarCompania(c: DatoEmpresa): ConnectionIO[Int] =
    sql"INSERT INTO ProductionCompany (company_id, name) VALUES (${c.company_id}, ${c.name})".update.run

  private def insertarPais(c: DatoPais): ConnectionIO[Int] =
    sql"INSERT INTO ProductionCountry (iso_3166_1, name) VALUES (${c.iso_3166_1}, ${c.name})".update.run

  private def insertarIdioma(l: DatoLenguaje): ConnectionIO[Int] =
    sql"INSERT INTO SpokenLanguage (iso_639_1, name) VALUES (${l.iso_639_1}, ${l.name})".update.run

  private def insertarKeyword(k: DatoKeyword): ConnectionIO[Int] =
    sql"INSERT INTO Keyword (keyword_id, name) VALUES (${k.keyword_id}, ${k.name})".update.run

  private def insertarColeccion(c: DatoCollection): ConnectionIO[Int] =
    sql"INSERT INTO Collection (collection_id, name, poster_path, backdrop_path) VALUES (${c.collection_id}, ${c.name}, ${c.poster}, ${c.backdrop})".update.run

  private def insertarPersona(p: DatoPersona): ConnectionIO[Int] =
    sql"INSERT INTO Person (person_id, name, gender, profile_path) VALUES (${p.person_id}, ${p.name}, ${p.gender}, ${p.profile_path})".update.run

  private def insertarUsuario(u: DatoUsuario): ConnectionIO[Int] =
    sql"INSERT INTO User (user_id) VALUES (${u.user_id})".update.run

  // Relaciones (Links)
  private def insertarLinkGenero(l: RelPeliGenero): ConnectionIO[Int] =
    sql"INSERT INTO Movie_Genre (movie_id, genre_id) VALUES (${l.movie_id}, ${l.genre_id})".update.run

  private def insertarLinkCompania(l: RelMovieCompany): ConnectionIO[Int] =
    sql"INSERT INTO Movie_ProductionCompany (movie_id, company_id) VALUES (${l.movie_id}, ${l.company_id})".update.run

  private def insertarLinkPais(l: RelMovieCountry): ConnectionIO[Int] =
    sql"INSERT INTO Movie_ProductionCountry (movie_id, country_code) VALUES (${l.movie_id}, ${l.iso_3166_1})".update.run

  private def insertarLinkIdioma(l: RelMovieLenguage): ConnectionIO[Int] =
    sql"INSERT INTO Movie_SpokenLanguage (movie_id, language_code) VALUES (${l.movie_id}, ${l.iso_639_1})".update.run

  private def insertarLinkKeyword(l: RelKeyword): ConnectionIO[Int] =
    sql"INSERT INTO Movie_Keyword (movie_id, keyword_id) VALUES (${l.movie_id}, ${l.keyword_id})".update.run

  private def insertarLinkElenco(l: RelReparto): ConnectionIO[Int] =
    sql"INSERT INTO Cast (movie_id, person_id, character_name, cast_order, credit_id, cast_id) VALUES (${l.movie_id}, ${l.person_id}, ${l.character_name}, ${l.order_cast}, ${l.credit_id}, ${l.cast_id})".update.run

  private def insertarLinkStaff(l: RelStaff): ConnectionIO[Int] =
    sql"INSERT INTO Crew (movie_id, person_id, department, job, credit_id) VALUES (${l.movie_id}, ${l.person_id}, ${l.department}, ${l.job}, ${l.credit_id})".update.run

  private def insertarRating(r: RelVoto): ConnectionIO[Int] =
    sql"INSERT INTO Rating (user_id, movie_id, rating, timestamp) VALUES (${r.user_id}, ${r.movie_id}, ${r.rating}, ${r.timestamp})".update.run

  // ===========================================
  // 4. FUNCIONES DE PROCESAMIENTO EN LOTE (BATCH)
  // ===========================================
  // Reciben Listas y ejecutan en una sola transacción pequeña

  private def lotePeliculas(xs: List[MovieClean]): ConnectionIO[Int] = xs.traverse(insertarPelicula).map(_.sum)
  private def loteGeneros(xs: List[DatoGenero]): ConnectionIO[Int] = xs.traverse(insertarGenero).map(_.sum)
  private def loteCompanias(xs: List[DatoEmpresa]): ConnectionIO[Int] = xs.traverse(insertarCompania).map(_.sum)
  private def lotePaises(xs: List[DatoPais]): ConnectionIO[Int] = xs.traverse(insertarPais).map(_.sum)
  private def loteIdiomas(xs: List[DatoLenguaje]): ConnectionIO[Int] = xs.traverse(insertarIdioma).map(_.sum)
  private def loteKeywords(xs: List[DatoKeyword]): ConnectionIO[Int] = xs.traverse(insertarKeyword).map(_.sum)
  private def loteColecciones(xs: List[DatoCollection]): ConnectionIO[Int] = xs.traverse(insertarColeccion).map(_.sum)
  private def lotePersonas(xs: List[DatoPersona]): ConnectionIO[Int] = xs.traverse(insertarPersona).map(_.sum)
  private def loteUsuarios(xs: List[DatoUsuario]): ConnectionIO[Int] = xs.traverse(insertarUsuario).map(_.sum)

  // Lotes de Relaciones
  private def loteLinkGeneros(xs: List[RelPeliGenero]): ConnectionIO[Int] = xs.traverse(insertarLinkGenero).map(_.sum)
  private def loteLinkCompanias(xs: List[RelMovieCompany]): ConnectionIO[Int] = xs.traverse(insertarLinkCompania).map(_.sum)
  private def loteLinkPaises(xs: List[RelMovieCountry]): ConnectionIO[Int] = xs.traverse(insertarLinkPais).map(_.sum)
  private def loteLinkIdiomas(xs: List[RelMovieLenguage]): ConnectionIO[Int] = xs.traverse(insertarLinkIdioma).map(_.sum)
  private def loteLinkKeywords(xs: List[RelKeyword]): ConnectionIO[Int] = xs.traverse(insertarLinkKeyword).map(_.sum)
  private def loteLinkElenco(xs: List[RelReparto]): ConnectionIO[Int] = xs.traverse(insertarLinkElenco).map(_.sum)
  private def loteLinkStaff(xs: List[RelStaff]): ConnectionIO[Int] = xs.traverse(insertarLinkStaff).map(_.sum)
  private def loteRatings(xs: List[RelVoto]): ConnectionIO[Int] = xs.traverse(insertarRating).map(_.sum)

  // ===========================================
  // 5. LECTURA DE CSV (Desde nueva ruta plana 'data/')
  // ===========================================
  def leerDatos[T](archivo: String)(using decoder: CsvRowDecoder[T, String]): IO[List[T]] = {
    // Apuntamos directo a data/, ya no existe 'normalized'
    val ruta = Path(s"src/main/resources/data/$archivo")
    Files[IO]
      .readAll(ruta)
      .through(text.utf8.decode)
      .through(decodeUsingHeaders[T](','))
      .compile
      .toList
  }

  // ===========================================
  // 6. FLUJO PRINCIPAL (MAIN)
  // ===========================================
  override def run: IO[Unit] = for {
    _ <- IO.println("\n" + "="*60)
    _ <- IO.println("      INICIANDO MIGRACIÓN DE DATOS A MYSQL")
    _ <- IO.println("="*60 + "\n")

    // 1. Crear Tablas
    _ <- IO.println("[1/4] Preparando esquema de base de datos...")
    _ <- inicializarTablas().transact(conRaiz)
    _ <- IO.println("      ✓ Tablas verificadas/creadas.")

    // 2. Cargar Entidades (Catálogos)
    _ <- IO.println("\n[2/4] Cargando catálogos maestros...")

    lGeneros <- leerDatos[DatoGenero]("entidad_generos.csv")
    _ <- Stream.emits(lGeneros).covary[IO].chunkN(1000).evalMap(c => loteGeneros(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Géneros: ${lGeneros.size}")

    lCompanias <- leerDatos[DatoEmpresa]("entidad_companias.csv")
    _ <- Stream.emits(lCompanias).covary[IO].chunkN(1000).evalMap(c => loteCompanias(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Compañías: ${lCompanias.size}")

    lPaises <- leerDatos[DatoPais]("entidad_paises.csv")
    _ <- Stream.emits(lPaises).covary[IO].chunkN(1000).evalMap(c => lotePaises(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Países: ${lPaises.size}")

    lIdiomas <- leerDatos[DatoLenguaje]("entidad_idiomas.csv")
    _ <- Stream.emits(lIdiomas).covary[IO].chunkN(1000).evalMap(c => loteIdiomas(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Idiomas: ${lIdiomas.size}")

    lKeywords <- leerDatos[DatoKeyword]("entidad_keywords.csv")
    _ <- Stream.emits(lKeywords).covary[IO].chunkN(1000).evalMap(c => loteKeywords(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Keywords: ${lKeywords.size}")

    lColecciones <- leerDatos[DatoCollection]("entidad_colecciones.csv")
    _ <- Stream.emits(lColecciones).covary[IO].chunkN(1000).evalMap(c => loteColecciones(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Colecciones: ${lColecciones.size}")

    lPersonas <- leerDatos[DatoPersona]("entidad_personas.csv")
    _ <- Stream.emits(lPersonas).covary[IO].chunkN(1000).evalMap(c => lotePersonas(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Personas: ${lPersonas.size}")

    lUsuarios <- leerDatos[DatoUsuario]("entidad_usuarios.csv")
    _ <- Stream.emits(lUsuarios).covary[IO].chunkN(1000).evalMap(c => loteUsuarios(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Usuarios: ${lUsuarios.size}")

    // 3. Cargar Películas
    _ <- IO.println("\n[3/4] Cargando base de películas...")
    lPeliculas <- leerDatos[MovieClean]("entidad_peliculas.csv")
    _ <- Stream.emits(lPeliculas).covary[IO].chunkN(500).evalMap(c => lotePeliculas(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Películas: ${lPeliculas.size}")

    // 4. Cargar Relaciones
    _ <- IO.println("\n[4/4] Estableciendo relaciones...")

    rGen <- leerDatos[RelPeliGenero]("relacion_pelicula_genero.csv")
    _ <- Stream.emits(rGen).covary[IO].chunkN(1000).evalMap(c => loteLinkGeneros(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Relaciones Género: ${rGen.size}")

    rComp <- leerDatos[RelMovieCompany]("relacion_pelicula_compania.csv")
    _ <- Stream.emits(rComp).covary[IO].chunkN(1000).evalMap(c => loteLinkCompanias(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Relaciones Compañía: ${rComp.size}")

    rPais <- leerDatos[RelMovieCountry]("relacion_pelicula_pais.csv")
    _ <- Stream.emits(rPais).covary[IO].chunkN(1000).evalMap(c => loteLinkPaises(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Relaciones País: ${rPais.size}")

    rIdio <- leerDatos[RelMovieLenguage]("relacion_pelicula_idioma.csv")
    _ <- Stream.emits(rIdio).covary[IO].chunkN(1000).evalMap(c => loteLinkIdiomas(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Relaciones Idioma: ${rIdio.size}")

    rKeys <- leerDatos[RelKeyword]("relacion_pelicula_keyword.csv")
    _ <- Stream.emits(rKeys).covary[IO].chunkN(1000).evalMap(c => loteLinkKeywords(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Relaciones Keyword: ${rKeys.size}")

    rCast <- leerDatos[RelReparto]("relacion_pelicula_elenco.csv")
    _ <- Stream.emits(rCast).covary[IO].chunkN(1000).evalMap(c => loteLinkElenco(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Relaciones Elenco: ${rCast.size}")

    rCrew <- leerDatos[RelStaff]("relacion_pelicula_staff.csv")
    _ <- Stream.emits(rCrew).covary[IO].chunkN(1000).evalMap(c => loteLinkStaff(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Relaciones Staff: ${rCrew.size}")

    rRat <- leerDatos[RelVoto]("relacion_pelicula_rating.csv")
    _ <- Stream.emits(rRat).covary[IO].chunkN(2000).evalMap(c => loteRatings(c.toList).transact(conApp)).compile.drain
    _ <- IO.println(s"      -> Ratings: ${rRat.size}")

    _ <- IO.println("\n" + "="*60)
    _ <- IO.println("      ¡MIGRACIÓN FINALIZADA CON ÉXITO!")
    _ <- IO.println("="*60)
  } yield ()
}