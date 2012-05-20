/*
 * This file is part of P2pChat
 *
 * Copyright (C) 2012 Timur Mehrvarz, timur.mehrvarz(at)gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation <http://www.gnu.org/licenses/>, either 
 * version 3 of the License, or (at your option) any later version.
 */

package timur.p2pChat

import java.text.SimpleDateFormat
import java.util.Calendar
import java.security.{ Security, MessageDigest }
import scala.collection.mutable
import android.util.Log
import android.app.{ Activity, ActivityManager, Notification, NotificationManager, PendingIntent, AlertDialog, Dialog }
import android.os.{ IBinder, Handler }
import android.view.View
import android.content.{ Context, Intent, DialogInterface, ComponentName }
import android.widget.{ Toast, EditText }
import ca.uwaterloo.crysp.otr.iface.{ OTRContext, OTRCallbacks }
import timur.p2pCore._
import timur.p2pChat._

object P2pChatService {
  val ACTIVITY_MSG_CONNECT_DIALOG_SHOW = 1
  val ACTIVITY_MSG_CONNECT_DIALOG_CONNECTING = 2
  val ACTIVITY_MSG_CONNECT_DIALOG_DISMISS = 3
  val ACTIVITY_MSG_ADD_CONVERSATION = 4
  val ACTIVITY_MSG_CONNECT_STATE_RELAY = 5
  val ACTIVITY_MSG_CONNECT_STATE_DIRECT = 6
  val ACTIVITY_MSG_CONNECT_STATE_CRYPTED = 7
}

class P2pChatService extends android.app.Service {
  private val TAG = "P2pChatService"
  private val D = true

  var p2pChatOTR:P2pChatOTR = null
  var p2pChatEncrypt:P2pChatEncrypt = null
  var connecting = false
  var manualDisconnect = false    // usually set by client app; if NOT set, p2pExit will show "P2P disconnect" dialog
  var connectionName = ""
  var preferReleayedCommunication = false

  class LocalBinder extends android.os.Binder {
    def getService = P2pChatService.this
  }
  private val localBinder = new LocalBinder
  override def onBind(intent:Intent) :IBinder = localBinder 

  private var myStartId:Int = 0
  @volatile private var activityResumed = false
  private var activity:Activity = null
  private var activityMsgHandler:Handler = null
  private var hasBeenDisconnected = false     // only used to prevent multiple disconnect AlertDialog's
  private val conversationQueue = new mutable.Queue[String]

  override def onCreate() {
    super.onCreate
    if(D) Log.i(TAG, "onCreate")
  }

  override def onStartCommand(intent:Intent, flags:Int, startId:Int) :Int = {
    // called every time a client explicitly starts the service by calling startService(Intent),
    if(D) Log.i(TAG, "onStartCommand flags="+flags+" startId="+startId)
    myStartId = startId
    return android.app.Service.START_NOT_STICKY
  }

  override def onLowMemory() {
    if(D) Log.i(TAG, "onLowMemory")
  }

  override def onDestroy() {
    super.onDestroy   
    if(D) Log.i(TAG, "onDestroy myStartId="+myStartId) //+" p2pEstablishedDialog="+p2pEstablishedDialog)
    // todo: was: mySocketProxyThread.close
    //if(D) Log.i(TAG, "onDestroy done")
  }


  /////////////////////////////////////////////////////////

  def onResumeActivity() {
    activityResumed = true
    Log.i(TAG, "onResumeActivity")
/*
    // remove/abort any current notifications
    if(P2pChatService.notificationManager!=null)
      P2pChatService.notificationManager.cancel(P2pChatService.NOTIFICATION_UNIQUE_ID)
*/
  }

  def onPauseActivity() {
    activityResumed = false
    Log.i(TAG, "onPauseActivity")
  }

  def setActivity(setActivity:Activity, setActivityMsgHandler:Handler) {
    activity = setActivity
    activityMsgHandler = setActivityMsgHandler
  }

  def conversationQueueLength() :Int = { 
    return conversationQueue.length
  }
  
  def conversationQueuePut(str:String) {
    conversationQueue.enqueue(new String(str))
    // keep up to 40 strings
    while(conversationQueue.length>40) conversationQueue.dequeue
  }

  def conversationQueueGet(age:Int) :String = {
    conversationQueue.get(age) match {
      case Some(str) => return str
      case None => return null
    }
  }

  def newP2pChatOTR(keyFolderPath:String, p2pSecret:String, smpSecret:String) :P2pChatOTR = {
    p2pChatOTR = new P2pChatOTRAndr(keyFolderPath, p2pSecret, smpSecret)
    return p2pChatOTR
  }

