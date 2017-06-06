.. highlight:: kotlin
.. raw:: html

   <script type="text/javascript" src="_static/jquery.js"></script>
   <script type="text/javascript" src="_static/codesets.js"></script>

The CorDapp Template
====================

When building a new CorDapp, you’ll generally want to start from the CorDapp Template. The Cordapp Template allows
you to quickly define a new CorDapp and spin it up on test network of dummy nodes to evaluate its functionality.

The template packages up all the software you need to run the test network and your CorDapp. There’s no need to
download any additional software.

Downloading the template
------------------------
Open a terminal window in the directory where you want to download the CorDapp template, and do the following:

.. code-block:: text

    # Clone the template from GitHub:
    git clone https://github.com/corda/cordapp-template.git & cd cordapp-template

    # Retrieve a list of the stable Milestone branches using:
    git branch -a --list *release-M*

    # Check out the Milestone branch with the latest version number:
    git checkout release-M[*version number*] & git pull

Template structure
------------------
There's a lot of boilerplate in the template, but most of it can be left as-is for now. For our CorDapp, we will only
be modifying the five files listed below.

Note that you can write the CorDapp in either Java or Kotlin. We will be providing the code in both languages
throughout.

.. container:: codeset

    .. code-block:: java

        // Our components of our CorDapp:
          // 1. The state
        java-source/src/main/java/com/template/state/TemplateState.java

          // 2. The contract
        java-source/src/main/java/com/template/contract/TemplateContract.java

          // 3. The flow
        java-source/src/main/java/com/template/flow/TemplateFlow.java

        // Tests for our contract and flow:
          // 1. The contract tests
        java-source/src/test/java/com/template/contract/ContractTests.java

          // 2. The flow tests
        java-source/src/test/java/com/template/flow/FlowTests.java

    .. code-block:: kotlin

        // Our components of our CorDapp:
          // 1. The state
        kotlin-source/src/main/kotlin/com/template/state/TemplateState.kt

          // 2. The contract
        kotlin-source/src/main/kotlin/com/template/contract/TemplateContract.kt

          // 3. The flow
        kotlin-source/src/main/kotlin/com/template/flow/TemplateFlow.kt

        // Tests for our contract and flow:
          // 1. The contract tests
        kotlin-source/src/test/kotlin/com/template/contract/ContractTests.kt

          // 2. The flow tests
        kotlin-source/src/test/kotlin/com/template/flow/FlowTests.kt