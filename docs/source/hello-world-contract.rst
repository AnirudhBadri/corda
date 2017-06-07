.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing the contract
====================

The Contract interface
----------------------
In Corda, the ledger is updated through transactions. Each transaction is a proposal to mark zero or more existing
states as historic (the inputs), and produce zero or more new states (the outputs). One condition for the proposed
transaction to be valid is that it has to meet the requirements of the contracts of every input and output state.

Every contract has to implement the ``Contract`` interface:

.. container:: codeset

    .. code-block:: kotlin

        interface Contract {
            // Implements the contract constraints in code.
            @Throws(IllegalArgumentException::class)
            fun verify(tx: TransactionForContract)

            // Expresses the contract constraints as legal prose.
            val legalContractReference: SecureHash
        }

Another quick Kotlin primer:

* ``fun`` declares a function
* The syntax ``fun funName(arg1Name: arg1Type): returnType`` declares that ``funName`` takes an argument of type
  ``arg1Type`` and returns a value of type ``returnType``

We can see that ``Contract`` expresses its constraints in two ways:

* In legal prose, through a hash referencing a legal contract that expresses the contract's constraints in legal prose
* In code, through a ``verify`` function that takes a transaction as input, and:
  * Throws an ``IllegalArgumentException`` if it rejects the transaction proposal
  * Returns silently if it accepts the transaction proposal

Controlling IOU evolution
-------------------------
What would a valid contract for an ``IOUState`` look like? For our CorDapp, we only want to allow the creation of
IOUs, rather than their transfer or redemption. We can enforce this behaviour by imposing the following constraints:

* An IOU transaction should consume zero inputs, and create one output of type ``IOUState``
* The transaction should also include a ``Create`` command, indicating the transaction's intent
* For the output IOU state:

  * Its value must be non-negative
  * Its sender and its recipient cannot be the same entity
  * All of its participants (i.e. both the sender and the recipient) must be signers on the transaction

Defining IOUContract
--------------------

The Create command
^^^^^^^^^^^^^^^^^^
Let's start defining our contract. The first thing we would like to do is add a *command*. Commands allow us to
achieve two things:

* Fork the execution of ``verify``, allowing us to perform different types of verification on transactions with
  different intents

  * For example, a transaction proposing the creation of an IOU will have to satisfy different constraints to one
    proposing the redemption of an IOU

* Attach required signers to the transaction

  * In the case of IOU creation, we're going to impose the constraint that both the sender and the recipient must
    sign. An IOU transfer might instead require a transaction from the current holder of the IOU only

Let's update the definition of ``TemplateContract`` in TemplateContract.java or TemplateContract.kt to define an
``IOUContract`` with a ``Create`` command:

.. container:: codeset

    .. code-block:: kotlin

        open class IOUContract : Contract {
            // Currently, verify() does no checking at all!
            override fun verify(tx: TransactionForContract) {}

            // Our Create command.
            class Create : CommandData

            // The legal contract reference - we'll leave this as a dummy hash for now.
            override val legalContractReference: SecureHash = SecureHash.sha256("Prose contract.")
        }

    .. code-block:: java

        public class IOUContract implements Contract {
            @Override
            // Currently, verify() does no checking at all!
            public void verify(TransactionForContract tx) {}

            // Our Create command.
            public static class Create implements CommandData {}

            // The legal contract reference - we'll leave this as a dummy hash for now.
            private final SecureHash legalContractReference = SecureHash.sha256("Prose contract.");
            @Override public final SecureHash getLegalContractReference() { return legalContractReference; }
        }

Firstly, we've renamed ``TemplateContract`` to ``IOUContract``.

We've also implemented the ``Create`` command. The command takes the form of a class that implements the
``CommandData`` interface. The ``CommandData`` interface is a very simple marker interface for commands. In fact, its
declaration is only two words long (in Kotlin, marker interfaces do not require a body):

.. container:: codeset

    .. code-block:: kotlin

        interface CommandData

The verify logic
^^^^^^^^^^^^^^^^
For our IOU CorDapp, we won't concern ourselves with writing valid legal prose to enforce the IOU agreement in court.
Instead, we'll focus on the code portion of the ``IOUContract`` in ``verify``.

