package org.cognoseed.fitbit4s

import java.net.URL
import java.util.Properties
import java.io.{InputStream, OutputStream}

import oauth.signpost.OAuthConsumer
import oauth.signpost.basic.DefaultOAuthProvider

import retrofit.RestAdapter

import se.akerfeldt.signpost.retrofit.RetrofitHttpOAuthConsumer
import org.cognoseed.retrofit.GsonNestedConverter
import org.cognoseed.retrofit.SigningUrlConnectionClient

import com.google.gson.Gson

import scala.collection.JavaConversions._

class FitbitClient(consumer: RetrofitHttpOAuthConsumer) extends FitbitEndpoints {
  private lazy val provider =
    new DefaultOAuthProvider(
      RequestTokenUrl,
      AccessTokenUrl,
      AuthorizeUrl
    )

  if (consumer.getConsumerKey == null || consumer.getConsumerSecret == null)
    throw new IllegalArgumentException("consumerKey/Secret cannot be null.")

  private val adapter = new RestAdapter.Builder()
    .setServer("https://api.fitbit.com")
    .setClient(new SigningUrlConnectionClient(consumer))
    .setConverter(new GsonNestedConverter(new Gson())).build()
  private val service = adapter.create(classOf[FitbitService])

  def this(
    consumerKey: String,
    consumerSecret: String,
    accessToken: String = null,
    accessTokenSecret: String = null
  ) = {
    this(new RetrofitHttpOAuthConsumer(consumerKey, consumerSecret))
    if (accessToken != null && accessTokenSecret != null)
      consumer.setTokenWithSecret(accessToken, accessTokenSecret)
  }

  def accessTokenUrl: String =
    provider.retrieveRequestToken(consumer, null)
    
  def getAccessTokens(verifier: String): Unit =
    provider.retrieveAccessToken(consumer, verifier)

  def store(stream: OutputStream): Unit = {
    val prop = new Properties()
    prop.setProperty("consumerKey", consumer.getConsumerKey)
    prop.setProperty("consumerSecret", consumer.getConsumerSecret)
    prop.setProperty("accessToken", consumer.getToken)
    prop.setProperty("accessTokenSecret", consumer.getTokenSecret)
    prop.store(stream, "OAuth credentials for this application.")
  }

  def getTimeSeries(
    resource: String,
    end: String = "1m",
    start: String = "today"
  ): List[TimeSeriesRecord] = {
    if (!start.equals("today") && !FitbitClient.isDate(start))
      throw new IllegalArgumentException("start must be a date or \"today\"")
    if (!FitbitClient.isDate(end) && !FitbitClient.isRange(end))
      throw new IllegalArgumentException("end must be a date or range")

    service.getTimeSeries(
      "-",
      resource.split("/")(0),
      resource.split("/")(1),
      start,
      end
    ).toList
  }

  def getUserInfo(): UserRecord = {
    service.getUserInfo("-")
  }

}

object FitbitClient {
  private val range =
    Set("1d", "7d", "30d", "1w", "1m", "3m", "6m", "1y", "max")

  def fromProperties(prop: Properties): FitbitClient = {
    new FitbitClient(
      prop.getProperty("consumerKey"),
      prop.getProperty("consumerSecret"),
      prop.getProperty("accessToken"),
      prop.getProperty("accessTokenSecret")
    )
  }

  def isRange(in: String): Boolean =
    range.contains(in)

  def isDate(in: String): Boolean =
    in.matches("""(\d\d\d\d)-(\d\d)-(\d\d)""")
}
