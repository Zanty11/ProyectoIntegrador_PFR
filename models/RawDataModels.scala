package models
//Aquí va solo lo que lees del CSV original
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*

case class MovieRaw(
                          adult: String,
                          belongs_to_collection: Option[String],
                          budget: String,
                          genres: String,
                          homepage: Option[String],
                          id: String,
                          imdb_id: Option[String],
                          original_language: String,
                          original_title: String,
                          overview: Option[String],
                          popularity: String,
                          poster_path: Option[String],
                          production_companies: String,
                          production_countries: String,
                          release_date: Option[String],
                          revenue: String,
                          runtime: Option[String],
                          spoken_languages: String,
                          status: Option[String],
                          tagline: Option[String],
                          title: String,
                          video: String,
                          vote_average: String,
                          vote_count: String,
                          keywords: String,
                          cast: String,
                          crew: String,
                          ratings: String
                        )

object MovieRaw {
  given CellDecoder[Option[String]] = CellDecoder[String].map(s => if (s.trim.isEmpty) None else Some(s.trim))
  given CsvRowDecoder[MovieRaw, String] = deriveCsvRowDecoder[MovieRaw]
}