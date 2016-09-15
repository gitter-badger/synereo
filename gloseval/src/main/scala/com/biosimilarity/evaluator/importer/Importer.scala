package com.biosimilarity.evaluator.importer

import java.io.File
import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.biosimilarity.evaluator.api._
import com.biosimilarity.evaluator.distribution.EvalConfConfig
import com.biosimilarity.evaluator.importer.models._
import com.biosimilarity.evaluator.omni.OmniClient
import com.biosimilarity.evaluator.spray.NodeUser
import com.biosimilarity.evaluator.spray.srp.ConversionUtils._
import com.biosimilarity.evaluator.spray.srp.SRPClient
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.Serialization.write
import org.json4s.native.JsonMethods._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

import scalaj.http.{Http, HttpOptions}

object LongPollActor {
  case object TerminateLongPoll
  case object SendPing
}

class LongPollActor(imp: Importer, sessionId: String) extends Actor {

  import Importer._
  import LongPollActor._
  implicit val formats = org.json4s.DefaultFormats

  var stopping = false
  def ping() = {
    try {
      val js = glosevalPost(SessionPing(sessionId))
      val arr = parse(js).extract[List[JObject]]
      arr.foreach(v => {
        val typ = (v \ "msgType").extract[String]
        typ match {
          case "sessionPong" => ()
          case "connectionProfileResponse" => ()
          case "addAliasLabelsResponse" => ()
          case "beginIntroductionResponse" => ()
          case "establishConnectionResponse" => ()
          case "evalComplete" => ()
          case "evalSubscribeResponse" => ()
          case _ =>
            println("WARNING - handler not provided for server sent message type : " + typ)
        }
      })
      context.system.scheduler.scheduleOnce(2.seconds, self, SendPing)

    } catch {
      case ex: Throwable =>
        println("exception during SessionPing, Session - " + sessionId + " : "+ ex)
        imp.stop(2)
    }
  }

  override def postStop(): Unit = {
    imp.sessionStopped(sessionId)
    super.postStop()
  }
  def receive = {
    case TerminateLongPoll =>
      stopping = true
    case SendPing =>
      if (stopping) context.stop(self)
      else ping()
  }
}

object Importer {
  private val GLOSEVAL_HOST = EvalConfConfig.serviceHostURI
  implicit val formats = org.json4s.DefaultFormats

  val longPollSystem: ActorSystem = ActorSystem("longpoll-system")

  def glosevalPost(data: RequestContent): String = {
    glosevalPost(data.asRequest)
  }

  def glosevalPost(msg : Request): String = {
    val requestBody = write(msg)
    glosevalPost(requestBody)
  }

  def glosevalPost(msgType: String, data: JValue): String = {
    //println(s"REQUEST: $msgType")
    val requestBody = write( ("msgType" -> msgType) ~ ("content" -> data) )
    glosevalPost(requestBody)
  }

  def glosevalPost(requestBody: String): String = {
    val tid = Thread.currentThread().getId()
    println(s"$tid: - REQUEST: $requestBody")
    val req = Http(GLOSEVAL_HOST)
      .timeout(1000, 600000)
      .header("Content-Type", "application/json")
      .option(HttpOptions.allowUnsafeSSL)
      .postData(requestBody)
    val response = req.asString.body

    println(s"$tid: - RESPONSE: $response")
    //if (response.startsWith("Malformed request")) throw new Exception(response)
    response
  }

  def makeAliasURI(alias: String) = s"alias://$alias/alias"

  //private def makeAliasLabel(label: String, color: String) = s"""leaf(text("${label}"),display(color("${color}"),image("")))"""

  def fromFile(dataJsonFile: File = EvalConfConfig.serviceDemoDataFile): Int = {
    println(s"Importing file: $dataJsonFile")
    val dataJson: String = scala.io.Source.fromFile(dataJsonFile).getLines.map(_.trim).mkString
    val imp = new Importer()
    imp.start()
    val rslt = imp.importData(dataJson)
    println("Import file returning : " + rslt)
    rslt
  }
}