Our goal in writing the ``verify`` function is to write a function that, for any possible transaction:

* Throws an ``IllegalArgumentException`` if its a transaction proposal we want to reject
* Does **not** throw an exception if its a transaction proposal we want to accept

In deciding whether to throw an exception, the ``verify`` function has access to everything defined on the
transaction, but nothing else:

* ``tx.inputs`` lists the inputs
* ``tx.outputs`` lists the outputs
* ``tx.commands`` lists the commands and their associated signers

Although we won't use them here, the ``verify`` function also has access to the transaction's attachements,
time-windows, notary and hash.

Our ``verify`` function will reject transactions on four grounds:

* The transaction doesn't include a ``Create`` command
* The transaction doesn't have no inputs and a single output
* The IOU itself is invalid
* The transaction doesn't require signatures from both the sender and the recipient

Let's add these constraints one by one.

Command constraints
~~~~~~~~~~~~~~~~~~~
To test for the presence of the ``Create`` command, we can use Corda's ``requireSingleCommand`` function:

.. container:: codeset

    .. code-block:: kotlin

        override fun verify(tx: TransactionForContract) {
            val command = tx.commands.requireSingleCommand<Create>()
        }

    .. code-block:: java

        @Override
        public void verify(TransactionForContract tx) {
            final AuthenticatedObject<Create> command = requireSingleCommand(tx.getCommands(), Create.class);
        }

Here, ``requireSingleCommand`` is reaching into the transaction's commands, asserting that there is exactly one
``Create`` command in the transaction, and returning the command in question.

Transaction constraints
~~~~~~~~~~~~~~~~~~~~~~~
We want no inputs and only a single output to our transaction. One way to impose this constraint is as follows:

.. container:: codeset

    .. code-block:: kotlin

        override fun verify(tx: TransactionForContract) {
            val command = tx.commands.requireSingleCommand<Create>()

            requireThat {
                // Constraints on the shape of the transaction.
                "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)
            }
        }

    .. code-block:: java

        @Override
        public void verify(TransactionForContract tx) {
            final AuthenticatedObject<Create> command = requireSingleCommand(tx.getCommands(), Create.class);

            requireThat(check -> {
                // Constraints on the shape of the transaction.
                check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
                check.using("Only one output state should be created.", tx.getOutputs().size() == 1);

                return null;
            });
        }

Here, we're using Corda's ``requireThat`` function. ``requireThat`` provides a terse way to throw an exception if a
given condition is not true. If the condition on the right-hand side does not evaluate to true, and
``IllegalArgumentException`` is thrown with the message on the left-hand side.

IOU constraints
~~~~~~~~~~~~~~~
We're imposing two constraints on the ``IOUState`` itself:

* Its value must be non-negative
* Its sender and its recipient cannot be the same entity

We can impose these constraints in the same ``requireThat`` function as before:

.. container:: codeset

    .. code-block:: kotlin

        @Override
        public void verify(TransactionForContract tx) {
            final AuthenticatedObject<Create> command = requireSingleCommand(tx.getCommands(), Create.class);

            requireThat(check -> {
                // Constraints on the shape of the transaction.
                check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
                check.using("Only one output state should be created.", tx.getOutputs().size() == 1);

                // IOU-specific constraints.
                final IOUState out = (IOUState) tx.getOutputs().get(0);
                check.using("The IOU's value must be non-negative.",out.getValue() > 0);
                check.using("The sender and the recipient cannot be the same entity.", out.getSender() != out.getRecipient());

                return null;
            });
        }

    .. code-block:: java

        override fun verify(tx: TransactionForContract) {
            val command = tx.commands.requireSingleCommand<Create>()

            requireThat {
                // Constraints on the shape of the transaction.
                "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)

                // IOU-specific constraints.
                val out = tx.outputs.single() as IOUState
                "The IOU's value must be non-negative." using (out.value > 0)
                "The sender and the recipient cannot be the same entity." using (out.sender != out.recipient)
            }
        }

You can see that we're not restricted to writing constraints in the ``requireThat`` block. We can also write standard
statements - in this case, extracting the transaction's single ``IOUState``.

