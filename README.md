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
