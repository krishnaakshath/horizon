package dev.tvlauncher.weather

import org.json.JSONArray
import org.json.JSONObject

/** Pure (de)serializer for the persisted weather state; tolerant on read. */
object StateCodec {
    private val DEFAULT = WeatherState(null, Units.C, null)

    fun encode(s: WeatherState): String {
        val o = JSONObject()
        o.put("unit", s.unit)
        s.place?.let { p ->
            o.put("place", JSONObject().apply {
                put("name", p.name); put("admin1", p.admin1 ?: JSONObject.NULL)
                put("country", p.country ?: JSONObject.NULL)
                put("lat", p.latitude); put("lon", p.longitude)
            })
        }
        s.snapshot?.let { n ->
            o.put("snapshot", JSONObject().apply {
                put("tempC", n.tempC); put("feelsC", n.feelsC); put("code", n.code)
                put("isDay", n.isDay); put("humidity", n.humidity); put("windKmh", n.windKmh)
                put("highC", n.highC); put("lowC", n.lowC); put("updatedMs", n.updatedMs)
                put("daily", JSONArray().apply {
                    n.daily.forEach { d ->
                        put(JSONObject().apply {
                            put("dayMs", d.dayMs); put("code", d.code)
                            put("highC", d.highC); put("lowC", d.lowC)
                        })
                    }
                })
            })
        }
        return o.toString()
    }

    fun decode(json: String?): WeatherState {
        if (json.isNullOrBlank()) return DEFAULT
        return try {
            val o = JSONObject(json)
            WeatherState(decodePlace(o.optJSONObject("place")), Units.normalize(o.optString("unit")),
                decodeSnapshot(o.optJSONObject("snapshot")))
        } catch (e: Exception) {
            DEFAULT
        }
    }

    private fun decodePlace(o: JSONObject?): Place? {
        if (o == null) return null
        val name = JsonUtil.str(o, "name") ?: return null
        val lat = JsonUtil.num(o, "lat") ?: return null
        val lon = JsonUtil.num(o, "lon") ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Place(name, JsonUtil.str(o, "admin1"), JsonUtil.str(o, "country"), lat, lon)
    }

    private fun decodeSnapshot(o: JSONObject?): WeatherSnapshot? {
        if (o == null) return null
        val temp = JsonUtil.num(o, "tempC") ?: return null
        val code = JsonUtil.num(o, "code")?.toInt() ?: return null
        val updated = (o.opt("updatedMs") as? Number)?.toLong() ?: return null
        val daily = ArrayList<DailyForecast>()
        val arr = o.optJSONArray("daily")
        if (arr != null) for (i in 0 until arr.length()) {
            val d = arr.optJSONObject(i) ?: continue
            val day = (d.opt("dayMs") as? Number)?.toLong() ?: continue
            val c = JsonUtil.num(d, "code")?.toInt() ?: continue
            val hi = JsonUtil.num(d, "highC") ?: continue
            val lo = JsonUtil.num(d, "lowC") ?: continue
            daily += DailyForecast(day, c, hi, lo)
        }
        return WeatherSnapshot(
            tempC = temp, feelsC = JsonUtil.num(o, "feelsC") ?: temp, code = code,
            isDay = o.optBoolean("isDay", true),
            humidity = (JsonUtil.num(o, "humidity") ?: 0.0).toInt(),
            windKmh = JsonUtil.num(o, "windKmh") ?: 0.0,
            highC = JsonUtil.num(o, "highC") ?: temp, lowC = JsonUtil.num(o, "lowC") ?: temp,
            updatedMs = updated, daily = daily,
        )
    }
}