Signer constraints
~~~~~~~~~~~~~~~~~~
Our final constraint is that the transaction must have both the IOU's sender and recipient as required signers. A
transaction's signers are listed on the commands, so we extract them from the ``Create`` command we
retrieved earlier.

.. container:: codeset

    .. code-block:: kotlin

        override fun verify(tx: TransactionForContract) {
            val command = tx.commands.requireSingleCommand<Create>()

            requireThat {
                // Constraints on the shape of the transaction.
                "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)

                // IOU-specific constraints.
                val out = tx.outputs.single() as IOUState
                "The IOU's value must be non-negative." using (out.value > 0)
                "The sender and the recipient cannot be the same entity." using (out.sender != out.recipient)

                // Constraints on the signers.
                "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))
            }
        }

    .. code-block:: java

        @Override
        public void verify(TransactionForContract tx) {
            final AuthenticatedObject<Create> command = requireSingleCommand(tx.getCommands(), Create.class);

            requireThat(check -> {
                // Constraints on the shape of the transaction.
                check.using("No inputs should be consumed when issuing an IOU.", tx.getInputs().isEmpty());
                check.using("Only one output state should be created.", tx.getOutputs().size() == 1);

                // IOU-specific constraints.
                final IOUState out = (IOUState) tx.getOutputs().get(0);
                check.using("The IOU's value must be non-negative.",out.getValue() > 0);
                check.using("The sender and the recipient cannot be the same entity.", out.getSender() != out.getRecipient());

                // Constraints on the signers.
                final List<PublicKey> requiredSigners = ImmutableList.of(
                        out.getSender().getOwningKey(),
                        out.getRecipient().getOwningKey());
                check.using("All of the participants must be signers.", command.getSigners().containsAll(requiredSigners));

                return null;
            });
        }

Now that we have defined our IOUContract, let's go back and modify our IOUState to point to this new contract:

.. container:: codeset

    .. code-block:: kotlin

        class IOUState(val value: Int,
                       val sender: Party,
                       val recipient: Party,
                       override val contract: IOUContract) : ContractState {

            override val participants get() = listOf(sender, recipient)
        }

    .. code-block:: java

        public class IOUState implements ContractState {
            private final Integer value;
            private final Party sender;
            private final Party recipient;
            private final IOUContract contract;

            public IOUState(Integer value, Party sender, Party recipient, IOUContract contract) {
                this.value = value;
                this.sender = sender;
                this.recipient = recipient;
                this.contract = contract;
            }

            public Integer getValue() {
                return value;
            }

            public Party getSender() {
                return sender;
            }

            public Party getRecipient() {
                return recipient;
            }

            @Override
            public IOUContract getContract() {
                return contract;
            }

            @Override
            public List<AbstractParty> getParticipants() {
                return ImmutableList.of(sender, recipient);
            }
        }

Transaction tests
-----------------
Before moving on, let's set up some tests to ensure that the ``IOUContract`` is providing the right controls on the
evolution of each ``IOUState``.

We'll be writing these tests using Corda's ``ledgerDSL`` transaction-testing framework. This will allow us to test
our contract without the overhead of spinning up a node.

Here's our first test:

.. container:: codeset

    .. code-block:: kotlin

        class IOUTransactionTests {
            @Test
            fun `transaction must include Create command`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        fails()
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        verifies()
                    }
                }
            }
        }

    .. code-block:: java

        public class IOUTransactionTests {
            static private final Party miniCorp = getMINI_CORP();
            static private final Party megaCorp = getMEGA_CORP();
            static private final PublicKey[] keys = new PublicKey[2];

            {
                keys[0] = getMEGA_CORP_PUBKEY();
                keys[1] = getMINI_CORP_PUBKEY();
            }

            @Test
            public void transactionMustIncludeCreateCommand() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.fails();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.verifies();
                        return null;
                    });
                    return null;
                });
            }
        }

Here, we're using ``ledgerDSL`` to create a fake transaction. We can add inputs, outputs, commands, etc. to the
transaction using the DSL's ``output``, ``input`` and ``command`` methods. At any point, we can assert that the
transaction so far is either contractually valid (using ``verifies``) or contractually invalid (using ``fails``).

