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

import java.security.{ Security, MessageDigest }
import java.text.SimpleDateFormat
import java.util.Calendar
import android.app.{Activity, AlertDialog, Dialog }
import android.app.AlertDialog.Builder
import android.util.Log
import android.content.{Context, Intent, DialogInterface, ComponentName, SharedPreferences, ServiceConnection }
import android.content.res.{Configuration, AssetFileDescriptor }
import android.content.res.Resources.NotFoundException
import android.content.pm.ActivityInfo
import android.os.{Handler, Message, SystemClock, Bundle, IBinder }
import android.view.{View, Window, Gravity, LayoutInflater }
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.{Button, ImageView, CheckBox, LinearLayout, ListView, ProgressBar,
                       AdapterView, TextView, CompoundButton, ScrollView, EditText, ArrayAdapter }
import android.widget.AdapterView.OnItemClickListener
import timur.p2pCore._

class P2pChatActivity extends Activity {
  private val TAG = "P2pChatActivity"
  private val D = true
  private val REQUEST_SELECT_TARGET_KEY = 1
  private var activity:Activity = null
  private var appService:P2pChatService = null
  private var appServiceConnection:ServiceConnection = null
  private var mConversationView:ListView = null
  private var mConversationArrayAdapter:ArrayAdapter[String] = null
  private var serviceIntent:Intent = null
  private var activityDestroyed = false
  private var connectDialog:Dialog = null
  private var connectText:TextView = null
  private var connectOTRButton:Button = null
  private var connectKeySelectButton:Button = null
  private var connectProgressBar:ProgressBar = null
  private var editLayout:LinearLayout = null
  private var indicatorRelay:TextView = null
  private var indicatorDirect:TextView = null
  private var indicatorCrypted:TextView = null
  private var otrDialog:AlertDialog = null
  private var otrString:String = null

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    activity = this
    activityDestroyed = false
    requestWindowFeature(Window.FEATURE_NO_TITLE)
    setContentView(R.layout.main)

    indicatorRelay = findViewById(R.id.relay).asInstanceOf[TextView]
    indicatorDirect = findViewById(R.id.direct).asInstanceOf[TextView]
    indicatorCrypted = findViewById(R.id.crypted).asInstanceOf[TextView]

    editLayout = findViewById(R.id.edit_fields).asInstanceOf[LinearLayout]
    mConversationView = findViewById(R.id.listview).asInstanceOf[ListView]
    if(mConversationView==null) {
      Log.e(TAG, "onCreate mConversationView=null")
      return
    }
    mConversationArrayAdapter = new ArrayAdapter[String](this, R.layout.message)
    mConversationView.setAdapter(mConversationArrayAdapter)

/*
    // todo: implement mConversationView.setOnItemClickListener() so we can click on links ?

    mConversationView.setOnItemClickListener(new OnItemClickListener() {
      override def onItemClick(adapterView:AdapterView[_], view:View, position:Int, id:Long) {
        // user has clicked into the listview
        val selectedString = view.asInstanceOf[TextView].getText.toString
        if(D) Log.i(TAG, "onClick listview "+selectedString)
        if(selectedString.indexOf("clientWaiting")>=0) {
          soAdminBrowser("waitAny")
        } else if(selectedString.indexOf("clientsConnected")>=0) {
          soAdminBrowser("connected")
        } else if(selectedString.indexOf("noClientWaiting")>=0) {
          soAdminBrowser("info")
        }
      }
    })
*/

