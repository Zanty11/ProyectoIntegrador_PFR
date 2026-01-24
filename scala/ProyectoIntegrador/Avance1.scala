import scala.io.Source
import scala.util.Try


// Definición de la estructura de datos (Schema)
case class Peliculas(
                      budget: Double,
                      popularity: Double,
                      revenue: Double,
                      runtime: Double,
                      vote_average: Double,
                      vote_count: Double,
                      id: Double,
                      original_language: String
                    )

object PeliculasStats extends App {

  // CONFIGURACIÓN: Ruta del archivo
  val filePath = "src/main/resources/data/pi_movies_small.csv"

  println("================================================================")
  println(s"PROCESANDO ARCHIVO: $filePath")
  println("================================================================\n")

  // 1. LECTURA Y PARSEO MANUAL (Estrategia Robustez)
  val peliculasRaw = try {
    val source = Source.fromFile(filePath, "UTF-8")
    val lines = source.getLines().drop(1).toList // Omitir encabezado
    val data = lines.map { line =>
      val cols = line.split(";")

      // Función auxiliar para limpieza de numéricos
      def getDouble(index: Int): Double = {
        if (index < cols.length)
          Try(cols(index).trim.replace(",", ".").toDouble).getOrElse(-1.0)
        else -1.0
      }

      // Mapeo de columnas según índice CSV
      Peliculas(
        budget        = getDouble(2),
        id            = getDouble(5),
        original_language = if (cols.length > 7) cols(7).trim else "Unknown",
        popularity    = getDouble(10),
        revenue       = getDouble(15),
        runtime       = getDouble(16),
        vote_average  = getDouble(22),
        vote_count    = getDouble(23)
      )
    }
    source.close()
    data
  } catch {
    case e: Exception =>
      println(s"[ERROR CRÍTICO] No se pudo leer el archivo: ${e.getMessage}")
      List.empty
  }

  // 2. LIMPIEZA DE DATOS (Data Cleaning)
  // Criterio: Runtime > 0 y Budget >= 0
  val peliculasLimpias = peliculasRaw.filter(p => p.runtime > 0 && p.budget >= 0)
  val eliminados = peliculasRaw.size - peliculasLimpias.size

  println(f"--- REPORTE DE LIMPIEZA DE DATOS ---")
  println(f"Total Registros Leídos:      ${peliculasRaw.size}%5d")
  println(f"Registros Excluidos (Sucios): $eliminados%5d")
  println(f"Registros Válidos para Uso:  ${peliculasLimpias.size}%5d\n")

  // 2.5 VERIFICACIÓN VISUAL (PUNTO 2: LECTURA)

  println("--- MUESTRA DE LECTURA DE COLUMNAS (PRIMEROS 5 REGISTROS) ---")

  // Función auxiliar para imprimir bonito
  def imprimirMuestra(nombre: String, datos: Seq[Any]): Unit = {
    println(f"$nombre%-15s: ${datos.take(5).mkString(", ")}")
  }

  imprimirMuestra("Budget", peliculasLimpias.map(_.budget))
  imprimirMuestra("Revenue", peliculasLimpias.map(_.revenue))
  imprimirMuestra("Runtime", peliculasLimpias.map(_.runtime))
  imprimirMuestra("Popularity", peliculasLimpias.map(_.popularity))
  imprimirMuestra("Vote Avg", peliculasLimpias.map(_.vote_average))

  println("-" * 72 + "\n")

  // 3. CÁLCULO DE ESTADÍSTICAS
  def calcularEstadisticas(datos: Seq[Double]): Map[String, Double] = {
    if (datos.isEmpty) Map.empty
    else {
      val n = datos.size
      val mean = datos.sum / n
      val variance = datos.map(x => math.pow(x - mean, 2)).sum / n
      val sorted = datos.sorted

      Map(
        "mean" -> mean,
        "std"  -> math.sqrt(variance),
        "min"  -> sorted.head,
        "max"  -> sorted.last,
        "25%"  -> sorted((0.25 * (n - 1)).toInt),
        "50%"  -> sorted((0.50 * (n - 1)).toInt),
        "75%"  -> sorted((0.75 * (n - 1)).toInt)
      )
    }
  }

  val statsMap = Map(
    "Budget"       -> calcularEstadisticas(peliculasLimpias.map(_.budget)),
    "Revenue"      -> calcularEstadisticas(peliculasLimpias.map(_.revenue)),
    "Runtime"      -> calcularEstadisticas(peliculasLimpias.map(_.runtime)),
    "Popularity"   -> calcularEstadisticas(peliculasLimpias.map(_.popularity)),
    "Vote Avg"     -> calcularEstadisticas(peliculasLimpias.map(_.vote_average))
  )

  // 4. IMPRESIÓN DE TABLA NUMÉRICA
  println("--- ANÁLISIS ESTADÍSTICO (VARIABLES NUMÉRICAS) ---")
  val headerFormat = "| %-12s | %-12s | %-12s | %-10s | %-12s |"
  val rowFormat    = "| %-12s | %12.2f | %12.2f | %10.2f | %12.2f |"

  println("-" * 72)
  println(String.format(headerFormat, "COLUMNA", "MEDIA", "STD DEV", "MIN", "MAX"))
  println("-" * 72)

  statsMap.foreach { case (k, v) =>
    println(String.format(rowFormat, k, v("mean"), v("std"), v("min"), v("max")))
  }
  println("-" * 72 + "\n")

  // 5. ANÁLISIS DE FRECUENCIA (TEXTO)
  println("--- ANÁLISIS DE FRECUENCIA (IDIOMA ORIGINAL) ---")
  val frecuencias = peliculasLimpias
    .map(_.original_language)
    .groupBy(identity)
    .map { case (k, v) => (k, v.size) }
    .toList
    .sortBy(-_._2)

  println(f"${"IDIOMA"}%-10s | ${"CANTIDAD"}")
  println("-" * 22)
  frecuencias.take(10).foreach { case (idioma, count) =>
    println(f"$idioma%-10s | $count%4d")
  }
}