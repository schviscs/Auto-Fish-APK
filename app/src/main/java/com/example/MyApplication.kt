package com.example

import android.app.Application

class MyApplication : Application() {
    val fishBotViewModel: FishBotViewModel by lazy {
        FishBotViewModel(this)
    }
}
