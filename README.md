# ProyectoIntegrador_PFR
Repositorio del proyecto integrador - Programación funcional y reactica

Reporte de Avance: Procesamiento de Estructuras Complejas (JSON)
Asignatura: Programación Funcional y Reactiva

Objetivos:

Implementar estrategias de lectura robusta para archivos CSV complejos.

Utilizar la librería Circe para el parseo de estructuras JSON anidadas.

Extraer información específica (Directores) de columnas de texto no estructurado.

1. Definición de Datos y Estructuras
En este avance, el análisis se centra en las columnas que contienen estructuras de datos anidadas. A continuación se describe el mapeo de los datos crudos a los tipos de Scala:

| Campo (CSV/JSON) | Tipo de Dato (Scala) | Estructura | Propósito en el Análisis |
| :--- | :---: | :---: | :--- |
| `title` | String | Plana | Identificador principal de la película. |
| `crew` | List[Crew] | JSON Array | Contiene la lista completa del staff de producción. |
| `job` | Option[String] | Campo JSON | Filtro clave para identificar el rol (ej. "Director"). |
| `name` | Option[String] | Campo JSON | Dato objetivo a extraer (Nombre del Director). |
| `id` | Option[Int] | Campo JSON | Identificador numérico (se maneja como opcional). |

## 2. Estrategia de Lectura (Regex vs Split)
El desafío técnico principal radica en que el CSV utiliza punto y coma (`;`) como separador, pero este caracter también aparece dentro de los textos entrecomillados, lo que rompe una lectura tradicional con `split`.

**Implementación Técnica:**
Se reemplazó el parseo simple por una **Expresión Regular (Regex)** que discrimina el contexto del separador.

* **Problema:** `split(";")` corta cadenas como `"Dracula; Dead and Loving It"`
* **Solución:** Usar un *Lookahead* positivo en la Regex

```scala
// Regex: Corta por ; SOLO si no está seguido por un número impar de comillas
val csvSplitter: Regex = ";(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".r
```

## 3. Análisis de Datos Numéricos (Estadísticas)
El análisis estadístico descriptivo es fundamental para comprender la distribución y las tendencias centrales del dataset. Se implementaron algoritmos de agregación para calcular métricas sobre las dimensiones financieras (Budget, Revenue) y temporales (Runtime)

Implementación Técnica: Se utilizaron funciones de orden superior sobre colecciones inmutables para calcular la media aritmética, desviación estándar y valores extremos,manejando la ausencia de datos mediante tipos seguros (Option/Try)

Problema: La existencia de valores atípicos (outliers) y escalas dispares entre películas independientes y blockbusters

Solución: Cálculo de desviación estándar y rangos para identificar la dispersión real de los datos

| Variable | Métrica | Valor Calculado | Interpretación |
| :--- | :---: | :---: | :--- |
| **Runtime** | Promedio | 105.30 min | Duración estándar. |
| **Runtime** | Máximo | 338.00 min | Valores extremos detectados. |
| **Budget** | Desviación | 35.0 M | Alta variabilidad de inversión. |
| **Revenue** | Promedio | 85.0 M | Retorno promedio por film. |
| **Vote Avg** | Promedio | 6.4 / 10 | Calificación media global. |

---

## 4. Análisis de Texto y JSON (Resultados)

Este módulo aborda la complejidad de extraer información estructurada (JSON) que se encuentra serializada dentro de columnas de texto plano. Específicamente, se procesó la columna crew para identificar y extraer los nombres de los directores de cada producción

Implementación Técnica: Se integró la librería Circe, utilizando decodificadores automáticos (generic.auto) para transformar cadenas de texto en grafos de objetos Scala. El proceso incluye navegación funcional dentro de la lista decodificada

Problema: El texto es plano y contiene miles de objetos anidados irrelevantes para el análisis

Solución: Parseo selectivo y filtrado funcional buscando el atributo job == "Director"

