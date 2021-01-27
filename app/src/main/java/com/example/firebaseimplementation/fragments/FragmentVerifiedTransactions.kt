package com.example.firebaseimplementation.fragments

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.firebaseimplementation.dataclasses.Transaction
import com.example.firebaseimplementation.R
import com.example.firebaseimplementation.adapters.FragmentsAdapter
import com.example.firebaseimplementation.objects.User
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.fragment_verified_transactions.*

/**
 * A simple [Fragment] subclass.
 */
class FragmentVerifiedTransactions : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var databaseReference: DatabaseReference
    private var fab: FloatingActionButton? = null  //FAB to open a dialog box to allow user to add a new transaction
    private var isUsernameValid: Boolean? = null  //boolean for checking if username entered in new transaction is valid
    private lateinit var adapter: FragmentsAdapter //adapter for recycler view
    var transactions = mutableListOf<Transaction>() //list of verified transactions
    private lateinit var listenerCheckUsernameExists: ValueEventListener
    private lateinit var listenerFetchData: ValueEventListener

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_verified_transactions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        databaseReference = FirebaseDatabase.getInstance().reference

        //find FAB
        fab = view.findViewById(R.id.fab_new_transaction)
    }

    override fun onResume() {
        super.onResume()

        var senderUsername=User.username

        //pop up a dialog box if user wants to add a transaction
        fab?.setOnClickListener {

            //open a dialog to allow user to enter new transaction details
            dialogAddTransaction()
        }

        //run a check to see if we got the sender data every 2 seconds
        val handler=Handler()

        handler.postDelayed(object : Runnable {

            override fun run() {

                //if data is not yet received, check back after two seconds
                if(senderUsername==null) {
                    senderUsername=User.username
                    handler.postDelayed(this, 2000)  //2 sec delay
                }

                //if data is received, fetch user data for pending transactions
                else {

                    if(progressBar!=null)
                        progressBar.visibility=View.GONE

                    fetchData(senderUsername!!)
                }
            }
        },0)

        //create adapter for recycler view
        adapter= FragmentsAdapter(context!!, transactions) { transactionID->

            //lambda function to determine what happens when an item is clicked
            dialogTransactionDetails(transactionID)
        }

        //set the adapter for recycler view
        recyclerView.adapter=adapter

        //set the layout manager for recycler view
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun dialogAddTransaction() {

        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_new_transaction, null)
        builder.setView(view)

        //add a node in pending transactions tab
        builder.setPositiveButton("Request") { _, _ -> }

        builder.setNegativeButton("Cancel") { _, _ -> }

        val dialog = builder.create()
        dialog.show()

        //define behaviour on click of positive button
        //done here to not allow dismissal of dialog box when username is empty or invalid
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

            //reset the username validity
            isUsernameValid=null

            //get the username entered
            val username = view.findViewById<EditText>(R.id.editTextRecipientUsername)
            //get the amount entered
            val amount= view.findViewById<EditText>(R.id.editTextAmount)

            //check if the username is empty
            if (username.text.toString().isEmpty()) {
                username.error = "Please enter a username"
                username.requestFocus()
            }

            //check if username is not the user himself
            else if(username.text.toString()==User.username) {
                username.error= "Cannot request a transaction to yourself"
                username.requestFocus()
            }

            //check if the amount is empty
            else if(amount.text.toString().isEmpty()) {
                amount.error= "Please enter a valid amount"
                amount.requestFocus()
            }

            else {
                checkUsernameExists(username.text.toString())

                //function that gets called every 2 seconds
                val handler = Handler()
                handler.postDelayed(object : Runnable {
                    override fun run() {

                        //first check if firebase has fetched data for username validity
                        if (isUsernameValid == null) {
                            Toast.makeText(
                                context,
                                "Please wait while we confirm identity of receiving user",
                                Toast.LENGTH_LONG
                            ).show()
                            handler.postDelayed(this, 2000)//2 sec delay
                        }

                        //check if username is valid
                        else if (isUsernameValid==false) {
                            username.error = "Username invalid"
                            username.requestFocus()

                            //remove listener
                            databaseReference.child(getString(R.string.node_usernames)).removeEventListener(listenerCheckUsernameExists)
                        }

                        //add transaction to database
                        else {
                            Toast.makeText(context, "Transaction Requested", Toast.LENGTH_SHORT).show()
                            addTransaction(view)
                            dialog.dismiss()

                            //remove listener
                            databaseReference.child(getString(R.string.node_usernames)).removeEventListener(listenerCheckUsernameExists)
                        }

                    }
                }, 0)

            }

        }

    }

    private fun checkUsernameExists(username: String) {

        listenerCheckUsernameExists=object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(context, "Error: ${databaseError.message}", Toast.LENGTH_SHORT)
                    .show()
            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                isUsernameValid = dataSnapshot.hasChild(username)
            }
        }

        if(isAdded) {
            databaseReference.child(getString(R.string.node_usernames))
                .addListenerForSingleValueEvent(listenerCheckUsernameExists)
        }
    }

    private fun addTransaction(view: View) {

        var senderUsername = User.username

        if (senderUsername != null)
            createNodes(senderUsername, view)

        //if firebase has not yet returned current user's username
        else
            Toast.makeText(context, "Some error occurred. Please try again in a moment", Toast.LENGTH_SHORT).show()

    }

    private fun createNodes(senderUsername: String?, view: View) {

        val username = view.findViewById<EditText>(R.id.editTextRecipientUsername)
        val amount = view.findViewById<EditText>(R.id.editTextAmount)
        val description = view.findViewById<EditText>(R.id.editTextDescription)

        //note that below transactions don't have a transaction ID yet

        //transaction to be added in sender node
        val transactionSender =
            Transaction(
                username.text.toString(),
                "Sent",
                amount.text.toString().toInt(),
                description.text.toString(),
                null
            )

        //transaction to be added in receiver node
        val transactionReceiver =
            Transaction(
                senderUsername!!,
                "Received",
                amount.text.toString().toInt(),
                description.text.toString(),
                null
            )


        //generate a unique key for this transaction. saving sender's and receiver's transactions under same key will help in deleting/accepting/rejecting
        val key=databaseReference.child(getString(R.string.node_pending_transactions)).child(senderUsername).push().key

        //save sender's transaction under that key
        databaseReference.child(getString(R.string.node_pending_transactions)).child(senderUsername).child(key!!)
            .setValue(transactionSender)

        //save receiver's transaction under that key
        databaseReference.child(getString(R.string.node_pending_transactions)).child(username.text.toString()).child(key)
            .setValue(transactionReceiver)

    }

    private fun fetchData(senderUsername: String) {

        listenerFetchData=object : ValueEventListener {

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(context, "Error: ${databaseError.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {

                val iterator = dataSnapshot.children

                //clear the list, to avoid duplicate entries and correspond to deleted data
                transactions.clear()

                //iterate through all the children of current data snapshot to add transactions
                iterator.forEach {

                    //note that the transactions added to the list of transactions all have a transaction ID
                    val transaction= Transaction(
                        it.child(getString(R.string.node_username)).getValue(String::class.java),
                        it.child(getString(R.string.node_type)).getValue(String::class.java),
                        it.child(getString(R.string.node_amount)).getValue(Int::class.java),
                        it.child(getString(R.string.node_description)).getValue(String::class.java),
                        it.key
                    )

                    //add transaction to the list
                    addTransactionToList(transaction)
                }
            }

        }

        if(isAdded) {
            databaseReference.child(getString(R.string.node_verified_transactions)).child(senderUsername)
                .addValueEventListener(listenerFetchData)
        }
    }

    private fun addTransactionToList(transaction: Transaction) {

        //add received transaction to list
        transactions.add(transaction)

        //notify the adapter about the data changed
        adapter.notifyDataSetChanged()
    }

    private fun dialogTransactionDetails(transactionID: String) {

        val builder = androidx.appcompat.app.AlertDialog.Builder(context!!)

        var transaction:Transaction?=null

        //find the concerned transaction
        for(item in transactions) {
            if(item.transactionID==transactionID) {
                transaction=item
            }
        }

        val view = layoutInflater.inflate(R.layout.dialog_transaction_details, null)

        val mUsername = view.findViewById<TextView>(R.id.textViewUsername)
        val mAmount = view.findViewById<TextView>(R.id.textViewAmount)
        val mType = view.findViewById<TextView>(R.id.textViewTransactionType)
        val mDescription = view.findViewById<TextView>(R.id.textViewDescription)

        mUsername.text = transaction?.username
        mAmount.text = transaction?.amount.toString()
        mType.text = transaction?.type
        mDescription.text = transaction?.description

        if(transaction?.type=="Sent") {
            builder.setPositiveButton("Mark as Complete") { _,_ ->

                //change type to Sent/Completed in current user
                databaseReference.child(getString(R.string.node_verified_transactions)).child(User.username!!).child(transactionID).child("type")
                    .setValue("Sent/Completed")

                //change type to Received/Completed
                databaseReference.child(getString(R.string.node_verified_transactions)).child(transaction.username!!).child(transactionID).child("type")
                    .setValue("Received/Completed")
            }
        }

        if(transaction?.type=="Sent/Completed" || transaction?.type=="Received/Completed") {

            //allow user to delete such transactions
            builder.setPositiveButton("Delete Transaction") {_,_ ->
                deleteTransaction(transactionID)
            }
        }

        builder.setView(view)
        builder.show()

    }

    private fun deleteTransaction(transactionID: String) {
        databaseReference.child(getString(R.string.node_verified_transactions)).child(User.username!!).child(transactionID).setValue(null)
    }


    override fun onPause() {
        super.onPause()

        Log.i("onVerifiedPause", "yes")

        if(User.username!=null && this::listenerFetchData.isInitialized) {
            databaseReference.child(getString(R.string.node_verified_transactions)).child(User.username!!).removeEventListener(listenerFetchData)
            Log.i("onVerifiedPause", "listener removed")
        }

    }
}