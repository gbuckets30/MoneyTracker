package com.example.firebaseimplementation.dataclasses

data class Transaction (
    var username: String?,
    var type: String?,
    val amount: Int?,
    val description: String?,
    var transactionID: String?
)