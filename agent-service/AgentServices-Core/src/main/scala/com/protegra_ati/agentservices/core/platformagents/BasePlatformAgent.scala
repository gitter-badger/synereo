/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.protegra_ati.agentservices.core.platformagents

import com.protegra_ati.agentservices.core.platformagents.behaviors._
import com.protegra.agentservicesstore.extensions.StringExtensions._
import com.protegra.agentservicesstore.extensions.ResourceExtensions._
import com.protegra_ati.agentservices.core.schema._
import com.protegra.agentservicesstore.AgentTS._
import com.protegra.agentservicesstore.AgentTS.acT._
import com.protegra.agentservicesstore.AgentTS.mTT._
import com.protegra_ati.agentservices.core.messages._
//import com.protegra.config.ConfigurationManager


import net.lag.configgy._

import scala.util.continuations._

import scala.concurrent.ops._

import java.net.URI
import java.util.UUID
import java.util.ArrayList
import com.protegra.agentservicesstore.util._
import actors.threadpool.LinkedBlockingQueue
import org.joda.time.DateTime
import com.protegra_ati.agentservices.core.util.serializer.Serializer


object BasePABaseXDefaults
{
  implicit val URI: String =
    "xmldb:basex://localhost:1984/"
  implicit val driver: String =
    "org.basex.api.xmldb.BXDatabase"
  implicit val dbRoot: String = "/db"
  implicit val createDB: Boolean = false
  implicit val indent: Boolean = false
  implicit val resourceType: String = "XMLResource"
  val queryServiceType: String = "XPathQueryService"
  val queryServiceVersion: String = "1.0"
  val managementServiceType: String =
    "CollectionManagementService"
  val managementServiceVersion: String = "1.0"
  val valueStorageType: String = "XStream"
  //why not   val valueStorageType : String = "CnxnCtxtLabel"
}

abstract class BasePlatformAgent
  extends Reporting
  with JunctionConfiguration
