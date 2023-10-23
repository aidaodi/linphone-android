/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.meetings.model

import androidx.annotation.WorkerThread
import androidx.lifecycle.MutableLiveData
import org.linphone.core.ConferenceInfo
import org.linphone.core.Participant
import org.linphone.utils.TimestampUtils

class MeetingModel @WorkerThread constructor(conferenceInfo: ConferenceInfo) {
    val id = conferenceInfo.uri?.asStringUriOnly() ?: ""

    private val timestamp = conferenceInfo.dateTime

    val day = TimestampUtils.dayOfWeek(timestamp)

    val dayNumber = TimestampUtils.dayOfMonth(timestamp)

    val month = TimestampUtils.month(timestamp)

    val isToday = TimestampUtils.isToday(timestamp)

    private val startTime = TimestampUtils.timeToString(timestamp)

    private val endTime = TimestampUtils.timeToString(timestamp + (conferenceInfo.duration * 60))

    val time = "$startTime - $endTime"

    val isBroadcast = MutableLiveData<Boolean>()

    val subject = MutableLiveData<String>()

    init {
        subject.postValue(conferenceInfo.subject)

        var allSpeaker = true
        for (participant in conferenceInfo.participantInfos) {
            if (participant.role == Participant.Role.Listener) {
                allSpeaker = false
                break
            }
        }

        isBroadcast.postValue(!allSpeaker)
    }
}