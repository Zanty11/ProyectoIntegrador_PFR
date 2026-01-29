package models
//Lo que se exportará a CSV para las tablas pivote/links
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*

case class RelPeliGenero(movie_id: Int, genre_id: Int)
object RelPeliGenero { given CsvRowEncoder[RelPeliGenero, String] = deriveCsvRowEncoder; given CsvRowDecoder[RelPeliGenero, String] = deriveCsvRowDecoder }

case class RelMovieCompany(movie_id: Int, company_id: Int)
object RelMovieCompany { given CsvRowEncoder[RelMovieCompany, String] = deriveCsvRowEncoder; given CsvRowDecoder[RelMovieCompany, String] = deriveCsvRowDecoder }

case class RelMovieCountry(movie_id: Int, iso_3166_1: String)
object RelMovieCountry { given CsvRowEncoder[RelMovieCountry, String] = deriveCsvRowEncoder; given CsvRowDecoder[RelMovieCountry, String] = deriveCsvRowDecoder }

case class RelMovieLenguage(movie_id: Int, iso_639_1: String)
object RelMovieLenguage { given CsvRowEncoder[RelMovieLenguage, String] = deriveCsvRowEncoder; given CsvRowDecoder[RelMovieLenguage, String] = deriveCsvRowDecoder }

case class RelKeyword(movie_id: Int, keyword_id: Int)
object RelKeyword { given CsvRowEncoder[RelKeyword, String] = deriveCsvRowEncoder; given CsvRowDecoder[RelKeyword, String] = deriveCsvRowDecoder }

case class RelReparto(movie_id: Int, person_id: Int, character_name: String, order_cast: Int, credit_id: String, cast_id: Int)
object RelReparto { given CsvRowEncoder[RelReparto, String] = deriveCsvRowEncoder; given CsvRowDecoder[RelReparto, String] = deriveCsvRowDecoder }

case class RelStaff(movie_id: Int, person_id: Int, department: String, job: String, credit_id: String)
object RelStaff { given CsvRowEncoder[RelStaff, String] = deriveCsvRowEncoder; given CsvRowDecoder[RelStaff, String] = deriveCsvRowDecoder }

case class RelVoto(movie_id: Int, user_id: Int, rating: Double, timestamp: Long)
object RelVoto { given CsvRowEncoder[RelVoto, String] = deriveCsvRowEncoder; given CsvRowDecoder[RelVoto, String] = deriveCsvRowDecoder }