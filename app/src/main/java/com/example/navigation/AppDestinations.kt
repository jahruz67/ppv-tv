package com.example.navigation

import kotlinx.serialization.Serializable

sealed interface AppDestination {
    @Serializable
    data object Browse : AppDestination

    @Serializable
    data class EventDetail(val eventId: Int) : AppDestination

    @Serializable
    data class Player(val eventId: Int, val sourceIndex: Int = 0) : AppDestination
}
