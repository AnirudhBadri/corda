.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing the flow
================

<<<<<<< HEAD
FlowLogic
---------
A flow describes the sequence of steps for agreeing a specific ledger update. By installing new flows on our node, we
allow the node to handle new business processes.

Each flow is a ``FlowLogic`` instance, with the logic of the flow described by overriding ``FlowLogic.call``. Flows are
often registered to communicate in pairs. In our case, we'll have two flows - one called `Initiator`, to be run by
the sender of the IOU, and one called `Acceptor`, to be run by the recipient:

.. container:: codeset

    .. code-block:: kotlin

        object IOUFlow {
            @InitiatingFlow
            @StartableByRPC
            class Initiator(val iouValue: Int,
                            val otherParty: Party): FlowLogic<SignedTransaction>() {

                /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
                override val progressTracker = ProgressTracker()

                /** The flow logic is encapsulated within the call() method. */
                @Suspendable
                override fun call(): SignedTransaction { }
            }

            @InitiatedBy(Initiator::class)
            class Acceptor(val otherParty: Party) : FlowLogic<Unit>() {

                @Suspendable
                override fun call() { }
            }
        }

    .. code-block:: java

        public class IOUFlow {
            @InitiatingFlow
            @StartableByRPC
            public static class Initiator extends FlowLogic<SignedTransaction> {
                private final Integer iouValue;
                private final Party otherParty;

                /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
                private final ProgressTracker progressTracker = new ProgressTracker();

                public Initiator(Integer iouValue, Party otherParty) {
                    this.iouValue = iouValue;
                    this.otherParty = otherParty;
                }

                /** The flow logic is encapsulated within the call() method. */
                @Suspendable
                @Override
                public SignedTransaction call() throws FlowException { }
            }

            @InitiatedBy(Initiator.class)
            public static class Acceptor extends FlowLogic<Void> {

                private final Party otherParty;

                public Acceptor(Party otherParty) {
                    this.otherParty = otherParty;
                }

                @Suspendable
                @Override
                public Void call() throws FlowException { }
            }
        }

We can see that we have two ``FlowLogic`` subclasses, each overriding ``FlowLogic.call``. There's a few things to note:

* ``FlowLogic.call`` has a return type that matches the generic passed to ``FlowLogic`` - this is the return type of
  running the flow
* The ``FlowLogic`` subclasses may have constructor parameters. These can be used to modify the behaviour of the rest
  of the flow
* ``FlowLogic.call`` is annotated ``@Suspendable`` - this allows the flow to be checkpointed to disk when it
  encounters a long-running operation, allowing your node to continue running other flows. Leaving this annotation
  out will lead to some very weird error messages indeed.
* We can also see a few more annotations, on the ``FlowLogic`` subclasses themselves:
  * ``@InitiatingFlow`` means that this flow can be started directly by the node
  * ``@InitiatedBy(Class)`` means that this flow can only start up in response to a message from another flow
  * ``StartableByRPC`` allows the node owner to start this flow via an RPC call

Flow outline
------------
Now that we've set up our ``FlowLogic`` subclasses, let's think about the steps we need to go through to issue a new
IOU onto the ledger:

* On the initiator side, we need to:
  1. Create a valid transaction proposing the creation of a new IOU
  2. Sign the transaction ourselves
  3. Gather the acceptor's signature
  4. Get the transaction notarised, to protect against double-spends
  5. Record the notarised transaction in our vault
  6. Send the notarised transaction to the acceptor so that they can approve it too

* On the acceptor side, we need to:
  1. Receive the partially-signed transaction from the initiator
  2. Verify its contents and signatures
  3. Append our signatures and send it back to the initiator
  4. Wait to receive back the notarised transaction from the initiator
  5. Record the transaction in our vault

Subflows
^^^^^^^^
This looks like a lot of work. However, we can actually automate a lot of these steps by invoking existing flows as
*subflows* to automate many of these tasks. Step 3 on the initiator's side can be automated by
``SignTransactionFlow``, while steps 4, 5 and 6 can be automated by ``FinalityFlow``.

Meanwhile, the *entirety* of the acceptor's flow can be automated through a combination of ``CollectSignaturesFlow``
and ``FinalityFlow``.

All we have to handle is the creation and signing of a valid transaction on the initiator side.

