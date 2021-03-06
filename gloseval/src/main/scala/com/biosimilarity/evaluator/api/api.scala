package com.biosimilarity.evaluator.api

import org.json4s._

// helpers
case class Connection(source: String, target: String, label: String)
case class EvalSubscribeContent(cnxns: List[Connection], label: String, value: String, uid: String)
case class EvalSubscribeExpression(msgType: String, content: EvalSubscribeContent)

// actual API
case class Request(msgType: String, content: RequestContent)

sealed trait RequestContent {
  def asRequest: Request = {
    val nm: String  = this.getClass.getSimpleName
    val tnm: String = Character.toLowerCase(nm.charAt(0)) + nm.substring(1)
    Request(tnm, this)
  }
}
case object VersionInfoRequest extends RequestContent {
  override def asRequest: Request = Request("versionInfoRequest", this)
}
case class ConfirmEmailToken(token: String)                                                          extends RequestContent
case class CreateUserRequest(email: String, password: String, jsonBlob: JObject)                     extends RequestContent
case class CreateUserStep1Request(email: String)                                                     extends RequestContent
case class CreateUserStep2Request(email: String, salt: String, verifier: String, jsonBlob: JObject)  extends RequestContent
case class GetAgentRequest(email: String, password: String)                                          extends RequestContent
case class GetConnectionProfiles(sessionURI: String)                                                 extends RequestContent
case class UpdateUserRequest(sessionURI: String, jsonBlob: JObject)                                  extends RequestContent
case class StartSessionRecording(sessionURI: String)                                                 extends RequestContent
case class StopSessionRecording(sessionURI: String)                                                  extends RequestContent
case class SpawnSessionRequest(sessionURI: String)                                                   extends RequestContent
case class SessionPing(sessionURI: String)                                                           extends RequestContent
case class InitializeSessionRequest(agentURI: String)                                                extends RequestContent
case class InitializeSessionStep1Request(agentURI: String)                                           extends RequestContent
case class InitializeSessionStep2Request(agentURI: String)                                           extends RequestContent
case class CloseSessionRequest(sessionURI: String)                                                   extends RequestContent
case class AddAliasLabelsRequest(sessionURI: String, alias: String, labels: List[String])            extends RequestContent
case class EstablishConnectionRequest(sessionURI: String, aURI: String, bURI: String, label: String) extends RequestContent
case class EvalSubscribeRequest(sessionURI: String, expression: EvalSubscribeExpression)             extends RequestContent
case class ResetDatabaseRequest(sessionURI: String)                                                  extends RequestContent
case class GetAmpWalletAddress(sessionURI: String)                                                   extends RequestContent
case class SetAmpWalletAddress(sessionURI: String, address: String)                                  extends RequestContent
case class OmniTransfer(sessionURI: String, target: String, amount: BigDecimal)                      extends RequestContent
case class OmniGetBalance(sessionURI: String)                                                        extends RequestContent

sealed trait ResponseContent
case class VersionInfoResponse(glosevalVersion: String, scalaVersion: String, mongoDBVersion: String, rabbitMQVersion: String)
    extends ResponseContent {
  override def toString: String = s"""|GLoSEVal version: $glosevalVersion
                                      |Scala version: $scalaVersion
                                      |MongoDB version: $mongoDBVersion
                                      |RabbitMQ version: $rabbitMQVersion""".stripMargin
}
case class InitializeSessionStep1Response(salt: String, B: String)   extends ResponseContent
case class InitializeSessionResponse(sessionURI: String, M2: String) extends ResponseContent
case class CreateUserStep1Response(salt: String)                     extends ResponseContent
case class CreateUserStep2Response(agentURI: String)                 extends ResponseContent
case class CreateUserWaiting(token: String)                          extends ResponseContent
case class ApiError(reason: String)                                  extends ResponseContent

case class Response(msgType: String, content: JObject) {
  val responseContent = (msgType, content) match {
    case ("createUserStep1Response", JObject(JField("salt", JString(s)) :: Nil))      => CreateUserStep1Response(s)
    case ("createUserStep2Response", JObject(JField("agentURI", JString(au)) :: Nil)) => CreateUserStep2Response(au)
    case ("createUserWaiting", JObject(JField("token", JString(tok)) :: Nil))         => CreateUserWaiting(tok)
    case ("initializeSessionStep1Response", JObject(JField("s", JString(s)) :: JField("B", JString(b)) :: Nil)) =>
      InitializeSessionStep1Response(s, b)
    case ("initializeSessionResponse",
          JObject(
          JField("sessionURI", JString(ssn)) ::
            JField("listOfAliases", JArray(la)) ::
              JField("defaultAlias", JString(da)) ::
                JField("listOfLabels", JArray(ll)) ::
                  JField("listOfConnections", JArray(lc)) ::
                    JField("lastActiveLabel", JString(lal)) ::
                      JField("jsonBlob", JObject(jb)) ::
                        JField("M2", JString(m2)) :: Nil)) =>
      InitializeSessionResponse(ssn, m2)
    case ("createUserError", JObject(JField("reason", JString(r)) :: Nil))        => ApiError(r)
    case ("initializeSessionError", JObject(JField("reason", JString(r)) :: Nil)) => ApiError(r)
  }
}

// ht 2016-08-26:
// I'm going to try something slightly different here.
// See ApiSpec's versionInfoRequest test for a usage example.
case class AltResponse[T <: ResponseContent](msgType: String, content: T)
