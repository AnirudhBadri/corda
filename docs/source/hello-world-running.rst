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

Let's do that now by running either:

.. code:: python

    // On Windows
    gradlew clean deployNodes

    // On Mac
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

.. code:: python

    // On Windows for a Java CorDapp
    call java-source/build/nodes/runnodes.bat

    // On Windows for a Kotlin CorDapp
    call kotlin-source/build/nodes/runnodes.bat

    // On Mac for a Java CorDapp
    java-source/build/nodes/runnodes

    // On Mac for a Kotlin CorDapp
    kotlin-source/build/nodes/runnodes

This will start a terminal window for each node, and an additional terminal window for each node's webserver - eight
terminal windows in all. Give each node a moment to start - you'll know its ready when its terminal windows displays
the message, "Welcome to the Corda interactive shell.".

[$screenshot of ready node]

Interacting with the nodes
--------------------------
Now that our nodes are running, we can order one of them to kick off our ``IOUFlow``. In a larger app, we'd generally
interact with our node via the node's RPC interface, or even an API built on top of that. Here, for simplicity, we'll
be interacting with the node via its built-in CRaSH shell.

Go to the terminal window with CRaSH shell of Node A. Typing ``help`` will display a list of the available commands.

Let's create an IOU of 100 with Node B. We start the ``IOUFlow`` by typing:

.. code:: python

    start IOUFlow arg0: 99, arg1: "CN=NodeB,O=NodeB,L=New York,C=US"

The process of agreeing the IOU between Node A and Node B will happen automatically, and we'll see a series of
progress steps appearing to show the flow's progress.

In theory, the success of the flow should have caused an IOU to be recorded in the vaults of both Node A and Node B.
Equally importantly, Node C - although part of the same network - should not be aware of this transaction.

We'll be using RPC operations to check the contents of each node. Typing ``run`` will display a list of the available
commands. We can examine don't forget dead link checking - GA, with a custom 404 page and alerts
the contents of a node's vault by running:

.. code:: python

     run vaultAndUpdates

And we can also examine a node's transaction storage by running:

.. code:: python

     run verifiedTransactions

Checking the vault of Node A and Node B should display the following output:

.. code:: python

    first:
    - state:
        data:
          value: 99
          sender: "CN=NodeA,O=NodeA,L=London,C=UK"
          recipient: "CN=NodeB,O=NodeB,L=New York,C=US"
          contract:
            legalContractReference: "559322B95BCF7913E3113962DC3F3CBD71C818C66977721580C045DC41C813A5"
          participants:
          - "CN=NodeA,O=NodeA,L=London,C=UK"
          - "CN=NodeB,O=NodeB,L=New York,C=US"
        notary: "CN=Controller,O=R3,OU=corda,L=London,C=UK,OU=corda.notary.validating"
        encumbrance: null
      ref:
        txhash: "656A1BF64D5AEEC6F6C944E287F34EF133336F5FC2C5BFB9A0BFAE25E826125F"
        index: 0
    second: "(observable)"

But checking the vault of Node C should output nothing!

.. code:: python

    first: []
    second: "(observable)"

Conclusion
----------
We have now deployed a simple CorDapp that allows IOUs to be issued onto the ledger. Like all CorDapps, our
CorDapp is made up of three key parts:

* The ``IOUState``, representing IOUs on the ledger
* The ``IOUContract``, controlling the evolution of IOUs over time
* The ``IOUFlow``, orchestrating the process of agreeing the creation of an IOU on-ledger.

Together, these three parts completely determine how IOUs are created and evolved on the ledger.

Next steps
----------
You should now be ready to develop your own CorDapps. There's
`a more fleshed-out version of the IOU CorDapp <https://github.com/corda/cordapp-tutorial>`_
with an API and web front-end, and a set of example CorDapps in
`the main Corda repo <https://github.com/corda/corda>`_, under ``samples``. You can read about how to run these
samples :doc:`here <running-the-demos>`.

As you write CorDapps, you can learn more about the API available :doc:`here <api>`.

If you get stuck at any point, please reach out on `Slack <https://slack.corda.net/>`_,
`Discourse <https://discourse.corda.net/>`_, or `Stack Overflow <https://stackoverflow.com/questions/tagged/corda>`_.