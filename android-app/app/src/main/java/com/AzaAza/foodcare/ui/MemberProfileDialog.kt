package com.AzaAza.foodcare.ui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.AzaAza.foodcare.R
import com.bumptech.glide.Glide

class MemberProfileDialog(
    private val name: String,
    private val email: String,
    private val profileUrl: String?,
    private val isOwner: Boolean
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = requireActivity().layoutInflater.inflate(R.layout.member_profile_dialog, null)
        val profileImage = view.findViewById<ImageView>(R.id.profileImage)
        val textName = view.findViewById<TextView>(R.id.textName)
        val textEmail = view.findViewById<TextView>(R.id.textEmail)
        val textRole = view.findViewById<TextView>(R.id.textRole)

        textName.text = name
        textEmail.text = email
        textRole.text = if (isOwner) "대표" else "구성원"

        if (!profileUrl.isNullOrBlank()) {
            Glide.with(requireContext())
                .load("https://foodcare-69ae76eec1bf.herokuapp.com${profileUrl}")
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .into(profileImage)
        } else {
            profileImage.setImageResource(R.drawable.ic_profile)
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setPositiveButton("닫기", null)
            .create()
    }
}