class Importer {
  import Importer._


  // maps loginId to agentURI
  private val agentsById = scala.collection.mutable.Map[String, String]()

  // maps loginId to sessionURI
  private val sessionsById = scala.collection.mutable.Map[String, String]()

  // maps sessionURI to ActorRef
  private val sessionActors = scala.collection.mutable.Map[String, ActorRef]()

  // maps src+trgt to label
  private val cnxnLabels = scala.collection.mutable.Map[String, String]()

  private val labels = scala.collection.mutable.Map[String, LabelDesc]()

  private def resolveLabel(id: String): LabelDesc = labels(id)

  def startLongPoll(ssn: String) = {
    val ref = longPollSystem.actorOf( Props(new LongPollActor(this, ssn)) )
    sessionActors.put(ssn, ref)
    ref ! LongPollActor.SendPing
  }

  def expect(msgType: String, session: String): Option[JValue] = {
    println("Sending Ping")
    val js = glosevalPost(SessionPing(session))
    var rslt: Option[JValue] = None
    var done = false
    while (!done) {
      val arr = parse(js).extract[List[JValue]]
      arr.foreach(v => {
        val typ = (v \ "msgType").extract[String]
        typ match {
          case "sessionPong" => done = true
          case `msgType` =>
            done = true
            rslt = Some(v \ "content")
          case _ => ()
        }
      })
    }
    rslt
  }

  def expectAll(session: String): List[JValue] = {
    println("Sending Ping")
    var rslt: List[JValue] = Nil
    var done = false
    while (!done) {
      val js = glosevalPost(SessionPing(session))
      val arr = parse(js).extract[List[JValue]]
      arr.foreach(v => {
        val typ = (v \ "msgType").extract[String]
        typ match {
          case "sessionPong" =>
            done = true
          case _ =>
            rslt = v :: rslt
        }
      })
    }
    rslt.reverse
  }

  def createAgent(agent: AgentDesc): Option[String] = {
    val eml = agent.email + (if (agent.email.contains("@")) "" else "@livelygig.com")
    val jsonBlob = parse(agent.jsonBlob).extract[JObject]
    val srpClient = new SRPClient()
    srpClient.init
    val r1 = parse(glosevalPost(CreateUserStep1Request(eml))).extract[Response]
    r1.responseContent match {
      case ApiError(reason) =>
        println(s"create user, step 1, failed, reason : $reason")
        None
      case CreateUserStep1Response(salt) =>
        srpClient.calculateX(eml, agent.pwd, salt)
        val r2 = parse(glosevalPost(CreateUserStep2Request("noconfirm:"+eml, salt, srpClient.generateVerifier, jsonBlob))).extract[Response]
        r2.responseContent match {
          case ApiError(reason) =>
            println(s"create user, step 2, failed, reason : $reason")
            None
          case CreateUserStep2Response(agentURI) =>
            Some(agentURI)
          case _ => throw new Exception("Unspecified response")
        }
      case _ => throw new Exception("Unspecified response")
    }
  }

  def createSession(email: String, pwd: String): Option[String] = {
    val srpClient = new SRPClient()
    srpClient.init
    val emluri = "agent://email/"+email
    val r1 = parse(glosevalPost(InitializeSessionStep1Request(s"$emluri?A=${srpClient.calculateAHex}")))
      .extract[Response]
    r1.responseContent match {
      case ApiError(reason) =>
        println(s"initialize session, step 1, failed, reason : $reason")
        None
      case InitializeSessionStep1Response(salt, bval) =>
        srpClient.calculateX(email, pwd, salt)
        val r2 = parse(glosevalPost(InitializeSessionStep2Request(s"$emluri?M=${srpClient.calculateMHex(bval)}")))
          .extract[Response]
        r2.responseContent match {
          case ApiError(reason) =>
            println(s"initialize session, step 2, failed, reason : $reason")
            None
          case InitializeSessionResponse(sessionURI, m2) =>
            if(srpClient.verifyServerEvidenceMessage(fromHex(m2))) Some(sessionURI)
            else throw new Exception("Authentication failed on client")
          case _ => throw new Exception("Unspecified response")
        }
      case _ => throw new Exception("Unspecified response")
    }
  }

