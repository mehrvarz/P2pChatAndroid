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

import java.util.ArrayList

import android.content.Context
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ArrayAdapter

class TargetKeyAdapter(context:Context, messageResourceId:Int)
  extends ArrayAdapter[String](context, messageResourceId) {

  private val TAG = "TargetKeyAdapter"
  private val D = true

  private var msgList = new ArrayList[String]()

  override def clear() {
    msgList.clear
	}
	
  override def getCount() :Int = {
		return msgList.size
	}

  override def getItem(id:Int) :String = {
		return msgList.get(id)
	}

  override def getView(position:Int, setView:View, parentViewGroup:ViewGroup) :View = {
    val fullString = msgList.get(position)

    var view = setView
    if(view == null) {
      //if(D) Log.i(TAG, "getView position="+position+" inflate a new view")
      val layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      if(layoutInflater!=null) {
        view = layoutInflater.inflate(messageResourceId, null)
      }
      //if(D) Log.i(TAG, "getView("+position+") view="+view+" from layoutInflater")
    }

    if(view == null) {
      Log.e(TAG, "getView view==null abort")
      return null
    }

    //if(D) Log.i(TAG, "getView("+position+") fullString="+fullString)
    val keyNameView = view.findViewById(R.id.keyName).asInstanceOf[TextView]
    keyNameView.setText(fullString)
    return view
  }

  override def add(msg:String) {
    msgList.add(msg)
  }
/*
  override def remove(msg:String) {
    msgList.remove(msg)
  }
*/
}

