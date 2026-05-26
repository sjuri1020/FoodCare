package com.AzaAza.foodcare.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.AzaAza.foodcare.R
import com.AzaAza.foodcare.models.MemberResponse
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class MemberAdapter(
    private val memberList: List<MemberResponse>,
    private val onMemberClick: (MemberResponse) -> Unit,
    private val isManageMode: Boolean,
    private val onKickClick: (MemberResponse) -> Unit,
    private val onLeaveClick: (MemberResponse) -> Unit,
    private val onCancelInviteClick: (MemberResponse) -> Unit,
    private val ownerId: Int,
    private val myUserId: Int
) : RecyclerView.Adapter<MemberAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profileImage: ImageView = view.findViewById(R.id.memberPhoto)
        val nameText: TextView = view.findViewById(R.id.memberName)
        val roleText: TextView = view.findViewById(R.id.memberRole)
        val btnMemberAction: Button = view.findViewById(R.id.btnMemberAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.member_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = memberList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = memberList[position]
        Log.d(
            "PROFILE_TEST",
            "username=${member.username}, profile_image_url=${member.profileImageUrl}"
        )

        holder.nameText.text = member.username
        // 역할 명칭
        holder.roleText.text = when {
            member.isOwner -> "대표"
            member.status == "pending" && member.id == myUserId -> "나 - 초대받음"
            member.status == "pending" -> "초대함"
            member.id == myUserId -> "나 - 구성원"
            else -> "구성원"
        }

        holder.btnMemberAction.visibility = View.GONE
        // 프로필 사진 세팅
        if (!member.profileImageUrl.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load("https://foodcare-69ae76eec1bf.herokuapp.com" + member.profileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(holder.profileImage)
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_profile)
        }

        if (isManageMode) {
            when {
                // 대표 자기자신은 버튼 안 뜸
                member.isOwner && member.id == myUserId -> holder.btnMemberAction.visibility = View.GONE

                // 초대 상태 (pending)
                member.status == "pending" -> {
                    holder.btnMemberAction.text = if (member.id == myUserId) "나가기" else "초대 취소"
                    holder.btnMemberAction.visibility = View.VISIBLE
                    holder.btnMemberAction.setOnClickListener {
                        if (member.id == myUserId) onLeaveClick(member)
                        else onCancelInviteClick(member)
                    }
                }

                // accepted 상태 - 구성원: 대표가 추방, 본인은 나가기
                member.status == "accepted" && !member.isOwner -> {
                    holder.btnMemberAction.text = if (member.id == myUserId) "나가기" else "추방"
                    holder.btnMemberAction.visibility = View.VISIBLE
                    holder.btnMemberAction.setOnClickListener {
                        if (member.id == myUserId) onLeaveClick(member)
                        else onKickClick(member)
                    }
                }

                // 기타 경우 버튼 없음
                else -> holder.btnMemberAction.visibility = View.GONE
            }
        } else {
            holder.btnMemberAction.visibility = View.GONE
        }

        // 기타 프로필, 클릭 등은 동일하게 처리
        holder.nameText.text = member.username
        if (!member.profileImageUrl.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load("https://foodcare-69ae76eec1bf.herokuapp.com" + member.profileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(holder.profileImage)
        } else {
            holder.profileImage.setImageResource(R.drawable.ic_profile)
        }
        holder.itemView.setOnClickListener { onMemberClick(member) }
    }
}
