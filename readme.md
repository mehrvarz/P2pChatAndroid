P2pChatAndroid
==============

P2pChatAndroid is a secure peer-to-peer chat application. P2pChatAndroid implements end-to-end encryption with [Off-the-Record Messaging](http://de.wikipedia.org/wiki/Off-the-Record_Messaging) and [Socialist Millionaire' Protocol](http://en.wikipedia.org/wiki/Socialist_millionaire). No XMPP or other accounts are needed to use this app. P2pChat sessions can bridge devices operated behind firewalls.

P2pChatAndroid is built upon P2pChatOTR and P2pCore. Implementation details can be found here: [P2pChatOTR](https://github.com/mehrvarz/P2pChatOTR#p2pchatotr) and here [P2pCore](https://github.com/mehrvarz/P2pCore#p2pcore---a-portable-peer-to-peer-framework).

*** This project is still work in progress. I will remove this warning when the app becomes stable. Thank you. ***


Building from source
--------------------

You need to install Scala 2.9.x, JDK6 and Ant, in order to build P2pChatGtk. On Ubuntu 12.04 you would:

    apt-get install scala ant

Rename the properties sample file and adjust sdk.dir and scala.dir pathes:

    mv local.properties.sample local.properties

To build the project, run:

    ./make


Licenses
--------

Please see [P2pChatOTR Licenses](https://github.com/mehrvarz/P2pChatOTR#licenses)

