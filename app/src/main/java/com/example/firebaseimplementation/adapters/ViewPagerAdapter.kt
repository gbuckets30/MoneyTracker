package com.example.firebaseimplementation.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.firebaseimplementation.fragments.FragmentPendingTransactions
import com.example.firebaseimplementation.fragments.FragmentVerifiedTransactions

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {


    override fun createFragment(position: Int): Fragment {

        return if(position==0)
            FragmentVerifiedTransactions()
        else
            FragmentPendingTransactions()
    }

    override fun getItemCount(): Int {
        return 2
    }
}