    // prepare connect dialog
    connectDialog = new Dialog(activity)
    connectDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    connectDialog.setContentView(R.layout.connect)
    connectDialog.getWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0))
    connectDialog.setCanceledOnTouchOutside(false)
    connectDialog.setCancelable(true)
    editLayout.setVisibility(View.GONE) // hide form field and send button of main view
    connectText = connectDialog.findViewById(R.id.connectText).asInstanceOf[TextView]
    connectText.setVisibility(View.GONE)
    connectOTRButton = connectDialog.findViewById(R.id.connectOTR).asInstanceOf[Button]
    connectKeySelectButton = connectDialog.findViewById(R.id.connectKeySelect).asInstanceOf[Button]
    connectProgressBar = connectDialog.findViewById(R.id.simpleProgressBar).asInstanceOf[ProgressBar]

    if(appService==null) {
      // initialize our app service
      appServiceConnection = new ServiceConnection() { 
        override def onServiceDisconnected(className:ComponentName) { 
          Log.e(TAG, "onCreate onServiceDisconnected set appService=null ########")
          //serviceFailedFkt()    // todo?
        } 

        override def onServiceConnected(className:ComponentName, rawBinder:IBinder) { 
          if(D) Log.i(TAG, "onCreate onServiceConnected localBinder.getService ...")
          appService = rawBinder.asInstanceOf[P2pChatService#LocalBinder].getService
          if(appService==null) {
            Log.e(TAG, "onCreate onServiceConnected no interface to service, appService==null")
            return
          }

          appService.setActivity(activity, msgFromServiceHandler)

          // we may get some current session conversation strings to (re-)display
          val conversationElements = appService.conversationQueueLength
          if(D) Log.i(TAG, "onCreate onServiceConnected conversationElements="+conversationElements)
          // display up to 20 strings
          val showOldConversationLines = scala.math.min(conversationElements, 20)
          if(D) Log.i(TAG, "onCreate get up to "+showOldConversationLines+" elements")
          for(i <- 0 until showOldConversationLines) {
            val str = appService.conversationQueueGet(i)
            if(str!=null)
              mConversationArrayAdapter.add(str)
          }
          
          // check if service is currently connected
          if(appService.connecting) {
            // service is currently connecting, we show connectDialog busy bee
            msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_CONNECTING,-1,-1, null).sendToTarget

          } else if(appService.p2pChatEncrypt!=null) {
            // service is currently fixed-key connected, hide connectDialog, enable text enter form
            msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_DISMISS, -1, -1, null).sendToTarget

            if(appService.p2pChatEncrypt.relayBasedP2pCommunication)
              msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 1, -1, null).sendToTarget

            if(appService.p2pChatEncrypt.udpConnectIpAddr!=null)
              msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 1, -1, null).sendToTarget

            msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED, 1, -1, null).sendToTarget

          } else if(appService.p2pChatOTR!=null) {
            // service is currently otr connected, hide connectDialog, enable text enter form
            msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_DISMISS, -1, -1, null).sendToTarget

            if(appService.p2pChatOTR.relayBasedP2pCommunication)
              msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY, 1, -1, null).sendToTarget

            if(appService.p2pChatOTR.udpConnectIpAddr!=null)
              msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT, 1, -1, null).sendToTarget

            msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED, 1, -1, null).sendToTarget

          } else {
            // service is currently not connected, show connectDialog with action buttons
            msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW,-1,-1, null).sendToTarget
          }
        }
      } 

      if(D) Log.i(TAG, "onCreate startService('P2pChatService') ...")
      serviceIntent = new Intent(activity, classOf[P2pChatService])
      startService(serviceIntent)
      if(D) Log.i(TAG, "onCreate startService DONE")

      bindService(serviceIntent, appServiceConnection, 0)
      if(D) Log.i(TAG, "onCreate bindService DONE")
    }

    // user hits back key while in connectDialog
    connectDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
      override def onCancel(dialog:DialogInterface) {
        if(D) Log.i(TAG, "connectDialog setOnCancelListener")

        // find out if we are truely NOT connected or connecting
        if(appService==null || 
          (appService.p2pChatEncrypt==null && appService.p2pChatOTR==null) ||
           !appService.connecting) {
          // we are NOT connected or connecting: kill service + activity
          if(D) Log.i(TAG, "connectDialog onCancelListener: not p2p connected => kill service + activity...")     
          if(serviceIntent!=null) {
            // kill our service
            stopService(serviceIntent)
            serviceIntent=null
            if(D) Log.i(TAG, "connectDialog onCancelListener stopService done")
          }
          if(D) Log.i(TAG, "connectDialog onCancelListener finished")
          finish  // -> onDestroy
          return
        }

        // we are currently connecting: ask user if abort is really desired
        if(D) Log.i(TAG, "connectDialog onCancelListener: we are currently p2p connected or connecting")
        val dialogClickListener = new DialogInterface.OnClickListener() {
          override def onClick(dialog:DialogInterface, whichButton:Int) {
            whichButton match {
              case DialogInterface.BUTTON_POSITIVE =>
                // p2p disconnect
                appService.manualDisconnect = true
                if(D) Log.i(TAG, "p2p disconnect -> p2pQuit")
                new Thread("p2pQuit") {
                  override def run() {
                    if(appService.p2pChatEncrypt!=null)
                      appService.p2pChatEncrypt.p2pQuit(true)
                    else
                    if(appService.p2pChatOTR!=null)
                      appService.p2pChatOTR.p2pQuit(true)  
                    // p2pReset will set udpConnectIpAddr=null
                  }
                }.start
                msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget

              case DialogInterface.BUTTON_NEGATIVE =>
                connectDialog.show
            }
          }
        }
        new AlertDialog.Builder(activity)
                       .setTitle("Abort connect request?")
                       .setPositiveButton("Yes",dialogClickListener)
                       .setNegativeButton("No", dialogClickListener)
                       .setCancelable(false)
                       .show
      }
    })

    // send message to remote client
    AndrTools.buttonCallback(findViewById(R.id.button_send).asInstanceOf[Button]) { () =>
      val unencryptedMessage = findViewById(R.id.edit_text_out).asInstanceOf[EditText].getText.toString.trim
      if(unencryptedMessage.length>0) {
        findViewById(R.id.edit_text_out).asInstanceOf[EditText].setText("")
        //if(D) Log.i(TAG, "button_send unencryptedMessage="+unencryptedMessage) // never log this
        if(appService!=null) {
          val displayMsg = "> "+unencryptedMessage

          if(appService.p2pChatEncrypt!=null && appService.p2pChatEncrypt.pubKeyRemote!=null) {
            mConversationArrayAdapter.add(displayMsg)
            appService.conversationQueuePut(displayMsg)
            new Thread("p2pChatEncrypt") {
              override def run() {
                val encryptedMessage = RsaEncrypt.encrypt(appService.p2pChatEncrypt.pubKeyRemote, unencryptedMessage)
                //if(D) Log.i(TAG, "button_send encryptedMessage="+encryptedMessage+" len="+encryptedMessage.length)
                appService.p2pChatEncrypt.p2pSend(encryptedMessage, 
                                                 appService.p2pChatEncrypt.udpConnectIpAddr, 
                                                 appService.p2pChatEncrypt.udpConnectPortInt,
                                                 "rsastr")
                // note: this may throw ioexcption on network issues
              }
            }.start

          } else if(appService.p2pChatOTR!=null) {
            mConversationArrayAdapter.add(displayMsg)
            appService.conversationQueuePut(displayMsg)
            new Thread("p2pChatOTR") {
              override def run() {
                appService.p2pChatOTR.otrMsgSend(unencryptedMessage)
              }
            }.start
          }
        }
      }
    }

    // otr connect
    AndrTools.buttonCallback(connectOTRButton) { () =>
      if(D) Log.i(TAG, "onClick connect")
      // todo: would be nice if we could disable all text-correction features here
      val editText = new EditText(activity)
      if(otrString!=null)
        editText.setText(otrString)
      otrDialog = new AlertDialog.Builder(activity)
                     .setTitle("Off the record")
                     .setMessage("Both clients must enter the exact same secret. It's best to use two words separated by space:")
                     .setView(editText)
                     .setPositiveButton("Connect",new DialogInterface.OnClickListener() {
                          override def onClick(dialogInterf:DialogInterface, whichButton:Int) {
                            otrString = editText.getText.toString.trim
                            val tokenArrayOfStrings = otrString split ' '
                            val p2pSecret = tokenArrayOfStrings(0)
                            val smpSecret = if(tokenArrayOfStrings.length>1) tokenArrayOfStrings(1) else null
                            connectOTR(p2pSecret, smpSecret)
                          }
                        })
                     .show

      // we want to make sure that user enters at least 5 characters
      def enableDisableConnectButton() = {
        if(editText.getText.toString.length>=5)
          otrDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(true)
        else
          otrDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setEnabled(false)
      }
      editText.addTextChangedListener(new android.text.TextWatcher() {
        override def onTextChanged(s:CharSequence, start:Int, before:Int, count:Int) { enableDisableConnectButton }
        override def afterTextChanged(s:android.text.Editable) { }
        override def beforeTextChanged(s:CharSequence, start:Int, before:Int, count:Int) { }
      })
      enableDisableConnectButton

      // we want the soft keyboard to auto-open
      otrDialog.getWindow.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }


    // connect with selected target-key
    AndrTools.buttonCallback(connectKeySelectButton) { () =>
      if(D) Log.i(TAG, "onClick keySelect")
      // open keySelect dialog; todo: hand over keyFolderPath = "/sdcard/p2pKeys/"
      val intent = new Intent(activity, classOf[SelectTargetKeyActivity])
      startActivityForResult(intent, REQUEST_SELECT_TARGET_KEY) // -> SelectTargetKeyActivity -> onActivityResult()
    }
  }

  private def connectOTR(p2pSecret:String, smpSecret:String) {
    appService.connectionName = p2pSecret
    msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_CONNECTING, -1, -1, null).sendToTarget

    new Thread("p2pChatOTR") {
      override def run() {
        if(D) Log.i(TAG, "connect new p2pChatOTR ...")
        val keyFolderPath = "/sdcard/p2pKeys/"
        val p2pChatOTR = appService.newP2pChatOTR(keyFolderPath, p2pSecret, smpSecret)
        if(D) Log.i(TAG, "connect p2pChatOTR ("+p2pChatOTR+") start ...")
        p2pChatOTR.start // blocking
        if(D) Log.i(TAG, "connect finished")
      }
    }.start
  }

  private def connectEncrypt(selectedKeyName:String, rendevouzString:String) {
    appService.connectionName = if(selectedKeyName!="-") selectedKeyName else ""
    msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_CONNECTING, -1, -1, null).sendToTarget

    new Thread("p2pChatEncrypt") {
      override def run() {
        if(D) Log.i(TAG, "connect new P2pChatEncrypt ...")
        val keyFolderPath = "/sdcard/p2pKeys/"
        val p2pChatEncrypt = appService.newP2pChatEncrypt(keyFolderPath, selectedKeyName, rendevouzString)
        if(D) Log.i(TAG, "connect p2pChatEncrypt ("+p2pChatEncrypt+") start ...")
        p2pChatEncrypt.start // blocking
        if(D) Log.i(TAG, "connect finished")
        // todo: must make sure menu buttons become visible
      }
    }.start
  }

  override def onResume() {
    super.onResume
    if(appService!=null)
      appService.onResumeActivity
  }

  override def onPause() {
    if(appService!=null)
      appService.onPauseActivity
    super.onPause
  }

  override def onDestroy() {
    if(D) Log.i(TAG, "onDestroy")
    if(connectDialog!=null) {
      connectDialog.dismiss
      connectDialog=null
    }
    if(appServiceConnection!=null) {
      unbindService(appServiceConnection)
      appServiceConnection=null
      if(D) Log.i(TAG, "onDestroy unbindService done")
      // our service is unbound now but it is not killed
      // it will be killed only, when the user purposfully exits the app via onBackPressed
    }
    activityDestroyed = true  // will make msgFromServiceHandler ignore all msgs
    super.onDestroy
    if(D) Log.i(TAG, "onDestroy finished")
  }

  override def onBackPressed() {
    // back key is pressed in p2p-connected mode (connectDialog is not shown)
    // we are currently connected: ask user if disconnect is really desired
    if(D) Log.i(TAG, "onBackPressed: we are currently p2p connected")
    val dialogClickListener = new DialogInterface.OnClickListener() {
      override def onClick(dialog:DialogInterface, whichButton:Int) {
        whichButton match {
          case DialogInterface.BUTTON_POSITIVE =>
            // p2p disconnect
            if(appService!=null) {
              appService.manualDisconnect = true
              if(D) Log.i(TAG, "p2p disconnect: p2pQuit")
              new Thread("p2pQuit") {
                override def run() {
                  if(appService.p2pChatEncrypt!=null) {
                    appService.p2pChatEncrypt.p2pQuit(true)
                    appService.p2pChatEncrypt.relayBasedP2pCommunication = false

                  } else if(appService.p2pChatOTR!=null) {
                    appService.p2pChatOTR.p2pQuit(true)
                    appService.p2pChatOTR.relayBasedP2pCommunication = false
                  }
                }
              }.start
              msgFromServiceHandler.obtainMessage(P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW, -1, -1, null).sendToTarget
            }

          case DialogInterface.BUTTON_NEGATIVE =>
        }
      }
    }
    new AlertDialog.Builder(activity)
                   .setTitle("Disconnect?")
                   .setPositiveButton("Yes",dialogClickListener)
                   .setNegativeButton("No", dialogClickListener)
                   .show
  }

  override def onActivityResult(requestCode:Int, resultCode:Int, intent:Intent) {
    if(D) Log.i(TAG, "onActivityResult resultCode="+resultCode+" requestCode="+requestCode)
    requestCode match {
      case REQUEST_SELECT_TARGET_KEY =>
        if(D) Log.i(TAG, "onActivityResult REQUEST_SELECT_TARGET_KEY")
        if(resultCode!=Activity.RESULT_OK) {
          if(D) Log.i(TAG, "REQUEST_SELECT_TARGET_KEY resultCode!=Activity.RESULT_OK ="+resultCode)
        } else if(intent==null) {
          Log.e(TAG, "onActivityResult REQUEST_SELECT_TARGET_KEY intent==null")
        } else {
          val bundle = intent.getExtras
          if(bundle==null) {
            Log.e(TAG, "onActivityResult REQUEST_SELECT_TARGET_KEY intent.getExtras==null")
          } else {
            val keyName = bundle.getString("keyName")
            if(D) Log.i(TAG, "REQUEST_SELECT_TARGET_KEY keyName="+keyName)
            if(keyName!=null)
              connectEncrypt(keyName, null)
          }
        }
    }
  }

  private val msgFromServiceHandler = new Handler() {
    override def handleMessage(msg:Message) {
      if(activityDestroyed)
        return

      msg.what match {
        case P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_SHOW =>
          Log.i(TAG, "msgFromServiceHandler CONNECT_DIALOG_SHOW")
          // hide edit form and busy bee
          editLayout.setVisibility(View.GONE)
          connectProgressBar.setVisibility(View.GONE)
          // hide "Connecting..."
          connectText.setVisibility(View.GONE)
          // show the connect buttons
          connectOTRButton.setVisibility(View.VISIBLE)
          connectKeySelectButton.setVisibility(View.VISIBLE)
          if(connectDialog!=null)
            connectDialog.show

        case P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_CONNECTING =>
          Log.i(TAG, "msgFromServiceHandler CONNECT_DIALOG_CONNECTING")
          // hide edit form
          editLayout.setVisibility(View.GONE)
          // show busy bee
          connectProgressBar.setVisibility(View.VISIBLE)
          // show "Connecting..."
          connectText.setText("Connecting "+appService.connectionName+"...")
          connectText.setVisibility(View.VISIBLE)
          // hide the connect buttons
          connectOTRButton.setVisibility(View.GONE)
          connectKeySelectButton.setVisibility(View.GONE)
          if(connectDialog!=null)
            connectDialog.show

        case P2pChatService.ACTIVITY_MSG_CONNECT_DIALOG_DISMISS =>
          Log.i(TAG, "msgFromServiceHandler CONNECT_DIALOG_DISMISS")
          connectDialog.dismiss
          editLayout.setVisibility(View.VISIBLE)
          // prepare connectDialog to be shown again later
          connectProgressBar.setVisibility(View.GONE)
          connectText.setVisibility(View.GONE)
          connectOTRButton.setVisibility(View.VISIBLE)
          connectKeySelectButton.setVisibility(View.VISIBLE)

        case P2pChatService.ACTIVITY_MSG_ADD_CONVERSATION =>
          val str = msg.obj.asInstanceOf[String]
          Log.i(TAG, "msgFromServiceHandler CONNECT_ADD_CONVERSATION "+str)
          mConversationArrayAdapter.add(str)
          // todo: audio-notification? (maybe only if activity is in background?)
          
        case P2pChatService.ACTIVITY_MSG_CONNECT_STATE_RELAY =>
          if(indicatorRelay!=null) {
            val active = msg.arg1
            Log.i(TAG, "msgFromServiceHandler CONNECT_STATE_RELAY "+active)
            if(active>0)
              indicatorRelay.setTextColor(0xfff0f0a0)
            else
              indicatorRelay.setTextColor(0xff666666)
          }

        case P2pChatService.ACTIVITY_MSG_CONNECT_STATE_DIRECT =>
          if(indicatorDirect!=null) {
            val active = msg.arg1
            Log.i(TAG, "msgFromServiceHandler CONNECT_STATE_DIRECT "+active)
            if(active>0)
              indicatorDirect.setTextColor(0xfff0f0a0)
            else
              indicatorDirect.setTextColor(0xff666666)
          }

        case P2pChatService.ACTIVITY_MSG_CONNECT_STATE_CRYPTED =>
          if(indicatorCrypted!=null) {
            val active = msg.arg1
            Log.i(TAG, "msgFromServiceHandler CONNECT_STATE_CRYPTED "+active)
            if(active>0)
              indicatorCrypted.setTextColor(0xfff0f0a0)
            else
              indicatorCrypted.setTextColor(0xff666666)
          }

        case _ =>
          Log.i(TAG, "msgFromServiceHandler UNKNOWN "+msg.what)
      }
    }
  }
}