Writing the flow
----------------
We can break the flow down into five steps:
* Building the transaction
* Verifying the transaction
* Signing the transaction
* Gathering the counterparty's signature
* Finalising the transaction

Building the transaction
^^^^^^^^^^^^^^^^^^^^^^^^
We'll approach building the transaction in three steps:

* Creating a transaction builder
* Creating the transaction's components
* Adding the components to the builder

TransactionBuilder
~~~~~~~~~~~~~~~~~~
We create a ``TransactionBuilder`` in ``Initiator.call`` as follows:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);
        }

In the first line, we create a ``TransactionBuilder``. This is a mutable transaction class that we can use to build
up our proposed transaction.

We then retrieve the identity of the notary who will be notarising our transaction and add it to the builder. Whenever
we need information about our node, its contents or the rest of the network within a flow, we use the node's
``ServiceHub``. ``ServiceHub.networkMapCache`` in particular provides information about the other nodes on the
network and the services they offer.

Transaction components
~~~~~~~~~~~~~~~~~~~~~~
We now need to create the components of our transaction:

* The output state
* The ``Create`` command, with the sender and recipient as signers

We create these components as follows:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);
        }

We start by retrieving our own identity, which we'll need this to build the state. As before, we get this information
from the ``ServiceHub`` - from ``ServiceHub.myInfo``, in this case. We then build the ``IOUState``, using our
identity and the ``FlowLogic``'s constructor parameters.

Adding the components
~~~~~~~~~~~~~~~~~~~~~
We now add the items to the transaction using the ``TransactionBuilder.withItems`` method:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);
        }

``TransactionBuilder.withItems`` takes a `vararg` of:
* `ContractState` objects, which are added to the builder as output states
* `StateRef` objects (references to the outputs of previous transactions), which are added to the builder as input
  state references
* `Command` objects, which are added to the builder as commands

Verifying the transaction
^^^^^^^^^^^^^^^^^^^^^^^^^
We've now built our proposed transaction. Before we sign it, we should check that the proposed ledger update is
valid, by running the transaction's contracts:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();
        }

To verify the transaction, we must:
* Convert the builder into an immutable ``WireTransaction``
* Convert the ``WireTransaction`` into a ``LedgerTransaction`` using the ``ServiceHub``. This step resolves the
  transaction's input state references and attachment references into actual states and attachments, in case
  they are needed to verify the transaction
* Call ``LedgerTransaction.verify`` to test whether the transaction is valid based on the contract of every input and
  output state in the transaction

If the verify step fails, a ``TransactionVerificationException`` will be throw, ending the flow

Signing the transaction
^^^^^^^^^^^^^^^^^^^^^^^
Now that we are satisfied that the transaction we've built is valid, we sign it to prevent any further changes being
made:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Signing the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();

            // Signing the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);
        }

``ServiceHub.signInitialTransaction`` returns a ``SignedTransaction`` - an object that pairs the contents of a
transaction with a list of signatures over the transaction.

We can now safely send the builder to our counterparty. If the counterparty tries to modify the transaction, the
transaction's hash will change, our digital signature will no longer be valid, and the transaction will not be accepted
as a valid ledger update.

Gathering counterparty signatures
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The next step is to collect the signature from the counterparty. As discussed above, we'll automate this process by
invoking the built-in ``CollectSignaturesFlow``:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Signing the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the signatures.
            val signedTx = subFlow(CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.tracker()))
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();

            // Signing the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Gathering the signatures.
            final SignedTransaction signedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.Companion.tracker()));
        }

``CollectSignaturesFlow`` gathers signatures from every participant, and returns a ``SignedTransaction`` with all the
 required signatures.

SignTransactionFlow
~~~~~~~~~~~~~~~~~~~
By default, every node is registered to respond to a message from ``CollectSignaturesFlow`` by invoking
``SignTransactionFlow``. ``SignTransactionFlow`` is an abstract class, and we have to subclass it in the responder
side of the flow and override ``SignTransactionFlow.checkTransaction``:

