/*
 * This file is part of P2pChat.
 *
 * Copyright (C) 2012 Timur Mehrvarz, timur.mehrvarz(a)gmail(.)com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation <http://www.gnu.org/licenses/>, either 
 * version 3 of the License, or (at your option) any later version.
 */

package timur.p2pChat

import android.util.Log
import android.app.{ Activity, AlertDialog, Dialog }
import android.content.{ Context, DialogInterface }
import android.view.View

object AndrTools {
  private val TAG = "AndrTools"
  private val D = true

  def buttonCallback(activity:Activity, resId:Int)(buttonAction:() => Unit) {
    val button = activity.findViewById(resId)
    buttonCallback(button)(buttonAction)
  }

  def buttonCallback(button:View)(buttonAction:() => Unit) {
    //if(D) Log.i(TAG, "buttonCallback button="+button)
    if(button!=null) {
      button.setOnClickListener(new View.OnClickListener() {
        override def onClick(view:View) { 
          //if(D) Log.i(TAG, "buttonCallback call buttonAction")
          buttonAction()
        }
      })
    }
  }

  def runOnUiThread(context:Context)(action:() => Unit) {
    //if(D) Log.i(TAG, "runOnUiThread context="+context)
    if(context!=null) {
      context.asInstanceOf[Activity].runOnUiThread(new Runnable() {
        override def run() {
          action()
        }
      })
    }
  }

  var alertDlg:AlertDialog = null

  def alertDialog(context:Context, title:String, msg:String=null)(action:() => Unit) :AlertDialog = {
    alertDialogClose
    alertDlg = new AlertDialog.Builder(context)
      .setTitle(title)
      .setMessage(msg)
      .setPositiveButton("OK",new DialogInterface.OnClickListener() {
          override def onClick(dialog:DialogInterface, whichButton:Int) {
            if(alertDlg!=null)
              alertDlg.dismiss
            action()
          }
        })
      .show
    return alertDlg
  }

  def alertDialogClose() {
    if(alertDlg!=null) {
      try {
        alertDlg.dismiss
      } catch {
        case ex:Exception =>
          //if(D) Log.i(TAG, "alertDialog.dismiss ex="+ex)
      }
      alertDlg=null
    }
  }
}

