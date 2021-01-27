package com.example.firebaseimplementation.adapters

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.firebaseimplementation.R
import com.example.firebaseimplementation.dataclasses.Transaction
import org.w3c.dom.Text

class FragmentsAdapter(private val context: Context, private var transactions: List<Transaction>, val onItemClick: (String) -> Unit) : RecyclerView.Adapter<FragmentsAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view=LayoutInflater.from(context).inflate(R.layout.card_view_pending_transactions,parent,false)
        return MyViewHolder(view, onItemClick)
    }

    override fun getItemCount(): Int {
        return transactions.count()
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {

        if(transactions.count()>0)
            holder!!.bindData(transactions[transactions.count()-position-1])
    }

    inner class MyViewHolder(itemView:View, onItemClick: (String) -> Unit): RecyclerView.ViewHolder(itemView) {
        val username= itemView.findViewById<TextView>(R.id.textViewUsername)
        val amount=itemView.findViewById<TextView>(R.id.textViewAmount)
        val type=itemView.findViewById<TextView>(R.id.textViewTransactionType)

        fun bindData(t: Transaction) {

            //reset text color every time
            type.setTextColor(Color.parseColor("#696969"))  //color for every transaction other than sent and received is default grey

            username.text=t.username
            amount.text=t.amount.toString()
            type.text=t.type

            //color for sent transaction is red
            if(t.type=="Sent") {
                type.setTextColor(Color.parseColor("#90EE90"))
            }
            //color for received transaction is green
            else if(t.type=="Received") {
                type.setTextColor(Color.parseColor("#FF0000"))
            }

            itemView.setOnClickListener {
                onItemClick(t.transactionID.toString())
            }
        }
    }
}