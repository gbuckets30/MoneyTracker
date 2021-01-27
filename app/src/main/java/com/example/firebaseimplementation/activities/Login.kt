package com.example.firebaseimplementation.activities

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.firebaseimplementation.R
import com.example.firebaseimplementation.objects.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_login.*

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var databaseReference: DatabaseReference
    private var user: FirebaseUser?=null
    private var isEmailRegistered: Boolean?=null
    private lateinit var listener: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth=FirebaseAuth.getInstance()
        databaseReference=FirebaseDatabase.getInstance().reference
    }

    public override fun onStart() {
        super.onStart()

        // Check if user is signed in (non-null) and update UI accordingly.
        user = auth.currentUser
        updateUI()
    }

    override fun onResume() {
        super.onResume()

        //user clicks on "Login"
        cardView.setOnClickListener {
            loginUser()
        }

        //user clicks on "Signup"
        button.setOnClickListener {

            //redirect to signup page
            startActivity(Intent(this, Signup::class.java))
        }

        //user clicks on "Forgot Password"
        textViewForgotPassword.setOnClickListener {
            //create dialog box
            dialogForgotPassword()
        }
    }

    private fun loginUser() {

        //check if email is empty
        if (editTextEmail.text.toString().isEmpty()) {
            editTextEmail.error = getString(R.string.error_email_empty)
            editTextEmail.requestFocus()
            return
        }

        //check if email is invalid
        if (!isEmailValid(editTextEmail.text.toString())) {
            editTextEmail.error=getString(R.string.invalid_email)
            editTextEmail.requestFocus()
            return
        }

        //check if password is empty
        if (editTextPassword.text.toString().isEmpty()) {
            editTextPassword.error = getString(R.string.error_empty_password)
            editTextPassword.requestFocus()
            return
        }

        progressBar3?.visibility=View.VISIBLE
        //start process of logging user in
        auth.signInWithEmailAndPassword(editTextEmail.text.toString(), editTextPassword.text.toString())
            .addOnCompleteListener(this) { task ->

                //check if it is a registered user
                if (task.isSuccessful) {
                    user = auth.currentUser

                    //check if user is verified
                    //if not, display a toast asking to verify email
                    if(!(user!!.isEmailVerified)) {

                        progressBar3?.visibility=View.GONE
                        Toast.makeText( this,getString(R.string.verify_email),Toast.LENGTH_SHORT).show()

                        //signout the user
                        auth.signOut()
                    }

                    //if user is verified, proceed to log in
                    //log in user
                    else
                        updateUI()
                }

                else {

                    progressBar3?.visibility=View.GONE

                    //show the error that occurred during authentication
                    Toast.makeText(baseContext, getString(R.string.authentication_failed)+ ":${task.exception!!.localizedMessage}",
                        Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun updateUI() {

        //if user is not null, proceed to main activity
        if(user!=null) {

            User.username=null

            val intent=Intent(this, MainActivity::class.java)
            startActivity(intent)

            //also finish this login activity, otherwise pressing back button in main activity will redirect here which in turn
            //redirects to main activity, rendering back button useless in main activity
            finish()
        }
    }

    private fun dialogForgotPassword() {

        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.enter_email))

        val view = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val email = view.findViewById<EditText>(R.id.editTextEnterEmail)
        builder.setView(view)

        builder.setPositiveButton("Reset") { _, _ -> }
        builder.setNegativeButton("Close") { _, _ -> }

        val dialog=builder.create()
        dialog.show()

        //define behaviour on click of positive button
        //done here to not allow dismissal of dialog box when  email is empty or invalid
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

            //reset to null
            isEmailRegistered=null

            //check if entered email is empty
            if (email.text.toString().isEmpty()) {
                email.error=getString(R.string.error_email_empty)
            }


            else {

                //call function to check if email is registered
                checkEmailRegistered(email.text.toString())

                //function that runs every two seconds
                val handler= Handler()

                handler.postDelayed(object : Runnable {

                    override fun run() {

                        //check has not yet been completed
                        if(isEmailRegistered==null) {
                            Toast.makeText(this@Login, getString(R.string.wait_check_email_registered), Toast.LENGTH_SHORT).show()
                            handler.postDelayed(this, 2000)//2 sec delay
                        }

                        //email is not registered
                        else if(isEmailRegistered==false) {
                            Toast.makeText(this@Login, getString(R.string.email_not_registered), Toast.LENGTH_SHORT).show()
                            databaseReference.child(getString(R.string.node_emails)).removeEventListener(listener)
                        }

                        //email is registered
                        else {
                            forgotPassword(email)
                            databaseReference.child(getString(R.string.node_emails)).removeEventListener(listener)
                            dialog.dismiss()
                        }
                    }

                }, 0)
            }
        }
    }

    private fun isEmailValid(email: CharSequence?): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email!!).matches()
    }

    private fun checkEmailRegistered(email: String) {

        //replace '.' with ',' to run a check on database
        val cleanEmail=email.replace('.',',')

        listener=object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@Login, "Error: ${databaseError.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onDataChange(dataSnapshot: DataSnapshot) {
                isEmailRegistered=dataSnapshot.hasChild(cleanEmail)
            }

        }

        databaseReference.child(getString(R.string.node_emails)).addListenerForSingleValueEvent(listener)
    }

    private fun forgotPassword(email: EditText) {

        //send a password reset email
        auth.sendPasswordResetEmail(email.text.toString())
            .addOnCompleteListener { task ->

                //check if email is sent
                if (task.isSuccessful) {
                    Toast.makeText(this,getString(R.string.email_sent),Toast.LENGTH_SHORT).show()
                }
                else
                    Toast.makeText(this,getString(R.string.error),Toast.LENGTH_SHORT).show()

            }

    }

}
