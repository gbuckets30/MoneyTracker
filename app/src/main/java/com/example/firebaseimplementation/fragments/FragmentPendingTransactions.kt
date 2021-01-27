package com.example.firebaseimplementation.fragments

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.firebaseimplementation.adapters.FragmentsAdapter
import com.example.firebaseimplementation.dataclasses.Transaction
import com.example.firebaseimplementation.R
import com.example.firebaseimplementation.objects.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.fragment_pending_transactions.*

/**
 * A simple [Fragment] subclass.
 */
class FragmentPendingTransactions() : Fragment() {


    private lateinit var auth: FirebaseAuth
    private lateinit var databaseReference: DatabaseReference
    var transactions = mutableListOf<Transaction>()
    private lateinit var adapter: FragmentsAdapter
    private lateinit var listener: ValueEventListener

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_pending_transactions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)

        auth=FirebaseAuth.getInstance()
        databaseReference=FirebaseDatabase.getInstance().reference

    }

    override fun onResume() {

        super.onResume()

        var senderUsername=User.username

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
                    if(progressBar2!=null)
                        progressBar2.visibility=View.GONE
                    fetchData(senderUsername!!)
                }

            }
        },0)

        //create adapter for recycler view
        adapter=FragmentsAdapter(context!!, transactions) { transactionID->

            //lambda function to determine what happens when an item is clicked
            dialogTransactionDetails(transactionID)
        }

        //set the adapter for recycler view
        recyclerView.adapter=adapter

        //set the layout manager for recycler view
        recyclerView.layoutManager = LinearLayoutManager(context)


    }

    private fun fetchData(senderUsername: String) {

        listener=object : ValueEventListener {

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
            databaseReference.child(getString(R.string.node_pending_transactions)).child(senderUsername)
                .addValueEventListener(listener)
        }

    }

    private fun addTransactionToList(transaction: Transaction) {

        //add received transaction to list
        transactions.add(transaction)

        //notify the adapter about the data changed
        adapter.notifyDataSetChanged()
    }

    private fun dialogTransactionDetails(transactionID: String) {

        val builder = AlertDialog.Builder(context!!)

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

        if(transaction?.type=="Received") {
            builder.setPositiveButton("Accept") { _,_ ->
                acceptPendingTransaction(transaction, transactionID)
            }

            builder.setNegativeButton("Reject") { _,_ ->
                rejectPendingTransaction(transaction)
            }
        }

        else if(transaction?.type=="Received/Rejected") {

            //allow the user to delete received/rejected transactions
            builder.setPositiveButton("Delete Transaction") {_,_ ->
                deleteTransaction(transactionID)
            }
        }

        else if (transaction?.type=="Sent/Rejected") {

            //allow user to request the same transaction again
            builder.setPositiveButton("Request Again") {_,_->
                requestTransactionAgain(transaction, transactionID)
            }

            //allow user to delete the transaction
            builder.setNegativeButton("Delete Transaction") {_,_ ->
                deleteTransaction(transactionID)
            }
        }

        builder.setView(view)
        builder.show()

    }

    private fun requestTransactionAgain(transaction: Transaction, transactionID: String) {

        //change status to Sent in current user
        databaseReference.child(getString(R.string.node_pending_transactions)).child(User.username!!).child(transactionID).child(getString(R.string.node_type))
            .setValue("Sent")

        //save other user's username first
        val username=transaction.username

        //modify the transaction for the other user

        //change type of transaction to "received", for other user
        transaction.type="Received"
        //change transaction ID to null
        transaction.transactionID=null
        //change transaction username
        transaction.username=User.username!!

        //request transaction to other user
        databaseReference.child(getString(R.string.node_pending_transactions)).child(username!!).child(transactionID)
            .setValue(transaction)
    }

    private fun deleteTransaction(transactionID: String) {
        databaseReference.child(getString(R.string.node_pending_transactions)).child(User.username!!).child(transactionID).setValue(null)
    }

    private fun acceptPendingTransaction(transaction: Transaction, transactionID: String) {

        val username=User.username //current user username

        //transaction object for corresponding user
        var transactionOther=Transaction(username, "Sent", transaction.amount, transaction.description, null) //no transactionID

        //remove the transactionID component from the transaction as of now
        transaction.transactionID=null

        //note that the below added transactions again don't have a transactionID, yet

        //remove transaction from current user's pending transactions using transactionID
        databaseReference.child(getString(R.string.node_pending_transactions)).child(username!!).child(transactionID).setValue(null)

        //using same transactionID, remove transaction from other corresponding user's pending transactions
        databaseReference.child(getString(R.string.node_pending_transactions)).child(transaction.username!!).child(transactionID).setValue(null)

        //generate key for this transaction. can't use the older key otherwise order will be messed up in verified transactions
        val key=databaseReference.child(getString(R.string.node_verified_transactions)).child(username).push().key

        //add transaction to current user's verified transactions
        databaseReference.child(getString(R.string.node_verified_transactions)).child(username).child(key!!).setValue(transaction)

        //using same transactionID, add transaction to other user's verified transactions
        databaseReference.child(getString(R.string.node_verified_transactions)).child(transaction.username!!).child(key).setValue(transactionOther)
    }

    private fun rejectPendingTransaction(transaction: Transaction) {

        //change 'type' in sender's transaction to sent/rejected using transactionID
        databaseReference.child(getString(R.string.node_pending_transactions)).child(User.username!!).child(transaction.transactionID!!).child("type")
            .setValue("Received/Rejected")

        //using same transactionID, change type in receiver's transaction to received/rejected
        databaseReference.child(getString(R.string.node_pending_transactions)).child(transaction.username!!).child(transaction.transactionID!!).child("type")
            .setValue("Sent/Rejected")

        adapter.notifyDataSetChanged()
    }


    override fun onPause() {
        super.onPause()

        Log.i("onPendingPause" , "yes")

        //remove the listener if it has been attached
        if(User.username!=null && this::listener.isInitialized) {
            Log.i("onPendingPause", "listener removed")

            databaseReference.child(getString(R.string.node_pending_transactions)).child(User.username!!).removeEventListener(listener)
        }

    }

}
