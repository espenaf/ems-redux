package ems

import java.io.File
import java.util.Properties
import javax.sql.DataSource

import scala.util.Properties._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import org.constretto._
import Constretto._
import Converter._
import com.typesafe.scalalogging.LazyLogging

case class Config(binary: File, sql: SqlConfig, cache: CacheConfig)

object Config extends LazyLogging {

  private implicit val cacheConfigConverter = fromObject(obj => {
    CacheConfig(obj[Int]("events"), obj[Int]("sessions"))
  })

  private implicit val sqlConfigConverter = fromObject(obj => {
    SqlConfig(
      obj.get[String]("host").getOrElse("localhost"),
      obj.get[Int]("port").getOrElse(5442),
      obj.get[String]("database").getOrElse("ems"),
      obj[String]("username"),
      obj[String]("password")
    )
  })

  private implicit val configConverter = fromObject(obj => {
    Config(
      obj.get[File]("binary").getOrElse(new File("binary")),
      obj.get[SqlConfig]("db").getOrElse(SqlConfig()),
      obj.get[CacheConfig]("cache").getOrElse(CacheConfig())
    )
  })

  def load(home: File): Config = {
    val env = propOrElse("CONSTRETTO_TAGS", envOrElse("CONSTRETTO_TAGS", "dev"))
    if (env == "dev") {
      logger.warn("EMS running in development mode")
      System.setProperty("CONSTRETTO_TAGS", "dev")
    }
    logger.info("EMS environment '%s'".format(env))
    val path = new File(home, "etc/ems-%s.conf".format(env))
    if (!path.exists()) {
      logger.info("No config file for %s; aborting".format(path.getAbsolutePath))
      sys.exit(1)
    }
    val constretto = Constretto(
      List(json(path.toURI.toString, "config", Some(env)))
    )

    constretto[Config]("config")
  }
}

case class CacheConfig(events: Int = 30, sessions: Int = 30)

case class SqlConfig(host: String = "localhost",
                     port: Int = 5442,
                     database: String = "ems",
                     username: String = "ems",
                     password: String = "ems") {
  val url = s"jdbc:postgresql://$host:$port/$database"
  val driver = "org.postgresql.Driver"

  def dataSource(): DataSource = {
    val config = new HikariConfig()
    config.setJdbcUrl(url)
    config.setUsername(username)
    config.setPassword(password)
    new HikariDataSource(config)
  }


}
