package com.sshex.multistack.navigation.extensions

import androidx.navigation.NavController
import com.sshex.multistack.R

fun NavController.resetStack(): Boolean {
    return popBackStack(graph.startDestination, true)
}

fun NavController.resetStackHard() {
    this.apply {
        graph = navInflater.inflate(R.navigation.nav_common).apply {
            startDestination = R.id.emptyFragment
        }
    }
    popBackStack(R.id.emptyFragment, true)
}