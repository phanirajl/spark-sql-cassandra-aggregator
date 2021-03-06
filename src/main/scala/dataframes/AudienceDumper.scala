package dataframes

import java.time.LocalDate

import org.apache.spark.sql.cassandra.{CassandraSourceRelation, DataFrameWriterWrapper}
import org.apache.spark.sql.{SaveMode, SparkSession}

object AudienceDumper{

    def main(args: Array[String]): Unit = {

        val ss = SparkSession.builder().config("spark.cassandra.connection.host", "localhost").master("local[*]").getOrCreate()
        import utils.StringUtils._

        val year = args(0)
        val month = args(1)
        val day = args(2)

        val filePath = s"/mnt/hadoop/hive-warehouse/bi_poi_audience/year=$year/month=$month/day=$day/"

        val listOfFiles = filePath.getListOfFiles

        listOfFiles.foreach(file => processAndSaveToCassandra(ss, file.getAbsolutePath))
    }

    def processAndSaveToCassandra(ss: SparkSession, filePath: String) = {
        /**
          * Create RDD from data.tsv file that we genrated with LogProducer class
          */

        import utils.DateUtils._
        import AudienceParser._
        val sourceRDD = ss.sparkContext.textFile(s"file:///$filePath")

        /**
          * we let the function defines the column names of our DF
          * because of using Activity case class
          */
        val audienceRDD = sourceRDD.map(line => {
            val record = line.split("\\t")

            val date = record(0).safeParse.getOrElse(LocalDate.now())
            val lastoffertypechangeDate = record(38).safeParse.getOrElse(LocalDate.now())
            parseAudiencePoi(record, date, lastoffertypechangeDate)
        })

        val audienceDF = ss.createDataFrame(audienceRDD)

        audienceDF.write
            .cassandraFormat("bi_poi_audience", "mappy_bi", CassandraSourceRelation.defaultClusterName, true)
            .mode(SaveMode.Append)
            .save()

    }
}
