.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Running our CorDapp
===================

Deploying our CorDapp
---------------------
Now that we've written a CorDapp, we need to test it by running it on some real Corda nodes.

Open the project's build file under ``java-source/build.gradle`` or ``kotlin-source/build.gradle`` and scroll down to
the ``task deployNodes`` section. This section defines four nodes - the Controller, and NodeA, NodeB and NodeC:

.. container:: codeset

    .. code-block:: kotlin

        task deployNodes(type: net.corda.plugins.Cordform, dependsOn: ['build']) {
            directory "./build/nodes"
            networkMap "CN=Controller,O=R3,OU=corda,L=London,C=UK"
            node {
                name "CN=Controller,O=R3,OU=corda,L=London,C=UK"
                advertisedServices = ["corda.notary.validating"]
                p2pPort 10002
                rpcPort 10003
                webPort 10004
                cordapps = []
            }
            node {
                name "CN=NodeA,O=NodeA,L=London,C=UK"
                advertisedServices = []
                p2pPort 10005
                rpcPort 10006
                webPort 10007
                cordapps = []
                rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
            }
            node {
                name "CN=NodeB,O=NodeB,L=New York,C=US"
                advertisedServices = []
                p2pPort 10008
                rpcPort 10009
                webPort 10010
                cordapps = []
                rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
            }
            node {
                name "CN=NodeC,O=NodeC,L=Paris,C=FR"
                advertisedServices = []
                p2pPort 10011
                rpcPort 10012
                webPort 10013
                cordapps = []
                rpcUsers = [[ user: "user1", "password": "test", "permissions": []]]
            }
        }

We can see that the Controller is running the network map service, and is also offering a validating notary service.
The remaining nodes are just standard nodes.

When we run the ``deployNodes`` task, for each node definition, our build system will:

* Build a CorDapp jar from the project's source files
* Create a node in ``build/nodes`` with the CorDapp installed

Let's do that now by running:

* For Windows:

    .. code:: python

        gradlew clean deployNodes

* For Mac:

    .. code:: python

        ./gradlew clean deployNodes

Running the nodes
-----------------
``deployNodes`` builds the nodes under ``java-source/build/nodes`` or ``kotlin-source/build/nodes``. If we navigate
to this folder, we'll see the four nodes, each with the following structure:

    .. code:: python

        .
        |____artemis
        |____attachments
        |____certificates
        // The runnable node
        |____corda.jar
        // The node's webserver
        |____corda-webserver.jar
        |____dependencies
        |____logs
        // The node's configuration file
        |____node.conf
        |____persistence.mv.db
        |____plugins
        // Our IOU CorDapp
        | |____java/kotlin-source-0.1.jar
        |____shell-commands

We can now run the nodes from the root of the project by running:

* For Windows:

    .. code:: python

        gradlew clean deployNodes

* For Mac:

    .. code:: python

        ./gradlew clean deployNodes

This will start a terminal window for each node, and an additional terminal windows for each node's webserver - eight
terminal windows in all. Give each node a moment to start - you'll know its ready when its terminal windows displays
the message, "Welcome to the Corda interactive shell.".

[$diagram of ready node]

Interacting with the nodes
--------------------------
// TODO: Interact via Crash shell

// TODO: Kick off flow on one node
// TODO: Look at output on two counterparties
// TODO: Show there's nothing in the vault of the third
