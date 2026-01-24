# ProyectoIntegrador_PFR
Repositorio del proyecto integrador - Programación funcional y reactica

# Proyecto Integrador: Procesamiento y Poblamiento de Datos (Avance 3)

En esta etapa del proyecto (Avance 3), el objetivo principal es **poblar una base de datos** a partir de un dataset de películas. Dado que el archivo fuente (`pi_movies_small.csv`) presentaba inconsistencias de formato y datos "sucios", se implementó un flujo de trabajo **ETL (Extract, Transform, Load)** completo en Scala.

El sistema lee el archivo raw, limpia los errores de formato (Avance 2) y genera automáticamente scripts SQL (`INSERT INTO`) para insertar los datos limpios en cualquier base de datos relacional (Avance 3).

---


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
Generación de SQL
```scala
// Genera el script de inserción escapando comillas
val safeTitle = movie.title.replace("'", "''")
val sql = s"INSERT INTO movies (title, director) VALUES ('$safeTitle', '$safeDirector');\n"
```

Resultados
Al finalizar la ejecución, se obtiene un script SQL listo para ser ejecutado en herramientas como MySQL Workbench, DBeaver o H2 Console.

Ejemplo de salida SQL:

```SQL

CREATE TABLE IF NOT EXISTS movies (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255), director VARCHAR(255));

INSERT INTO movies (title, director) VALUES ('Avatar', 'James Cameron');
INSERT INTO movies (title, director) VALUES ('Pirates of the Caribbean: At World''s End', 'Gore Verbinski');
INSERT INTO movies (title, director) VALUES ('Spectre', 'Sam Mendes');
```
