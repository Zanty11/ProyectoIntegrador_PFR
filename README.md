##  Avance 1: Ingesta Reactiva y Sanitización de Tipos

**Estado:**  Completado

**Archivos Principales:** `LimpiezaInicial.scala`, `RawDataModels.scala`, `ModeloNormalizado.scala`

### 1. Descripción Técnica
El objetivo de este primer hito fue establecer una **capa de defensa** contra la "suciedad" de los datos. El dataset original presenta inconsistencias típicas. Se implementó una arquitectura de **Streaming** que actúa como filtro de calidad.

Se utilizó el patrón **"Raw to Clean"**:
1.  **Ingesta Agnostica:** Se leen todas las columnas como `String` (`MovieRaw`) para evitar fallos de parseo inmediatos.
2.  **Validación Funcional:** Se aplican transformaciones que devuelven tipos seguros (`Option[Double]`, `Boolean`) encapsulados en `MovieClean`.

### 2. Implementación de Ingeniería

#### A. Lectura Eficiente de Memoria (FS2)
Utilizamos `fs2.Stream` para procesar el archivo línea por línea, manteniendo el consumo de memoria constante.

```scala
// Fragmento de LimpiezaInicial.scala
val flujoLectura = Files[IO].readAll(rutaEntrada)
  .through(text.utf8.decode)
  .through(attemptDecodeUsingHeaders[MovieRaw](';')) // Decodificación tolerante a fallos
```
B. Estrategia de Sanitización
Se creó el objeto ReglasSanitizacion para centralizar la lógica de limpieza y validar tipos estrictos.
```
Scala
// Lógica crítica en LimpiezaInicial.scala
def validarDouble(s: String): Option[Double] = 
  s.trim.toDoubleOption.filter(_ >= 0) // Solo acepta positivos
```

3. Transformación de Datos (Raw vs Clean)
   
| Campo Original (CSV Raw) | Tipo Dato | Transformación | Resultado (MovieClean) |
| :--- | :--- | :--- | :--- |
| `budget` | `String` | `validarDouble` (Filtra negativos y letras) | `Option[Double]` |
| `video` | `String` | `validarBool` (Norm. "True"/"False") | `Option[Boolean]` |
| `release_date` | `String` | Formato estandarizado | `Option[String]` |
| `id` | `String` | `toIntOption` + Deduplicación | `Int` (PK) |
| `genres`, `cast` | `String` | Limpieza de caracteres basura | `String` (JSON Clean) |

| Regla de Negocio | Implementación Técnica | Objetivo |
| :--- | :--- | :--- |
| **Integridad de IDs** | `groupBy(_.id).map(_.head)` | Eliminar duplicados que rompen la PK. |
| **Sanitización de Texto** | `replaceAll("[\r\n]+", " ")` | Evitar saltos de línea que rompen el CSV. |
| **Manejo de Nulos** | `Option` (Mónada) | Evitar `NullPointerExceptions` en tiempo de ejecución. |

## Avance 2: Normalización Compleja y Parsing JSON

**Estado:** Completado

**Archivos Principales:** `GeneradorTablas.scala`, `FormatosIntermedios.scala`

### 1. Descripción Técnica
El segundo hito abordó el desafío de la **"Explosión de Datos"**. Las columnas del dataset original como `genres`, `cast` o `production_companies` contenían estructuras anidadas (JSONs) y no valores atómicos.

Para cumplir con la Tercera Forma Normal (3FN), se implementó un proceso de extracción que descompone estas listas en **Entidades** y **Relaciones** separadas.

### 2. Implementación de Ingeniería

#### A. Reparación de JSONs "Sucios" (`ReparadorJson`)
El dataset presentaba un formato inválido para las librerías estándar (uso de comillas simples `'`, valores `None` de Python, y booleanos `True/False` en mayúscula). Antes de usar la librería **Circe**, se inyectó una capa de reparación léxica.

```scala
// Fragmento de GeneradorTablas.scala
private def arreglarFormato(raw: String, esLista: Boolean): String = {
  var txt = raw.trim
  txt.replaceAll("None", "null")   // Compatibilidad Python -> JSON
     .replaceAll("True", "true")   // Booleanos
     .replace("'", "\"")           // Comillas estándar
}

```

| Columna Compleja (JSON Input) | Problema de Formato | Solución Aplicada | Salida Normalizada |
| :--- | :--- | :--- | :--- |
| `genres` | Array de objetos `[{'id':...}]` | Parsing con Circe | `entidad_generos.csv`<br>`relacion_pelicula_genero.csv` |
| `production_companies` | Formato Python (`None`, `'`) | `ReparadorJson` + Regex | `entidad_companias.csv`<br>`relacion_pelicula_compania.csv` |
| `cast` (Elenco) | Lista masiva con metadatos | Extracción de Actores | `entidad_personas.csv`<br>`relacion_pelicula_elenco.csv` |
| `crew` (Equipo Técnico) | IDs compartidos con actores | Filtrado por Dept/Job | `entidad_personas.csv`<br>`relacion_pelicula_staff.csv` |

