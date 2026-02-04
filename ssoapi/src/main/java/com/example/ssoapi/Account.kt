package com.example.ssoapi

import android.os.Parcel
import android.os.Parcelable

/**
 * Account data class that represents a user account.
 * This class is Parcelable so it can be passed via AIDL.
 * 
 * Fields match the service's Account class:
 * - email: String
 * - name: String
 * - isActive: Boolean
 */
data class Account(
    val email: String = "",
    val name: String = "",
    val isActive: Boolean = false
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(email)
        parcel.writeString(name)
        parcel.writeByte(if (isActive) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Account> {
        override fun createFromParcel(parcel: Parcel): Account {
            return Account(parcel)
        }

        override fun newArray(size: Int): Array<Account?> {
            return arrayOfNulls(size)
        }
    }
}