  def newP2pChatEncrypt(keyFolderPath:String, remoteKeyName:String, rendevouzString:String) :P2pChatEncrypt = {
    p2pChatEncrypt = new P2pChatEncrypt(keyFolderPath, remoteKeyName, rendevouzString)
    return p2pChatEncrypt
  }


  class P2pChatOTRAndr(keyFolderPath:String, p2pSecret:String, smpSecret:String) 
    extends P2pChatOTR(p2pSecret:String, smpSecret:String, null:timur.p2pChat.LogClassTrait) {

    var privKeyLocal:String = null
    var pubKeyLocal:String = null
    var pubKeyLocalFingerprint:String = null
    var remoteKeyName:String = null

    otrCallbacks = new LocalCallbackAndr(this, otrContext)
    hasBeenDisconnected = false

    override def log(str:String) {
      val dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance.getTime)
      if(D) Log.i(TAG, dateTime+" "+appName+" "+str)
    }

    override def init() {
      //Security.removeProvider("BC")
      Security.addProvider(new ext.org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    override def start() :Int = {
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 0, -1, null).sendToTarget
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 0, -1, null).sendToTarget
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED, 0, -1, null).sendToTarget

      var ret = readkeys
      if(ret!=0)
        return ret

      manualDisconnect = false
      connecting = true
      try {
        ret = super.start
        if(ret == -1) {
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "argument error", Toast.LENGTH_LONG).show
          }
        } else if(ret == -2) {
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "failed to create key fingerprints", Toast.LENGTH_LONG).show
          }
        }

      } catch {
        case conex:java.net.ConnectException =>
          ret = -3
          conex.printStackTrace
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "Failed to connect", conex.getMessage) { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }

        case ex:Exception =>
          ret = -3
          ex.printStackTrace
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "Failed on", ex.getMessage) { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }

        case unknown =>
          ret = -3
          if(D) Log.i(TAG, "Failed for unknown reason "+unknown)
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "Failed for unknown reason") { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }

        case _ =>
          ret = -3
          if(D) Log.i(TAG, "Failed for unspecified reason")
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "Failed for unspecified reason") { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }
      }

      connecting = false
      p2pChatOTR = null
      log("start has finished with ret="+ret)
      return ret
    }

    def readkeys() :Int = {
      // create folder for remote keys
      new java.io.File(keyFolderPath).mkdir
      val fullLocalKeyName = keyFolderPath+"/key.pub"

      try {
        // load local key pair
        log("readkeys fullLocalKeyName="+fullLocalKeyName+" used for fingerprint matching")
        pubKeyLocal = io.Source.fromFile(fullLocalKeyName).mkString

        log("readkeys read private key from private folder")
        privKeyLocal = io.Source.fromInputStream(openFileInput("key")).mkString

      } catch {
        case ex:Exception =>
          // generate local key pair
          log("readkeys generating local key pair")
          val keyPair = RsaKeyGenerate.rsaKeyGenerate
          privKeyLocal = Base64.encode(keyPair.getPrivate.getEncoded)
          pubKeyLocal = Base64.encode(keyPair.getPublic.getEncoded)

          // delete old private key
          val privateKeyFile = getFileStreamPath("key")
          if(privateKeyFile!=null) {
            log("readkeys privateKeyFile="+privateKeyFile.getAbsolutePath)
            if(privateKeyFile.exists) {
              log("readkeys privateKeyFile delete")
              privateKeyFile.delete
            }
          }

          // write new private key in private directory
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "creating new private and public keys", Toast.LENGTH_LONG).show
          }
          log("readkeys write new key pair")
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "write new key pair", Toast.LENGTH_LONG).show
          }

          val fileOutputStream2 = openFileOutput("key", Context.MODE_PRIVATE)
          fileOutputStream2.write(privKeyLocal.getBytes)
          fileOutputStream2.close

          // write new public key in public keyFolderPath
          log("readkeys write new public key; len="+pubKeyLocal.length)
          Tools.writeToFile(keyFolderPath+"/key.pub", pubKeyLocal)
      }

      if(buildMatchStrings!=0)
        return -2
        
      return 0
    }

    override def logEx(str:String) {
      super.logEx(str)
      AndrTools.runOnUiThread(activity) { () =>
        Toast.makeText(activity, "Exception: "+str, Toast.LENGTH_LONG).show
      }
    }

