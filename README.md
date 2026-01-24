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
# Proyecto Integrador: Procesamiento y Poblamiento de Datos (Avance 3)

En esta etapa del proyecto (Avance 3), el objetivo principal es **poblar una base de datos** a partir de un dataset de películas. Dado que el archivo fuente (`pi_movies_small.csv`) presentaba inconsistencias de formato y datos "sucios", se implementó un flujo de trabajo **ETL (Extract, Transform, Load)** completo en Scala.

El sistema lee el archivo raw, limpia los errores de formato (Avance 2) y genera automáticamente scripts SQL (`INSERT INTO`) para insertar los datos limpios en cualquier base de datos relacional (Avance 3).

---

## Arquitectura de la Solución

El código se diseñó modularmente para separar la lógica de limpieza de la lógica de generación de archivos.

### 1. Extracción y Limpieza (Integración con Avance 2)
Para poder generar SQL válido, primero fue necesario resolver problemas críticos en el archivo CSV original.

* **Problema detectado:** El uso de expresiones regulares (`Regex`) estándar causaba un `StackOverflowError` debido a la longitud excesiva de la columna `crew`. Además, el formato no respetaba el estándar CSV (comas dentro de campos entrecomillados y JSONs con comillas simples estilo Python).
* **Solución implementada:** Se desarrolló un **Parser Manual** (`MovieLoader.parseCsvLine`) que recorre la línea carácter por carácter.
    * Esto evita el desbordamiento de memoria.
    * Respeta las comas que están dentro de las comillas (ej: *"Lock, Stock and Two Smoking Barrels"* se lee como una sola columna).
* **Extracción del Director:** Se implementó una lógica de "Rastreo". Como el CSV estaba malformado, la columna del director no siempre estaba en la misma posición. El algoritmo busca el patrón `'job': 'Director'` en cualquier fragmento de la fila y extrae el nombre adyacente.

### 2. Transformación de Datos
Una vez extraídos el título y el director, se aplican transformaciones en memoria:
* **Limpieza de Títulos:** Eliminación de comillas redundantes y espacios en blanco (`trim`).
* **Filtrado:** Se descartan registros donde no se pudo identificar al director ("Desconocido").

### 3. Carga y Generación de SQL (Foco del Avance 3)
Esta es la fase final donde transformamos los objetos `Movie` en instrucciones de base de datos.

* **Generador SQL (`SqlGenerator`):**
    * Crea un archivo `insert_movies.sql`.
    * Define la estructura de la tabla con `CREATE TABLE IF NOT EXISTS`.
    * **Manejo de Caracteres Especiales (Sanitización):** Una parte crítica del Avance 3 es evitar que el SQL falle por errores de sintaxis.
        * *Caso:* Películas como `Schindler's List`.
        * *Solución:* Se aplica `.replace("'", "''")` para escapar las comillas simples, convirtiendo el título en `Schindler''s List` (formato válido para SQL).

---

## Cómo Ejecutar el Proyecto

El punto de entrada es el objeto `ProyectoIntegrador.Crew`.

1.  Asegurarse de tener el archivo de datos en: `src/main/resources/data/pi_movies_small.csv`.
2.  Ejecutar la clase principal.
3.  El programa generará dos archivos en la carpeta de recursos:

| Archivo Generado | Descripción | Avance Relacionado |
| :--- | :--- | :--- |
| `pi_movies_limpio.csv` | Archivo CSV normalizado con separador `;` | **Avance 2** (Limpieza) |
| `insert_movies.sql` | Script con sentencias `INSERT` masivas | **Avance 3** (Poblamiento) |

---

## Snippets de Código Clave

### Parser Seguro (Sin Regex)
```scala
// Evita StackOverflow recorriendo el String manualmente
def parseCsvLine(line: String): Array[String] = {
    // Lógica de acumulador de caracteres respetando "inQuotes"
    // ...
}

```

### Generación de SQL

```scala
// Genera el script de inserción escapando comillas
val safeTitle = movie.title.replace("'", "''")
val sql = s"INSERT INTO movies (title, director) VALUES ('$safeTitle', '$safeDirector');\n"

```

---

## Resultados

Al finalizar la ejecución, se obtiene un script SQL listo para ser ejecutado en herramientas como MySQL Workbench, DBeaver o H2 Console.

**Ejemplo de salida SQL:**

```sql
CREATE TABLE IF NOT EXISTS movies (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), director VARCHAR(255));

INSERT INTO movies (title, director) VALUES ('Avatar', 'James Cameron');
INSERT INTO movies (title, director) VALUES ('Pirates of the Caribbean: At World''s End', 'Gore Verbinski');
INSERT INTO movies (title, director) VALUES ('Spectre', 'Sam Mendes');

```
