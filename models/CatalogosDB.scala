package models
//Lo que se exportará a CSV para las tablas maestras
import fs2.data.csv.*
import fs2.data.csv.generic.semiauto.*

case class DatoGenero(genre_id: Int, name: String)
object DatoGenero { given CsvRowEncoder[DatoGenero, String] = deriveCsvRowEncoder; given CsvRowDecoder[DatoGenero, String] = deriveCsvRowDecoder }

case class DatoEmpresa(company_id: Int, name: String)
object DatoEmpresa { given CsvRowEncoder[DatoEmpresa, String] = deriveCsvRowEncoder; given CsvRowDecoder[DatoEmpresa, String] = deriveCsvRowDecoder }

case class DatoPais(iso_3166_1: String, name: String)
object DatoPais { given CsvRowEncoder[DatoPais, String] = deriveCsvRowEncoder; given CsvRowDecoder[DatoPais, String] = deriveCsvRowDecoder }

case class DatoLenguaje(iso_639_1: String, name: String)
object DatoLenguaje { given CsvRowEncoder[DatoLenguaje, String] = deriveCsvRowEncoder; given CsvRowDecoder[DatoLenguaje, String] = deriveCsvRowDecoder }

case class DatoKeyword(keyword_id: Int, name: String)
object DatoKeyword { given CsvRowEncoder[DatoKeyword, String] = deriveCsvRowEncoder; given CsvRowDecoder[DatoKeyword, String] = deriveCsvRowDecoder }

case class DatoCollection(collection_id: Int, name: String, poster: String, backdrop: String)
object DatoCollection { given CsvRowEncoder[DatoCollection, String] = deriveCsvRowEncoder; given CsvRowDecoder[DatoCollection, String] = deriveCsvRowDecoder }

case class DatoPersona(person_id: Int, name: String, gender: Int, profile_path: String)
object DatoPersona { given CsvRowEncoder[DatoPersona, String] = deriveCsvRowEncoder; given CsvRowDecoder[DatoPersona, String] = deriveCsvRowDecoder }

case class DatoUsuario(user_id: Int)
object DatoUsuario { given CsvRowEncoder[DatoUsuario, String] = deriveCsvRowEncoder; given CsvRowDecoder[DatoUsuario, String] = deriveCsvRowDecoder }