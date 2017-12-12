import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.ExecutionContext.Implicits.global
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._

import java.io.FileOutputStream

object Main {
    def main(args: Array[String]): Unit = {
        implicit val system = ActorSystem()
        implicit val materializer = ActorMaterializer()
        val url = "https://search.yahoo.co.jp/image/search?p=scala"

        val browser = JsoupBrowser()
        val doc = browser.get(url)

        val yahoo_image_list = Source {
            (doc >> elementList("p.tb a")).map(_.attr("href"))
        }

        val get_image = Flow[String].map { url =>
            _get_image(url)

            def _get_image(url: String): Unit = {
                val r = Http().singleRequest(HttpRequest(uri = url))
                val filename = () => {
                    url.split('/').last.split('?').head
                }
                r.foreach {
                    case HttpResponse(StatusCodes.OK, _, entity, _) => {
                        val channel = new FileOutputStream("images\\%s".format(filename())).getChannel
                        entity.dataBytes.runWith(Sink.foreach { s =>
                            channel.write(s.asByteBuffer)
                        })
                        channel.close()
                    }
                    case HttpResponse(StatusCodes.MovedPermanently | StatusCodes.Found, headers, _, _) => {
                        _get_image(headers.find(_.name == "Location").get.value)
                    }
                    case _ =>
                }
            }

            url
        }

        val log = Sink.foreach[String](println)

        val r = yahoo_image_list via get_image to log
        r.run()
    }
}