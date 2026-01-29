package models
//El objeto principal de la película ya procesada
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*

case class MovieClean(
                           adult: Option[Boolean],
                           budget: Option[Double],
                           homepage: Option[String],
                           id: Int,
                           imdb_id: Option[String],
                           original_language: Option[String],
                           original_title: String,
                           overview: Option[String],
                           popularity: Option[Double],
                           poster_path: Option[String],
                           release_date: Option[String],
                           revenue: Option[Double],
                           runtime: Option[Double],
                           status: Option[String],
                           tagline: Option[String],
                           title: String,
                           video: Option[Boolean],
                           vote_average: Option[Double],
                           vote_count: Option[Double],
                           collection_id: Option[Int]
                         )

object MovieClean {
  given CsvRowEncoder[MovieClean, String] = deriveCsvRowEncoder
  given CsvRowDecoder[MovieClean, String] = deriveCsvRowDecoder
}