| Columna | Tipo de Análisis | Lógica / Código Aplicado | Resultado Principal |
| :--- | :--- | :--- | :--- |
| **original_language** | Frecuencia | `groupBy(identity).map(_.size)` | **Inglés (83%)**, Francés (5%). |
| **crew (JSON)** | Extracción | `decode[List[Crew]](json)` | Objetos Crew creados. |
| **job (JSON)** | Filtrado | `find(_.job.contains("Director"))` | Rol identificado. |
| **name (JSON)** | Obtención | `flatMap(_.name)` | **Nombres de Directores** extraídos. |
```Scala
// Código de extracción del Director usando Circe
if (jsonLimpio.startsWith("[")) {
  decode[List[Crew]](jsonLimpio) match {
    case Right(listaCrew) =>
      // Buscamos el objeto donde el trabajo sea 'Director'
      val director = listaCrew.find(_.job.contains("Director"))
      director.flatMap(_.name) // Extraemos el nombre
    case Left(_) => None
  }
}
```
---

## 5. Limpieza de Datos (Resumen)

La calidad de los datos es un prerrequisito para cualquier análisis confiable. El dataset original presentaba inconsistencias sintácticas heredadas de su origen (exportación de Python), lo que los hacía incompatibles con el estándar JSON RFC 8259

Implementación Técnica: Se desarrolló una capa de saneamiento ("Sanitization Layer") que normaliza las cadenas de texto antes de su procesamiento, además de aplicar reglas de negocio para descartar registros financieramente inválidos

