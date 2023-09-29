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
package org.linphone.ui.call.viewmodel

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.Locale
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.core.Address
import org.linphone.core.Alert
import org.linphone.core.AlertListenerStub
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.CallListenerStub
import org.linphone.core.ChatRoom.SecurityLevel
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.MediaDirection
import org.linphone.core.MediaEncryption
import org.linphone.core.tools.Log
import org.linphone.ui.call.model.AudioDeviceModel
import org.linphone.ui.main.contacts.model.ContactAvatarModel
import org.linphone.ui.main.history.model.NumpadModel
import org.linphone.utils.AppUtils
import org.linphone.utils.AudioRouteUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class CurrentCallViewModel @UiThread constructor() : ViewModel() {
    companion object {
        private const val TAG = "[Current Call ViewModel]"

        // Keys are hardcoded in SDK
        private const val ALERT_NETWORK_TYPE_KEY = "network-type"
        private const val ALERT_NETWORK_TYPE_WIFI = "wifi"
        private const val ALERT_NETWORK_TYPE_CELLULAR = "mobile"
    }

    val contact = MutableLiveData<ContactAvatarModel>()

    val displayedName = MutableLiveData<String>()

    val displayedAddress = MutableLiveData<String>()

    val isVideoEnabled = MutableLiveData<Boolean>()

    val showSwitchCamera = MutableLiveData<Boolean>()

    val isOutgoing = MutableLiveData<Boolean>()

    val isRecording = MutableLiveData<Boolean>()

    val isMicrophoneMuted = MutableLiveData<Boolean>()

    val isSpeakerEnabled = MutableLiveData<Boolean>()

    val isHeadsetEnabled = MutableLiveData<Boolean>()

    val isBluetoothEnabled = MutableLiveData<Boolean>()

    val fullScreenMode = MutableLiveData<Boolean>()

    val pipMode = MutableLiveData<Boolean>()

    val halfOpenedFolded = MutableLiveData<Boolean>()

    val incomingCallTitle: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    // To synchronize chronometers in UI
    val callDuration = MutableLiveData<Int>()

    val showAudioDevicesListEvent: MutableLiveData<Event<ArrayList<AudioDeviceModel>>> by lazy {
        MutableLiveData<Event<ArrayList<AudioDeviceModel>>>()
    }

    // ZRTP related

    val isRemoteDeviceTrusted = MutableLiveData<Boolean>()

    val showZrtpSasDialogEvent: MutableLiveData<Event<Pair<String, String>>> by lazy {
        MutableLiveData<Event<Pair<String, String>>>()
    }

    // Extras actions

    val isActionsMenuExpanded = MutableLiveData<Boolean>()

    val toggleExtraActionsBottomSheetEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val showNumpadBottomSheetEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    val transferInProgressEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val transferFailedEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val numpadModel: NumpadModel

    val appendDigitToSearchBarEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    val removedCharacterAtCurrentPositionEvent: MutableLiveData<Event<Boolean>> by lazy {
        MutableLiveData<Event<Boolean>>()
    }

    // Alerts related

    val showLowWifiSignalEvent = MutableLiveData<Event<Boolean>>()

    val showLowCellularSignalEvent = MutableLiveData<Event<Boolean>>()

    private lateinit var currentCall: Call

    private val callListener = object : CallListenerStub() {
        @WorkerThread
        override fun onEncryptionChanged(call: Call, on: Boolean, authenticationToken: String?) {
            updateEncryption()
        }

        @WorkerThread
        override fun onStateChanged(call: Call, state: Call.State, message: String) {
            Log.i("$TAG Call [${call.remoteAddress.asStringUriOnly()}] state changed [$state]")
            if (LinphoneUtils.isCallOutgoing(call.state)) {
                isVideoEnabled.postValue(call.params.isVideoEnabled)
            } else if (LinphoneUtils.isCallEnding(call.state)) {
                // If current call is being terminated but there is at least one other call, switch
                val core = call.core
                val callsCount = core.callsNb
                Log.i(
                    "$TAG Call is being ended, check for another current call (currently [$callsCount] calls)"
                )
                if (callsCount > 0) {
                    val newCurrentCall = core.currentCall ?: core.calls.firstOrNull()
                    if (newCurrentCall != null) {
                        Log.i(
                            "$TAG From now on current call will be [${newCurrentCall.remoteAddress.asStringUriOnly()}]"
                        )
                        configureCall(newCurrentCall)
                    } else {
                        Log.e("$TAG Failed to get a valid call to display!")
                    }
                }
            } else {
                val videoEnabled = call.currentParams.isVideoEnabled
                if (videoEnabled && isVideoEnabled.value == false) {
                    if (corePreferences.routeAudioToSpeakerWhenVideoIsEnabled) {
                        Log.i("$TAG Video is now enabled, routing audio to speaker")
                        AudioRouteUtils.routeAudioToSpeaker(call)
                    }
                }
                isVideoEnabled.postValue(videoEnabled)

                // Toggle full screen OFF when remote disables video
                if (!videoEnabled && fullScreenMode.value == true) {
                    Log.w("$TAG Video is not longer enabled, leaving full screen mode")
                    fullScreenMode.postValue(false)
                }
            }
        }

        @WorkerThread
        override fun onAudioDeviceChanged(call: Call, audioDevice: AudioDevice) {
            Log.i("$TAG Audio device changed [${audioDevice.id}]")
            updateOutputAudioDevice(audioDevice)
        }
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            if (::currentCall.isInitialized) {
                if (call != currentCall) {
                    if (call == currentCall.core.currentCall) {
                        Log.w(
                            "$TAG Current call has changed, now is [${call.remoteAddress.asStringUriOnly()}] with state [$state]"
                        )
                        currentCall.removeListener(callListener)
                        configureCall(call)
                    } else if (LinphoneUtils.isCallIncoming(call.state)) {
                        Log.w(
                            "$TAG A call is being received [${call.remoteAddress.asStringUriOnly()}], using it as current call unless declined"
                        )
                        currentCall.removeListener(callListener)
                        configureCall(call)
                    }
                }
            } else {
                Log.w(
                    "$TAG There was no current call (shouldn't be possible), using [${call.remoteAddress.asStringUriOnly()}] anyway"
                )
                configureCall(call)
            }
        }

        @WorkerThread
        override fun onNewAlertTriggered(core: Core, alert: Alert) {
            val remote = alert.call.remoteAddress.asStringUriOnly()
            Log.w("$TAG Alert of type [${alert.type}] triggered for call from [$remote]")
            alert.addListener(alertListener)

            if (alert.call != currentCall) {
                Log.w("$TAG Terminated alert wasn't for current call, do not display it")
                return
            }

            if (alert.type == Alert.Type.QoSLowSignal) {
                when (val networkType = alert.informations?.getString(ALERT_NETWORK_TYPE_KEY)) {
                    ALERT_NETWORK_TYPE_WIFI -> {
                        Log.i("$TAG Triggered low signal alert is for Wi-Fi")
                        showLowWifiSignalEvent.postValue(Event(true))
                    }
                    ALERT_NETWORK_TYPE_CELLULAR -> {
                        Log.i("$TAG Triggered low signal alert is for cellular")
                        showLowCellularSignalEvent.postValue(Event(true))
                    }
                    else -> {
                        Log.w(
                            "$TAG Unexpected type of signal [$networkType] found in alert information"
                        )
                    }
                }
            }
        }

        @WorkerThread
        override fun onTransferStateChanged(core: Core, transfered: Call, state: Call.State) {
            Log.i(
                "$TAG Transferred call [${transfered.remoteAddress.asStringUriOnly()}] state changed [$state]"
            )

            // TODO FIXME: Remote is call being transferred, not transferee !
            if (state == Call.State.OutgoingProgress) {
                val displayName = coreContext.contactsManager.findDisplayName(
                    transfered.remoteAddress
                )
                transferInProgressEvent.postValue(Event(displayName))
            } else if (LinphoneUtils.isCallEnding(state)) {
                val displayName = coreContext.contactsManager.findDisplayName(
                    transfered.remoteAddress
                )
                transferFailedEvent.postValue(Event(displayName))
            }
        }
    }

    private val alertListener = object : AlertListenerStub() {
        @WorkerThread
        override fun onTerminated(alert: Alert) {
            val remote = alert.call.remoteAddress.asStringUriOnly()
            Log.w("$TAG Alert of type [${alert.type}] dismissed for call from [$remote]")
            alert.removeListener(this)

            if (alert.call != currentCall) {
                Log.w("$TAG Terminated alert wasn't for current call, do not display it")
                return
            }

            if (alert.type == Alert.Type.QoSLowSignal) {
                when (val signalType = alert.informations?.getString(ALERT_NETWORK_TYPE_KEY)) {
                    ALERT_NETWORK_TYPE_WIFI -> {
                        Log.i("$TAG Wi-Fi signal no longer low")
                        showLowWifiSignalEvent.postValue(Event(false))
                    }
                    ALERT_NETWORK_TYPE_CELLULAR -> {
                        Log.i("$TAG Cellular signal no longer low")
                        showLowCellularSignalEvent.postValue(Event(false))
                    }
                    else -> {
                        Log.w(
                            "$TAG Unexpected type of signal [$signalType] found in alert information"
                        )
                    }
                }
            }
        }
    }

    init {
        isVideoEnabled.value = false
        isMicrophoneMuted.value = false
        fullScreenMode.value = false
        isActionsMenuExpanded.value = false

        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            val call = core.currentCall ?: core.calls.firstOrNull()

            if (call != null) {
                Log.i("$TAG Found call [${call.remoteAddress.asStringUriOnly()}]")
                configureCall(call)
            } else {
                Log.e("$TAG Failed to find call!")
            }

            showSwitchCamera.postValue(coreContext.showSwitchCameraButton())
        }

        numpadModel = NumpadModel(
            { digit -> // onDigitClicked
                appendDigitToSearchBarEvent.value = Event(digit)
                coreContext.postOnCoreThread {
                    if (::currentCall.isInitialized) {
                        Log.i("$TAG Sending DTMF [${digit.first()}]")
                        currentCall.sendDtmf(digit.first())
                    }
                }
            },
            { // OnBackspaceClicked
                removedCharacterAtCurrentPositionEvent.value = Event(true)
            },
            { // OnCallClicked
            }
        )
    }

    @UiThread
    override fun onCleared() {
        super.onCleared()

        coreContext.postOnCoreThread { core ->
            core.removeListener(coreListener)

            if (::currentCall.isInitialized) {
                currentCall.removeListener(callListener)
            }
        }
    }

    @UiThread
    fun answer() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                Log.i("$TAG Answering call [$currentCall]")
                coreContext.answerCall(currentCall)
            }
        }
    }

    @UiThread
    fun hangUp() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                Log.i("$TAG Terminating call [${currentCall.remoteAddress.asStringUriOnly()}]")
                currentCall.terminate()
            }
        }
    }

    @UiThread
    fun updateZrtpSas(verified: Boolean) {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                currentCall.authenticationTokenVerified = verified
            }
        }
    }

    @UiThread
    fun toggleMuteMicrophone() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: request record audio permission
            return
        }

        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                currentCall.microphoneMuted = !currentCall.microphoneMuted
                isMicrophoneMuted.postValue(currentCall.microphoneMuted)
            }
        }
    }

    @UiThread
    fun changeAudioOutputDevice() {
        val routeAudioToSpeaker = isSpeakerEnabled.value != true

        coreContext.postOnCoreThread { core ->
            val audioDevices = core.audioDevices
            val list = arrayListOf<AudioDeviceModel>()
            for (device in audioDevices) {
                // Only list output audio devices
                if (!device.hasCapability(AudioDevice.Capabilities.CapabilityPlay)) continue

                val isSpeaker = device.type == AudioDevice.Type.Speaker
                val isHeadset = device.type == AudioDevice.Type.Headset || device.type == AudioDevice.Type.Headphones
                val isBluetooth = device.type == AudioDevice.Type.Bluetooth
                val model = AudioDeviceModel(device, device.id, isSpeaker, isHeadset, isBluetooth) {
                    // onSelected
                    coreContext.postOnCoreThread {
                        Log.i("$TAG Selected audio device with ID [${device.id}]")
                        if (::currentCall.isInitialized) {
                            when {
                                isHeadset -> AudioRouteUtils.routeAudioToHeadset(currentCall)
                                isBluetooth -> AudioRouteUtils.routeAudioToBluetooth(currentCall)
                                isSpeaker -> AudioRouteUtils.routeAudioToSpeaker(currentCall)
                                else -> AudioRouteUtils.routeAudioToEarpiece(currentCall)
                            }
                        }
                    }
                }
                list.add(model)
                Log.i("$TAG Found audio device [$device]")
            }

            if (list.size > 2) {
                Log.i("$TAG Found more than two devices, showing list to let user choose")
                showAudioDevicesListEvent.postValue(Event(list))
            } else {
                Log.i(
                    "$TAG Found less than two devices, simply switching between earpiece & speaker"
                )
                if (::currentCall.isInitialized) {
                    if (routeAudioToSpeaker) {
                        AudioRouteUtils.routeAudioToSpeaker(currentCall)
                    } else {
                        AudioRouteUtils.routeAudioToEarpiece(currentCall)
                    }
                }
            }
        }
    }

    @UiThread
    fun toggleVideo() {
        if (ActivityCompat.checkSelfPermission(
                coreContext.context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: request video permission
            return
        }

        coreContext.postOnCoreThread { core ->
            if (::currentCall.isInitialized) {
                val params = core.createCallParams(currentCall)
                if (currentCall.conference != null) {
                    if (params?.isVideoEnabled == false) {
                        params.isVideoEnabled = true
                        params.videoDirection = MediaDirection.SendRecv
                    } else {
                        if (params?.videoDirection == MediaDirection.SendRecv || params?.videoDirection == MediaDirection.SendOnly) {
                            params.videoDirection = MediaDirection.RecvOnly
                        } else {
                            params?.videoDirection = MediaDirection.SendRecv
                        }
                    }
                } else {
                    params?.isVideoEnabled = params?.isVideoEnabled == false
                    Log.i(
                        "$TAG Updating call with video enabled set to ${params?.isVideoEnabled}"
                    )
                }
                currentCall.update(params)
            }
        }
    }

    @UiThread
    fun switchCamera() {
        coreContext.postOnCoreThread {
            coreContext.switchCamera()
        }
    }

    @UiThread
    fun toggleRecording() {
        coreContext.postOnCoreThread {
            if (::currentCall.isInitialized) {
                if (currentCall.params.isRecording) {
                    Log.i("$TAG Stopping call recording")
                    currentCall.stopRecording()
                } else {
                    Log.i("$TAG Starting call recording")
                    currentCall.startRecording()
                }
                val recording = currentCall.params.isRecording
                isRecording.postValue(recording)
            }
        }
    }

    @UiThread
    fun toggleFullScreen() {
        if (fullScreenMode.value == false && isVideoEnabled.value == false) return
        fullScreenMode.value = fullScreenMode.value != true
    }

    @UiThread
    fun toggleExpandActionsMenu() {
        toggleExtraActionsBottomSheetEvent.value = Event(true)
    }

    @UiThread
    fun showNumpad() {
        showNumpadBottomSheetEvent.value = Event(true)
    }

    @WorkerThread
    fun blindTransferCallTo(to: Address) {
        if (::currentCall.isInitialized) {
            Log.i(
                "$TAG Call [${currentCall.remoteAddress.asStringUriOnly()}] is being blindly transferred to [${to.asStringUriOnly()}]"
            )
            if (currentCall.transferTo(to) == 0) {
                Log.i("$TAG Blind call transfer is successful")
            } else {
                Log.e("$TAG Failed to make blind call transfer!")
                val displayName = coreContext.contactsManager.findDisplayName(to)
                transferFailedEvent.postValue(Event(displayName))
            }
        }
    }

    @WorkerThread
    private fun showZrtpSasDialog(authToken: String) {
        val toRead: String
        val toListen: String
        when (currentCall.dir) {
            Call.Dir.Incoming -> {
                toRead = authToken.substring(0, 2)
                toListen = authToken.substring(2)
            }
            else -> {
                toRead = authToken.substring(2)
                toListen = authToken.substring(0, 2)
            }
        }
        showZrtpSasDialogEvent.postValue(Event(Pair(toRead, toListen)))
    }

    @WorkerThread
    private fun updateEncryption(): Boolean {
        when (currentCall.currentParams.mediaEncryption) {
            MediaEncryption.ZRTP -> {
                val authToken = currentCall.authenticationToken
                val isDeviceTrusted = currentCall.authenticationTokenVerified && authToken != null
                Log.i(
                    "$TAG Current call media encryption is ZRTP, auth token is ${if (isDeviceTrusted) "trusted" else "not trusted yet"}"
                )
                isRemoteDeviceTrusted.postValue(isDeviceTrusted)
                val securityLevel = if (isDeviceTrusted) SecurityLevel.Encrypted else SecurityLevel.Safe
                contact.value?.trust?.postValue(securityLevel)

                if (!isDeviceTrusted && authToken.orEmpty().isNotEmpty()) {
                    Log.i("$TAG Showing ZRTP SAS confirmation dialog")
                    showZrtpSasDialog(authToken!!.uppercase(Locale.getDefault()))
                }

                return isDeviceTrusted
            }
            MediaEncryption.SRTP, MediaEncryption.DTLS -> {
            }
            else -> {
            }
        }
        return false
    }

    @WorkerThread
    private fun configureCall(call: Call) {
        currentCall = call
        call.addListener(callListener)

        if (call.dir == Call.Dir.Incoming) {
            if (call.core.accountList.size > 1) {
                val displayName = LinphoneUtils.getDisplayName(call.toAddress)
                incomingCallTitle.postValue(
                    AppUtils.getFormattedString(
                        R.string.call_incoming_for_account,
                        displayName
                    )
                )
            } else {
                incomingCallTitle.postValue(AppUtils.getString(R.string.call_incoming))
            }
        }

        if (LinphoneUtils.isCallOutgoing(call.state)) {
            isVideoEnabled.postValue(call.params.isVideoEnabled)
        } else {
            isVideoEnabled.postValue(call.currentParams.isVideoEnabled)
        }

        isMicrophoneMuted.postValue(call.microphoneMuted)
        val audioDevice = call.outputAudioDevice
        updateOutputAudioDevice(audioDevice)

        isOutgoing.postValue(call.dir == Call.Dir.Outgoing)

        if (call.params.isRecording) {
            // Do not set it to false to prevent the "no longer recording" toast to be displayed
            isRecording.postValue(true)
        }

        val address = call.remoteAddress.clone()
        address.clean()
        displayedAddress.postValue(address.asStringUriOnly())

        val isDeviceTrusted = updateEncryption()
        val securityLevel = if (isDeviceTrusted) SecurityLevel.Encrypted else SecurityLevel.Safe
        val friend = coreContext.contactsManager.findContactByAddress(address)
        if (friend != null) {
            displayedName.postValue(friend.name)
            val model = ContactAvatarModel(friend)
            model.trust.postValue(securityLevel)
            contact.postValue(model)
        } else {
            val fakeFriend = coreContext.core.createFriend()
            fakeFriend.name = LinphoneUtils.getDisplayName(address)
            fakeFriend.addAddress(address)
            val model = ContactAvatarModel(fakeFriend)
            model.trust.postValue(securityLevel)
            contact.postValue(model)
            displayedName.postValue(fakeFriend.name)
        }

        callDuration.postValue(call.duration)
    }

    @WorkerThread
    fun updateCallDuration() {
        if (::currentCall.isInitialized) {
            callDuration.postValue(currentCall.duration)
        }
    }

    private fun updateOutputAudioDevice(audioDevice: AudioDevice?) {
        isSpeakerEnabled.postValue(audioDevice?.type == AudioDevice.Type.Speaker)
        isHeadsetEnabled.postValue(
            audioDevice?.type == AudioDevice.Type.Headphones || audioDevice?.type == AudioDevice.Type.Headset
        )
        isBluetoothEnabled.postValue(audioDevice?.type == AudioDevice.Type.Bluetooth)
    }
}