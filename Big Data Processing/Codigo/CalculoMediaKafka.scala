package main.scala.com.bddprocessing.interestelar

import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka010._
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe

object CalculoMediaKafka {

  val COMMA_DELIMITER = ",(?=([^\"]*\"[^\"]*\")*[^\"]*$)"

  def main(args: Array[String]): Unit = {

    Logger.getLogger("org").setLevel(Level.ERROR)

    //Obtención datos naves y trayectos ---BATCH---:
    val conf = new SparkConf().setAppName("calculo-medias").setMaster("local[*]")
    val sc = new SparkContext(conf)

    val registros = sc.textFile("in/interestelar/naves_transporte.csv")
    val registrosNaves = registros.filter(!_.contains("Codigo")).
      map(nave => (nave.split(COMMA_DELIMITER)(0).replaceAll("\"",""),
        nave.split(COMMA_DELIMITER)(1).replaceAll("\"","")))

    //Concatenamos los nombres de aquellas naves cuyo código está duplicado para no
    // perder ningún valor ya que desconocemos cual de ellos es el correcto o actual
    val naves = registrosNaves.reduceByKey((x,y) => x + " - " + y)

    val registros2 = sc.textFile("in/interestelar/trayectos.csv")
    val registrosTrayectos = registros2.map(trayecto => (
      trayecto.split(COMMA_DELIMITER)(1),(1,trayecto.split(COMMA_DELIMITER)(9).toDouble)))

    //Cálculo de registros existentes de cada nave para poder sacar las medias
    val consumoMedioNavesBatch = registrosTrayectos.reduceByKey((x,y) => (x._1 + y._1,x._2 + y._2))
    val precioConsumoMedioNavesBatch = consumoMedioNavesBatch.mapValues(infoValor => infoValor._2 / infoValor._1)

    val joinedNavesTrayectosBatch = naves.join(precioConsumoMedioNavesBatch)

    //----------------------------------------------------------------------------

    //Obtención datos trayectos ---STREAMING---:
    val ssc = new StreamingContext(sc,Seconds(1))
    ssc.checkpoint("out/checkpoint")

    val kafkaParams = Map[String,Object](
      "bootstrap.servers" -> "localhost:9092",
      "key.deserializer" -> classOf[StringDeserializer],
      "value.deserializer" -> classOf[StringDeserializer],
      "group.id" -> "spark-group",
      "auto.offset.reset" -> "latest",
      "enable.auto.commit" -> (false:java.lang.Boolean))

    val topics = Array("navesTrayecto")
    val stream = KafkaUtils.createDirectStream[String,String](
      ssc,PreferConsistent,Subscribe[String,String](topics,kafkaParams))

    val lines = stream.map(_.value)
    val naveConsumo = lines.map(linea => (linea.split(",")(1),(1,linea.split(",")(9).toDouble)))

    val consumoMedioNave = naveConsumo.reduceByKey((x,y) => (x._1 + y._1,x._2 + y._2))
    val precioConsumoMedioNave = consumoMedioNave.mapValues(infoValor => infoValor._2 / infoValor._1)

    //Medias de consumo de todas las naves de la flota agrupadas por nave obtenidas en tiempo real
    val joinedNavesTrayectosStreaming = precioConsumoMedioNave.transform(rdd => rdd.join(naves))

    //Join con los consumos históricos y en streaming de cada nave
    val mediasBatchStreaming = precioConsumoMedioNave.transform(rdd => rdd.join(precioConsumoMedioNavesBatch))

    //Media de cada nave entre las medias del histórico y el tiempo real ordenada por el valor de las mismas
    val mejoresMedias = mediasBatchStreaming.mapValues(x => (x._1 + x._2) / 2).
      transform(rdd => rdd.sortBy(x => x._2,false))

    //Diferencias de cada nave entre las medias del histórico y el tiempo real
    val diferenciasMedias = mediasBatchStreaming.mapValues(x => x._2 - x._1)

    val mejoresNaves = diferenciasMedias.transform(rdd => rdd.sortBy(x => x._2,false))
    val joinedNavesDiferMedias = mejoresNaves.transform(rdd => rdd.join(naves))
    val joinedNavesDiferMediasOrdered = joinedNavesDiferMedias.transform(rdd => rdd.sortBy(x => x._2,false))

    //joinedNavesDiferMediasOrdered.print()

    ssc.start()
  }
}