In this instance:

* We initially create a transaction with an output but no command. We assert that this transaction fails contract
verification (since the ``Create`` command is missing)
* We then add the ``Create`` command, and assert that contract verification now succeeds

Here is the full set of tests we'll be using to test the ``IOUContract``:

.. container:: codeset

    .. code-block:: kotlin

        class IOUTransactionTests {
            @Test
            fun `transaction must include Create command`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        fails()
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        verifies()
                    }
                }
            }

            @Test
            fun `transaction must have no inputs`() {
                ledger {
                    transaction {
                        input { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("No inputs should be consumed when issuing an IOU.")
                    }
                }
            }

            @Test
            fun `transaction must have one output`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("Only one output state should be created.")
                    }
                }
            }

            @Test
            fun `sender must sign transaction`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MINI_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("All of the participants must be signers.")
                    }
                }
            }

            @Test
            fun `recipient must sign transaction`() {
                ledger {
                    transaction {
                        output { IOUState(1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("All of the participants must be signers.")
                    }
                }
            }

            @Test
            fun `sender is not recipient`() {
                ledger {
                    transaction {
                        output { IOUState(1, MEGA_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("The sender and the recipient cannot be the same entity.")
                    }
                }
            }

            @Test
            fun `cannot create negative-value IOUs`() {
                ledger {
                    transaction {
                        output { IOUState(-1, MINI_CORP, MEGA_CORP, IOUContract()) }
                        command(MEGA_CORP_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Create() }
                        `fails with`("The IOU's value must be non-negative.")
                    }
                }
            }
        }

    .. code-block:: java

        public class IOUTransactionTests {
            static private final Party miniCorp = getMINI_CORP();
            static private final Party megaCorp = getMEGA_CORP();
            static private final PublicKey[] keys = new PublicKey[2];

            {
                keys[0] = getMEGA_CORP_PUBKEY();
                keys[1] = getMINI_CORP_PUBKEY();
            }

            @Test
            public void transactionMustIncludeCreateCommand() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.fails();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.verifies();
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void transactionMustHaveNoInputs() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.input(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("No inputs should be consumed when issuing an IOU.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void transactionMustHaveOneOutput() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("Only one output state should be created.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void senderMustSignTransaction() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        PublicKey[] keys = new PublicKey[1];
                        keys[0] = getMINI_CORP_PUBKEY();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("All of the participants must be signers.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void recipientMustSignTransaction() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, miniCorp, megaCorp, new IOUContract()));
                        PublicKey[] keys = new PublicKey[1];
                        keys[0] = getMEGA_CORP_PUBKEY();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("All of the participants must be signers.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void senderIsNotRecipient() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(1, megaCorp, megaCorp, new IOUContract()));
                        PublicKey[] keys = new PublicKey[1];
                        keys[0] = getMEGA_CORP_PUBKEY();
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("The sender and the recipient cannot be the same entity.");
                        return null;
                    });
                    return null;
                });
            }

            @Test
            public void cannotCreateNegativeValueIOUs() {
                ledger(ledgerDSL -> {
                    ledgerDSL.transaction(txDSL -> {
                        txDSL.output(new IOUState(-1, miniCorp, megaCorp, new IOUContract()));
                        txDSL.command(keys, IOUContract.Create::new);
                        txDSL.failsWith("The IOU's value must be non-negative.");
                        return null;
                    });
                    return null;
                });
            }
        }

Take a moment to run these tests to ensure that the ``IOUState`` and ``IOUContract`` are running fine.

Progress so far
---------------
We've now written an ``IOUContract`` constraining the evolution of each ``IOUState`` over time. Now, an ``IOUState``
can only be created on the ledger, not transferred or redeemed. Creating an ``IOUState`` requires an issuance
transaction with no inputs, a single ``IOUState`` output, and a ``Create`` command. And the ``IOUState`` created by
the issuance transaction must have a non-negative value, and its sender and recipient must be separate entities.

The final step in the creation of our CorDapp will be to write the ``IOUFlow`` that will allow two node to agree the
creation of a new ``IOUState``on the ledger, without informing the rest of the network.