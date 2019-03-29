package main.scala.com.bddprocessing.interestelar

import main.scala.com.bddprocessing.commons.GroupConcat
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{DoubleType, IntegerType}

object CalculoMediaBatchSQL {
  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.ERROR)

    val spark: SparkSession = SparkSession
      .builder()
      .appName("calculo-media-batch-sql")
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val navesFile = "in/interestelar/naves_transporte.csv"
    val dfNaves = spark.read.format("csv")
      .option("header", true)
      .load(navesFile)

    val navesDF = dfNaves.select(dfNaves.col("Codigo"), dfNaves.col("Descripcion"))

    val navesNoDupsDF = navesDF.groupBy($"Codigo").agg(GroupConcat($"Descripcion"))
    navesNoDupsDF.createOrReplaceTempView("tabla_navesNoDups")

    val trayectosFile = "in/interestelar/trayectos.csv"
    val dfTrayectos = spark.read.format("csv")
      .option("header", "false")
      .option("inferSchema","false")
      .option("delimiter",",")
      .load(trayectosFile)

    // Assign column names to the Region dataframe
    val trayectosDF = dfTrayectos.select(
      dfTrayectos("_c1").cast(IntegerType).as("Codigo"),
      dfTrayectos("_c9").cast(DoubleType).as("Consumo"))

    trayectosDF.createOrReplaceTempView("tabla_trayectos")

    var mediasDF = spark.sql("SELECT Codigo, AVG(Consumo) AS Media FROM tabla_trayectos GROUP BY Codigo")

    mediasDF.createOrReplaceTempView("tabla_medias")

    val resultsNoDups = spark.sql("SELECT naves.*, medias.Media "
      + "FROM tabla_navesNoDups AS naves "
      + "JOIN tabla_medias AS medias ON naves.Codigo = medias.Codigo");

    /*
    resultsNoDups.show(false)

    +------+------------------------------------------------------+------------------+
    |Codigo|groupconcat$(Descripcion)                             |Media             |
    +------+------------------------------------------------------+------------------+
    |19393 |Nave interestelar RDD                                 |101.24648524463511|
    |19977 |Nave interestelar Zookeeper - Nave interestelar Master|172.20168688207116|
    |20398 |Nave interestelar Broker - Nave interestelar Scala    |72.77056514197024 |
    |19790 |Nave interestelar Kafka                               |118.27510610363042|
    |20366 |Nave interestelar Cluster                             |74.73102801378141 |
    |20355 |Nave interestelar Apache                              |122.41904511760929|
    |20409 |Nave interestelar Spark                               |145.8389957834537 |
    +------+------------------------------------------------------+------------------+
     */

    spark.stop()
  }
}