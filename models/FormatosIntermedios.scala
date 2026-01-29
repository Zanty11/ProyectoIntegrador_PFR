package models
//Solo las clases para parsear el JSON string
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

case class GenreJson(id: Int, name: String)
object GenreJson { implicit val decoder: Decoder[GenreJson] = deriveDecoder }

case class CompanyJson(id: Int, name: String)
object CompanyJson { implicit val decoder: Decoder[CompanyJson] = deriveDecoder }

case class CountryJson(iso_3166_1: String, name: String)
object CountryJson { implicit val decoder: Decoder[CountryJson] = deriveDecoder }

case class LanguageJson(iso_639_1: String, name: String)
object LanguageJson { implicit val decoder: Decoder[LanguageJson] = deriveDecoder }

case class KeywordJson(id: Int, name: String)
object KeywordJson { implicit val decoder: Decoder[KeywordJson] = deriveDecoder }

case class CollectionJson(id: Int, name: String, poster_path: Option[String], backdrop_path: Option[String])
object CollectionJson { implicit val decoder: Decoder[CollectionJson] = deriveDecoder }

case class CastJson(cast_id: Int, character: String, credit_id: String, gender: Option[Int], id: Int, name: String, order: Int, profile_path: Option[String])
object CastJson { implicit val decoder: Decoder[CastJson] = deriveDecoder }

case class CrewJson(credit_id: String, department: String, gender: Option[Int], id: Int, job: String, name: String, profile_path: Option[String])
object CrewJson { implicit val decoder: Decoder[CrewJson] = deriveDecoder }

case class RatingJson(userId: Int, rating: Double, timestamp: Long)
object RatingJson { implicit val decoder: Decoder[RatingJson] = deriveDecoder }