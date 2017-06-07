.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

Writing the state
=================

The ContractState interface
---------------------------
In Corda, the shared facts on the ledger are represented as states. Every state must implement the ``ContractState``
interface:

.. container:: codeset

    .. code-block:: kotlin

        interface ContractState {
            // the contract controlling transactions involving this state
            val contract: Contract

            // The list of entities considered to have a stake in this state
            val participants: List<AbstractParty>
        }

The core Corda platform, including the interface declaration above, is written in Kotlin. However, because Kotlin
code is compiled down to JVM bytecode, CorDapps written in other JVM languages can interoperate with Corda.

A quick Kotlin primer:

* ``val`` declares a read-only property, similar to Java's ``final`` keyword
* The syntax ``varName: varType`` declares ``varName`` as being of type ``varType``

We can see that the ``ContractState`` interface declares two properties:

* ``contract``: the contract controlling transactions involving this state
* ``participants``: the list of entities that have to approve state changes such as changing the state's notary or
  upgrading the state's contract

Modelling IOUs
--------------
Let's start defining the ``IOUState`` that will represent IOUs on the ledger. Beyond implementing the ``ContractState``
interface, our ``IOUState`` will also need properties to track any relevant features of the IOU:

* The sender of the IOU
* The IOU's recipient
* The value of the IOU

We will abstract away any other details for now, such as the IOU's currency. Adding them is as simple as adding an
additional property to a Java or Kotlin class.

Defining IOUState
-----------------
Let's update the definition of ``TemplateState`` in TemplateState.java or TemplateState.kt to define an ``IOUState``:

.. container:: codeset

    .. code-block:: kotlin

        class IOUState(val value: Int,
                       val sender: Party,
                       val recipient: Party,
                       // TODO: Once we've defined IOUContract, come back and update this.
                       override val contract: TemplateContract) : ContractState {

            override val participants get() = listOf(sender, recipient)
        }

    .. code-block:: java

        public class IOUState implements ContractState {
            private final Integer value;
            private final Party sender;
            private final Party recipient;
            // TODO: Once we've defined IOUContract, come back and update this.
            private final TemplateContract contract;

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
            // TODO: Once we've defined IOUContract, come back and update this.
            public TemplateContract getContract() {
                return contract;
            }

            @Override
            public List<AbstractParty> getParticipants() {
                return ImmutableList.of(sender, recipient);
            }
        }

We've renamed ``TemplateState`` to ``IOUState``, and added properties for ``value``, ``sender`` and ``recipient``
(along with any getters and setters in Java). ``value`` is just a standard Integer (in Java)/Int (in Kotlin), but
``sender`` and ``recipient`` are of type ``Party``. ``Party`` is a Corda type that represents an entity on the network.

In turn, we've defined ``participants`` as the list of the ``sender`` and ``recipient``.

Finally, we've left ``IOUState``'s contract as ``TemplateContract``. We'll update this once we've defined the
``IOUContract``.

Progress so far
---------------
We now have an ``IOUState`` that can represent IOUs as shared facts on the ledger. As we've seen, shared facts in Corda
are simply JVM classes that implement the ``ContractState`` interface. From there, you can add any properties and
methods you like.

Next, we'll be writing the ``IOUContract`` that controls the evolution of these shared facts over time.