Problema: Sintaxis inválida en JSON (comillas simples ', literales None, True)

Solución: Pipeline de reemplazo de caracteres y normalización de tipos

| Columna Afectada | Problema Detectado | Solución Implementada (Código) |
| :--- | :--- | :--- |
| **Runtime** | Valores $\le$ 0 | `filter(p => p.runtime > 0)` |
| **Budget** | Valores Negativos | `filter(p => p.budget >= 0)` |
| **Crew (JSON)** | Comillas Simples (`'`) | `.replace("'", "\"")` |
| **Crew (JSON)** | `None` (Python Style) | `.replace("None", "null")` |
| **Crew (JSON)** | `True` (Mayúscula) | `.replace("True", "true")` |
```Scala
// Función de limpieza de JSON (Saneamiento)
def cleanCrewJson(json: String): String = {
  json.trim
    .replace("'", "\"")        // Corregir comillas
    .replace("None", "null")   // Corregir nulos
    .replace("True", "true")   // Corregir booleanos
    .replace("False", "false")
    .replaceAll("\\\\", "")    // Eliminar escapes dobles
}
```
---
# Proyecto Integrador: Pipeline ETL Reactivo para Datos de Cine

Este repositorio aloja el **Proyecto Integrador** de la asignatura *Programación Funcional y Reactiva*.

El objetivo principal es implementar una arquitectura **ETL (Extract, Transform, Load)** robusta para procesar un dataset masivo de películas con problemas de formato (CSV sucio, JSONs anidados malformados) y poblar una base de datos relacional mediante scripts SQL generados programáticamente.

---

## Arquitectura de la Solución (Evolución Técnica)

El proyecto evolucionó de un enfoque imperativo a un **Pipeline Reactivo** de alto rendimiento para garantizar la integridad de los datos.

### 1. Extracción (Extract) - Streaming Reactivo
Para manejar el archivo `pi_movies_complete.csv` sin saturar la memoria RAM, se utilizó la librería **FS2 (Functional Streams)**.

* **Problema Inicial:** El uso de `split(";")` o Regex fallaba porque el CSV contenía saltos de línea y puntos y coma *dentro* de las comillas (ej: sinopsis o diálogos).
* **Solución Final:** Implementación de `fs2-data-csv-lowlevel`. Este parser procesa el archivo byte a byte, respetando el estándar CSV (RFC 4180) y manejando correctamente las comillas escapadas.

### 2. Transformación (Transform) - Limpieza y Sanitización
Se aplicó una estrategia de **"Defensive Coding"** (Programación Defensiva) para lidiar con la suciedad de los datos:

* **Mapeo Manual Seguro:** En lugar de intentar decodificar automáticamente filas que podrían estar incompletas, se extraen los datos manualmente usando un mapa clave-valor. Esto evita que el programa se detenga (Crash) si una columna opcional falta.
* **Reparación de JSON:** La columna `crew` (personal) venía en un formato pseudo-JSON (estilo Python con `None`, `False`, comillas simples). Se creó un *Sanitizer* que normaliza estos textos a JSON válido antes de procesarlos.
* **Filtrado de Calidad:** Solo se procesan registros que tengan un Título válido y un Director identificado.

### 3. Carga (Load) - Generación SQL
La etapa final transforma los objetos limpios en instrucciones `INSERT` de SQL.
* **Sanitización SQL:** Se escapan caracteres especiales (ej: `Schindler's List` se convierte en `Schindler''s List`) para prevenir errores de sintaxis en la base de datos.

---

## Tecnologías Utilizadas

* **Cats Effect (v3.5.4):** Runtime para manejo de efectos puros (IO) y control de concurrencia.
* **FS2 Core / IO (v3.12.2):** Manejo de flujos de datos (Streams) y lectura eficiente de archivos sin desbordamiento de memoria.
* **FS2 Data CSV (v1.11.1):** Parsing de bajo nivel para CSVs complejos y malformados.
* **Circe (v0.14.10):** Decodificación y navegación robusta de estructuras JSON.

---

## Detalles de Implementación (Snippets Clave)

### Estrategia de Lectura Resiliente
El siguiente código muestra cómo se configura el stream para leer filas complejas sin usar Regex propensos a fallos:

```scala
// Pipeline Reactivo: Lectura y Mapeo Manual
val processingStream = Files[IO].readAll(inputFile)
  .through(text.utf8.decode)
  .through(rows[IO, String](';'))      // 1. Parsing inteligente de filas (respeta comillas)
  .through(headers[IO, String])        // 2. Lectura de cabeceras
  .map { csvRow =>
     // 3. Extracción segura: Si falta una columna, usa un valor por defecto en vez de fallar
     val title = csvRow.toMap.getOrElse("title", "")
     val crew = csvRow.toMap.getOrElse("crew", "[]")
     (title, crew)
  }
```
Limpieza de JSON (Deep Cleaning)
Antes de usar la librería Circe, reparamos el texto raw para hacerlo compatible con el estándar JSON.

```Scala
def repairJson(raw: String): String = {
  raw.trim
    .replace("None", "null")       // Convertir nulos de Python
    .replace("False", "false")     // Convertir booleanos
    .replace("'", "\"")            // Normalizar comillas
    // Lógica adicional para cerrar arrays truncados por el CSV...
}
```

## Mapeo de Datos
El análisis se centra en extraer y normalizar las siguientes dimensiones clave:

Campo: title (String)

Transformación: SanitizeTitle.

Objetivo: Título de la película escapado para evitar errores SQL (ej. comillas simples).

Campo: crew (List[Json])

Transformación: Circe Parser + repairJson.

Objetivo: Convertir texto sucio en una estructura navegable.

Campo: job (String - dentro del JSON)

Transformación: Filtro lógico (filter).

Objetivo: Se busca estrictamente el valor job == "Director".

Campo: name (String - dentro del JSON)

Transformación: Extracción (map).

Objetivo: Obtener el nombre final del director asociado al trabajo filtrado.

##  Guía de Ejecución
Requisitos Previos
Java JDK 11 o superior.

sbt (Scala Build Tool).

Pasos
Clonar el repositorio.

Asegurar que el dataset pi-movies-complete-2025-12-04.csv esté en la ruta: src/main/resources/data/.

Ejecutar el pipeline desde la terminal:

```Bash
sbt run
```
El sistema procesará el archivo y generará insert_movies_final.sql en la carpeta de recursos.

Evidencia de Resultados
El script generado (insert_movies_final.sql) ha sido validado exitosamente importándolo en una base de datos MySQL. El código maneja correctamente títulos con caracteres especiales y descarta registros corruptos.


```SQL
CREATE TABLE IF NOT EXISTS movies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500),
    director VARCHAR(255)
);

INSERT INTO movies (title, director) VALUES ('Lock, Stock and Two Smoking Barrels', 'Guy Ritchie');
INSERT INTO movies (title, director) VALUES ('Flight Command', 'Frank Borzage');
INSERT INTO movies (title, director) VALUES ('Dumb and Dumber To', 'Bobby Farrelly');
```
