package dev.tvlauncher.weather

import org.junit.Assert.*
import org.junit.Test

class GeocodingParserTest {
    @Test fun parsesResults() {
        val j = """{"results":[
          {"id":1,"name":"Berlin","latitude":52.52437,"longitude":13.41053,"admin1":"Berlin","country":"Germany","x":1},
          {"name":"Berlin","latitude":44.46,"longitude":-71.18,"admin1":"New Hampshire","country":"United States"},
          {"name":"NoCoords","country":"X"},
          {"name":"BadLat","latitude":91.0,"longitude":0.0},
          {"name":"BadLon","latitude":0.0,"longitude":181.0},
          {"latitude":1.0,"longitude":1.0},
          {"name":"Null Island","latitude":0,"longitude":0}
        ]}"""
        val p = GeocodingParser.parse(j)
        assertEquals(3, p.size)
        assertEquals("Berlin", p[0].name)
        assertEquals("Germany", p[0].country)
        assertEquals(52.52437, p[0].latitude, 0.0)
        assertEquals("New Hampshire", p[1].admin1)
        assertEquals("Null Island", p[2].name)
        assertNull(p[2].admin1)
    }

    @Test fun label() {
        assertEquals("Berlin, Berlin, Germany", Place("Berlin", "Berlin", "Germany", 1.0, 1.0).label())
        assertEquals("Paris, France", Place("Paris", null, "France", 1.0, 1.0).label())
        assertEquals("Oslo", Place("Oslo", null, null, 1.0, 1.0).label())
    }

    @Test fun noResultsOrGarbage() {
        assertTrue(GeocodingParser.parse("""{"generationtime_ms":0.5}""").isEmpty())
        assertTrue(GeocodingParser.parse("").isEmpty())
        assertTrue(GeocodingParser.parse("nope").isEmpty())
        assertTrue(GeocodingParser.parse("""{"results":5}""").isEmpty())
        assertTrue(GeocodingParser.parse("""{"results":[1,"a",null]}""").isEmpty())
    }
}