// tmtmtm: new
    /** we are now p2p-connected via relay server (tcp) */
    override def connectedThread(connectString:String) {
      log("connectedThread connectString="+connectString)
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 1, -1, null).sendToTarget
      if(preferReleayedCommunication) {
        log("connectedThread preferReleayedCommunication ####")
        relayBasedP2pCommunication = true  // we will NOT disconnect our relay connection
        // set publicUdpAddrString and otherUdpAddrString AS IF we are UDP connected
        val tokenArrayOfStrings = connectString split '|'
        val otherPublicIpAddr = tokenArrayOfStrings(2)
        val otherPublicPort = new java.lang.Integer(tokenArrayOfStrings(3)).intValue
        val myPublicIpAddr = tokenArrayOfStrings(4)
        val myPublicPort = new java.lang.Integer(tokenArrayOfStrings(5)).intValue
        otherUdpAddrString = otherPublicIpAddr+":"+otherPublicPort
        publicUdpAddrString = myPublicIpAddr+":"+myPublicPort
        log("connectedThread otherUdpAddrString="+otherUdpAddrString+" -> p2pSendThread")
        p2pSendThread

      } else {
        super.connectedThread(connectString)  // receiving datagram's
      }
    }

// tmtmtm: new
    /** we receive data via (or from) the relay server */
    override def receiveMsgHandler(str:String) {
      if(preferReleayedCommunication) {
        //log("receiveMsgHandler preferReleayedCommunication str="+str+" ####")
        // forward all receiveMsgHandler(str) to p2pReceivePreHandler(str)
        if(str.startsWith("udpAddress=")) {
          // ignore
        } else {
          p2pReceivePreHandler(str)  // -> p2pReceiveHandler()
        }
      } else {
        super.receiveMsgHandler(str)
      }
    }

