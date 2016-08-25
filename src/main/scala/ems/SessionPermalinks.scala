package ems

import java.io.File
import java.net.URI

import com.typesafe.scalalogging.LazyLogging
import ems.model.Session
import org.apache.commons.codec.digest._
import uritemplate.Syntax._
import uritemplate._

case class Expansion(variable: String, template: URITemplate) {
  def expand(value: String): URI = {
    URI.create(template.expand(variable := value))
  }
}

case class SessionPermalinks(map: Map[String, Expansion]) {
  def expand(session: Session, href: URI): Option[URI] = {
    map.get(session.eventId.toString).flatMap(
      exp => {
        exp.variable match {
          case "title" => Some(exp.expand(escapeTitle(session.abs.title)))
          case "href" => Some(exp.expand(hash(href)))
          case _ => None
        }
      }
    )
  }

  private[ems] def expandHref(eventId: String, href: URI): Option[URI] = {
    map.get(eventId).map(exp => exp.expand(hash(href)))
  }

  private[ems] def expandTitle(eventId: String, title: String): Option[URI] = {
    map.get(eventId).map(exp => exp.expand(escapeTitle(title)))
  }

  def escapeTitle(title: String) = {
    title.trim.toLowerCase.
      replaceAll(" +", "-").
      replace("æ", "ae").
      replace("ø", "oe").
      replace("aa", "å").
      replaceAll("[^a-z0-9-]", "")
  }

  def hash(href: URI): String = DigestUtils.sha256Hex(href.toString).trim
}

object SessionPermalinks extends LazyLogging {
  import org.json4s._
  import org.json4s.native.JsonMethods._

  private val links: Map[String, SessionPermalinks] = {
    val f = {
      var f = new File(Jetty.home, "etc/permalinks.json")
      if (!f.exists) {
        f = new File(Jetty.home, "current/etc/permalinks.json")
      }
      f
    }
    logger.info("Loading Permalinks from " + f)

    val loaded = parse(f)

    logger.info("Loaded " + loaded)
    loaded
  }

  private def parse(f: File): Map[String, SessionPermalinks] = {
    def stringOrEmpty(jv: JValue) = jv match {
      case JString(s) => s
      case _ => ""
    }

    def parseExpansion(v: JValue): Expansion = {
      v match {
        case obj@JObject(_) => {
          Expansion(
            stringOrEmpty(obj \ "variable"),
            URITemplate(stringOrEmpty(obj \ "template"))
          )
        }
        case j => sys.error("Failed" + j)
      }
    }

    def parsePermaLinks(v: JObject): SessionPermalinks = {
      SessionPermalinks(v.obj.foldLeft(Map.empty[String, Expansion]){ case (map, (key, value)) =>
        map + (key -> parseExpansion(value))
      })
    }


    def parseIt(obj: JObject): Map[String, SessionPermalinks] = {
      obj.obj.foldLeft(Map.empty[String, SessionPermalinks]){case (map, (key, value)) =>
        map + (key -> parsePermaLinks(value.asInstanceOf[JObject]))
      }
    }


    parseOpt(f).collect{ case j: JObject => j}.map(parseIt).getOrElse(Map.empty)
  }

  def fromEnvironment(name: String): SessionPermalinks = {
    val fromEnv = links.getOrElse(name, SessionPermalinks(Map.empty))
    logger.info("Env " + name)
    logger.info("permalinks " + fromEnv)
    fromEnv
  }
}
