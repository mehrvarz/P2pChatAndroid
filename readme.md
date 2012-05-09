P2pChatAndroid
==============

P2pChatAndroid is a secure peer-to-peer chat application. P2pChatAndroid implements end-to-end encryption using [Off-the-Record Messaging](http://de.wikipedia.org/wiki/Off-the-Record_Messaging) and [Socialist Millionaire' Protocol](http://en.wikipedia.org/wiki/Socialist_millionaire). No XMPP or other accounts are needed to use this app. P2pChat sessions can bridge devices operated behind firewalls.

P2pChatAndroid is built upon: [P2pChatOTR](https://github.com/mehrvarz/P2pChatOTR#p2pchatotr) and here [P2pCore](https://github.com/mehrvarz/P2pCore#p2pcore---a-portable-peer-to-peer-framework). All technical information can be found there.

*** This project is still work in progress. I will remove this warning when the app becomes stable. Thank you. ***


Building from source
--------------------

You need to install Scala 2.9.x, JDK6 and Ant, in order to build P2pChatGtk. On Ubuntu 12.04 you would:

    apt-get install scala ant

Rename the properties sample file and adjust `sdk.dir` and `scala.dir` pathes:

    mv local.properties.sample local.properties

In order to build this project, run:

    ./make


Licenses
--------

- P2pChatOTR + P2pCore source code and library

  licensed under the GNU General Public [LICENSE](P2pChatOTR/blob/master/licenses/LICENSE), Version 3.

  Copyright (C) 2012 timur.mehrvarz@gmail.com

  https://github.com/mehrvarz/P2pChatOTR

  https://github.com/mehrvarz/P2pCore

- The Java Off-the-Record Messaging library

  covered by the LGPL [LICENSE](P2pChatOTR/blob/master/licenses/java-otr/COPYING).

  [java-otr README](P2pChatOTR/blob/master/licenses/java-otr/README)

  http://www.cypherpunks.ca/otr/

- ProGuard Java class file shrinker

  [ProGuard](http://proguard.sourceforge.net/license.html) is distributed under the terms of the GNU General Public License (GPL), version 2, as published by the Free Software Foundation (FSF)

  http://proguard.sourceforge.net/
  
- Bouncy Castle 

  http://bouncycastle.org/

- Google Protobuf 

  [New BSD License](http://www.opensource.org/licenses/bsd-license.php)

  https://code.google.com/p/protobuf/

- Apache Commons-codec 

  All software produced by The Apache Software Foundation or any of its projects or subjects is licensed according to the terms of [Apache License, Version 2.0](http://www.apache.org/licenses/)

  http://commons.apache.org/codec/

- Emoticon icon

  License [CC Attribution Non-Commercial No Derivatives](http://creativecommons.org/licenses/by-nc-nd/3.0)

  http://www.iconspedia.com/icon/emoticon-confuse-icon-25159.html