  def makeAgent(agent: AgentDesc): Unit = {
    createAgent(agent) match {
      case None => ()
      case Some(agentURI) =>
        val agentCap = agentURI.replace("agent://cap/", "").slice(0, 36)
        agentsById.put(agent.id, agentCap)
        createSession(agent.email, agent.pwd) match {
          case None => throw new Exception("Create session failure.")
          case Some(session) =>
            sessionsById.put(agent.id, session)
            startLongPoll(session)

            agent.aliasLabels match {
              case None =>()
              case Some(l) =>
                val lbls = l.map(lbl => makeLabel(LabelDesc.extractFrom(lbl)).toTermString(resolveLabel))
                glosevalPost(AddAliasLabelsRequest(session, "alias", lbls))
            }
        }
      case _ => throw new Exception("Unspecified response")
    }
  }

  def makeLabel(label: LabelDesc): LabelDesc = {

    def matchFunctor(name: String, lbl: LabelDesc): Boolean = {
      lbl match {
        case ComplexLabelDesc(_, fnctr, _) => name == fnctr
        case SimpleLabelDesc(_, _, Some(fnctr)) => name == fnctr
        case _ => false
      }
    }

    def reorderComponents(lbl: LabelDesc): LabelDesc = {
      lbl match {
        case ComplexLabelDesc(id, "leaf", lbls) =>
          val (tp, r) = lbls.partition(matchFunctor("text", _))
          if (tp.length > 1) throw new Exception("label must contain at most one text field")
          val (dp, r2) = r.partition(matchFunctor("display", _))
          if (dp.length > 1) throw new Exception("label must contain at most one display field")
          val lbls2 = tp ++ dp ++ r2
          ComplexLabelDesc(id, "leaf", lbls2)
        case _ => lbl
      }
    }

    label match {
      case smpl: SimpleLabelDesc =>
        smpl.id.foreach(s => labels.put(s, smpl))
        smpl
      case cmplx: ComplexLabelDesc =>
        val rslt = reorderComponents(cmplx)
        cmplx.id.foreach(s => labels.put(s, rslt))
        rslt
      case ref: LabelRef => ref //labels(ref.label)  // throw if not present??
    }

  }

  def makeCnxn(sessionId: String, connection: ConnectionDesc): Unit = {
    try {
      val sourceId = agentsById(connection.src.replace("agent://", ""))
      val sourceURI = makeAliasURI(sourceId)
      val targetId = agentsById(connection.trgt.replace("agent://", ""))
      val targetURI = makeAliasURI(targetId)
      val cnxnLabel = UUID.randomUUID().toString

      if (!cnxnLabels.contains(sourceId + targetId)) {
        glosevalPost(EstablishConnectionRequest(sessionId, sourceURI, targetURI, cnxnLabel))
        cnxnLabels.put(sourceId + targetId, cnxnLabel)
        cnxnLabels.put(targetId + sourceId, cnxnLabel)
      }
    } catch {
      case ex: Throwable => println("exception while creating connection: " + ex)
    }
  }

  def makePost(post: PostDesc): Unit = {
    try {
      var cnxns: List[Connection] = Nil

      val sourceId = agentsById(post.src)
      val sourceAlias = makeAliasURI(sourceId)
      val sourceSession = sessionsById(post.src)

      val selfcnxn = Connection("agent://" + sourceId, "agent://" + sourceId, "alias")

      post.trgts.foreach(trgt => {
        val targetId = agentsById(trgt)
        val lbl = cnxnLabels(sourceId + targetId)
        val trgtAlias = makeAliasURI(agentsById(trgt))
        cnxns = Connection(sourceAlias, trgtAlias, lbl) :: cnxns
      })

      val cont = EvalSubscribeContent(selfcnxn :: cnxns, post.label, post.value, post.uid)
      glosevalPost(EvalSubscribeRequest(sourceSession, EvalSubscribeExpression("insertContent", cont)))

    } catch {
      case ex: Throwable => println("exception while creating post: " + ex)
    }
  }

  def parseData(dataJsonFile: File = EvalConfConfig.serviceDemoDataFile) = {
    val dataJson = scala.io.Source.fromFile(dataJsonFile).getLines.map(_.trim).mkString
    parse(dataJson).extract[DataSetDesc]
  }

  def getAgentURI(email: String, password: String) = {
    val json = glosevalPost(GetAgentRequest(email, password))
    val jsv = parse(json)

    val tmsg = (jsv \ "msgType").extract[String]
    if (tmsg == "getAgentError") {
      println("create user failed, reason : " + (jsv \ "content" \ "reason").extract[String])
      None
    }
    else {
      val agentURI = (jsv \ "content" \ "agentURI").extract[String]
      Some(agentURI)
    }
  }

  private var _rslt = 0
  def terminating() = {
    _rslt > 0
  }

  def start() = {
    _rslt = 0
  }

  def sessionStopped(ssn: String) = {
    sessionActors.remove(ssn)
    glosevalPost(CloseSessionRequest(ssn))
  }

  def stop(rslt: Int) = {
    val doClose = !terminating()
    _rslt = Math.max(rslt, _rslt)
    if (doClose) {
      sessionsById.foreach(pr => {
        val ssn = pr._2
        sessionActors.get(ssn) match {
          case Some(ref) =>
            ref ! LongPollActor.TerminateLongPoll
          case None => println("ssn actor missing : " + ssn)
        }
      })
    }
    while (sessionActors.nonEmpty) {
      println("Sessions not yet closed : " + sessionActors.size)
      Thread.sleep(2000L)
    }
    sessionsById.clear()
    _rslt
  }

  def importData(dataJson: String) = {
    val dataset = parse(dataJson).extract[DataSetDesc]
    getAgentURI(NodeUser.email, NodeUser.password) match {
      case Some(uri) =>
        val adminId = uri.replace("agent://", "")
        try {
          val adminSession = createSession(NodeUser.email, NodeUser.password).get
          sessionsById.put(adminId, adminSession)
          startLongPoll(adminSession)

          //println(s"using admin session URI: $adminSession")
          dataset.labels match {
            case Some(lbls) => lbls.foreach(l => {
              if (!terminating()) makeLabel(LabelDesc.extractFrom(l))
            })
            case None => ()
          }
          dataset.agents.foreach(a => {
            if (!terminating()) makeAgent(a)
          })
          dataset.cnxns match {
            case Some(cnxns) => cnxns.foreach(cnxn => {
              if (!terminating()) makeCnxn(adminSession, cnxn)
            })
            case None => ()
          }
          dataset.posts match {
            case Some(posts) => posts.foreach(p => {
              if (!terminating()) makePost(p)
            })
            case None => ()
          }
        } catch {
          case ex: Throwable =>
            println("ERROR : "+ex)
            stop(1)
        }

      case _ => throw new Exception("Unable to open admin session")
    }
    stop(0)
  }

  def runTestFile(dataJsonFile: String = "src/test/resources/test-posts.json"): Unit = {
    // this routine doesn't keep sessions alive via longpoll.
    // the calls to expect might ...
    println("testing file : " + dataJsonFile)

    val adminURI =
      getAgentURI(NodeUser.email, NodeUser.password) match {
        case Some(uri) => uri
        case _ => throw new Exception("unable to open admin session")
      }
    val adminId = adminURI.replace("agent://", "")
    val adminSession = createSession(NodeUser.email, NodeUser.password).get
    sessionsById.put(adminId, adminSession) // longpoll on adminSession
    println("using admin session URI : " + adminSession)
    var testOmni = EvalConfConfig.isOmniRequired()

    def runTests(ssn : String, tests : List[JObject]) : Unit = {
      tests.foreach(el => {
        val typ = (el \ "type").extract[String]
        typ match {
          case "spawn" =>
            val withssn = (el \ "session").extractOpt[String] match {
              case Some(id) => sessionsById(id)
              case None => ssn
            }
            val js = glosevalPost(SpawnSessionRequest(withssn))
            val tssn = (parse(js) \ "content" \ "sessionURI").extract[String]
            println(tssn)
            val tsts = (el \ "content").extract[List[JObject]]
            runTests(tssn, tsts)
            glosevalPost(CloseSessionRequest(tssn))

          case "startCam" =>
            val js = glosevalPost(StartSessionRecording(ssn))
            println(js)

          case "stopCam" =>
            glosevalPost(SessionPing(ssn))  // make sure messages get returned to us before closing the cam
            val js = glosevalPost(StopSessionRecording(ssn))
            //println(js)
            val els = parse(js).extract[List[JObject]]
            els foreach (el => println(el))

          case "agent" =>
            val agent = (el \ "content").extract[AgentDesc]
            makeAgent(agent)
            if (testOmni) {
              val session = sessionsById(agent.id)
              val isok = glosevalPost(GetAmpWalletAddress(session))
              if (isok != "OK") throw new Exception("Unable to call getAmpWalletAddress")
              expect("getAmpWalletAddressResponse", session) match {
                case Some(js) =>
                  //println(pretty(js))
                  val oldaddr = (js \ "address").extract[String]
                  val isok2 = glosevalPost(SetAmpWalletAddress(session, OmniClient.testAmpAddress))
                  if (isok2 != "OK") throw new Exception("Unable to call setAmpWalletAddress")
                  expect("setAmpWalletAddressResponse", session) match {
                    case Some(js2) => {
                      //println(pretty(js))
                      val newaddr = (js2 \ "newaddress").extract[String]
                      if (newaddr != OmniClient.testAmpAddress) throw new Exception("setAmpWalletAddressResponse invalid")
                      val isok3 = glosevalPost(SetAmpWalletAddress(session, oldaddr))
                      if (isok3 != "OK") throw new Exception("Unable to call setAmpWalletAddress")
                      expect("setAmpWalletAddressResponse", session) match {
                        case Some(js3) => {
                          val addr = (js3 \ "newaddress").extract[String]
                          if (addr != oldaddr) throw new Exception("setAmpWalletAddressResponse invalid")
                        }
                        case _ => throw new Exception("Unable to set wallet address")
                      }
                    }
                    case _ => throw new Exception("Unable to set wallet address")
                  }
                case _ => throw new Exception("Unable to get wallet address")
              }
              testOmni = false // once is enough ...
            }

          case "cnxn" =>
            val cnxn = (el \ "content").extract[ConnectionDesc]
            makeCnxn(ssn, cnxn)

          case "label" =>
            val jo = (el \ "content").extract[JObject]
            val lbl = LabelDesc.extractFrom(jo)
            makeLabel(lbl)

          case "post" =>
            val post = (el \ "content").extract[PostDesc]
            makePost(post)

          case "getConnectionProfiles" => {
            val reqcnt = (el \ "requireCount").extract[Option[BigInt]]
            val isok = glosevalPost(GetConnectionProfiles(ssn))
            if (isok != "OK") throw new Exception("Unable to call getConnectionProfiles")
            val rsps = expectAll(ssn)
            reqcnt match {
              case Some(l) =>
                if (l != rsps.length) throw new Exception("Expected : " + l + ", but received: " + rsps.length)
                println("requireCount matched ok")
              case _ => println("requirecount not supplied?")
            }
            rsps foreach { js =>
              println(pretty(render(js)))
            }
          }
          case _ =>
            throw new Exception("Unknown test element : "+ typ)
        }
      })
    }

    val dataJson = scala.io.Source.fromFile(dataJsonFile).getLines.map(_.trim).mkString
    val tests = parse(dataJson).extract[List[JObject]]
    runTests(adminSession, tests)

    if (testOmni) OmniClient.runTests()
  }
}

