package dev.tvlauncher.weather

import org.json.JSONObject

object GeocodingParser {
    /** Never throws (except OutOfMemoryError); hostile input yields an empty list. */
    fun parse(json: String): List<Place> = JsonUtil.guarded {
        val arr = JSONObject(json).optJSONArray("results")
        val out = ArrayList<Place>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = (o.opt("name") as? String)?.trim().orEmpty()
                val lat = JsonUtil.num(o, "latitude")
                val lon = JsonUtil.num(o, "longitude")
                if (name.isEmpty() || lat == null || lon == null) continue
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                out += Place(name, JsonUtil.str(o, "admin1"), JsonUtil.str(o, "country"), lat, lon)
            }
        }
        out as List<Place>
    } ?: emptyList()
}