//  with Scheduler
{

  var _id: UUID = null

  protected def agentCnxn(sourceId: UUID, targetId: UUID) = new AgentCnxn(sourceId.toString.toURI, "", targetId.toString.toURI)

  def initFromConfig(configFilePath: String)
  {
    Configgy.configure(configFilePath)

//    ConfigurationManager.getConfigurationManager().initForProductive()
    initBase(Configgy.config)
    init(Configgy.config)

    loadQueues
    startListening
  }

  def initBase(configUtil: Config)
  {
    try {
      val idKey = "id"
      this._id = UUID.fromString(configUtil.getString(idKey).getOrElse(""))
    }
    catch {
      case e: Exception => report("failed to load id from config")
    }
  }

  protected def init(configUtil: Config)

  //make this protected and have another public loadFromConfig or similar method to pass in addresses?
  //leaving this method for now for tests so they don't break
  //set it right there.  same arguement for passing in sourceAddress which is our _location
  def initForTest(id: UUID)
  {
    _id = id

    loadQueues
    startListening
  }

  //override with each specialized agent
  protected def loadQueues()

  protected def startListening()

  //deprecate these 3?
  //  def listen (queue: PartitionedStringMGJ, cnxn: AgentCnxn, channel:Channel.Value,  channelType:ChannelType.Value, handler:(AgentCnxn, Message) => Unit) :Unit =
  //  {
  ////    val key = channel.toString + channelType.toString + "(_)"
  //    listen(queue, cnxn, channel, channelType, ChannelLevel.Private, handler)
  //  }

  //TODO: add some smarts around this to purge after certain size/length of time if we keep doing this instead of cursor
  //temporary solution is to ignore duplicate processing of the same request msg by id
  var _processedMessages = new LinkedBlockingQueue[ String ]()

  def listen(queue: PartitionedStringMGJ, cnxn: AgentCnxn, channel: Channel.Value, channelType: ChannelType.Value, channelLevel: ChannelLevel.Value, handler: (AgentCnxn, Message) => Unit): Unit =
  {
    listen(queue, cnxn, channel, None, channelType, channelLevel, handler)
  }

  def removeAllProcessedMessages(): Unit =
  {
    if ( _processedMessages != null ) _processedMessages.clear()
    else _processedMessages = new LinkedBlockingQueue[ String ]()
  }

  def listen(queue: PartitionedStringMGJ, cnxn: AgentCnxn, channel: Channel.Value, channelRole: Option[ ChannelRole.Value ], channelType: ChannelType.Value, channelLevel: ChannelLevel.Value, handler: (AgentCnxn, Message) => Unit): Unit =
  {
    val key = channel.toString + channelRole.getOrElse("") + channelType.toString + channelLevel.toString + "(_)"
    listen(queue, cnxn, key, handler, None)
  }

  // TODO we are continue to listen on especial channel after one message is consumed and not expired yet. Potentially we have one waiting thread per channel, if no expired message is recived.
  // TODO solution: to create artificial expired dummy message as soon as we have enought results or timeout, so that we don't need to continue to wait
  def listen(queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, handler: (AgentCnxn, Message) => Unit, expiry: Option[ DateTime ]): Unit =
  {
    val lblChannel = key.toLabel

    report("listen: channel: " + lblChannel.toString + " id: " + _id + " cnxn: " + cnxn.toString + " key: " + key, Severity.Info)

    //really should be a subscribe but can only be changed when put/subscribe works. get is a one listen deal.
    reset {
      for ( e <- queue.get(cnxn)(lblChannel) ) {
        if ( e != None && !isExpired(expiry) ) {
          //keep the main thread listening, see if this causes debug headache
          spawn {
            val msg = Serializer.deserialize[ Message ](e.dispatch)
            report("!!! Listen Received !!!: " + msg.toString.short + " channel: " + lblChannel + " id: " + _id + " cnxn: " + cnxn.toString, Severity.Info)
            //race condition on get get get with consume bringing back the same item, cursor would get around this problem
            //BUG 54 - can't use a cursor get before a put because no results are returned, problem with cursors and waiters
            //temporary solution is to ignore duplicate processing of the same request msg by id
            if ( !_processedMessages.contains(key + msg.ids.id) ) {
              _processedMessages.add(key + msg.ids.id)
              handler(cnxn, msg)
            }
            else
              report("already processed id : " + msg.ids.id, Severity.Info)
          }
          listen(queue, cnxn, key, handler, expiry)
        }
        else {
          report("listen received - none", Severity.Info)
        }
      }
    }
  }

  def isExpired(expiry: Option[ DateTime ]): Boolean =
  {
    expiry match {
      case None =>
        false
      case Some(x: DateTime) => {
        if ( x.isBeforeNow ) true
        else false
      }
      case _ => {
        false
      }
    }
  }

  //  //new style
  //  def listenList(queue: PartitionedStringMGJ, cnxn: AgentCnxn, key:String, handler:(AgentCnxn, List[Message]) => Unit) :Unit =
  //  {
  //    val lblChannel = key.toLabel
  //
  //    report("listen: channel: " + lblChannel.toString + " id: " + _id + " cnxn: " + cnxn.toString + " key: " + key, Severity.Info)
  //
  //    //really should be a subscribe but can only be changed when put/subscribe works. get is a one listen deal.
  //    reset {
  //      for( e <- queue.get( true )( cnxn )(lblChannel))
  //      {
  //        if ( e != None ) {
  //          spawn {
  //            val results: List[ Message ] = e.dispatchCursor.toList.map(x => Serializer.deserialize[ Message ](x.dispatch))
  //            results.map(x => report("!!! Listen Received !!!: " + x.toString.short + " channel: " + lblChannel + " id: " + _id + " cnxn: " + cnxn.toString, Severity.Info))
  //            handler(cnxn, results)
  //          }
  //          //keep the main thread listening, see if this causes debug headache
  //          listenList(queue, cnxn, key, handler)
  //        }
  //        else {
  //          report("listen received - none", Severity.Info)
  //        }
  //      }
  //    }
  //  }

  def singleListen[ T ](queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, handler: (AgentCnxn, T) => Unit): Unit =
  {
    val lblChannel = key.toLabel

    report("listen: channel: " + lblChannel.toString + " id: " + _id + " cnxn: " + cnxn.toString + " key: " + key, Severity.Info)

    //really should be a subscribe but can only be changed when put/subscribe works. get is a one listen deal.
    reset {
      for ( e <- queue.get(cnxn)(lblChannel) ) {
        if ( e != None ) {
          //keep the main thread listening, see if this causes debug headache
          spawn {
            val msg = Serializer.deserialize[ T ](e.dispatch)
            report("!!! Listen Received !!!: " + msg.toString.short + " channel: " + lblChannel + " id: " + _id + " cnxn: " + cnxn.toString, Severity.Info)
            handler(cnxn, msg)
          }
        }
        else {
          report("listen received - none", Severity.Info)
        }
      }
    }
  }

  //the only public method to be used by apps is send
  //apps should just be concerned with sending types of request messages and listening for response events
  //the apps only needs to deal with the message q level
  //  def send (msg: Message, sourceId:UUID, targetId:UUID ): Unit =
  //  {
  //    //TODO: implement serialization of the message object
  //    send(_publicQ, agentCnxn(sourceId, targetId), msg)
  //  }

  //make everything below here protected once tests are sorted out
  def send(queue: PartitionedStringMGJ, cnxn: AgentCnxn, msg: Message)
  {
    report("send --- key: " + msg.getChannelKey + " cnxn: " + cnxn.toString, Severity.Info)
    if ( msg.eventKey != null ) {
      report("send --- eventKey: " + msg.eventKey.toString, Severity.Info)
    }
    put(queue, cnxn, msg.getChannelKey, Serializer.serialize[ Message ](msg))
  }

  def singleSend(queue: PartitionedStringMGJ, cnxn: AgentCnxn, msg: Message)
  {
    msg.channelLevel = Some(ChannelLevel.Single)
    send(queue, cnxn, msg)
  }

  def put(queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, value: String) =
  {
    report("put --- key: " + key + ", value: " + value.short + " cnxn: " + cnxn.toString)
    val lbl = key.toLabel
    reset {queue.put(cnxn)(lbl, Ground(value))}
  }

  def get[ T ](queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, handler: (AgentCnxn, T) => Unit) =
  {
    report("get --- key: " + key)
    val lbl = key.toLabel
    var result = ""
    reset {
      for ( e <- queue.get(cnxn)(lbl) ) {
        if ( e != None ) {
          //multiple results will call handler multiple times
          handler(cnxn, Serializer.deserialize[ T ](e.dispatch))
        }
      }
    }
  }

  def getList[ T ](queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, handler: (AgentCnxn, List[ T ]) => Unit) =
  {
    report("get --- key: " + key + " cnxn: " + cnxn.toString, Severity.Info)
    val lbl = key.toLabel

    reset {
      for ( e <- queue.get(true)(cnxn)(lbl) ) {
        if ( e != None ) {
          val results: List[ T ] = e.dispatchCursor.toList.map(x => Serializer.deserialize[ T ](x.dispatch))
          handler(cnxn, results)
        }
      }
    }
  }

  def getData(queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, handler: (AgentCnxn, Data) => Unit) =
  {
    report("get --- key: " + key)
    val lbl = key.toLabel
    var result = ""
    reset {
      for ( e <- queue.get(cnxn)(lbl) ) {
        if ( e != None ) {
          //multiple results will call handler multiple times
          handler(cnxn, Serializer.deserialize[ Data ](e.dispatch))
        }
      }
    }
  }

  def store(queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, value: String) =
  {
    report("store --- key: " + key + ", cnxn: " + cnxn.toString + ", value: " + value.short, Severity.Info)
    val lbl = key.toLabel

    //this should really be store
    queue.store(cnxn)(lbl, Ground(value))
  }

  def fetch[ T ](queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, handler: (AgentCnxn, T) => Unit) =
  {
    report("fetch --- key: " + key + " cnxn: " + cnxn.toString, Severity.Info)
    val lbl = key.toLabel
    reset {
      for ( e <- queue.fetch(cnxn)(lbl) ) {
        if ( e != None ) {
          //multiple results will call handler multiple times
          val result = Serializer.deserialize[ T ](e.dispatch)
          if (result != null)
            handler(cnxn, result)
        }
      }
    }
  }

  def fetchList[ T ](queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String, handler: (AgentCnxn, List[ T ]) => Unit) =
  {
    report("fetch --- key: " + key + " cnxn: " + cnxn.toString, Severity.Info)
    val lbl = key.toLabel

    reset {
      for ( e <- queue.fetch(true)(cnxn)(lbl) ) {
        if ( e != None ) {
          val results: List[ T ] = e.dispatchCursor.toList.map(x => Serializer.deserialize[ T ](x.dispatch))
          val cleanResults = results.filter(x => x != null)
          handler(cnxn, cleanResults)
        }
      }
    }
  }

  /**
   * Extended fetch to run different searches at once asynchronously.
   * The search happens recursively, so that results of the previous search will be passed to the next search etc.
   * At the end if all searches were successfully, the handler will be executed whole result list will be passed int it.
   * It worce it to define keyList so, that at the begin of the list will stay the search keys for the objects which will probably not found, this way it is possible to reduce the search depth
   * @param queue queue for fetch
   * @param cnxn connection
   * @param keyList list of the search keys for different searches. Has to be distinct from Nil, otherwise NoSuchElementException will be raised
   * @param handler to be executed at the end of the search with all search results, won't be executed if one of the searches fails
   * @tparam T type of the data to be fetched, if different types are expected, use a common interface
   * @return
   */
  def fetchList[ T ](queue: PartitionedStringMGJ, cnxn: AgentCnxn, keyList: List[ String ], handler: (AgentCnxn, List[ T ]) => Unit) =
  {
     recursiveFetch(queue, cnxn, keyList, Nil, handler)
  }

  protected def recursiveFetch[ T ](queue: PartitionedStringMGJ, cnxn: AgentCnxn, remainKeyList: List[ String ], intermediateResults: List[ T ], finalHandler: (AgentCnxn, List[ T ]) => Unit): Unit =
  {
    val lbl = remainKeyList.head.toLabel
    reset {
      for ( e <- queue.fetch(true)(cnxn)(lbl) ) {
        if ( e != None ) {
          val results: List[ T ] = e.dispatchCursor.toList.map(x => Serializer.deserialize[ T ](x.dispatch))
          val newRemainKeyList = remainKeyList.tail
          val newIntermediateResults = intermediateResults ::: results
          // last search is performed execute final handler
          if ( newRemainKeyList.isEmpty ) finalHandler(cnxn, newIntermediateResults)
          // next step of the fetch
          else recursiveFetch(queue, cnxn, newRemainKeyList, newIntermediateResults, finalHandler)
        }
      }
    }
  }

  //note:  this doesn't work with wildcards right now
  //delete must use an exact key, no unification like get/fetch use occurs
  def delete(queue: PartitionedStringMGJ, cnxn: AgentCnxn, key: String) =
  {
    report("delete --- key: " + key.toLabel + " cnxn: " + cnxn.toString, Severity.Info)
    queue.delete(cnxn)(key.toLabel)
  }

  def drop(queue: PartitionedStringMGJ, cnxn: AgentCnxn) =
  {
    report("drop --- cnxn: " + cnxn.toString, Severity.Trace)
    queue.drop(cnxn);
  }


  //// tests for when BUG 54 is fixed
  //  def listenCursor(queue: PartitionedStringMGJ, cnxn: AgentCnxn, channel:Channel.Value, channelType:ChannelType.Value, channelLevel:ChannelLevel.Value, handler:(AgentCnxn, Message) => Unit) :Unit =
  //  {
  //    listenCursor(queue, cnxn, channel, None, channelType, channelLevel, handler)
  //  }
  //  def listenCursor(queue: PartitionedStringMGJ, cnxn: AgentCnxn, channel:Channel.Value, channelRole:Option[ChannelRole.Value], channelType:ChannelType.Value, channelLevel:ChannelLevel.Value, handler:(AgentCnxn, Message) => Unit) :Unit =
  //  {
  //    val key = channel.toString + channelRole.getOrElse("") + channelType.toString + channelLevel.toString + "(_)"
  //    listenCursor(queue, cnxn, key, handler)
  //  }
  //  def listenCursor(queue: PartitionedStringMGJ, cnxn: AgentCnxn, key:String, handler:(AgentCnxn, Message) => Unit) :Unit =
  //    {
  //      val lblChannel = key.toLabel
  //
  //      report("listen: channel: " + lblChannel.toString + " id: " + _id + " cnxn: " + cnxn.toString + " key: " + key, Severity.Info)
  //
  //      //really should be a subscribe but can only be changed when put/subscribe works. get is a one listen deal.
  //      reset {
  //        for( c <- queue.get( true )( cnxn )(lblChannel))
  //        {
  //          report("LISTENED TO CURSOR -------------- " + c.toString, Severity.Info)
  //          if (c != None)
  //          {
  //  //          spawn {
  //            val iter = c.dispatchCursor
  //            for ( e <- iter ) {
  //              val msg = Serializer.deserialize[ Message ](e.dispatch)
  //              report("!!! Listen Received !!!: " + msg.toString.short + " channel: " + lblChannel + " id: " + _id + " cnxn: " + cnxn.toString, Severity.Info)
  //              handler(cnxn, msg)
  //            }
  //  //            val messages: List[ Message ] = e.dispatchCursor.toList.map(x => Serializer.deserialize[ Message ](x.dispatch))
  //  //            messages.foreach(msg => {
  //  //              spawn {
  //  //              }
  //  //          }
  //            //keep the main thread listening, see if this causes debug headache
  //
  //            listenCursor(queue, cnxn, key, handler)
  //          }
  //        else {
  //            report("listen received - none", Severity.Info)
  //          }
  //        }
  //      }
  //    }

}
