package main.scala.com.bddprocessing.interestelar

import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkConf, SparkContext}

object CalculoMediaBatch {

  val COMMA_DELIMITER = ",(?=([^\"]*\"[^\"]*\")*[^\"]*$)"

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.ERROR)

    val conf = new SparkConf().setAppName("calculo-medias-batch").setMaster("local[*]")
    val sc = new SparkContext(conf)

    //Ingesta de datos de naves de transporte
    val registros1 = sc.textFile("in/interestelar/naves_transporte.csv")

    val registrosNaves = registros1.filter(!_.contains("Codigo")).
      map(nave => (nave.split(COMMA_DELIMITER)(0).replaceAll("\"",""),
        nave.split(COMMA_DELIMITER)(1).replaceAll("\"","")))

    //Concatenamos los nombres de aquellas naves cuyo código está duplicado para no
    // perder ningún valor ya que desconocemos cual de ellos es el correcto o actual
    val naves = registrosNaves.reduceByKey((x,y) => (x + " - " + y))

    //Ingesta de datos de trayectos
    val registros2 = sc.textFile("in/interestelar/trayectos.csv")

    val registrosTrayectos = registros2.map(trayecto => (trayecto.split(COMMA_DELIMITER)(1),(1,trayecto.split(COMMA_DELIMITER)(9).toDouble)))

    //Cálculo de registros existentes de cada nave para poder sacar las medias
    val consumoMedioNave = registrosTrayectos.reduceByKey((x,y) => (x._1 + y._1,x._2 + y._2))
    val precioConsumoMedioNave = consumoMedioNave.mapValues(infoValor => infoValor._2 / infoValor._1)

    //Join para obtener los datos de medias de consumo de las naves a tener en cuenta
    val joinNavesTrayectos = naves.join(precioConsumoMedioNave)

    /*
    for((nave,media) <- joinNavesTrayectos.sortByKey().collect()) { println(nave + ":" + media) }

    Codigo nave : (Descripcion nave , media nave)
    ---------------------------------------------
    19393:(Nave interestelar RDD,101.24648524463511)
    19790:(Nave interestelar Kafka,118.27510610363042)
    19977:(Nave interestelar Zookeeper - Nave interestelar Master,172.20168688207116)
    20355:(Nave interestelar Apache,122.41904511760929)
    20366:(Nave interestelar Cluster,74.73102801378141)
    20398:(Nave interestelar Broker - Nave interestelar Scala,72.77056514197024)
    20409:(Nave interestelar Spark,145.8389957834537)
    */
  }
}