| Archivo Generado (CSV) | Tipo de Tabla | Claves (PK / FK) | Descripción |
| :--- | :--- | :--- | :--- |
| `entidad_personas.csv` | Catálogo Maestro | `person_id` | Lista única de actores y directores. |
| `entidad_generos.csv` | Catálogo Maestro | `genre_id` | Géneros de películas (Acción, Drama...). |
| `entidad_companias.csv` | Catálogo Maestro | `company_id` | Empresas productoras. |
| `relacion_pelicula_elenco.csv` | Tabla Pivote | `movie_id`, `person_id` | Relación N:M (Película <-> Actor). |
| `relacion_pelicula_staff.csv` | Tabla Pivote | `movie_id`, `person_id` | Relación N:M (Película <-> Director/Writer). |
| `relacion_pelicula_genero.csv` | Tabla Pivote | `movie_id`, `genre_id` | Clasificación de la película. |

## Avance 3: Persistencia Relacional y Carga Masiva (SQL)

**Estado:** Completado

**Archivos Principales:** `ImportadorFinal.scala`, `CatalogosDB.scala`, `LinksDB.scala`

### 1. Descripción Técnica
El último hito del proyecto consistió en la **Persistencia de Datos**. Una vez que la información fue limpiada y normalizada en memoria, el objetivo fue volcarla en una base de datos **MySQL** robusta.

Se diseñó un esquema relacional (`MoviesDB`) con integridad referencial estricta (Claves Foráneas) y se implementó un pipeline de carga optimizado que minimiza las conexiones a la base de datos mediante inserciones por lotes (**Batch Inserts**).

### 2. Implementación de Ingeniería

#### A. Infraestructura como Código (DDL Automático)
En lugar de crear las tablas manualmente en MySQL Workbench, el sistema reconstruye el esquema completo al iniciarse. Esto garantiza que el entorno de ejecución sea siempre idéntico al de desarrollo.

```scala
// Fragmento de ImportadorFinal.scala
private def inicializarTablas(): ConnectionIO[Unit] = {
  sql"""CREATE SCHEMA IF NOT EXISTS MoviesDB ...""".update.run
  // Definición de Foreign Keys para garantizar consistencia
  sql"""CONSTRAINT fk_movie_collection FOREIGN KEY (collection_id) ...""".update.run
}
```

#### B. Optimización de Carga (Batch Processing)
Insertar 50,000 registros uno por uno sería ineficiente y lento. Utilizamos fs2 para agrupar los datos en "chunks" (trozos) de 1000 elementos. De esta forma, enviamos una sola transacción a la base de datos por cada 1000 filas, reduciendo drásticamente la latencia de red.

```Scala
// Estrategia de carga masiva en ImportadorFinal.scala
Stream.emits(listaGeneros)
  .chunkN(1000) // Agrupa 1000 registros
  .evalMap(chunk => loteGeneros(chunk.toList).transact(conApp)) // 1 Transacción x Lote
  .compile.drain
```
| Estructura Scala (Origen) | Tabla SQL (Destino) | Estrategia de Carga |
| :--- | :--- | :--- |
| `MovieClean` | `movie` | Inserción Simple (Doobie) |
| `DatoPersona` | `person` | Batch Insert (Lotes de 1000) |
| `RelReparto` | `movie_cast` | Batch Insert (Relación N:M) |
| `RelStaff` | `movie_crew` | Batch Insert (Relación N:M) |
| `DatoGenero` | `genre` | Carga de Catálogo |

| Componente Técnico | Función en el Pipeline | Archivo Fuente |
| :--- | :--- | :--- |
| `inicializarTablas` | Reconstrucción DDL (Schema + FKs) | `ImportadorFinal.scala` |
| `Stream.chunkN(1000)` | Optimización de Red (Batch) | `ImportadorFinal.scala` |
| `Transactor` | Gestión de Pool de Conexiones | `ImportadorFinal.scala` |
| `Foreign Keys` | Garantía de Integridad Referencial | `MySQL` (Definición SQL) |

<img width="1000" height="656" alt="image" src="https://github.com/user-attachments/assets/b8b57eff-d207-4267-bdd9-2b32fce2feb1" />
<img width="1777" height="515" alt="image" src="https://github.com/user-attachments/assets/c796ab0d-509e-41a8-b159-78bca639cf85" />