//tmtmtm
    /** we are now p2p connected (if relayBasedP2pCommunication is set, p2p is relayed; else it is direct) */
    override def p2pSendThread() {
      //connecting = false
      // todo: change connect message from "Connecting..." to "Establish encryption..."
      if(!relayBasedP2pCommunication)
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 1, -1, null).sendToTarget
      val connectionType = if(!relayBasedP2pCommunication) "direct" else "relayed"
      val connectionString = connectionType + " P2P connection"
      conversationQueuePut(connectionString)
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, connectionString).sendToTarget

      val displayMessage = "from: "+publicUdpAddrString+" to: "+udpConnectIpAddr+":"+udpConnectPortInt
      log("p2pSendThread displayMessage='"+displayMessage+"'")
      conversationQueuePut(displayMessage)
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, displayMessage).sendToTarget

      log("p2pSendThread otherUdpAddrString='"+otherUdpAddrString+"'")
      if(publicUdpAddrString>otherUdpAddrString) {
        val firstMessage = "start otr/smp..."
        log("send first msg: '"+firstMessage+"'")
        // client A will send a msg to get to "AKE succeeded" state, where the other client will do initiateSmp()
        otrMsgSend(firstMessage)       
      } else {
        log("not send first msg ####")
      }
    }

    override def p2pSend(sendString:String, 
                         host:String=udpConnectIpAddr, 
                         port:Int=udpConnectPortInt, 
                         cmd:String="string") :Unit = synchronized {
      if(relayBasedP2pCommunication) {
        send(sendString)
      } else {
        super.p2pSend(sendString,host,port,cmd)
      }
    }

    override def p2pReceiveHandler(str:String, host:String, port:Int) {
      // here we receive and process data from other client
      // sent directly per UDP - or (if relayBasedP2pCommunication is set) relayed per TCP
      // if relayBasedP2pCommunication is not set, we may disconnect the relay connection now 
      //log("p2pReceiveHandler str='"+str+"'")  // never log user data

      // disconnect our relay connection (stay connected via direct p2p)
      if(relaySocket!=null && !relayBasedP2pCommunication) {
        log("force relaySocket.close (str="+str+")")
        relayQuitFlag=true
        try { relaySocket.close } catch { case ex:Exception => }
        relaySocket=null
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 0, -1, null).sendToTarget
      }

      log(esc1+"From network:"+str.length+":"+esc3+str.substring(0,math.min(str.length,54))+esc2)
      val stringTLV = otrInterface.messageReceiving(accountname, protocol, recipient, str, otrCallbacks)
      if(stringTLV!=null){
        val msg = stringTLV.msg
        if(msg.length>0) {
          //log(esc1+"From OTR:"+msg.length+":"+esc2+msg)
          //log("< "+msg)
          val displayMessage = "< "+msg
          conversationQueuePut(displayMessage)
          activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, displayMessage).sendToTarget
        }
      }
    }

    override def relayExit() {
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 0, -1, null).sendToTarget
      super.relayExit
    }

    def storeRemotePublicKey(keyName:String, keystring:String) {
      if(keyName=="-") {
        // let user enter a name for this newly received connection key
        AndrTools.runOnUiThread(activity) { () =>
          val editText = new EditText(activity)
          val dialogClickListener = new DialogInterface.OnClickListener() {
            override def onClick(dialog:DialogInterface, whichButton:Int) {
              val tmpRemoteKeyName = editText.getText.toString
              if(tmpRemoteKeyName.length<3) {
                Toast.makeText(activity, "At least 3 characters are required", Toast.LENGTH_SHORT).show

              } else if(tmpRemoteKeyName=="key") {
                Toast.makeText(activity, "'key.pub' cannot be overwritten", Toast.LENGTH_SHORT).show

              } else {
                val keyPathName = keyFolderPath+"/"+tmpRemoteKeyName+".pub"

                // if keyPathName exists already, ask before overwriting
                if(new java.io.File(keyPathName).exists) {
                  val dialogClickListener2 = new DialogInterface.OnClickListener() {
                    override def onClick(dialog:DialogInterface, whichButton:Int) {
                      whichButton match {
                        case DialogInterface.BUTTON_POSITIVE =>
                          remoteKeyName = tmpRemoteKeyName
                          val displayMessage = "["+remoteKeyName+"]"
                          conversationQueuePut(displayMessage)
                          activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, 
                                                           displayMessage).sendToTarget
                          Tools.writeToFile(keyPathName, keystring)

                        case DialogInterface.BUTTON_NEGATIVE =>
                      }
                    }
                  }
                  new AlertDialog.Builder(activity)
                                 .setTitle(tmpRemoteKeyName+" already exists")
                                 .setMessage("Overwrite existing file with newly received key?")
                                 .setPositiveButton("Yes",dialogClickListener2)
                                 .setNegativeButton("No",dialogClickListener2)
                                 .show

                } else {
                  remoteKeyName = tmpRemoteKeyName
                  val displayMessage = "["+remoteKeyName+"]"
                  conversationQueuePut(displayMessage)
                  activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, 
                                                   displayMessage).sendToTarget
                  Tools.writeToFile(keyPathName, keystring)
                }
              }
            }
          }

          new AlertDialog.Builder(activity)
                         .setTitle("Received encryption key")
                         .setMessage("Please provide a unique name for this partner, "+
                                     "so you can quick-connect next time.")
                         .setView(editText)
                         .setPositiveButton("Store",dialogClickListener)
                         .show
        }

      } else {
        val displayMessage = "["+keyName+"]"
        conversationQueuePut(displayMessage)
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, displayMessage).sendToTarget
        Tools.writeToFile(keyFolderPath+"/"+keyName+".pub", keystring)
      }
    }

    override def initHostPubKey() {
      // load the relay server public key
      hostPubKey = io.Source.fromInputStream(activity.getResources.openRawResource(R.raw.relaykeypub)).mkString
      log("initHostPubKey hostPubKey="+hostPubKey)
    }

    override def p2pFault(attempts:Int) {
      // fixed? todo: this still appears twice (see: P2pBase "all datagramSendThread's have failed")
      log("p2pFault failed to establish direct p2p connection over "+attempts+" separate pathes")
      connecting = false

      // direct p2p has failed, encrypted communication will be routed over our relay link
      AndrTools.runOnUiThread(activity) { () =>
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 0, -1, null).sendToTarget
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_DISMISS, -1, -1, null).sendToTarget

        Toast.makeText(activity, "Direct P2P link failed", Toast.LENGTH_SHORT).show
      }
    }

    override def p2pExit(ret:Int) {
      // the p2p connection has ended now
      log("p2pExit hasBeenDisconnected="+hasBeenDisconnected)
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 0, -1, null).sendToTarget
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 0, -1, null).sendToTarget
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED, 0, -1, null).sendToTarget

      if(!hasBeenDisconnected) {
        hasBeenDisconnected = true
        log("p2pExit udpConnectIpAddr="+udpConnectIpAddr+" relayBasedP2pCommunication="+relayBasedP2pCommunication+
           " p2pQuitFlag="+p2pQuitFlag+" manualDisconnect="+manualDisconnect)
        if((udpConnectIpAddr!=null || relayBasedP2pCommunication) && !manualDisconnect) {
          try {
            AndrTools.runOnUiThread(activity) { () =>
              AndrTools.alertDialog(activity, "P2P disconnect") { () =>
                activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
              }
            }
          } catch {
            case ex:Exception =>
              // p2pExit may be called AFTER "onDestroy finished" / "Shutting down VM"
              // -> "Attempted to add application window with unknown token"
          }
        }
      }

      // todo: may want to remind these for quick reconnect?
      //udpConnectIpAddr = null
      //udpConnectPortInt = 0
      p2pReset
    }

    def buildMatchStrings() :Int = {
      try {
        // create pubKeyLocalFingerprint based on pubKeyLocal
        val messageDigest = MessageDigest.getInstance("SHA-1")
        messageDigest.update(Base64.decode(pubKeyLocal))
        pubKeyLocalFingerprint = RsaEncrypt.getHexString(messageDigest.digest)
      } catch {
        case ex:Exception =>
          logEx("fingerprint setup error ex="+ex)
          return -1
      }

      // build match strings based on the p2pSecret string
      matchSource = p2pSecret
      matchTarget = p2pSecret
      log("matching clients with p2pSecret string '"+p2pSecret+"'")
      return 0
    }

    /**
     * p2pReceivePreHandler is called as soon as p2p data was encrypted
     * process special commands, such as "//requestPubKeyFingerprint", "//pubKeyFingerprint=...", "//check", "//ack", "//quit"
     */
    override def p2pReceivePreHandler(str:String) {
      //log("p2pReceivePreHandler P2pChatOTR reset msgMsRcv; receiving=["+str+"]")
      if(str=="//requestPubKeyFingerprint") {
        log("p2pReceivePreHandler: sending fingerprint of our pubkey on request="+pubKeyLocalFingerprint)
        p2pSend("//pubKeyFingerprint="+pubKeyLocalFingerprint)

      } else if(str.startsWith("//pubKeyFingerprint=")) {
        val remoteKeyFingerprint = str.substring(20)
        log("p2pReceivePreHandler: remoteKeyFingerprint="+remoteKeyFingerprint)

        // search all stored pub keys for a match to remoteKeyFingerprint
        var pubKeyRemote:String = null
        var fileName:String = null
        val fileArray = new java.io.File(keyFolderPath).listFiles
        for(file <- fileArray.iterator.toList) {
          if(pubKeyRemote==null) {
            fileName = file.getName.trim
            if(fileName.length>4 && fileName.endsWith(".pub") && fileName!="key.pub") {
              val key = io.Source.fromFile(keyFolderPath+"/"+fileName).mkString
              val messageDigest = MessageDigest.getInstance("SHA-1")
              messageDigest.update(Base64.decode(key))
              val fingerprint = RsaEncrypt.getHexString(messageDigest.digest)
              if(fingerprint==remoteKeyFingerprint) {
                log("p2pReceivePreHandler: found stored pubKeyRemote in file "+fileName)
                pubKeyRemote = key
              } else {
                //log("p2pReceivePreHandler: NOT found pubKeyRemote in file "+fileName+"="+fingerprint)
              }
            }
          }
        }

        if(pubKeyRemote==null) {
          // not found pubKeyRemote for remoteKeyFingerprint; request full key delivery
          log("p2pReceivePreHandler: not found stored pubKeyRemote; send requestPubKey")
          p2pSend("//requestPubKey")
          return
        }
        
        // pubKeyRemote for remoteKeyFingerprint found in local folder; nothing more to do
        log("p2pReceivePreHandler: found pubKeyRemote in fileName="+fileName+" #####")
        remoteKeyName = fileName.substring(0,fileName.length-4)

        val displayMessage = "["+remoteKeyName+"]"
        conversationQueuePut(displayMessage)
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, displayMessage).sendToTarget

      } else if(str=="//requestPubKey") {
        // send full public key
        log("p2pReceivePreHandler: send full public key")
        p2pSend("//pubKey="+pubKeyLocal)

      } else if(str.startsWith("//pubKey=")) {
        val remoteKey = str.substring(9)
        // store remoteKey
        log("p2pReceivePreHandler: received remoteKey="+remoteKey+" WILL BE STORED #####")
        val keyName = "-" //"receivedkey.pub"
        storeRemotePublicKey(keyName, remoteKey)

      } else {
        super.p2pReceivePreHandler(str) // -> p2pReceiveHandler()
      }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    class LocalCallbackAndr(p2pBase:P2pBase, otrContext:OTRContext) 
      extends LocalCallback(p2pBase:P2pBase, otrContext:OTRContext) {

      override def handleSmpEvent(smpEvent:Int, context:OTRContext, progress_percent:Int, question:String) {
        if(smpEvent == OTRCallbacks.OTRL_SMPEVENT_ASK_FOR_SECRET) {
          if(publicUdpAddrString>otherUdpAddrString) {
            // Client A will respondSmp; if all goes well, both clients will receive OTRL_SMPEVENT_SUCCESS
            if(smpSecret!=null && smpSecret.length>0) {
              log("handleSmpEvent respond OMP with smpSecret="+smpSecret)   // never log the secret
              otrContext.respondSmp(smpSecret, otrCallbacks)
            } else {
              log("handleSmpEvent respond OMP with p2pSecret="+p2pSecret)   // never log the secret
              otrContext.respondSmp(p2pSecret, otrCallbacks)
            }
          }

        } else if(smpEvent == OTRCallbacks.OTRL_SMPEVENT_SUCCESS) {
          log("************* SMP succeeded ***************")
          connecting = false
          p2pEncryptedCommunication
        }

        else if(smpEvent == OTRCallbacks.OTRL_SMPEVENT_FAILURE) {
          log("************* SMP failed ***************")
          // abort "connecting..."
          connecting = false
          p2pQuit(true)

          // show error message and switch back to connect dialog
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "SMP failed") { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }
        }
      }
    }

    // encryption is now in place
    override def p2pEncryptedCommunication() {
      AndrTools.runOnUiThread(activity) { () =>
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED, 1, -1, null).sendToTarget
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_DISMISS, -1, -1, null).sendToTarget
      }

      val encryptionMessage = "OTR/SMP encryption established"
      conversationQueuePut(encryptionMessage)
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, encryptionMessage).sendToTarget

      // send request for remote public key fingerprint delivery
      p2pSend("//requestPubKeyFingerprint")  // will display "["+remoteKeyName+"]"

      super.p2pEncryptedCommunication  // start p2pWatchdog
    }
  }


  class P2pChatEncrypt(keyFolderPath:String, setRemoteKeyName:String, rendezvous:String) 
    extends P2pEncrypt(keyFolderPath:String, setRemoteKeyName:String, rendezvous:String) {
    
    var remoteNameShown = false  // used to flag if the remote keyname was displayed using ACTIVITY_MSG_ADD_CONVERSATION

    hasBeenDisconnected = false

    override def log(str:String) {
      val dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance.getTime)
      if(D) Log.i(TAG, dateTime+" "+appName+" "+str)
    }

    override def start() :Int = {
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 0, -1, null).sendToTarget
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 0, -1, null).sendToTarget
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED, 0, -1, null).sendToTarget
      connecting = true
      manualDisconnect = false
      var ret=0
      try {
        ret = super.start
        if(ret == -1) {
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "argument error", Toast.LENGTH_LONG).show
          }
        } else if(ret == -2) {
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "failed to create key fingerprints", Toast.LENGTH_LONG).show
          }
        }

      } catch {
        case conex:java.net.ConnectException =>
          ret = -3
          conex.printStackTrace
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "Failed to connect", conex.getMessage) { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }

        case ex:Exception =>
          ret = -3
          ex.printStackTrace
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "Failed on", ex.getMessage) { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }

        case unknown =>
          ret = -3
          if(D) Log.i(TAG, "Failed for unknown reason "+unknown)
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "Failed for unknown reason") { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }

        case _ =>
          ret = -3
          if(D) Log.i(TAG, "Failed for unspecified reason")
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "Failed for unspecified reason") { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }
      }

      connecting = false
      p2pChatEncrypt = null
      log("start has finished with ret="+ret)
      return ret
    }

    override def readkeys() :Int = {
      log("readkeys remoteKeyName=["+remoteKeyName+"]")
      if(remoteKeyName.length<=0)
        return -1

      // create folder for remote keys
      new java.io.File(keyFolderPath).mkdir
      val fullLocalKeyName = keyFolderPath+"/key.pub"

      try {
        // load local key pair
        log("readkeys fullLocalKeyName="+fullLocalKeyName+" used for fingerprint matching")
        pubKeyLocal = io.Source.fromFile(fullLocalKeyName).mkString
        log("readkeys read private key from private folder")
        privKeyLocal = io.Source.fromInputStream(openFileInput("key")).mkString

      } catch {
        case ex:Exception =>
          // generate local key pair
          log("readkeys generating local key pair")
          val keyPair = RsaKeyGenerate.rsaKeyGenerate
          privKeyLocal = Base64.encode(keyPair.getPrivate.getEncoded)
          pubKeyLocal = Base64.encode(keyPair.getPublic.getEncoded)

          // delete old private key
          val privateKeyFile = getFileStreamPath("key")
          if(privateKeyFile!=null) {
            log("readkeys privateKeyFile="+privateKeyFile.getAbsolutePath)
            if(privateKeyFile.exists) {
              log("readkeys privateKeyFile delete")
              privateKeyFile.delete
            }
          }

          // write new private key in private directory
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "creating new private and public keys", Toast.LENGTH_LONG).show
          }
          log("readkeys write new key pair")
          AndrTools.runOnUiThread(activity) { () =>
            Toast.makeText(activity, "write new key pair", Toast.LENGTH_LONG).show
          }

          val fileOutputStream2 = openFileOutput("key", Context.MODE_PRIVATE)
          fileOutputStream2.write(privKeyLocal.getBytes)
          fileOutputStream2.close

          // write new public key in public keyFolderPath
          log("readkeys write new public key; len="+pubKeyLocal.length)
          Tools.writeToFile(keyFolderPath+"/key.pub", pubKeyLocal)
      }

      if(buildMatchStrings!=0)
        return -2
        
      return 0
    }

    override def logEx(str:String) {
      super.logEx(str)
      AndrTools.runOnUiThread(activity) { () =>
        Toast.makeText(activity, "Exception: "+str, Toast.LENGTH_LONG).show
      }
    }

    override def relayReceiveEncryptionFailed(remoteKeyFingerprint:String) {
      // remote public key not found in filesystem after evaluating received fingerprint
      AndrTools.runOnUiThread(activity) { () =>
        Toast.makeText(activity, "Could not find remote public key for fingerprint "+remoteKeyFingerprint, Toast.LENGTH_LONG).show
      }
    }

    override def connectedThread(connectString:String) {
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 1, -1, null).sendToTarget
      super.connectedThread(connectString)
    }

    // we are p2p connected, but no encryption is yet in place
    override def p2pSendThread() {

      if(!relayBasedP2pCommunication)
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 1, -1, null).sendToTarget

      connecting = false

      // this connect-dialog may immediately be replaced with an encryption-dialog
      AndrTools.runOnUiThread(activity) { () =>
        val titleString = 
          if(!relayBasedP2pCommunication)
            "Direct P2P connection established"
          else
            "Relayed P2P connection established"
        val messageString = "connected to: "+remoteKeyName+"\n\n"+
                            "from: "+publicUdpAddrString+"\n\n"+
                            "to: "+udpConnectIpAddr+":"+udpConnectPortInt
        AndrTools.alertDialog(activity, titleString, messageString) { () => }
      }

      super.p2pSendThread
    }

    override def relayExit() {
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 0, -1, null).sendToTarget
      super.relayExit
    }

    override def storeRemotePublicKey(keyName:String, keystring:String) {
      if(keyName=="-") {
        // let user enter a name for this newly received connection key
        AndrTools.runOnUiThread(activity) { () =>
          val editText = new EditText(activity)
          val dialogClickListener = new DialogInterface.OnClickListener() {
            override def onClick(dialog:DialogInterface, whichButton:Int) {
              val tmpRemoteKeyName = editText.getText.toString
              if(tmpRemoteKeyName.length<3) {
                Toast.makeText(activity, "At least 3 characters are required", Toast.LENGTH_SHORT).show

              } else if(tmpRemoteKeyName=="key") {
                Toast.makeText(activity, "'key.pub' cannot be overwritten", Toast.LENGTH_SHORT).show

              } else {
                val keyPathName = keyFolderPath+"/"+tmpRemoteKeyName+".pub"

                // if keyPathName exists already, ask before overwriting
                if(new java.io.File(keyPathName).exists) {
                  val dialogClickListener2 = new DialogInterface.OnClickListener() {
                    override def onClick(dialog:DialogInterface, whichButton:Int) {
                      whichButton match {
                        case DialogInterface.BUTTON_POSITIVE =>
                          remoteKeyName = tmpRemoteKeyName
                          val displayMessage = "["+remoteKeyName+"]"
                          conversationQueuePut(displayMessage)
                          activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, 
                                                           displayMessage).sendToTarget
                          remoteNameShown = true

                          Tools.writeToFile(keyPathName, keystring)

                        case DialogInterface.BUTTON_NEGATIVE =>
                      }
                    }
                  }
                  new AlertDialog.Builder(activity)
                                 .setTitle(remoteKeyName+" already exists")
                                 .setMessage("Overwrite existing file with newly received key?")
                                 .setPositiveButton("Yes",dialogClickListener2)
                                 .setNegativeButton("No",dialogClickListener2)
                                 .show

                } else {
                  remoteKeyName = tmpRemoteKeyName
                  val displayMessage = "["+remoteKeyName+"]"
                  conversationQueuePut(displayMessage)
                  activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, 
                                                   displayMessage).sendToTarget
                  remoteNameShown = true

                  Tools.writeToFile(keyPathName, keystring)
                }
              }
            }
          }

          new AlertDialog.Builder(activity)
                         .setTitle("Received encryption key")
                         .setMessage("Please provide a unique name for this partner, "+
                                     "so you can quick-connect next time.")
                         .setView(editText)
                         .setPositiveButton("Store",dialogClickListener)
                         .show
        }

      } else {
        val displayMessage = "["+remoteKeyName+"]"
        conversationQueuePut(displayMessage)
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, displayMessage).sendToTarget
        remoteNameShown = true

        Tools.writeToFile(keyFolderPath+"/"+keyName+".pub", keystring)
      }
    }

    override def p2pReceiveHandler(str:String, host:String, port:Int) {
      // here we receive and process decrypted data strings from the other client
      // sent directly per UDP - or relayed per TCP (if relayBasedP2pCommunication is set)
      // if relayBasedP2pCommunication is not set, we may disconnect the relay connection now 
      // log("p2pReceiveHandler str='"+str+"'")  // never log user data

      // disconnect our relay connection (stay connected via direct p2p)
      if(relaySocket!=null && !relayBasedP2pCommunication) {
        log("relaySocket.close")
        relayQuitFlag=true
        relaySocket.close
        relaySocket=null
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 0, -1, null).sendToTarget
      }

      val displayMessage = "< "+str
      conversationQueuePut(displayMessage)
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, displayMessage).sendToTarget
    }

    override def initHostPubKey() {
      // load the relay server public key
      hostPubKey = io.Source.fromInputStream(activity.getResources.openRawResource(R.raw.relaykeypub)).mkString
      log("initHostPubKey hostPubKey="+hostPubKey)
    }

    // encryption is now in place
    override def p2pEncryptedCommunication() {
      AndrTools.runOnUiThread(activity) { () =>
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED, 1, -1, null).sendToTarget
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_DISMISS, -1, -1, null).sendToTarget

        // display dialog showing local public addr:port (publicUdpAddrString) 
        //                    and remote public addr:port (udpConnectIpAddr:udpConnectPortInt)
        AndrTools.runOnUiThread(activity) { () =>
          val titleString = 
            if(!relayBasedP2pCommunication)
              "Encrypted direct P2P connection established"
            else
              "Encrypted relayed P2P connection established"
          // remoteKeyName might not be available yet
          val messageString = if(remoteKeyName==null || remoteKeyName.length<3)
                                "from: "+publicUdpAddrString+"\n\n"+
                                "to: "+udpConnectIpAddr+":"+udpConnectPortInt
                              else
                                "connected to: "+remoteKeyName+"\n\n"+
                                "from: "+publicUdpAddrString+"\n\n"+
                                "to: "+udpConnectIpAddr+":"+udpConnectPortInt
          AndrTools.alertDialog(activity, titleString, messageString) { () => }
        }

        if(remoteKeyName!=null && remoteKeyName.length>1 && !remoteNameShown) {
          // show the remoteKeyName in the listview, if it is not "-"
          val displayMessage = "["+remoteKeyName+"]"
          conversationQueuePut(displayMessage)
          activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION, -1, -1, displayMessage).sendToTarget
          remoteNameShown = true
        }
      }
    }

    override def p2pFault(attempts:Int) {
      // fixed? todo: this still appears twice (see: P2pBase "all datagramSendThread's have failed")
      log("p2pFault failed to establish direct p2p connection over "+attempts+" separate pathes")
      connecting = false

      // direct p2p has failed, encrypted communication will be routed over our relay link
      AndrTools.runOnUiThread(activity) { () =>
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 0, -1, null).sendToTarget
        activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_DISMISS, -1, -1, null).sendToTarget

        Toast.makeText(activity, "Direct P2P link failed", Toast.LENGTH_SHORT).show
      }
    }

    override def p2pExit(ret:Int) {
      // the p2p connection has ended now
      log("p2pExit")
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 0, -1, null).sendToTarget
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 0, -1, null).sendToTarget
      activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED, 0, -1, null).sendToTarget

      if(!hasBeenDisconnected) {
        hasBeenDisconnected = true
        log("p2pExit udpConnectIpAddr="+udpConnectIpAddr+" relayBasedP2pCommunication="+relayBasedP2pCommunication+
           " p2pQuitFlag="+p2pQuitFlag+" manualDisconnect="+manualDisconnect)
        if((udpConnectIpAddr!=null || relayBasedP2pCommunication) && !manualDisconnect) {
          AndrTools.runOnUiThread(activity) { () =>
            AndrTools.alertDialog(activity, "P2P disconnect") { () =>
              activityMsgHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }
          }
        }
      }

      // todo: may want to remind these for quick reconnect?
      //udpConnectIpAddr = null
      //udpConnectPortInt = 0
      p2pReset
    }
  }
}
