package info.anodsplace.evtimer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform