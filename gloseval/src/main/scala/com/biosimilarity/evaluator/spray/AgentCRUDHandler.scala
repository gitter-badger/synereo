// -*- mode: Scala;-*- 
// Filename:    AgentCRUDHandler.scala 
// Authors:     lgm                                                    
// Creation:    Tue Oct  1 15:44:37 2013 
// Copyright:   Not supplied 
// Description: 
// ------------------------------------------------------------------------

package com.biosimilarity.evaluator.spray

import com.protegra_ati.agentservices.protocols.msgs._

import com.biosimilarity.evaluator.distribution._
import com.biosimilarity.evaluator.msgs.agent.crud._
import com.biosimilarity.evaluator.prolog.PrologDSL._
import com.biosimilarity.lift.model.store._
import com.biosimilarity.lift.lib._
import com.biosimilarity.evaluator.spray.agent.{ExternalIdentity, ExternalIdType}

import spray.http._

import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._


import java.net.URI

trait AgentCRUDSchema {
  self : EvaluationCommsService =>

  type CCL = CnxnCtxtLabel[String,String,String]

  var _aliasStorageLocation : Option[CCL] = None
  def aliasStorageLocation() : CCL = {
    _aliasStorageLocation match {
      case Some( asl ) => asl
      case None => {
        fromTermString(
          "aliasList( true )"
        ).getOrElse(
          throw new Exception( "Couldn't parse label: " + "aliasList( true )" )
        )          
      }
    }
  }

  var _defaultAliasStorageLocation : Option[CCL] = None
  def defaultAliasStorageLocation() : CCL = {
    _defaultAliasStorageLocation match {
      case Some( dasl ) => dasl
      case None => {
        fromTermString(
          "defaultAlias( true )"
        ).getOrElse(
          throw new Exception( "Couldn't parse label: " + "defaultAlias( true )" )
        )
      }
    }
  }

  var _externalIdsStorageLocation : Option[CCL] = None
  def externalIdsStorageLocation() : CCL = {
    _externalIdsStorageLocation match {
      case Some( csl ) => csl
      case None => {
        fromTermString(
          "externalIdsList( true )"
        ).getOrElse(
          throw new Exception( "Couldn't parse label: " + "externalIdsList( true )" )
        )
      }
    }
  }

  var _defaultExternalIdStorageLocation : Option[CCL] = None
  def defaultExternalIdStorageLocation() : CCL = {
    _defaultExternalIdStorageLocation match {
      case Some( csl ) => csl
      case None => {
        fromTermString(
          "defaultExternalId( true )"
        ).getOrElse(
          throw new Exception( "Couldn't parse label: " + "defaultExternalId( true )" )
        )
      }
    }
  }
  
  var _labelsStorageLocation : Option[CCL] = None
  def labelsStorageLocation() : CCL = {
    _labelsStorageLocation match {
      case Some( lsl ) => lsl
      case None => {
        fromTermString(
          "labelList( true )"
        ).getOrElse(
          throw new Exception( "Couldn't parse label: " + "labelList( true )" )
        )          
      }
    }
  }

  var _defaultLabelStorageLocation : Option[CCL] = None
  def defaultLabelStorageLocation() : CCL = {
    _defaultLabelStorageLocation match {
      case Some( dlsl ) => dlsl
      case None => {
        fromTermString(
          "defaultLabel( true )"
        ).getOrElse(
          throw new Exception( "Couldn't parse label: " + "defaultLabel( true )" )
        )
      }
    }
  }

  var _biCnxnsStorageLocation : Option[CCL] = None
  def biCnxnsStorageLocation() : CCL = {
    _biCnxnsStorageLocation match {
      case Some( csl ) => csl
      case None => {
        fromTermString(
          "biCnxnsList( true )"
        ).getOrElse(
          throw new Exception( "Couldn't parse label: " + "biCnxnsList( true )" )
        )
      }
    }
  }

  def agentFromSession(
    sessionURI: URI
  ) : URI = {
    new URI(
      "agent",
      sessionURI.getHost(),
      null,
      null
    )    
  }

  def aliasFromSession(
    sessionURI: URI
  ) : URI = {
    new URI(
      "alias",
      sessionURI.getHost(),
      "/alias",
      null
    )    
  }

  def identityAliasFromAgent(
    agentURI : URI
  ) : PortableAgentCnxn = {
    PortableAgentCnxn(agentURI, "identity", agentURI)
  }  

  def getAliasCnxn(
    sessionURI : URI,
    aliasStr : String
  ) : PortableAgentCnxn = {
    val agentURI : URI = agentFromSession( sessionURI )
    PortableAgentCnxn( agentURI, aliasStr, agentURI )
  }    
  
}

trait AgentCRUDHandler extends AgentCRUDSchema {
  self : EvaluationCommsService =>
 
  import DSLCommLink.mTT
  import ConcreteHL._

  //## Methods on Sessions
  //### Ping and pong
  def handlesessionPing(
    msg : sessionPing
  ) : Unit = {
    BasicLogService.tweet( 
      "Entering: handlesessionPing with msg : " + msg
    )
  }
  def handlesessionPong(
    msg : sessionPong
  ) : Unit = {
    BasicLogService.tweet( 
      "Entering: handlesessionPong with msg : " + msg
    )
  }

  //## Methods on Agents
  //### createAgent
  def handlecreateAgentRequest(
    msg : createAgentRequest
  ) : Unit = {
    BasicLogService.tweet( 
      "Entering: handlecreateAgentRequest with msg : " + msg
    )
  }
  //    - `authType == "password"` (case-insensitive)
  
  //### initializeSession
  def handleinitializeSessionRequest(
    msg : initializeSessionRequest
  ) : Unit = {
    BasicLogService.tweet( 
      "Entering: handleinitializeSessionRequest with msg : " + msg
    )
  }

  //### External identities
  //#### addAgentExternalIdentity
  def handleaddAgentExternalIdentityRequest(
     //    - We only support adding one identity per message because of need for confirmation
    msg : addAgentExternalIdentityRequest[ExternalIdentity]
  ) : Unit = {
    BasicLogService.tweet( "Entering: handleaddAgentExternalIdentityRequest with msg : " + msg )

    val sessionURIStr = msg.sessionURI.toString
    val aliasStorageCnxn = identityAliasFromAgent( agentFromSession( msg.sessionURI ) )

    def handleRsp( v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handleaddAgentExternalIdentityRequest | onGet: got " + v )

      val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
        BasicLogService.tweet( "handleaddAgentExternalIdentityRequest | onGet | onPut" )

        SessionManager.cometMessage(
          sessionURIStr,
          compact(
            render(
              ( "msgType" -> "addAgentExternalIdentityResponse" ) ~
                ( "content" -> ( "sessionURI" -> sessionURIStr ) )
            )
          )
        )
      }

      BasicLogService.tweet( "handleaddAgentExternalIdentityRequest | onGet | onPut | adding " + msg.id )

      put[ExternalIdentity]( externalIdsStorageLocation(), List( aliasStorageCnxn ), msg.id, onPut )
    }

    val onGet : Option[mTT.Resource] => Unit = (optRsrc) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handleaddAgentExternalIdentityRequest | onGet: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( v )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "addAgentExternalIdentityError" ) ~
              ( "content" -> (
                ( "sessionURI" -> sessionURIStr ) ~
                  ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
                ) )
          ) ) )
        }
      }
    }

    //agentMgr().get( aliasStorageLocation(), List( aliasStorageCnxn ), onGet )
    get( aliasStorageLocation(), List( aliasStorageCnxn ), onGet )
  }

  def handleaddAgentExternalIdentityToken(
    msg : addAgentExternalIdentityToken
  ) : Unit = {
    BasicLogService.tweet( 
      "Entering: handleaddAgentExternalIdentityToken with msg : " + msg
    )
  }
  
  
  //#### removeAgentExternalIdentities
  def handleremoveAgentExternalIdentitiesRequest(
    msg : removeAgentExternalIdentitiesRequest[ExternalIdentity]
  ) : Unit = {
    BasicLogService.tweet(
      "Entering: handleremoveAgentExternalIdentitiesRequest with msg : " + msg
    )
  }
  
  //#### getAgentExternalIdentities
  def handlegetAgentExternalIdentitiesRequest(
    msg : getAgentExternalIdentitiesRequest[ExternalIdType]
  ) : Unit = {
    BasicLogService.tweet(
      "Entering: handlegetAgentExternalIdentitiesRequest with msg : " + msg
    )
  }
  //    - One value of `IDType` is `ANY`

  //### Aliases
  //#### addAgentAliases
  def handleaddAgentAliasesRequest(
    msg : addAgentAliasesRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handleaddAgentAliasesRequest with msg: " + msg )

    val sessionURIStr = msg.sessionURI.toString
    val aliasStorageCnxn = identityAliasFromAgent( agentFromSession( msg.sessionURI ) )

    def handleRsp( v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handleaddAgentAliasesRequest | onGet: got " + v )

      val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
        BasicLogService.tweet( "handleaddAgentAliasesRequest | onGet | onPut" )
        
        SessionManager.cometMessage(
          sessionURIStr,
          compact(
            render(
              ( "msgType" -> "addAgentAliasesResponse" ) ~
              ( "content" -> ( "sessionURI" -> sessionURIStr ) )
            )
          )
        )
      }

      val prevAliasList = v match {
        case PostedExpr( ( PostedExpr( prevAliasListStr : String ), _, _ ) ) =>
          Serializer.deserialize[List[String]]( prevAliasListStr )
        case Bottom => Nil
      }
      
      val newAliasList = prevAliasList ++ msg.aliases
      val newAliasListStr = Serializer.serialize( newAliasList )
      
      BasicLogService.tweet( "handleaddAgentAliasesRequest | onGet | onPut | updating aliasList with " + newAliasListStr )
      
      //agentMgr().put[String]( aliasStorageLocation(), List( aliasStorageCnxn ), newAliasListStr, onPut )
      put[String]( aliasStorageLocation(), List( aliasStorageCnxn ), newAliasListStr, onPut )
    }

    val onGet : Option[mTT.Resource] => Unit = (optRsrc) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handleaddAgentAliasesRequest | onGet: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( v )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "addAgentAliasesError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }
      
    //agentMgr().get( aliasStorageLocation(), List( aliasStorageCnxn ), onGet )
    get( aliasStorageLocation(), List( aliasStorageCnxn ), onGet )
  }

  
  //#### removeAgentAliases
  def handleremoveAgentAliasesRequest(
    msg : removeAgentAliasesRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handleremoveAgentAliasesRequest with msg: " + msg )

    val aliasStorageCnxn = identityAliasFromAgent( agentFromSession( msg.sessionURI ) )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handleremoveAgentAliasesRequest | onGet: got " + v )
      
      val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
        BasicLogService.tweet( "handleremoveAgentAliasesRequest | onGet | onPut" )
        
        SessionManager.cometMessage( sessionURIStr, compact( render(
          ( "msgType" -> "removeAgentAliasesResponse" ) ~
          ( "content" ->
           ( "sessionURI" -> sessionURIStr )
         )
        ) ) )
      }

      val prevAliasList = v match {
        case PostedExpr( ( PostedExpr( prevAliasListStr : String ), _, _, _ ) ) =>
          Serializer.deserialize[List[String]]( prevAliasListStr )
        case Bottom => Nil
      }
      
      val newAliasList = prevAliasList.filterNot( msg.aliases.contains )
      val newAliasListStr = Serializer.serialize( newAliasList )
      
      BasicLogService.tweet( "handleremoveAgentAliasesRequest | onGet | onPut | updating aliasList with " + newAliasListStr )
      
      //agentMgr().put[String]( aliasStorageLocation(), List( aliasStorageCnxn ), newAliasListStr, onPut )
      put[String]( aliasStorageLocation(), List( aliasStorageCnxn ), newAliasListStr, onPut )
    }

    val onGet : Option[mTT.Resource] => Unit = (optRsrc) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handleremoveAgentAliasesRequest | onGet: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( v )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "removeAgentAliasesError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }

    //agentMgr().get( aliasStorageLocation(), List( aliasStorageCnxn ), onGet )
    get( aliasStorageLocation(), List( aliasStorageCnxn ), onGet )
  }
  
  
  //#### getAgentAliases
  def handlegetAgentAliasesRequest(
    msg : getAgentAliasesRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handlegetAgentAliasesRequest with msg: " + msg )

    val aliasStorageCnxn = identityAliasFromAgent( agentFromSession( msg.sessionURI ) )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handlegetAgentAliasesRequest | onFetch: got " + v )

      val aliasList = v match {
        case PostedExpr( ( PostedExpr( aliasListStr : String ), _, _, _ ) ) =>
          Serializer.deserialize[List[String]]( aliasListStr )
        case Bottom => Nil
      }
      
      SessionManager.cometMessage( sessionURIStr, compact( render(
        ( "msgType" -> "getAgentAliasesResponse" ) ~
        ( "content" ->
         ( "sessionURI" -> sessionURIStr ) ~
         ( "aliases" -> aliasList )
       )
      ) ) )
    }

    val onFetch : Option[mTT.Resource] => Unit = (optRsrc) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handlegetAgentAliasesRequest | onFetch: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( v )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "getAgentAliasesError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }
    
    //agentMgr().fetch( aliasStorageLocation(), List( aliasStorageCnxn ), onFetch )
    fetch( aliasStorageLocation(), List( aliasStorageCnxn ), onFetch )
  }
  
  
  //#### getDefaultAlias
  def handlegetDefaultAliasRequest(
    msg : getDefaultAliasRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handlegetDefaultAliasRequest with msg: " + msg )

    val aliasStorageCnxn = identityAliasFromAgent( agentFromSession( msg.sessionURI ) )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( optRsrc : Option[mTT.Resource], v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handlegetDefaultAliasRequest | onFetch: got " + v )

      v match {
        case PostedExpr( ( PostedExpr( defaultAlias : String ), _, _, _ ) ) => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "getDefaultAliasResponse" ) ~
            ( "content" ->
             ( "sessionURI" -> sessionURIStr ) ~
             ( "alias" -> defaultAlias )
           )
          ) ) )
        }
        case Bottom => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "getDefaultAliasError" ) ~
            ( "content" ->
             ( "sessionURI" -> sessionURIStr ) ~
             ( "reason" -> "No default alias exists" )
           )
          ) ) )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "getDefaultAliasError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }
    
    val onFetch : Option[mTT.Resource] => Unit = (optRsrc) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handlegetDefaultAliasRequest | onFetch: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( optRsrc, v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( optRsrc, v )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "getDefaultAliasError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }

    //agentMgr().fetch( defaultAliasStorageLocation(), List( aliasStorageCnxn ), onFetch )
    fetch( defaultAliasStorageLocation(), List( aliasStorageCnxn ), onFetch )
  }
  
  
  //#### setDefaultAlias
  def handlesetDefaultAliasRequest(
    msg : setDefaultAliasRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handlesetDefaultAliasRequest with msg: " + msg )

    val aliasStorageCnxn = identityAliasFromAgent( agentFromSession( msg.sessionURI ) )

    val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
      BasicLogService.tweet( "handlesetDefaultAliasRequest | onPut" )

      val sessionURIStr = msg.sessionURI.toString

      SessionManager.cometMessage( sessionURIStr, compact( render(
        ( "msgType" -> "setDefaultAliasResponse" ) ~
        ( "content" ->
          ( "sessionURI" -> sessionURIStr )
        )
      ) ) )
    }

    //agentMgr().put[String]( defaultAliasStorageLocation(), List( aliasStorageCnxn ), msg.alias, onPut )
    put[String]( defaultAliasStorageLocation(), List( aliasStorageCnxn ), msg.alias, onPut )
  }


  //## Methods on Aliases
  //### External identities
  //#### addAliasExternalIdentities
  def handleaddAliasExternalIdentitiesRequest(
    msg : addAliasExternalIdentitiesRequest[ExternalIdentity]
  ) : Unit = {
    BasicLogService.tweet( "Entering: handleaddAliasExternalIdentitiesRequest with msg : " + msg )

    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( optRsrc : Option[mTT.Resource], v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handleaddAliasExternalIdentitiesRequest | onGet: got " + v )

      val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
        BasicLogService.tweet( "handleaddAliasExternalIdentitiesRequest | onGet | onPut" )
        
        SessionManager.cometMessage(sessionURIStr, compact( render(
          ( "msgType" -> "addAliasExternalIdentityResponse" ) ~
          ( "content" -> ( "sessionURI" -> sessionURIStr ) )
        ) ) )
      }
      
      v match {
        case PostedExpr( (PostedExpr( previousExternalIdsList : List[ExternalIdentity] ), _, _, _) ) => {
          val newExternalIdsList = previousExternalIdsList ++ msg.ids
          
          BasicLogService.tweet(
            "handleaddAliasExternalIdentitiesRequest | onGet | onPut | updating externalIdsList with " + newExternalIdsList
          )
          
          //agentMgr().put[List[ID]]( externalIdsStorageLocation, List( aliasStorageCnxn ), newExternalIdsList, onPut )
          put[List[ExternalIdentity]]( externalIdsStorageLocation, List( aliasStorageCnxn ), newExternalIdsList, onPut )
        }
        case Bottom => {
          //agentMgr().put[List[ID]]( externalIdsStorageLocation, List( aliasStorageCnxn ), msg.ids, onPut )
          put[List[ExternalIdentity]]( externalIdsStorageLocation, List( aliasStorageCnxn ), msg.ids, onPut )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "addAliasExternalIdentitiesError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }

    val onGet : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handleaddAliasExternalIdentitiesRequest | onGet: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( optRsrc, v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( optRsrc, v )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "addAliasExternalIdentitiesError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }

    //agentMgr().get( externalIdsStorageLocation, List( aliasStorageCnxn ), onGet )
    get( externalIdsStorageLocation, List( aliasStorageCnxn ), onGet )
  }
  //    - Only ids already on the agent are allowed
  
  
  //#### removeAliasExternalIdentities
  def handleremoveAliasExternalIdentitiesRequest[ID](
    msg : removeAliasExternalIdentitiesRequest[ID]
  ) : Unit = {
    BasicLogService.tweet( "Entering: handleremoveAliasExternalIdentitiesRequest with msg : " + msg )

    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( optRsrc : Option[mTT.Resource], v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handleremoveAliasExternalIdentitiesRequest | onGet: got " + v )
      
      val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
        BasicLogService.tweet( "handleremoveAliasExternalIdentitiesRequest | onGet | onPut" )
        
        SessionManager.cometMessage(sessionURIStr, compact( render(
          ( "msgType" -> "removeAliasExternalIdentitiesResponse" ) ~
          ( "content" -> ( "sessionURI" -> sessionURIStr ) )
        ) ) )
      }
      
      v match {
        case PostedExpr( (PostedExpr( previousExternalIdsList : List[ID] ), _, _, _) ) => {
          val newExternalIdsList = previousExternalIdsList.filterNot( msg.ids.contains )
          
          BasicLogService.tweet(
            "handleremoveAliasExternalIdentitiesRequest | onGet | onPut | updating cnxnList with " + newExternalIdsList
          )
          
          //agentMgr().put[List[ID]]( externalIdsStorageLocation, List( aliasStorageCnxn ), newExternalIdsList, onPut )
          put[List[ID]]( externalIdsStorageLocation, List( aliasStorageCnxn ), newExternalIdsList, onPut )
        }
        case Bottom => {
          BasicLogService.tweet( "handleremoveAliasExternalIdentitiesRequest | onGet: no externalIdsList exists" )
          
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "removeAliasExternalIdentitiesError" ) ~
            ( "content" ->
             ( "sessionURI" -> sessionURIStr ) ~
             ( "reason" -> "no externalIdsList exists" )
           )
          ) ) )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "removeAliasExternalIdentitiesError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }

    val onGet : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handleremoveAliasExternalIdentitiesRequest | onGet: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( optRsrc, v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( optRsrc, v )
        }
        case _ => {
          SessionManager.cometMessage( sessionURIStr, compact( render(
            ( "msgType" -> "removeAliasExternalIdentitiesError" ) ~
            ( "content" -> (
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            ) )
          ) ) )
        }
      }
    }

    //agentMgr().get( externalIdsStorageLocation, List( aliasStorageCnxn ), onGet )
    get( externalIdsStorageLocation, List( aliasStorageCnxn ), onGet )
  }


  //#### getAliasExternalIdentities
  def handlegetAliasExternalIdentitiesRequestByType( msg : getAliasExternalIdentitiesRequest[ExternalIdType]) : Unit = {
    BasicLogService.tweet( "Entering: handlegetAliasExternalIdentitiesRequestByType with msg : " + msg )
  }
  //    - One value of `IDType` is `ANY`

  //#### getAliasExternalIdentities
  //def handlegetAliasExternalIdentitiesRequest( msg : getAliasExternalIdentitiesRequest ) : Unit = {
  //  BasicLogService.tweet( "Entering: handlegetAliasExternalIdentitiesRequest with msg : " + msg )
  //}

  //#### setAliasDefaultExternalIdentity
  def handlesetAliasDefaultExternalIdentityRequest(
    msg : setAliasDefaultExternalIdentityRequest[ExternalIdType]
  ) : Unit = {

    BasicLogService.tweet( "Entering: handlesetAliasDefaultExternalIdentityRequest with msg : " + msg )

    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )

    val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
      BasicLogService.tweet( "handlesetAliasDefaultExternalIdentityRequest | onPut" )

      val sessionURIStr = msg.sessionURI.toString

      SessionManager.cometMessage(sessionURIStr, compact( render(
        ( "msgType" -> "setAliasDefaultExternalIdentityResponse" ) ~
        ( "content" -> ( "sessionURI" -> sessionURIStr ) )
      ) ) )
    }

    //agentMgr().put[ID]( defaultExternalIdStorageLocation, List( aliasStorageCnxn ), msg.id, onPut )
    put[ExternalIdType]( defaultExternalIdStorageLocation, List( aliasStorageCnxn ), msg.id, onPut )
  }
  
  
  //### Connections
  //#### removeAliasConnections
  def handleremoveAliasConnectionsRequest(
    msg : removeAliasConnectionsRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handleremoveAliasConnectionsRequest with msg : " + msg )

    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( optRsrc : Option[mTT.Resource], v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handleremoveAliasConnectionsRequest | onGet: got " + v )

      val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
        BasicLogService.tweet( "handleremoveAliasConnectionsRequest | onGet | onPut" )
        
        // TODO: Should the write cnxn be cleared out or removed?
        
        SessionManager.cometMessage(sessionURIStr, compact( render(
          ( "msgType" -> "removeAliasConnectionsResponse" ) ~
          ( "content" -> ( "sessionURI" -> sessionURIStr ) )
        ) ) )
      }
      
      v match {
        case PostedExpr( (PostedExpr( previousBiCnxnListStr : String ), _, _, _) ) => {
          // TODO: Deserialize string
          // TODO: Filter BiCnxns based on passed in Cnxns
          // TODO: Serialize new BiCnxns list
          val newBiCnxnListStr = previousBiCnxnListStr
          
          BasicLogService.tweet(
            "handleremoveAliasConnectionsRequest | onGet | onPut | updating biCnxnList with " + newBiCnxnListStr
          )
          
          //agentMgr().put( biCnxnsStorageLocation, List( aliasStorageCnxn ), newBiCnxnListStr, onPut )
          put( biCnxnsStorageLocation, List( aliasStorageCnxn ), newBiCnxnListStr, onPut )
        }
        case _ => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "removeAliasConnectionsError" ) ~
            ( "content" -> 
             ( "sessionURI" -> sessionURIStr ) ~
             ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
           )
          ) ) )
        }
      }
    }

    val onGet : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handleremoveAliasConnectionsRequest | onGet: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( optRsrc, v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( optRsrc, v )
        }
        case _ => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "removeAliasConnectionsError" ) ~
            ( "content" -> 
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            )
          ) ) )
        }
      }
    }

    //agentMgr().get( biCnxnsStorageLocation, List( aliasStorageCnxn ), onGet )
    get( biCnxnsStorageLocation, List( aliasStorageCnxn ), onGet )
  }

  //#### getAliasConnections
  def handlegetAliasConnectionsRequest(
    msg : getAliasConnectionsRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handlegetAliasConnectionsRequest with msg : " + msg )

    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handlegetAliasConnectionsRequest | onFetch: got " + v )

      val biCnxnList = v match {
        case PostedExpr( (PostedExpr( biCnxnListStr : String ), _, _, _) ) => {
          Serializer.deserialize[List[PortableAgentBiCnxn]]( biCnxnListStr )
        }
        case Bottom => Nil
        case _ => throw new Exception("Unrecognized resource!")
      }

      def biCnxnToJObject( biCnxn : PortableAgentBiCnxn ) : JObject = {
        ( "source" -> biCnxn.writeCnxn.src.toString ) ~
        ( "label" -> biCnxn.writeCnxn.label ) ~
        ( "target" -> biCnxn.writeCnxn.trgt.toString )
      }

      SessionManager.cometMessage(sessionURIStr, compact( render(
        ( "msgType" -> "getAliasConnectionsResponse" ) ~
        ( "content" ->
         ( "sessionURI" -> sessionURIStr ) ~
         ( "connections" -> biCnxnList.map( biCnxnToJObject( _ ) ) )
       )
      ) ) )
    }

    val onFetch : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => try {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handlegetAliasConnectionsRequest | onFetch: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( v )
        }
        case _ => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "getAliasConnectionsError" ) ~
            ( "content" -> 
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            )
          ) ) )
        }
      }
    } catch {
      case e: Exception => {
        SessionManager.cometMessage(sessionURIStr, compact( render(
          ( "msgType" -> "getAliasConnectionsError" ) ~
          ( "content" -> 
            ( "sessionURI" -> sessionURIStr ) ~
            ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
          )
        ) ) )
      }
    }

    //agentMgr().fetch( biCnxnsStorageLocation, List( aliasStorageCnxn ), onFetch )
    fetch( biCnxnsStorageLocation, List( aliasStorageCnxn ), onFetch )
  }

  //### Labels
  //#### addAliasLabels
  def handleaddAliasLabelsRequest(
    msg : addAliasLabelsRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handleaddAliasLabelsRequest with msg : " + msg )

    val sessionURIStr = msg.sessionURI.toString
    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )
    
    def handleRsp( optRsrc : Option[mTT.Resource], v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handleaddAliasLabelsRequest | onGet: got " + v )
      
      val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
        BasicLogService.tweet( "handleaddAliasLabelsRequest | onGet | onPut" )
        
        SessionManager.cometMessage(sessionURIStr, compact( render(
          ( "msgType" -> "addAliasLabelsResponse" ) ~
          ( "content" -> ( "sessionURI" -> sessionURIStr ) )
        ) ) )
      }
      
      v match {
        case PostedExpr( (PostedExpr( previousLabelList : String ), _, _, _) ) => {
          val newLabelList = compact(render(parse(previousLabelList) ++ msg.labels.map(_.show)))
          BasicLogService.tweet("handleaddAliasLabelsRequest | onGet | onPut | updating labelList with " + newLabelList )          
          put[String](
            labelsStorageLocation,
            List( aliasStorageCnxn ),
            newLabelList,
            onPut
          )
        }
        case Bottom => {
          put[String](
            labelsStorageLocation,
            List( aliasStorageCnxn ),
            compact(render(msg.labels.map(_.show))),
            onPut
          )
        }
        case _ => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "addAliasLabelsError" ) ~
            ( "content" -> 
             ( "sessionURI" -> sessionURIStr ) ~
             ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
           )
          ) ) )
        }
      }
    }

    val onGet : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {      
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handleaddAliasLabelsRequest | onGet: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( optRsrc, v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( optRsrc, v )
        }
        case _ => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "addAliasLabelsError" ) ~
            ( "content" -> 
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            )
          ) ) )
        }
      }
    }
    
    //agentMgr().get( labelsStorageLocation, List( aliasStorageCnxn ), onGet )
    get( labelsStorageLocation, List( aliasStorageCnxn ), onGet )
  }
  //    - `Label = String`
  
  
  //#### updateAliasLabels
  def handleupdateAliasLabelsRequest(
    msg : updateAliasLabelsRequest
  ) : Unit = {
    BasicLogService.tweet(
      "Entering: handleupdateAliasLabelsRequest with msg : " + msg
    )
    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )
    val sessionURIStr = msg.sessionURI.toString
    
    def handleRsp( v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet(
        "handleupdateAliasLabelsRequest | onGet: got " + v
      )
      val onPut : Option[mTT.Resource] => Unit =
        ( optRsrc : Option[mTT.Resource] ) => {
          BasicLogService.tweet("handleupdateAliasLabelsRequest | onGet | onPut")
          SessionManager.cometMessage(
            sessionURIStr,
            compact(
              render(
                ( "msgType" -> "updateAliasLabelsResponse" ) ~ ( "content" -> ( "sessionURI" -> msg.sessionURI.toString ) )
              )
            )
          )
        }
      BasicLogService.tweet(
        ( 
          "handleupdateAliasLabelsRequest | onGet | onPut | updating labelList with "
          +  compact(render(msg.labels.map(_.show)))
        )
      )
      //agentMgr().put[String](
      put[String](
        labelsStorageLocation,
        List( aliasStorageCnxn ),
        compact(render(msg.labels.map(_.show))),
        onPut
      )
    }

    val onGet : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet(
            "handleupdateAliasLabelsRequest | onGet: got None"
          )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( v )
        }
        case _ => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "updateAliasLabelsError" ) ~
            ( "content" -> 
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            )
          ) ) )
        }
      }
    }

    //agentMgr().get( labelsStorageLocation, List( aliasStorageCnxn ), onGet )
    get( labelsStorageLocation, List( aliasStorageCnxn ), onGet )
  }
  
  
  //#### getAliasLabels
  def handlegetAliasLabelsRequest(
    msg : getAliasLabelsRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handlegetAliasLabelsRequest with msg : " + msg )

    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handlegetAliasLabelsRequest | onFetch: got " + v )
      
      val labelList: JArray = v match {
        case PostedExpr( (PostedExpr( labelList : String ), _, _, _) ) =>
          parse(labelList).asInstanceOf[JArray]
        case Bottom => Nil
        case _ => throw new Exception("Unrecognized resource")
      }

      SessionManager.cometMessage(sessionURIStr, compact( render(
        ( "msgType" -> "getAliasLabelsResponse" ) ~
        ( "content" ->
         ( "sessionURI" -> sessionURIStr ) ~
         ( "labels" -> labelList )
       )
      ) ) )
    }

    val onFetch : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => try {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handlegetAliasLabelsRequest | onFetch: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( v )
        }
        case _ => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "getAliasLabelsError" ) ~
            ( "content" -> 
              ( "sessionURI" -> sessionURIStr ) ~
              ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
            )
          ) ) )
        }
      }
    } catch {
      case e: Exception => {
        SessionManager.cometMessage(sessionURIStr, compact( render(
          ( "msgType" -> "getAliasLabelsError" ) ~
          ( "content" -> 
            ( "sessionURI" -> sessionURIStr ) ~
            ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
          )
        ) ) )
      }
    }

    //agentMgr().fetch( labelsStorageLocation, List( aliasStorageCnxn ), onFetch )
    agentMgr().fetch( labelsStorageLocation, List( aliasStorageCnxn ), onFetch )
  }
  
  //#### setAliasDefaultLabel
  def handlesetAliasDefaultLabelRequest(
    msg : setAliasDefaultLabelRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handlesetAliasDefaultLabelRequest with msg : " + msg )

    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )

    val onPut : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
      BasicLogService.tweet( "handlesetAliasDefaultLabelRequest | onPut" )

      val sessionURIStr = msg.sessionURI.toString

      SessionManager.cometMessage(sessionURIStr, compact( render(
        ( "msgType" -> "setAliasDefaultLabelResponse" ) ~
        ( "content" -> ( "sessionURI" -> sessionURIStr ) )
      ) ) )
    }
    
    put[CnxnCtxtLabel[String,String,String]](
      defaultLabelStorageLocation,
      List( aliasStorageCnxn ),
      msg.label,
      onPut
    )
  }
  
  
  //#### getAliasDefaultLabel
  def handlegetAliasDefaultLabelRequest(
    msg : getAliasDefaultLabelRequest
  ) : Unit = {
    BasicLogService.tweet( "Entering: handlegetAliasDefaultLabelRequest with msg : " + msg )

    val aliasStorageCnxn = getAliasCnxn( msg.sessionURI, msg.alias )
    val sessionURIStr = msg.sessionURI.toString

    def handleRsp( optRsrc : Option[mTT.Resource], v : ConcreteHL.HLExpr ) : Unit = {
      BasicLogService.tweet( "handlegetAliasDefaultLabelRequest | onFetch: got " + v )

      v match {
        case PostedExpr( (PostedExpr( defaultLabel : CnxnCtxtLabel[String,String,String] ), _, _, _) ) => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "getAliasDefaultLabelResponse" ) ~
            ( "content" ->
             ( "sessionURI" -> sessionURIStr ) ~
             ( "label" -> defaultLabel.toString )
           )
          ) ) )
        }
        case Bottom => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "getAliasDefaultLabelError" ) ~
            ( "content" ->
             ( "sessionURI" -> sessionURIStr ) ~
             ( "reason" -> "No default label exists" )
           )
          ) ) )
        }
        case _ => {
          SessionManager.cometMessage(sessionURIStr, compact( render(
            ( "msgType" -> "getAliasDefaultLabelError" ) ~
            ( "content" -> 
             ( "sessionURI" -> sessionURIStr ) ~
             ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
           )
          ) ) )
        }
      }
    }

    val onFetch : Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => try {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handlegetAliasDefaultLabelRequest | onFetch: got None" )
        }
        case Some( mTT.Ground( v ) ) => {
          handleRsp( optRsrc, v )
        }
        case Some( mTT.RBoundHM( Some( mTT.Ground( v ) ), _ ) ) => {
          handleRsp( optRsrc, v )
        }
        case _ => throw new Exception("Unrecognized resource")
      }
    } catch {
      case e: Exception => {
        SessionManager.cometMessage(sessionURIStr, compact( render(
          ( "msgType" -> "getAliasDefaultLabelError" ) ~
          ( "content" -> 
            ( "sessionURI" -> sessionURIStr ) ~
            ( "reason" -> ("Unrecognized resource: optRsrc = " + optRsrc))
          )
        ) ) )
      }
    }

    fetch( defaultLabelStorageLocation, List( aliasStorageCnxn ), onFetch )
  }
  
  //### DSL
  //#### evalSubscribe
  def handleevalSubscribeRequest[GloSExpr](
    msg : evalSubscribeRequest[GloSExpr]
  ) : Unit = {
    BasicLogService.tweet(
      "Entering: handleevalSubscribeRequest with msg : " + msg
    )
  }
  //    - `GlosExpr =`
  //        - `InsertContent(Labels: List, cnxns: List[Cnxn], value: Value)`
  //            - `Value = String`
  //        - `FeedExpr(Labels: List, cnxns: List[Cnxn])`
  //        - `ScoreExpr(Labels: List, cnxns: List[Cnxn], staff: Staff`
  //            - `Staff =`
  //                - `List[Cnxn]`
  //                - `List`
  //- Can we know when we are done to send back an `evalSubscribeComplete`?
  
  //#### evalSubscribeCancel
  def handleevalSubscribeCancelRequest(
    msg : evalSubscribeCancelRequest
  ) : Unit = {
    BasicLogService.tweet( 
      "Entering: handleevalSubscribeCancelRequest with msg : " + msg
    )
    val onCancel: Option[mTT.Resource] => Unit = ( optRsrc : Option[mTT.Resource] ) => {
      optRsrc match {
        case None => {
          // Nothing to be done
          BasicLogService.tweet( "handleevalSubscribeCancelRequest | onFetch: got None" )
        }
        case _ => {
          SessionManager.cometMessage(msg.sessionURI.toString, compact(render(
            ("msgType" -> "evalSubscribeCancelResponse") ~
            ("content" -> ("sessionURI" -> msg.sessionURI.toString))
          )))
        }
      }
    }
    for (filter <- msg.filter) {
      //agentMgr().cancel('user('p1(filter),'p2('uid("UID")),'p3('new("_")),'p4('nil("_"))), msg.connections, onCancel)
      cancel('user('p1(filter),'p2('uid("UID")),'p3('new("_")),'p4('nil("_"))), msg.connections, onCancel)
    }
  }
  
  def handleinitiateClaim(ic: InitiateClaim): Unit = {
    val sessionId = ic.sessionId
    val selfURI = aliasFromSession(new URI(sessionId))
    val pacSelfToGlos = PortableAgentCnxn(selfURI, "verificationProtocol", new URI("ui://gloseval"))
    post(
      InitiateClaim.toLabel,
      List(pacSelfToGlos),
      ic,
      (optRsrc) => {
        println("initiateClaim: optRsrc = " + optRsrc)
        BasicLogService.tweet("initiateClaim: optRsrc = " + optRsrc)
        optRsrc match {
          case None => ()
          case _ => {
            SessionManager.cometMessage(sessionId, compact(render(
              ("msgType" -> "initiateClaimResponse")~
              ("content" ->
                ("sessionURI" -> sessionId)
              )
            )))
          }
        }
      }
    )
  }
}
