package com.example.firebaseimplementation.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.firebaseimplementation.R
import com.example.firebaseimplementation.objects.User
import com.example.firebaseimplementation.adapters.ViewPagerAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var databaseReference: DatabaseReference
    private lateinit var listener: ValueEventListener
    private var uid:String?=null

    var mEmail:String?=null //current user email
    var mUsername:String?=null //current user username

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth=FirebaseAuth.getInstance()

        //set custom toolbar title
        toolbar.title=getString(R.string.money_tracker)
        //set toolbar as your action bar
        setSupportActionBar(toolbar)

        //set the view pager adapter
        viewPager.adapter= ViewPagerAdapter(this)

        //tabs.setupWithViewPager(viewPager)
        TabLayoutMediator(tabs, viewPager) { tab, position ->
            if(position==0)
                tab.text="Verified Transactions"
            else
                tab.text="Pending Transactions"
        }.attach()
        //gather user info
        findUserInfo()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        //define actions for various item selections
        when(item.itemId) {
            R.id.signout -> signUserOut() //signs user out
            R.id.user -> displayUserInfo() //displays user info
        }

        return super.onOptionsItemSelected(item)
    }

    private fun findUserInfo() {

        //gather user email and username from firebase database
        databaseReference=FirebaseDatabase.getInstance().reference
        uid=auth.uid //user uid

        listener=object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error: ${databaseError.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {

                //extract the user info
                mEmail=dataSnapshot.child(getString(R.string.node_email)).getValue(String::class.java)
                mUsername=dataSnapshot.child(getString(R.string.node_username)).getValue(String::class.java)

                setUserInfo(mUsername) //saves the user information found for further use by fragments
            }
        }

         databaseReference.child(getString(R.string.node_users)).child(uid!!).addListenerForSingleValueEvent(listener)
    }

    private fun setUserInfo(mUsername: String?) {
        User.username=mUsername
    }

    private fun displayUserInfo() {

        //build an alert dialog box to display user info
        val builder=AlertDialog.Builder(this)

        //extracting dialog box views
        val view=LayoutInflater.from(this).inflate(R.layout.dialog_user_info, null)
        val email=view.findViewById<TextView>(R.id.textViewEmail)
        val user=view.findViewById<TextView>(R.id.textViewUsername)

        email.text=mEmail
        user.text=mUsername

        builder.setView(view)

        //firebase has not yet sent data
        if(mEmail==null)
            Toast.makeText(this, getString(R.string.wait_loading_information), Toast.LENGTH_SHORT).show()

        //data has been received
        else
            builder.show()
    }

    private fun signUserOut() {
        auth.signOut()

        val intent=Intent(this, Login::class.java)
        startActivity(intent)

        //finish this activity
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()

        finish()
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.i("onDestroy", "yes")

        //remove the single value event listener
        databaseReference.child(getString(R.string.node_users)).child(uid!!).removeEventListener(listener)
    }
}
