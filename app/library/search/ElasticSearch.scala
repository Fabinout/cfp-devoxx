package library.search

import play.api.libs.ws.WS
import play.api.libs.json.{JsValue, Json}
import akka.actor._
import play.api.Play.current
import controllers.routes
import play.api.libs.concurrent.Execution.Implicits._
import scala.util.{Try, Failure, Success}
import java.net.URLEncoder
import scala.concurrent.{Future, Promise}
import play.api.Play
import play.api.cache.Cache
import com.ning.http.client.Realm
import com.ning.http.client.Realm.AuthScheme
/**
 * Wrapper and helper, to reuse the ElasticSearch REST API.
 *
 * Author: nicolas martignole
 * Created: 23/09/2013 12:31
 */
object ElasticSearch {

  val host = Play.current.configuration.getString("elasticsearch.host").getOrElse("http://localhost:9200")
  val username=Play.current.configuration.getString("elasticsearch.username").getOrElse("")
  val password=Play.current.configuration.getString("elasticsearch.password").getOrElse("")

  def index(index: String, json: String) = {
    val futureResponse = WS.url(host + "/" + index + "?ttl=1d")
      .withAuth(username,password, AuthScheme.BASIC)
      .put(json)
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def indexBulk(json:String)={
    val futureResponse = WS.url(host + "/_bulk?ttl=1d")
      .withAuth(username,password, AuthScheme.BASIC)
      .post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to bulk import, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def deleteIndex(indexName:String)={
    val futureResponse = WS.url(host + "/" + indexName + "/")
      .withAuth(username,password, AuthScheme.BASIC)
      .delete()
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to delete index, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }

  }

  def doSearch(query: String): Future[Try[String]] = {
    val serviceParams = Seq(("q", query))
    val futureResponse = WS.url(host + "/_search")
      .withAuth(username,password, AuthScheme.BASIC)
      .withQueryString(serviceParams: _*).get()
    futureResponse.map {
      response =>
        response.status match {
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to search, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def doSearch(index: String, query: String) = {
    val serviceParams = Seq(("q", query))
    val futureResponse = WS.url(host +"/" + index +"/_search")
      .withFollowRedirects(true)
      .withAuth(username, password, AuthScheme.BASIC)
      .withQueryString(serviceParams: _*).get()
    futureResponse.map {
      response =>
        response.status match {
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to perform search, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }

  def doAdvancedSearch(index:String, query:Option[String], p:Option[Int])={

    val someQuery=   query.filterNot(_ == "").filterNot(_ == "*")
    val zeQuery = someQuery.map { q => "\"query_string\" : { \"query\": \"" + q + "\"}"}.getOrElse("\"match_all\" : { }")

     val pageUpdated:Int = p match{
      case None => 0
      case Some(page) if page<=0 => 0
      case Some(other) => other-1
    }

    val json:String=
      s"""
        |{
        | "from" : $pageUpdated,
        | "size" : 25,
        | "query" : {
        |   $zeQuery
        | }
        |}
      """.stripMargin

    if(play.Logger.of("library.ElasticSearch").isDebugEnabled){
      play.Logger.of("library.ElasticSearch").debug(s"Elasticsearch query $json")
    }

    val futureResponse = WS.url(host +"/" + index +"/_search")
      .withFollowRedirects(true)
      .withRequestTimeout(4000)
      .withAuth(username, password, AuthScheme.BASIC)
      .post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to perform search, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }


  // This is interesting if you want to build a cloud of Words.
  def getTag(index:String)={

    val json:String =
      """
        |{
        |   "query" : { "match_all" : {} },
        |   "facets" : {
        |     "tags" : { "terms" : {"fields" : ["summary"], "size":100,
        |"exclude": ["de","et","les","la","des","pour","le","en","un","à","vous","une","est","dans","cette",
        |"du","que","avec","comment","nous","sur","ce","plus","qui","au","ou","il","votre","pas",
        |"mais","par","applications","ne","tout","présentation","faire","vos","peut","sont","you",
        |"si","aussi","se","son","can","ces","je","bien","être","tous","comme","we","sans","mettre",
        |"verrons","permet","quelques","avez","aux","y","travers","notre","entre",
        |"cet","ont","même","mise","soit","permettant","développeur","also",
        |"your", "quand", "temps", "systèmes", "data", "système","permettent",
        |"réel", "hands", "facile", "rencontre", "puissance", "outils","peuvent","etc",
        |"minutes", "why", "who", "webs", "vivre", "vite", "tour", "time","l'occasion",
        |"testez", "sérialisez", "suite", "side", "recommandation","d’un","qu'il",
        |"programming", "programmation", "pourquoi","cela","like","point","chaque","bonnes","ans","when",
        |"alors","lors","leur","leurs","pourtant","peu","elle","il","là","toutes",
        |"venez", "temps", "ses", "talk", "sa", "allons", "all", "différents",
        |"mieux", "have", "propose", "new", "place", "également", "fait","from",
        |"about", "base", "autres", "très", "ça", "what", "some", "do", "want", "using",
        |"s","so", "7","2", "8", "30", "them", "session", "application", "moins","moi","ainsi",
        |"how", "c'est", "d'un", "d'une", "souvent", "depuis", "sera", "cas",  "après", "sous", "encore",
        |"non", "use","n'est", "utilisateurs", "utilisant","more","plusieurs","nombreux","été","vie",
        |"i","look","has","grâce","différentes","take","toute","get","devient","afin","surtout","toujours",
        |"via","tels","avons","d'expérience",
        |"va","user","seront","déjà","mode","avoir","most",
        |"où","mon","see","which","quel","donc", "nos","d'applications", "aujourd'hui", "used", "learn"] } }
        |   }
        | }
      """.stripMargin
    val futureResponse = WS.url(host + "/" + index + "/_search?")
      .withAuth(username,password,AuthScheme.BASIC)
      .post(json)
    futureResponse.map {
      response =>
        response.status match {
          case 201 => Success(response.body)
          case 200 => Success(response.body)
          case other => Failure(new RuntimeException("Unable to load tag, HTTP Code " + response.status + ", ElasticSearch responded " + response.body))
        }
    }
  }
}

