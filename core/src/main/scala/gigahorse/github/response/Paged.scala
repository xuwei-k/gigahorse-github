package gigahorse.github
package response

import gigahorse.FullResponse
import sjsonnew.JsonFormat
// import scalajson.ast.unsafe._
// import sjsonnew.support.scalajson.unsafe.Converter
import sjsonnew.support.spray.Converter
import spray.json._


/** represents pagination.
 */
case class Paged[A](items: Vector[A],
    links: Map[String, String],
    total_count: Option[Long],
    incomplete_results: Option[Boolean]) {
  def next_page: Option[String] = links.get("next")
  def last_page: Option[String] = links.get("last")
  def first_page: Option[String] = links.get("first")
  def prev_page: Option[String] = links.get("prev")
}

object Paged {
  implicit def pageToSeq[A](paged: Paged[A]): Vector[A] = paged.items

  def parseArray[A: JsonFormat]: FullResponse => Paged[A] =
    (res: FullResponse) => {
      val json = Github.asJson(res)
      val links = linkHeader(res)
      val ary = json match {
        case JsArray(ary) => ary
        case _           => sys.error(s"JArray expected but found: $json")
      }
      Paged(ary.toVector map Converter.fromJsonUnsafe[A], links, None, None)
    }

  def parseSearchResult[A: JsonFormat]: FullResponse => Paged[A] =
    (res: FullResponse) => {
      val json = Github.asJson(res)
      val links = linkHeader(res)
      val fields = json match {
        case JsObject(fields) => fields.toVector
        case _               => sys.error(s"JObject expected but found: $json")
      }
      val items: Vector[JsValue] = (for {
        ("items", v) <- fields
      } yield (v match {
        case JsArray(ary) => ary.toVector
        case _           => sys.error(s"JArray expected but found: $v")
      })).flatten
      val xs = items map Converter.fromJsonUnsafe[A]
      val total_count: Option[Long] = (for {
        ("total_count", v) <- fields
      } yield (v match {
        case JsNumber(num) => num.toLong
        case _            => sys.error(s"JNumber expected but found: $v")
      })).headOption
      val incomplete_results: Option[Boolean] = (for {
        ("incomplete_results", v) <- fields
      } yield (v match {
        case JsBoolean(b) => b
        case _           => sys.error(s"JBoolean expected but found: $v")
      })).headOption
      Paged(xs, links, total_count, incomplete_results)
    }

  def linkHeader(res: FullResponse): Map[String, String] =
    Map((res.header("Link") match {
      case Some(s) =>
        s.split(",").toList flatMap { x => x.split(";").toList match {
          case v :: k :: Nil =>
            Some(k.trim.replaceAllLiterally("rel=", "").replaceAllLiterally("\"", "") ->
              v.trim.replaceAllLiterally(">", "").replaceAllLiterally("<", ""))
          case _ => None
        }}
      case None => Nil
    }): _*)
}
