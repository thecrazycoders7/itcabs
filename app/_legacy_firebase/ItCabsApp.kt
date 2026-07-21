package com.itcabs

import android.app.Application

class ItCabsApp : Application() {
    // Single Repo for the whole app. No DI framework — one graph, one owner.
    val repo by lazy { Repo() }
}
