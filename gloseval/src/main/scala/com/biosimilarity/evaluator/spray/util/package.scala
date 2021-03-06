package com.biosimilarity.evaluator.spray

import java.io.{FileInputStream, InputStream}

import com.biosimilarity.evaluator.distribution.EvalConfConfig
import com.mongodb.casbah.Imports.MongoClient
import com.rabbitmq.client.{Connection, ConnectionFactory}
import com.mongodb.casbah.MongoClientURI

import scala.collection.mutable
import scala.sys.process._
import scala.util.Try

package object util {

  def resourceStream(resourceName: String): InputStream = {
    val is: InputStream = new FileInputStream(Boot.resourcesDir.resolve(resourceName).toFile)
    require(is.ne(null), s"Resource $resourceName not found")
    is
  }

  def resetMongo(): Unit = {
    val dbHost = EvalConfConfig.readStringOrElse("dbHost", "localhost")
    val dbPort = EvalConfConfig.readStringOrElse("dbPort", "27017")
    val uri                             = MongoClientURI(s"mongodb://$dbHost:$dbPort/")
    val mongoClient: MongoClient        = MongoClient(uri)
    val dbNames: mutable.Buffer[String] = mongoClient.databaseNames
    dbNames.foreach { (name: String) =>
      mongoClient(name).dropDatabase()
    }
  }

  def mongoVersion(): Option[String] =
    Try("mongod --version".!!).toOption.map { (s: String) =>
      val pattern = """(?m)^db version v(\S+)$""".r
      pattern.findFirstIn(s) match {
        case Some(pattern(version)) => version
      }
    }

  def rabbitMQVersion(): Option[String] =
    Try {
      val factory: ConnectionFactory = new ConnectionFactory()
      val conn: Connection           = factory.newConnection()
      val version: String            = conn.getServerProperties.get("version").toString
      conn.close()
      version
    }.toOption
}
