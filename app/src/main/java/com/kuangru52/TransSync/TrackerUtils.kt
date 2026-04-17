package com.kuangru52.TransSync

object TrackerUtils {
    fun getTrackerNameFromUrl(url: String): String? {
        if (url.isEmpty()) return null
        
        // 预定义映射
        val mapping = mapOf(
            "pt.btschool.club" to "BTSchool",
            "agsvpt.trackers.work" to "AGSV",
            "tracker.hdkyl.in" to "Kylin",
            "www.hdkylin.top" to "Kylin",
            "zmpt.cc" to "ZmPT",
            "tracker.piggo.me" to "Pigo",
            "crabpt.vip" to "Crab",
            "et8.org" to "TCCF",
            "pttime.fun" to "PTT",
            "tracker.cyanbug.net" to "BUG",
            "tracker.0ff.cc" to "Farmm",
            "zhuque.in" to "ZHUQUE",
            "hdfans.org" to "HDFans",
            "www.oshen.win" to "Oshen",
            "hdtime.org" to "HDTime",
            "hudbt.hust.edu.cn" to "HUDBT",
            "reactor.filelist.io" to "FL",
            "reactor.thefl.org" to "FL",
            "xingtan.one" to "xingtan",
            "dubhe.site" to "Dubhe",
            "t-ru.org/ann" to "RuTracker",
            "pt.eastgame.org" to "TLF",
            "www.nicept.net" to "Nice",
            "pt.soulvoice.club" to "SoulVoice",
            "tracker.ptcafe.club" to "cafe",
            "tracker.greatposterwall.com" to "GPW",
            "routing.bgp.technology" to "ipt",
            "nicept.net" to "Nice",
            "gamegamept.com" to "ggpt",
            "tracker.qingwa.pro" to "qingwa",
            "tracker.qingwapt.com" to "qingwa",
            "tracker.qingwapt.org" to "qingwa",
            "tracker.zhixing.bjtu.edu.cn" to "zhixing",
            "tracker.carpt.net" to "carpt",
            "ultrahd.net" to "UltraHD",
            "tracker.hxpt.org" to "hxpt",
            "htpt.cc" to "htpt",
            "sunnytk.top" to "Sunny",
            "kamept.com" to "kamept",
            "tracker.52dic.vip" to "DIC",
            "t.ubits.club" to "Ubits",
            "t.hddolby.com" to "Dolby",
            "tracker.m-team.cc" to "M-Team",
            "on.springsunday.net" to "SSD",
            "tracker.ptchdbits.co" to "CHD",
            "tracker.hdsky.me" to "HDSky",
            "t.audiences.me" to "Audies",
            "tracker.cinefiles.info" to "Audies",
            "ourbits.club" to "OurBits",
            "tracker.totheglory.im" to "TTG",
            "tracker.keepfrds.com" to "FRDS",
            "tracker.hhanclub.net" to "HHan",
            "hdhome.org" to "Home",
            "tracker.pterclub.com" to "PTer",
            "tracker.pterclub.net" to "PTer",
            "tracker.open.cd" to "OpenCD"
        )
        
        for ((key, value) in mapping) {
            if (url.contains(key, ignoreCase = true)) return value
        }

        // 兜底逻辑：尝试自动提取域名
        return try {
            val uri = java.net.URI(url)
            val host = uri.host ?: url
            host.removePrefix("www.").substringBefore(":")
        } catch (_: Exception) {
            url.substringBefore("/")
        }
    }
}

