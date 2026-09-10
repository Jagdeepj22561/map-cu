package com.example.maps123.utils

import com.example.maps123.data.local.UserEntity
import com.example.maps123.data.local.FriendRequestEntity
import com.example.shared.model.PureUser
import com.example.shared.model.PureFriendRequest

fun UserEntity.toPureUser() = PureUser(
    uid = uid,
    email = email,
    name = name,
    phoneNumber = phoneNumber,
    year = year,
    semester = semester,
    course = course,
    dob = dob,
    profilePicUrl = profilePicUrl,
    lastUpdated = lastUpdated
)

fun FriendRequestEntity.toPureFriendRequest() = PureFriendRequest(
    requestId = requestId,
    senderId = senderId,
    senderName = senderName,
    senderEmail = senderEmail,
    receiverId = receiverId,
    status = status,
    timestamp = timestamp
)
