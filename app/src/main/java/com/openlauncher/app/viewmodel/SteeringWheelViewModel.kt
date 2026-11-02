package com.openlauncher.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.openlauncher.app.data.SteeringWheelManager
import com.openlauncher.app.data.WheelFunction

class SteeringWheelViewModel(app:Application):AndroidViewModel(app) {
    val uiState=SteeringWheelManager.state
    init { SteeringWheelManager.connect(app); SteeringWheelManager.load() }
    fun select(function:WheelFunction,longPress:Boolean=false)=SteeringWheelManager.select(function,longPress)
    fun cancel()=SteeringWheelManager.cancelStudy()
    fun save()=SteeringWheelManager.save()
    fun reset()=SteeringWheelManager.save(reset=true)
    fun reload()=SteeringWheelManager.load()
    fun setContextual(enabled:Boolean)=SteeringWheelManager.setContextual(enabled)
    fun contextualEnabled()=SteeringWheelManager.contextualEnabled()
    override fun onCleared() { cancel() }
}