.. container:: codeset

    .. code-block:: kotlin

        @InitiatedBy(Initiator::class)
        class Acceptor(val otherParty: Party) : FlowLogic<Unit>() {

            @Suspendable
            override fun call() {
                // Stage 1 - Verifying and signing the transaction.
                subFlow(object : SignTransactionFlow(otherParty, tracker()) {
                    override fun checkTransaction(stx: SignedTransaction) {
                        // Define custom verification logic here.
                    }
                })
            }
        }

    .. code-block:: java

        @InitiatedBy(Initiator.class)
        public static class Acceptor extends FlowLogic<Void> {

            private final Party otherParty;

            public Acceptor(Party otherParty) {
                this.otherParty = otherParty;
            }

            @Suspendable
            @Override
            public Void call() throws FlowException {
                // Stage 1 - Verifying and signing the transaction.

                class signTxFlow extends SignTransactionFlow {
                    private signTxFlow(Party otherParty, ProgressTracker progressTracker) {
                        super(otherParty, progressTracker);
                    }

                    @Override
                    protected void checkTransaction(SignedTransaction signedTransaction) {
                        // Define custom verification logic here.
                    }
                }

                subFlow(new signTxFlow(otherParty, SignTransactionFlow.Companion.tracker()));

                return null;
            }
        }

``SignTransactionFlow`` already checks the transaction's signatures, and whether the transaction is contractually
valid. In ``SignTransactionFlow.checkTransaction``, we define any additional verification of the transaction that we
wish to perform before we sign it. For example, we may wish to check that the value of the IOU is not too high.

Finalising the transaction
^^^^^^^^^^^^^^^^^^^^^^^^^^
We now have a valid transaction signed by all the required parties. We now need to have it notarised and recorded by
all the relevant parties for it to become part of the ledger. Again, we'll do this by invoking the built-in
``FinalityFlow``:

.. container:: codeset

    .. code-block:: kotlin

        @Suspendable
        override fun call(): SignedTransaction {
            // We create a transaction builder
            val txBuilder = TransactionBuilder()
            val notaryIdentity = serviceHub.networkMapCache.getAnyNotary()
            txBuilder.notary = notaryIdentity

            // We create the transaction's components.
            val ourIdentity = serviceHub.myInfo.legalIdentity
            val iou = IOUState(iouValue, ourIdentity, otherParty)
            val txCommand = Command(IOUContract.Create(), iou.participants.map { it.owningKey })

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand)

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(serviceHub).verify()

            // Signing the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the signatures.
            val signedTx = subFlow(CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.tracker()))

            // Finalising the transaction.
            return subFlow(FinalityFlow(signedTx)).single()
        }

    .. code-block:: java

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // We create a transaction builder
            final TransactionBuilder txBuilder = new TransactionBuilder();
            final Party notary = getServiceHub().getNetworkMapCache().getAnyNotary(null);
            txBuilder.setNotary(notary);

            // We create the transaction's components.
            final Party ourIdentity = getServiceHub().getMyInfo().getLegalIdentity();
            final IOUState iou = new IOUState(iouValue, ourIdentity, otherParty, new IOUContract());
            final List<PublicKey> signers = ImmutableList.of(ourIdentity.getOwningKey(), otherParty.getOwningKey());
            final Command txCommand = new Command(new IOUContract.Create(), signers);

            // Adding the item's to the builder.
            txBuilder.withItems(iou, txCommand);

            // Verifying the transaction.
            txBuilder.toWireTransaction().toLedgerTransaction(getServiceHub()).verify();

            // Signing the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Gathering the signatures.
            final SignedTransaction signedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, CollectSignaturesFlow.Companion.tracker()));

            // Finalising the transaction.
            return subFlow(new FinalityFlow(signedTx)).get(0);
        }

``FinalityFlow`` will completely automate the process of:
* Notarising the transaction
* Recording it in our vault
* Sending it to the counterparty for them to record as well

Once ``FinalityFlow`` completes, we will have a valid new IOU recorded on the ledger.

Progress so far
---------------
We now have a flow that we can invoke at will to automate the process of issuing an IOU onto the ledger. Under the
hood, this flow takes the form of two communicating ``FlowLogic`` subclasses.

We now have a complete CorDapp, made up of:
* The ``IOUState``, representing our IOUs on ledger
* The ``IOUContract``, controlling the evolution of ``IOUState`` objects over time
* The ``IOUFlow``, allowing us to orchestrate the agreement of a new IOU

The final step is to spin up some nodes and test our CorDapp.
=======
TODO
>>>>>>> e06ff2fa5704607c07a0c5a4835b0d63d9184484
