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

import java.io.File

import android.app.Activity
import android.app.ListActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.view.View
import android.widget.ListView
import android.widget.ArrayAdapter

class SelectTargetKeyActivity extends ListActivity {
  private val TAG = "SelectTargetKeyActivity"
  private val D = true

  private var context:Context = null

  override def onCreate(savedInstanceState:Bundle) {
    super.onCreate(savedInstanceState)
    if(D) Log.i(TAG, "onCreate()")
    //requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    context = this

    val targetKeyAdapter = new TargetKeyAdapter(this, R.layout.key_name_entry)
		setListAdapter(targetKeyAdapter)

    val keyFolderPath = "/sdcard/p2pKeys/"
    val fileArray = new File(keyFolderPath).listFiles
    if(fileArray!=null) {
      val fileSortedList = fileArray.iterator.toList.sortWith(_.getName < _.getName)
      for(file <- fileSortedList) { 
        val fileName = file.getName.trim
        if(fileName.length>4 && fileName.endsWith(".pub") && fileName!="key.pub") {
          if(D) Log.i(TAG, "onCreate() adding '"+fileName+"'")
          targetKeyAdapter.add(fileName.substring(0,fileName.length-4))
        }
      }
      targetKeyAdapter.notifyDataSetChanged
    }
  }

	override def onListItemClick(listView:ListView, view:View, position:Int, id:Long) :Unit = {
		super.onListItemClick(listView, view, position, id)

		// Get the item that was clicked
		val obj = getListAdapter.getItem(position)
		if(obj==null) {
      Log.e(TAG, "onListItemClick position="+position+" getListAdapter.getItem(position)=null")
		  return
		}

		val keyNameString = obj.toString
    if(D) Log.i(TAG, "onListItemClick keyNameString="+keyNameString)

    val returnIntent = new Intent()
    val bundle = new Bundle()
    bundle.putString("keyName", keyNameString)
    returnIntent.putExtras(bundle)
    setResult(Activity.RESULT_OK,returnIntent)
    finish
	}
}

