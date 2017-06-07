Introduction
============

By this point, :doc:`your dev environment is set up <getting-set-up>`, and you've run
:doc:`your first CorDapp <tutorial-cordapp>` and possibly :doc:`some demos <running-the-demos>`. What comes next?

Corda is a platform for the development of *CorDapps* (Corda Distributed Applications). CorDapps are installed on
Corda nodes to give them the ability to conduct some new process – from issuing a debt instrument to making a restaurant
booking.

Each CorDapp has at least three core elements:
-	**States** – the shared facts that will be agreed upon and stored on the ledger
-	**Contracts** – the rules governing how these states can evolve over time
-	**Flows** – the step-by-step process for carrying out a ledger update

Our IOU CorDapp
---------------
Corda excels for use-cases where a small set of parties want to agree on a fact without having to share this fact with
everyone else on the network. Our CorDapp will take advantage of this functionality to model IOUs on the ledger. An
IOU – short for “I Owe yoU” – records the fact that one person owes another a given amount of money. Corda’s
architecture will allow us to share this (potentially sensitive!) information on a need-to-know basis only.

As we said, each CorDapp includes definitions for at least one state, one contract, and one flow. Our IOU CorDapp
will have the following elements:

* The states will be IOUStates, each instance of which represents an IOU. We can visualize an IOUState as follows:

  .. image:: resources/tutorial-state.png
     :scale: 25%
     :align: center

* Our contract will be the IOUContract, which will impose rules on how IOUs can evolve over time. Our contract will
  only allow nodes to create new IOUs - transferring existing IOUs or paying off an IOU with cash will not be allowed.
  We can easily extend our CorDapp to handle these use-cases later on.

* Our flow will be the IOUFlow, allowing two nodes to communicate and agree on the creation of a new IOU on the
  ledger. In traditional distributed ledger systems, where all data is broadcast to every network participant, you
  don’t even think about this step – you simply package up your ledger update and send it out into the world. In
  Corda, on the other hand, we use flows to carefully control who sees what during the process of agreeing a
  ledger update.

  The IOUFlow will have the following steps:

  $[diagram of the IOUFlow]