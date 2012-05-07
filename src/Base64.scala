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

package timur.p2pCore

object Base64 {

  def decode(str:String) :Array[Byte] = {
    return android.util.Base64.decode(str,android.util.Base64.DEFAULT)
  }

  def encode(array:Array[Byte]) :String = {
    return android.util.Base64.encodeToString(array,android.util.Base64.NO_WRAP)
  }
}

