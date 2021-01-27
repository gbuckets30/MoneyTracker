package com.example.firebaseimplementation.activities

import android.os.Bundle
import android.os.Handler
import android.util.Patterns.EMAIL_ADDRESS
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.firebaseimplementation.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_login.cardView
import kotlinx.android.synthetic.main.activity_login.editTextEmail
import kotlinx.android.synthetic.main.activity_login.editTextPassword
import kotlinx.android.synthetic.main.activity_signup.*

class Signup : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var databaseReference: DatabaseReference
    private var listener: ValueEventListener?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()
        databaseReference=FirebaseDatabase.getInstance().reference

    }

    override fun onResume() {
        super.onResume()

        //user clicks on sign up
        cardView.setOnClickListener {
            signUpUser()
        }
    }

    private fun signUpUser() {

        //check if email is entered
        if (editTextEmail.text.toString().isEmpty()) {
            editTextEmail.error = getString(R.string.error_email_empty)
            editTextEmail.requestFocus()
            return
        }

        //check if email is valid
        if (!isEmailValid(editTextEmail.text.toString())) {
            editTextEmail.error=getString(R.string.invalid_email)
            editTextEmail.requestFocus()
            return
        }

        //check if username is entered
        if (editTextUsername.text.toString().isEmpty()) {
            editTextUsername.error = getString(R.string.error_empty_username)
            editTextUsername.requestFocus()
            return
        }

        //check if username is longer than 15 characters
        if(editTextUsername.text.toString().length>15){
            editTextUsername.error=getString(R.string.error_username_too_long)
            editTextUsername.requestFocus()
            return
        }

        //check if username contains special characters

        //check if password is entered
        if (editTextPassword.text.toString().isEmpty()) {
            editTextPassword.error = getString(R.string.error_empty_password)
            editTextPassword.requestFocus()
            return
        }

        //check if cnf password is entered
        if (editTextConfirmPassword.text.toString().isEmpty()) {
            editTextConfirmPassword.error = getString(R.string.error_empty_cnfpassword)
            editTextConfirmPassword.requestFocus()
            return
        }

        //check if password and cnf password match
        if (editTextPassword.text.toString() != editTextConfirmPassword.text.toString()) {
            editTextConfirmPassword.error = getString(R.string.error_passwords_diff)
            editTextConfirmPassword.requestFocus()
            return
        }

        progressBar4?.visibility=View.VISIBLE

        listener=object :ValueEventListener {

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Signup,"Error: ${databaseError.message}",Toast.LENGTH_SHORT).show()
                progressBar4?.visibility=View.GONE
            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {

                //show error if username is already taken
                if(dataSnapshot.hasChild(editTextUsername.text.toString())) {
                    progressBar4?.visibility=View.GONE
                    editTextUsername.error=getString(R.string.error_username_unavailable)
                    editTextUsername.requestFocus()
                }

                //sign up the user otherwise
                else {

                    auth.createUserWithEmailAndPassword(editTextEmail.text.toString(), editTextPassword.text.toString())
                        .addOnCompleteListener(this@Signup) { task ->

                            if(!task.isSuccessful) {
                                Toast.makeText(this@Signup, "Error: ${task.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
                                progressBar4?.visibility=View.GONE
                            }

                            else if (task.isSuccessful) {

                                //display the spinner
                                progressBar4?.visibility=View.VISIBLE

                                //send a verification mail to the email provided
                                sendVerificationEmail()

                                //create a node in database
                                val uid= auth.uid  //user uid
                                val map= HashMap<String,String>()
                                map["email"] = editTextEmail.text.toString()
                                map["username"]=editTextUsername.text.toString()
                                databaseReference.child(getString(R.string.node_users)).child(uid!!).setValue(map)
                                databaseReference.child(getString(R.string.node_usernames)).child(editTextUsername.text.toString()).setValue(uid)

                                //replace '.' in emails to ',' to index using emailID
                                val cleanEmail=editTextEmail.text.toString().replace('.',',')
                                databaseReference.child(getString(R.string.node_emails)).child(cleanEmail).setValue(uid)

                                //sign the user out, so that he can log in AFTER he verifies his email
                                auth.signOut()

                                //close the activity
                                finish()
                            }

                        }
                }
            }
        }

        //check if username is available, if it is, sign up the user
        databaseReference.child(getString(R.string.node_usernames)).addListenerForSingleValueEvent(listener!!)
    }

    private fun sendVerificationEmail() {

        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful)
                    Toast.makeText(this, getString(R.string.cnf_verification_email_sent),Toast.LENGTH_SHORT).show()
            }
    }

    private fun isEmailValid(email: CharSequence?): Boolean {
        return EMAIL_ADDRESS.matcher(email).matches()
    }

    override fun onDestroy() {

        //remove listener if attached
        if (listener!=null)
            databaseReference.child(getString(R.string.node_usernames)).removeEventListener(listener!!)

        super.onDestroy()
    }
}

