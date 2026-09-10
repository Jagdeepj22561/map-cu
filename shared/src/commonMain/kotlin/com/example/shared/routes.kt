package com.example.shared

data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val instruction: String? = null
)

data class Route(
    val id: String,
    val name: String,
    val points: List<GeoPoint>
)

private const val d4GateLat = 30.769819
private const val d4GateLng = 76.570414
private const val gate3Lat = 30.773800
private const val gate3Lng = 76.572058
private const val d2Lat = 30.770919
private const val d2Lng = 76.571232
private const val d1Lat = 30.771612
private const val d1Lng = 76.570748
private const val d3Lat = 30.770529
private const val d3Lng = 76.570911
private const val d4Lat = 30.770258
private const val d4Lng = 76.570707

private fun GeoPoint.isD4GatePoint(): Boolean = lat == d4GateLat && lng == d4GateLng

private fun GeoPoint.toNamedPoint(lat: Double, lng: Double, label: String): GeoPoint {
    val updatedInstruction = instruction
        ?.replace("D4 Gate", label)
        ?.replace("d4 gate", label)
    return GeoPoint(lat, lng, updatedInstruction)
}

private fun cloneD4GateRoute(
    route: Route,
    newId: String,
    newName: String,
    targetLat: Double,
    targetLng: Double,
    targetLabel: String
): Route {
    val lastIndex = route.points.lastIndex
    return Route(
        id = newId,
        name = newName,
        points = route.points.mapIndexed { index, point ->
            if ((index == 0 || index == lastIndex) && point.isD4GatePoint()) {
                point.toNamedPoint(targetLat, targetLng, targetLabel)
            } else {
                point
            }
        }
    )
}

private val d2ToD1Extension = listOf(
    GeoPoint(30.770976974855365, 76.57124598520952),
    GeoPoint(30.77121212248917, 76.57124792799692),
    GeoPoint(30.771297392641902, 76.57121842369867),
    GeoPoint(30.771362497504967, 76.5711379574319),
    GeoPoint(d1Lat, d1Lng)
)

private val gate3ToD1Path = listOf(
    GeoPoint(gate3Lat, gate3Lng),
    GeoPoint(30.773448894259122, 76.57201743311498),
    GeoPoint(30.77192548707332, 76.57193039770239),
    GeoPoint(30.771881748715042, 76.57191582976664),
    GeoPoint(30.771848332246112, 76.5718836432606),
    GeoPoint(30.771833352445498, 76.57174282729378),
    GeoPoint(30.771836686849284, 76.57091082420088),
    GeoPoint(d1Lat, d1Lng)
)

private val d1ToD2Path = listOf(
    GeoPoint(d1Lat, d1Lng),
    GeoPoint(30.771362497504967, 76.5711379574319),
    GeoPoint(30.771297392641902, 76.57121842369867),
    GeoPoint(30.77121212248917, 76.57124792799692),
    GeoPoint(30.770976974855365, 76.57124598520952),
    GeoPoint(d2Lat, d2Lng)
)

private fun appendD1SegmentAfterD2(route: Route): Route {
    val endsAtD2 = route.points.lastOrNull()?.let { it.lat == d2Lat && it.lng == d2Lng } == true
    return if (endsAtD2) route.copy(points = route.points + d2ToD1Extension) else route
}

private fun cloneGate3RouteFromD2(
    route: Route,
    newId: String,
    newName: String
): Route = Route(
    id = newId,
    name = newName,
    points = gate3ToD1Path + d1ToD2Path.drop(1) + route.points.drop(1)
)

private fun buildGate3RouteFromPoints(
    id: String,
    name: String,
    extraPointsAfterD2: List<GeoPoint> = emptyList()
): Route = Route(
    id = id,
    name = name,
    points = gate3ToD1Path + d1ToD2Path.drop(1) + extraPointsAfterD2
)

private fun cloneD1RouteFromD2(
    route: Route,
    newId: String,
    newName: String
): Route = Route(
    id = newId,
    name = newName,
    points = d1ToD2Path + route.points.drop(1)
)

private fun buildD1RouteFromPoints(
    id: String,
    name: String,
    extraPointsAfterD2: List<GeoPoint> = emptyList()
): Route = Route(
    id = id,
    name = name,
    points = d1ToD2Path + extraPointsAfterD2
)


object RoutesData {

    val VIPtoa1 = Route(
        id = "vip_to_a1",
        name = "VIP Gate → A1",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.771997, 76.578344)
        )
    )

    val gate2ToC3 = Route(
        id = "gate2_c3",
        name = "Gate 2 → C3",
        points = listOf(
            GeoPoint(30.772817, 76.576464),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767670, 76.575001),
            GeoPoint(30.767500, 76.574944)
        )
    )


    val gate2ToC1 = Route(
        id = "gate2_c1",
        name = "Gate 2 → C1",
        points = listOf(
            GeoPoint(30.772817, 76.576464),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.767051859115032, 76.57589589757104)

        )
    )
    val gate2ToB3 = Route(
        id = "gate2_b3",
        name = "Gate 2 → B3",
        points = listOf(
            GeoPoint(30.772817, 76.576464),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768525, 76.575919)
        )
    )

    val gate2ToB4 = Route(
        id = "gate2_b4",
        name = "Gate 2 → B4",
        points = listOf(
            GeoPoint(30.772817, 76.576464),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768578, 76.574595)
        )
    )

    val gate2ToA3 = Route(
        id = "gate2_a3",
        name = "Gate 2 → A3",
        points = listOf(
            GeoPoint(30.772817, 76.576464),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768968, 76.578338)
        )
    )

    val gate2ToA2 = Route(
        id = "gate2_a2",
        name = "Gate 2 → A2",
        points = listOf(
            GeoPoint(30.772817, 76.576464),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.769586, 76.578476),
            GeoPoint(30.769498, 76.578552),
            GeoPoint(30.769484, 76.578896)
        )
    )

    val A1ToC3 = Route(
        id = "A1_c3",
        name = "A1 → C3",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767670, 76.575001),
            GeoPoint(30.767500, 76.574944)
        )
    )

    val gate2ToDaca = Route(
        id = "gate2_daca",
        name = "Gate 2 → DACA",
        points = listOf(
            GeoPoint(30.772817, 76.576464),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768634, 76.573667),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769803, 76.572588)
        )
    )

    val gate3ToD1 = Route(
        id = "gate3_d1",
        name = "Gate 3 -> D1",
        points = gate3ToD1Path
    )

    val d1ToGate3 = Route(
        id = "d1_gate3",
        name = "D1 -> Gate 3",
        points = gate3ToD1Path.reversed()
    )

    val d4gateToC3 = Route(
        id = "d4gate_c3",
        name = "D4 Gate → C3",
        points = listOf(
            GeoPoint(30.769819, 76.570414),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767682, 76.575016),
            GeoPoint(30.767500, 76.574944)
        )
    )

    val d4gateToC2 = Route(
        id = "d4gate_c2",
        name = "D4 Gate → C2",
        points = listOf(
            GeoPoint(30.769819, 76.570414),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870)
        )
    )

    val d4gateToC1 = Route(
        id = "d4gate_c1",
        name = "D4 Gate → C1",
        points = listOf(
            GeoPoint(30.769819, 76.570414),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766892, 76.575894)
        )
    )

    val d4gateToDaca = Route(
        id = "d4gate_daca",
        name = "D4 Gate → DACA",
        points = listOf(
            GeoPoint(30.769819, 76.570414),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769803, 76.572588)
        )
    )

    val d4gateToB3 = Route(
        id = "d4gate_b3",
        name = "D4 Gate → B3",
        points = listOf(
            GeoPoint(30.769819, 76.570414),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768566, 76.574632),
            GeoPoint(30.768536, 76.575860)
        )
    )

    val d2ToC3 = cloneD4GateRoute(d4gateToC3, "d2_c3", "D2 -> C3", d2Lat, d2Lng, "D2")

    val d2ToC2 = cloneD4GateRoute(d4gateToC2, "d2_c2", "D2 -> C2", d2Lat, d2Lng, "D2")

    val d2ToC1 = cloneD4GateRoute(d4gateToC1, "d2_c1", "D2 -> C1", d2Lat, d2Lng, "D2")

    val d2ToDaca = cloneD4GateRoute(d4gateToDaca, "d2_daca", "D2 -> DACA", d2Lat, d2Lng, "D2")

    val d2ToB3 = cloneD4GateRoute(d4gateToB3, "d2_b3", "D2 -> B3", d2Lat, d2Lng, "D2")

    val d3ToC3 = cloneD4GateRoute(d4gateToC3, "d3_c3", "D3 -> C3", d3Lat, d3Lng, "D3")

    val d3ToC2 = cloneD4GateRoute(d4gateToC2, "d3_c2", "D3 -> C2", d3Lat, d3Lng, "D3")

    val d3ToC1 = cloneD4GateRoute(d4gateToC1, "d3_c1", "D3 -> C1", d3Lat, d3Lng, "D3")

    val d3ToDaca = cloneD4GateRoute(d4gateToDaca, "d3_daca", "D3 -> DACA", d3Lat, d3Lng, "D3")

    val d3ToB3 = cloneD4GateRoute(d4gateToB3, "d3_b3", "D3 -> B3", d3Lat, d3Lng, "D3")

    val d4ToC3 = cloneD4GateRoute(d4gateToC3, "d4_c3", "D4 -> C3", d4Lat, d4Lng, "D4")

    val d4ToC2 = cloneD4GateRoute(d4gateToC2, "d4_c2", "D4 -> C2", d4Lat, d4Lng, "D4")

    val d4ToC1 = cloneD4GateRoute(d4gateToC1, "d4_c1", "D4 -> C1", d4Lat, d4Lng, "D4")

    val d4ToDaca = cloneD4GateRoute(d4gateToDaca, "d4_daca", "D4 -> DACA", d4Lat, d4Lng, "D4")

    val d4ToB3 = cloneD4GateRoute(d4gateToB3, "d4_b3", "D4 -> B3", d4Lat, d4Lng, "D4")

    val a1ToA3 = Route(
        id = "a1_a3",
        name = "A1 → A3",
        points = listOf(
            GeoPoint(30.771448, 76.578241),
            GeoPoint(30.771265, 76.579166),
            GeoPoint(30.770954, 76.579109),
            GeoPoint(30.770867, 76.579517),
            GeoPoint(30.770296, 76.579358),
            GeoPoint(30.769506, 76.578405),
            GeoPoint(30.769136, 76.578354)
        )
    )

    val a1ToA2 = Route(
        id = "a1_a2",
        name = "A1 → A2",
        points = listOf(
            GeoPoint(30.771395, 76.578427),
            GeoPoint(30.771312, 76.579002),
            GeoPoint(30.771271, 76.579156),
            GeoPoint(30.770964, 76.579122),
            GeoPoint(30.770864, 76.579508),
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992)
        )
    )

    val a1ToB1 = Route(
        id = "a1_b1",
        name = "A1 → B1",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769924, 76.576320),
            GeoPoint(30.769919, 76.575901),
            GeoPoint(30.769595, 76.575869),
        )
    )

    val gate2ToC2 = Route(
        id = "gate2_c2",
        name = "Gate 2 → C2",
        points = listOf(
            GeoPoint(30.772817, 76.576464, "Start from Gate 2"),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870, "You have arrived at C2")
        )
    )

    val gate2ToB1 = Route(
        id = "gate2_b1",
        name = "Gate 2 → B1",
        points = listOf(
            GeoPoint(30.772817, 76.576464, "Start from Gate 2"),
            GeoPoint(30.769924, 76.576320),
            GeoPoint(30.769919, 76.575901),
            GeoPoint(30.769923, 76.576318, "You have arrived at B1")
        )
    )

    val gate2ToB2 = Route(
        id = "gate2_b2",
        name = "Gate 2 → B2",
        points = listOf(
            GeoPoint(30.772817, 76.576464, "Start from Gate 2"),
            GeoPoint(30.769924, 76.576320),
            GeoPoint(30.769919, 76.575901),
            GeoPoint(30.769595, 76.575869),
            GeoPoint(30.769278, 76.575853, "You have arrived at B2")
        )
    )
    val d4gateToB4 = Route(
        id = "d4gate_b4",
        name = "D4 Gate → B4",
        points = listOf(
            GeoPoint(30.769819, 76.570414, "Start from D4 Gate"),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595, "You have arrived at B4")
        )
    )

    val d4gateToA3 = Route(
        id = "d4gate_A3",
        name = "D4 Gate → A3",
        points = listOf(
            GeoPoint(30.769819, 76.570414, "Start from D4 Gate"),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(
                30.768968, 76.578338,
            )
        )
    )
    val d4gateToB1 = Route(
        id = "d4gate_b1",
        name = "D4 Gate → B1",
        points = listOf(
            GeoPoint(30.769819, 76.570414, "Start from D4 Gate"),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769285155141368, 76.57586352923434),
            GeoPoint(30.769596222152483, 76.57587390998721)


        )
    )
    val d4gateToA2 = Route(
        id = "d4gate_a2",
        name = "D4 Gate → A2",
        points = listOf(
            GeoPoint(30.769819, 76.570414, "Start from D4 Gate"),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.769586, 76.578476),
            GeoPoint(30.769498, 76.578552),
            GeoPoint(30.769484, 76.578896)



        )
    )
    val dacaToc1 = Route(
        id = "c1_daka",
        name = "C1 → DACA",
        points = listOf(
            GeoPoint(30.769803, 76.572588),
            GeoPoint(30.769643, 76.572580),

            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766892, 76.575894)

        )
    )
    val d4gateToA1 = Route(
        id = "d4_A1",
        name = "D4 Gate → A1",
        points = listOf(
            GeoPoint(30.769819, 76.570414, "Start from D4 Gate"),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.76926228441716, 76.57631164593464),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.771450, 76.578228)
        )
    )

    val d2ToB4 = cloneD4GateRoute(d4gateToB4, "d2_b4", "D2 -> B4", d2Lat, d2Lng, "D2")

    val d2ToA3 = cloneD4GateRoute(d4gateToA3, "d2_a3", "D2 -> A3", d2Lat, d2Lng, "D2")

    val d2ToB1 = cloneD4GateRoute(d4gateToB1, "d2_b1", "D2 -> B1", d2Lat, d2Lng, "D2")

    val d2ToA2 = cloneD4GateRoute(d4gateToA2, "d2_a2", "D2 -> A2", d2Lat, d2Lng, "D2")

    val d2ToA1 = cloneD4GateRoute(d4gateToA1, "d2_a1", "D2 -> A1", d2Lat, d2Lng, "D2")

    val d3ToB4 = cloneD4GateRoute(d4gateToB4, "d3_b4", "D3 -> B4", d3Lat, d3Lng, "D3")

    val d3ToA3 = cloneD4GateRoute(d4gateToA3, "d3_a3", "D3 -> A3", d3Lat, d3Lng, "D3")

    val d3ToB1 = cloneD4GateRoute(d4gateToB1, "d3_b1", "D3 -> B1", d3Lat, d3Lng, "D3")

    val d3ToA2 = cloneD4GateRoute(d4gateToA2, "d3_a2", "D3 -> A2", d3Lat, d3Lng, "D3")

    val d3ToA1 = cloneD4GateRoute(d4gateToA1, "d3_a1", "D3 -> A1", d3Lat, d3Lng, "D3")

    val d4ToB4 = cloneD4GateRoute(d4gateToB4, "d4_b4", "D4 -> B4", d4Lat, d4Lng, "D4")

    val d4ToA3 = cloneD4GateRoute(d4gateToA3, "d4_a3", "D4 -> A3", d4Lat, d4Lng, "D4")

    val d4ToB1 = cloneD4GateRoute(d4gateToB1, "d4_b1", "D4 -> B1", d4Lat, d4Lng, "D4")

    val d4ToA2 = cloneD4GateRoute(d4gateToA2, "d4_a2", "D4 -> A2", d4Lat, d4Lng, "D4")

    val d4ToA1 = cloneD4GateRoute(d4gateToA1, "d4_a1", "D4 -> A1", d4Lat, d4Lng, "D4")

    val gate3ToD2 = buildGate3RouteFromPoints(
        id = "gate3_d2",
        name = "Gate 3 -> D2"
    )

    val gate3ToD3 = buildGate3RouteFromPoints(
        id = "gate3_d3",
        name = "Gate 3 -> D3",
        extraPointsAfterD2 = listOf(GeoPoint(d3Lat, d3Lng))
    )

    val gate3ToD4 = buildGate3RouteFromPoints(
        id = "gate3_d4",
        name = "Gate 3 -> D4",
        extraPointsAfterD2 = listOf(
            GeoPoint(d3Lat, d3Lng),
            GeoPoint(d4Lat, d4Lng)
        )
    )

    val gate3ToC3 = cloneGate3RouteFromD2(d2ToC3, "gate3_c3", "Gate 3 -> C3")

    val gate3ToC2 = cloneGate3RouteFromD2(d2ToC2, "gate3_c2", "Gate 3 -> C2")

    val gate3ToC1 = cloneGate3RouteFromD2(d2ToC1, "gate3_c1", "Gate 3 -> C1")

    val gate3ToDaca = cloneGate3RouteFromD2(d2ToDaca, "gate3_daca", "Gate 3 -> DACA")

    val gate3ToB3 = cloneGate3RouteFromD2(d2ToB3, "gate3_b3", "Gate 3 -> B3")

    val gate3ToB4 = cloneGate3RouteFromD2(d2ToB4, "gate3_b4", "Gate 3 -> B4")

    val gate3ToA3 = cloneGate3RouteFromD2(d2ToA3, "gate3_a3", "Gate 3 -> A3")

    val gate3ToB1 = cloneGate3RouteFromD2(d2ToB1, "gate3_b1", "Gate 3 -> B1")

    val gate3ToA2 = cloneGate3RouteFromD2(d2ToA2, "gate3_a2", "Gate 3 -> A2")

    val gate3ToA1 = cloneGate3RouteFromD2(d2ToA1, "gate3_a1", "Gate 3 -> A1")

    val d1ToD2 = buildD1RouteFromPoints(
        id = "d1_d2",
        name = "D1 -> D2"
    )

    val d1ToD3 = buildD1RouteFromPoints(
        id = "d1_d3",
        name = "D1 -> D3",
        extraPointsAfterD2 = listOf(GeoPoint(d3Lat, d3Lng))
    )

    val d1ToD4 = buildD1RouteFromPoints(
        id = "d1_d4",
        name = "D1 -> D4",
        extraPointsAfterD2 = listOf(
            GeoPoint(d3Lat, d3Lng),
            GeoPoint(d4Lat, d4Lng)
        )
    )

    val d1ToC3 = cloneD1RouteFromD2(d2ToC3, "d1_c3", "D1 -> C3")

    val d1ToC2 = cloneD1RouteFromD2(d2ToC2, "d1_c2", "D1 -> C2")

    val d1ToC1 = cloneD1RouteFromD2(d2ToC1, "d1_c1", "D1 -> C1")

    val d1ToDaca = cloneD1RouteFromD2(d2ToDaca, "d1_daca", "D1 -> DACA")

    val d1ToB3 = cloneD1RouteFromD2(d2ToB3, "d1_b3", "D1 -> B3")

    val d1ToB4 = cloneD1RouteFromD2(d2ToB4, "d1_b4", "D1 -> B4")

    val d1ToA3 = cloneD1RouteFromD2(d2ToA3, "d1_a3", "D1 -> A3")

    val d1ToB1 = cloneD1RouteFromD2(d2ToB1, "d1_b1", "D1 -> B1")

    val d1ToA2 = cloneD1RouteFromD2(d2ToA2, "d1_a2", "D1 -> A2")

    val d1ToA1 = cloneD1RouteFromD2(d2ToA1, "d1_a1", "D1 -> A1")

    val dacaToc3 = Route(
        id = "c3_daka",
        name = "C3 → DACA",
        points = listOf(
            GeoPoint(30.769803, 76.572588),
            GeoPoint(30.769643, 76.572580),

            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767682, 76.575016),
            GeoPoint(30.767500, 76.574944)
        )
    )
    val dacaToc2 = Route(
        id = "c2_daka",
        name = "C2 → DACA",
        points = listOf(
            GeoPoint(30.769803, 76.572588),
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766892, 76.575894),
            GeoPoint(30.766130, 76.575870)
        )
    )
    val a3toc3 = Route(
        id = "a3_c3",
        name = "A3 → C3",
        points = listOf(


            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768864040254584, 76.57637772796957),
            GeoPoint(30.767690516807892, 76.57633241712492),
            GeoPoint(30.767687186369493, 76.5759293142013),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767682, 76.575016),
            GeoPoint(30.767500, 76.574944)
        )
    )

    val a3toc2 = Route(
        id = "a3_c2",
        name = "A3 → C2",
        points = listOf(


            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768864040254584, 76.57637772796957),
            GeoPoint(30.767690516807892, 76.57633241712492),
            GeoPoint(30.767687186369493, 76.5759293142013),
            GeoPoint(30.767682190709635, 76.57592543819753),
            GeoPoint(30.766892, 76.575894),
            GeoPoint(30.766130, 76.575870)

        )
    )
    val a3toc1 = Route(
        id = "a3_c1",
        name = "A3 → C1",
        points = listOf(


            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768864040254584, 76.57637772796957),
            GeoPoint(30.767690516807892, 76.57633241712492),
            GeoPoint(30.767687186369493, 76.5759293142013),
            GeoPoint(30.767682190709635, 76.57592543819753),
            GeoPoint(30.766892, 76.575894),
        )
    )
    val d4GateToD4 = Route(
        id = "d4gate_d4",
        name = "D4 Gate → D4",
        points = listOf(
            GeoPoint(30.769808, 76.570428),
            GeoPoint(30.770075, 76.570557)
        )
    )

    val d4GateToD3 = Route(
        id = "d4gate_d3",
        name = "D4 Gate → D3",
        points = listOf(
            GeoPoint(30.769808, 76.570428),
            GeoPoint(30.770529, 76.570911)
        )
    )

    val d4GateToD2 = appendD1SegmentAfterD2(Route(
        id = "d4gate_d2",
        name = "D4 Gate → D2",
        points = listOf(
            GeoPoint(30.769808, 76.570428),
            GeoPoint(30.770919, 76.571232)
        )
    )
    )

    val VIPtoA2 = Route(
        id = "vip_to_a2",
        name = "VIP Gate → A2",
        points = listOf(
            GeoPoint(30.772596, 76.577559), // VIP Start
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158981737349, 76.57749975817659),
            GeoPoint(30.771450, 76.578228), // Turn at A1 Junction
            GeoPoint(30.771312, 76.579002),
            GeoPoint(30.771274585612854, 76.57916902258707),
            GeoPoint(30.770940151744387, 76.579132815092),
            GeoPoint(30.770854598707523, 76.57951299373609),// A1-A2 transition turn
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992)  // A2 Arrival
        )
    )

    val VIPtoA3 = Route(
        id = "vip_to_a3",
        name = "VIP Gate → A3",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772140529320897, 76.57760305578853),
            GeoPoint(30.771997, 76.578344),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.771265, 76.579166), // Turn toward A3
            GeoPoint(30.771274585612854, 76.57916902258707),
            GeoPoint(30.770940151744387, 76.579132815092),
            GeoPoint(30.770854598707523, 76.57951299373609),
            GeoPoint(30.770269568489077, 76.57935688129655),
            GeoPoint(30.76970228317711, 76.57863187404715),
            GeoPoint(30.769503311244744, 76.57838747152468),
            GeoPoint(30.769136, 76.578354)  // A3 Arrival
        )
    )
    val VIPtoB4 = Route(
        id = "vip_to_b4",
        name = "VIP Gate → B4",
        points = listOf(
            GeoPoint(30.772596, 76.577559), // VIP Start
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),

            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),


            GeoPoint(30.770539, 76.576338), // Turn South
            GeoPoint(30.769254, 76.576312), // Central Junction
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372), // South-West Turn
            GeoPoint(30.768578, 76.574595)  // B4 Arrival
        )

    )
    val dacaToB1 = Route(
        id = "daca_b1",
        name = "DACA → B1",
        points = listOf(
            GeoPoint(30.769803, 76.572588), // DACA Start
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769573, 76.573647), // Intersection
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.76851763656345, 76.57637775016627),
            GeoPoint(30.769211, 76.576445), // Main Junction B
            GeoPoint(30.769285, 76.575863),
            GeoPoint(30.769596, 76.575873)  // B1 Arrival
        )
    )
    val VIPtoB2 = Route(
        id = "vip_to_b2",
        name = "VIP Gate → B2",
        points = listOf(
            GeoPoint(30.772596, 76.577559), // Start
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),

            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.770539, 76.576338), // The "Gate 2 Corridor" pivot
            GeoPoint(30.769924, 76.576320), // The B-Block entrance turn
            GeoPoint(30.769919, 76.575901),
            GeoPoint(30.769595, 76.575869),
            GeoPoint(30.769278, 76.575853)  // Arrival at B2
        )
    )
    val VIPtoB1 = Route(
        id = "vip_to_b1",
        name = "VIP Gate → B1",
        points = listOf(
            GeoPoint(30.772596, 76.577559), // Start
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),

            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.770539, 76.576338), // The "Gate 2 Corridor" pivot
            GeoPoint(30.769924, 76.576320), // The B-Block entrance turn
            GeoPoint(30.769919, 76.575901),
            GeoPoint(30.769595, 76.575869),
            GeoPoint(30.769631943197396, 76.57587766156179),                       // B1
        )
    )


    val A2ToC3 = Route(
        id = "a2_c3",
        name = "A2 → C3",
        points = listOf(
            GeoPoint(30.769484, 76.578896), // A2 Start
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338), // A3 Turn point
            GeoPoint(30.769067, 76.576413), // Main intersection turn
            GeoPoint(30.767690, 76.576344), // South Pivot
            GeoPoint(30.767670, 76.575001),
            GeoPoint(30.767500, 76.574944)  // C3 Arrival
        )
    )
    val vipgateToC1 = Route(
        id = "vipgate_c1",
        name = "VIP Gate → C1",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.767051859115032, 76.57589589757104)
        )
    )
    val a2ToB3 = Route(
        id = "a2_to_b3",
        name = "A2 → B3",
        points = listOf(
            GeoPoint(30.769484, 76.578896), // A2 Start
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338), // Pivot at A3
            GeoPoint(30.769067, 76.576413), // Main intersection turn
            GeoPoint(30.768506, 76.576372), // Turn toward B-corridor
            GeoPoint(30.768525, 76.575919)  // B3 Arrival
        )
    )
    val dacaToA2 = Route(
        id = "daca_to_a2",
        name = "DACA → A2",
        points = listOf(
            GeoPoint(30.769803, 76.572588), // DACA Start
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769573, 76.573647), // Intersection Turn
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.76850234041828, 76.57637597823197),
            GeoPoint(30.769067, 76.576413), // Turn North-East
            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.769484, 76.578896)  // A2 Arrival
        )
    )
    val dacaToA3 = Route(
        id = "daca_to_a3",
        name = "DACA → A3",
        points = listOf(
            GeoPoint(30.769803, 76.572588), // DACA Start
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769573, 76.573647), // Intersection Turn
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.76850234041828, 76.57637597823197),
            GeoPoint(30.769067, 76.576413), // Turn North-East
            GeoPoint(30.768968, 76.578338),

        )
    )
    // ----------------- A1 -> other routes (hardcoded) -----------------

    val a1ToGate2 = Route(
        id = "a1_gate2",
        name = "A1 → Gate 2",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.772817, 76.576464)
        )
    )

    val a1ToB2 = Route(
        id = "a1_b2",
        name = "A1 → B2",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769924, 76.576320),
            GeoPoint(30.769919, 76.575901),
            GeoPoint(30.769595, 76.575869),
            GeoPoint(30.769278, 76.575853)
        )
    )

    val a1ToB3 = Route(
        id = "a1_b3",
        name = "A1 → B3",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768525, 76.575919)
        )
    )

    val a1ToB4 = Route(
        id = "a1_b4",
        name = "A1 → B4",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768578, 76.574595)
        )
    )

    val a1ToC1 = Route(
        id = "a1_c1",
        name = "A1 → C1",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766892, 76.575894)
        )
    )

    val a1ToC2 = Route(
        id = "a1_c2",
        name = "A1 → C2",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870)
        )
    )


    val a1ToDaca = Route(
        id = "a1_daca",
        name = "A1 → DACA",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768634, 76.573667),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769803, 76.572588)
        )
    )

    val a1ToD4Gate = Route(
        id = "a1_d4gate",
        name = "A1 → D4 Gate",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.76926228441716, 76.57631164593464),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769819, 76.570414)
        )
    )

    val a1ToD2 = appendD1SegmentAfterD2(Route(
        id = "a1_d2",
        name = "A1 → D2",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.76926228441716, 76.57631164593464),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.770919, 76.571232)
        )
    ))

    val a1ToD3 = Route(
        id = "a1_d3",
        name = "A1 → D3",
        points = listOf(
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.76926228441716, 76.57631164593464),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.770529, 76.570911)
        )
    )

    val a1ToD4 = cloneD4GateRoute(a1ToD4Gate, "a1_d4", "A1 -> D4", d4Lat, d4Lng, "D4")

    val b1ToB3 = Route(
        id = "b1_b3",
        name = "B1 → B3",
        points = listOf(
            GeoPoint(30.769631943197396, 76.57587766156179),                       // B1
            GeoPoint(30.769285155141368, 76.57586352923434),
            GeoPoint(30.769300074807536, 76.5753251265011),// intermediate
            GeoPoint(30.76855797609174, 76.57527952894124),
            GeoPoint(30.768525, 76.575919)
        )
    )

    val b1ToB4 = Route(
        id = "b1_b4",
        name = "B1 → B4",
        points = listOf(
            GeoPoint(30.769631943197396, 76.57587766156179),                       // B1
            GeoPoint(30.769285155141368, 76.57586352923434),
            GeoPoint(30.769300074807536, 76.5753251265011),// intermediate
            GeoPoint(30.76855797609174, 76.57527952894124),                      // curve toward B4
            GeoPoint(30.768578, 76.574595)                       // B4
        )
    )


    val b2ToB4 = Route(
        id = "b2_b4",
        name = "B2 → B4",
        points = listOf(
            GeoPoint(30.769278, 76.575853),
            GeoPoint(30.769300495620797, 76.57533664417387),// B2
            GeoPoint(30.768900, 76.575300),
            GeoPoint(30.7685424811577, 76.57528423658626),// mid
            GeoPoint(30.768578, 76.574595)                       // B4
        )
    )

    val b3ToB4 = Route(
        id = "b3_b4",
        name = "B3 → B4",
        points = listOf(
            GeoPoint(30.768536, 76.575860),                       // B3
            GeoPoint(30.768560, 76.575300),                      // small curve
            GeoPoint(30.768578, 76.574595)                       // B4
        )
    )

    val b1ToA3 = Route(
        id = "b1_a3",
        name = "B1 → A3",
        points = listOf(
            GeoPoint(30.769923, 76.576318), // B1
            GeoPoint(30.770163, 76.576332),
            GeoPoint(30.770549, 76.576366),
            GeoPoint(30.770483, 76.577293),
            GeoPoint(30.770723, 76.577640),
            GeoPoint(30.771445, 76.578307), // A1
            GeoPoint(30.771265, 76.579166),
            GeoPoint(30.770954, 76.579109),
            GeoPoint(30.769136, 76.578354) // A3
        )
    )

    val b1ToC3 = Route(
        id = "b1_c3",
        name = "B1 → C3",
        points = listOf(
            GeoPoint(30.769923, 76.576318), // B1
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767670, 76.575001),
            GeoPoint(30.767500, 76.574944) // C3
        )
    )

    val b1ToC2 = Route(
        id = "b1_c2",
        name = "B1 → C2",
        points = listOf(
            GeoPoint(30.769923, 76.576318), // B1
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870) // C2
        )
    )


    val b1ToDaca = Route(
        id = "b1_daca",
        name = "B1 → DACA",
        points = listOf(
            GeoPoint(30.769923, 76.576318), // B1
            GeoPoint(30.769596222152483, 76.57587390998721),
            GeoPoint(30.769285155141368, 76.57586352923434),
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769803, 76.572588) // DACA
        )
    )

    val b1ToD4 = Route(
        id = "b1_d4",
        name = "B1 → D4",
        points = listOf(
            GeoPoint(30.769560986931726, 76.57586722773597), // B1
            GeoPoint(30.769285155141368, 76.57586352923434),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.76849969225161, 76.57636877354216),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.77025776694348, 76.57070741206844) // D4
        )
    )
    val b2ToD4GATE = Route(
        id = "b2_d4gate",
        name = "B2 → D4 Gate",
        points = listOf(
            GeoPoint(30.769285155141368, 76.57586352923434),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.76849969225161, 76.57636877354216),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.77025776694348, 76.57070741206844) // D4
        )
    )

    val b2ToD2 = appendD1SegmentAfterD2(cloneD4GateRoute(b2ToD4GATE, "b2_d2", "B2 -> D2", d2Lat, d2Lng, "D2"))

    val b2ToD3 = cloneD4GateRoute(b2ToD4GATE, "b2_d3", "B2 -> D3", d3Lat, d3Lng, "D3")

    val b2ToD4 = cloneD4GateRoute(b2ToD4GATE, "b2_d4", "B2 -> D4", d4Lat, d4Lng, "D4")

    val b2ToC3 = Route(
        id = "b2_c3",
        name = "B2 → C3",
        points = listOf(
            GeoPoint(30.769278, 76.575853), // B2
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767680947704896, 76.57500345487125),
            GeoPoint(30.767500, 76.574944) // C3
        )
    )
    val b2ToC2 = Route(
        id = "b2_c2",
        name = "B2 → C2",
        points = listOf(
            GeoPoint(30.769278, 76.575853), // B2
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767689484623553, 76.57634063953864),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870) // C2
        )
    )

    val b2ToC1 = Route(
        id = "b2_c1",
        name = "B2 → C1",
        points = listOf(
            GeoPoint(30.769278, 76.575853), // B2
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767693226705514, 76.57633170765313),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766892, 76.575894) // C1
        )
    )

    val b2ToDaca = Route(
        id = "b2_daca",
        name = "B2 → DACA",
        points = listOf(
            GeoPoint(30.769803, 76.572588), // DACA Start
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769573, 76.573647), // Intersection
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.76851763656345, 76.57637775016627),
            GeoPoint(30.769211, 76.576445), // Main Junction B
            GeoPoint(30.769285, 76.575863),
        )
    )


    val b3ToC3 = Route(
        id = "b3_c3",
        name = "B3 → C3",
        points = listOf(
            GeoPoint(30.768536, 76.575860), // B3
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767680947704896, 76.57500345487125),
            GeoPoint(30.767500, 76.574944) // C3
        )
    )

    val b3ToC2 = Route(
        id = "b3_c2",
        name = "B3 → C2",
        points = listOf(
            GeoPoint(30.768536, 76.575860), // B3
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767689848518927, 76.57537946601282),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870) // C2
        )
    )


    val b3ToDaca = Route(
        id = "b3_daca",
        name = "B3 → DACA",
        points = listOf(
            GeoPoint(30.768536, 76.575860), // B3
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769645022177464, 76.57257647796877),
            GeoPoint(30.769803, 76.572588) // DACA
        )
    )

    val b3ToD4 = Route(
        id = "b3_d4",
        name = "B3 → D4",
        points = listOf(
            GeoPoint(30.768536, 76.575860), // B3
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.77025776694348, 76.57070741206844)
        )
    )


    // ----------------- B4 -> ... -----------------
    val b4ToGate2 = Route(
        id = "b4_gate2",
        name = "B4 → Gate 2",
        points = listOf(
            GeoPoint(30.768578, 76.574595), // B4
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.772817, 76.576464) // Gate 2
        )
    )


    val b4ToC3 = Route(
        id = "b4_c3",
        name = "B4 → C3",
        points = listOf(
            GeoPoint(30.768578, 76.574595), // B4
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.767678658071723, 76.57500218626026),
            GeoPoint(30.767500, 76.574944) // C3
        )
    )

    val b4ToC2 = Route(
        id = "b4_c2",
        name = "B4 → C2",
        points = listOf(
            GeoPoint(30.768578, 76.574595), // B4
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.76769105760702, 76.57538026637098),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870) // C2
        )
    )

    val b4ToC1 = Route(
        id = "b4_c1",
        name = "B4 → C1",
        points = listOf(
            GeoPoint(30.768578, 76.574595), // B4
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.76769105760702, 76.57538026637098),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766892, 76.575894) // C1
        )
    )


    val b4ToDaca = Route(
        id = "b4_daca",
        name = "B4 → DACA",
        points = listOf(
            GeoPoint(30.768578, 76.574595), // B4
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769639543487223, 76.57257261800041),
            GeoPoint(30.769803, 76.572588) // DACA
        )
    )

    val b4ToD4 = Route(
        id = "b4_d4",
        name = "B4 → D4",
        points = listOf(
            GeoPoint(30.768578, 76.574595), // B4
            GeoPoint(30.768597111607896, 76.57376094456905),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.76953668060101, 76.57368145351477),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.77019251385089, 76.57065353147499)
        )
    )
    // ----------------- Updated VIP Gate -> other routes -----------------

    val vipgateToGate2 = Route(
        id = "vipgate_gate2",
        name = "VIP Gate → Gate 2",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.772817, 76.576464)
        )
    )

    val vipgateToB3 = Route(
        id = "vipgate_b3",
        name = "VIP Gate → B3",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768525, 76.575919)
        )
    )
    val vipgateToC3 = Route(
        id = "vipgate_c3",
        name = "VIP Gate → C3",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767670, 76.575001),
            GeoPoint(30.767500, 76.574944)        )
    )
    val vipgateToC2 = Route(
        id = "vipgate_c2",
        name = "VIP Gate → C2",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870)
        )
    )

    val vipgateToDaca = Route(
        id = "vipgate_daca",
        name = "VIP Gate → DACA",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768634, 76.573667),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769803, 76.572588)
        )
    )

    val vipgateToD4Gate = Route(
        id = "vipgate_d4gate",
        name = "VIP Gate → D4 Gate",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.76926228441716, 76.57631164593464),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),
            GeoPoint(30.769819, 76.570414)
        )
    )

    val vipgateToD2 = appendD1SegmentAfterD2(Route(
        id = "vipgate_d2",
        name = "VIP Gate → D2",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.76926228441716, 76.57631164593464),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),

            GeoPoint(30.770919, 76.571232)
        )
    ))

    val vipgateToD3 = Route(
        id = "vipgate_d3",
        name = "VIP Gate → D3",
        points = listOf(
            GeoPoint(30.772596, 76.577559),
            GeoPoint(30.772322, 76.577535),
            GeoPoint(30.772139, 76.577615),
            GeoPoint(30.77158356912947, 76.57752008561624),
            GeoPoint(30.771450, 76.578228),
            GeoPoint(30.770582, 76.577526),
            GeoPoint(30.770504, 76.577412),
            GeoPoint(30.770458, 76.576655),
            GeoPoint(30.770539, 76.576338),
            GeoPoint(30.76926228441716, 76.57631164593464),
            GeoPoint(30.769251018167566, 76.57642454264028),
            GeoPoint(30.769211105541395, 76.5764458000439),
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.76850393754788, 76.57637891954589),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),

            GeoPoint(30.770529, 76.570911)
        )
    )

    val vipgateToD4 = cloneD4GateRoute(vipgateToD4Gate, "vipgate_d4", "VIP Gate -> D4", d4Lat, d4Lng, "D4")

    val c1ToB1 = Route(
        id = "c1_b1",
        name = "C1 → B1",
        points = listOf(
            GeoPoint(30.766892, 76.575894), // C1
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.767688694189303, 76.57633556325749),

            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.769278179487724, 76.57586530128097),
            GeoPoint(30.769680011492163, 76.57588089007612),// B1
        )
    )


    val c1ToB3 = Route(
        id = "c1_b3",
        name = "C1 → B3",
        points = listOf(
            GeoPoint(30.766892, 76.575894), // C1
            GeoPoint(30.767689746946008, 76.57593444925705),
            GeoPoint(30.767687514505873, 76.57538624328919),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.768536, 76.575860) // B3
        )
    )


    val c1ToDACA = Route(
        id = "c1_daca",
        name = "C1 → DACA",
        points = listOf(
            GeoPoint(30.766892, 76.575894), // C1
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769803, 76.572588) // DACA
        )
    )


    val c3ToDACA = Route(
        id = "c3_daca",
        name = "C3 → DACA",
        points = listOf(
            GeoPoint(30.767500, 76.574944), // C3
            GeoPoint(30.767682, 76.575016),
            GeoPoint(30.767685, 76.575377),
            GeoPoint(30.768552, 76.575417),
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769803, 76.572588) // DACA
        )
    )


    val a2ToB1 = Route(
        id = "a2_b1",
        name = "A2 → B1",
        points = listOf(
            GeoPoint(30.769484, 76.578896), // A2 Start
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338), // Pivot at A3
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.76924853081152, 76.57642481500365),
            GeoPoint(30.76928325292016, 76.57586176179309),
            GeoPoint(30.769578704074227, 76.57587026141996)// B1
        )
    )
    val gate1toB3 = Route(
        id="gate1_b3",
        name="Gate 1 → B3",
        points = listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992), // A2

            GeoPoint(30.769505902489716, 76.57839137246361),
            GeoPoint(30.768964380924213, 76.57834958878301),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768525, 76.575919) // B3
        )
    )

    val gate1toB1 = Route(
        id="gate1_b1",
        name="Gate 1 → B1",
        points = listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992), // A2

            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.76924853081152, 76.57642481500365),
            GeoPoint(30.76928325292016, 76.57586176179309),
            GeoPoint(30.769578704074227, 76.57587026141996) // B1
        )
    )
    val gate1ToGate2 = Route(
        id="gate1_gate2",
        name="Gate 1 → Gate 2",
        points=listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992), // A2

            GeoPoint(30.769484, 76.578896),
            GeoPoint(30.769498, 76.578552),
            GeoPoint(30.769586, 76.578476),
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.769241, 76.576432),
            GeoPoint(30.769254, 76.576312),
            GeoPoint(30.772817, 76.576464)
        )
    )
    val gate1ToA1 = Route(
        id="gate1_a1",
        name="Gate 1 → A1",
        points=listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992), // A2

            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.770864, 76.579508),
            GeoPoint(30.770964, 76.579122),
            GeoPoint(30.771271, 76.579156),
            GeoPoint(30.771312, 76.579002),
            GeoPoint(30.771395, 76.578427)
        )
    )
    val gate1ToD4Gate = Route(
        id="gate1_d4gate",
        name="Gate 1 → D4 Gate",
        points=listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),
            GeoPoint(30.770280, 76.579368),

            // A2
            GeoPoint(30.769931, 76.578992),

            // Move toward A3 junction
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338),

            // Main campus intersection
            GeoPoint(30.769067, 76.576413),

            // B corridor
            GeoPoint(30.768858713384326, 76.57637927472929),
            GeoPoint(30.76850393754788, 76.57637891954589),

            // DACA road
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768639, 76.573639),

            // D4 corridor
            GeoPoint(30.769499, 76.573686),
            GeoPoint(30.769545, 76.573675),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769690, 76.571247),
            GeoPoint(30.769738, 76.571015),
            GeoPoint(30.769984, 76.570507),

            // D4 Gate
            GeoPoint(30.769819, 76.570414)
        )
    )

    val gate1ToD2 = appendD1SegmentAfterD2(cloneD4GateRoute(gate1ToD4Gate, "gate1_d2", "Gate 1 -> D2", d2Lat, d2Lng, "D2"))

    val gate1ToD3 = cloneD4GateRoute(gate1ToD4Gate, "gate1_d3", "Gate 1 -> D3", d3Lat, d3Lng, "D3")

    val gate1ToD4 = cloneD4GateRoute(gate1ToD4Gate, "gate1_d4", "Gate 1 -> D4", d4Lat, d4Lng, "D4")

    val gate1ToDaca = Route(
        id="gate1_daca",
        name="Gate 1 → DACA",
        points=listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992),

            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.76850234041828, 76.57637597823197),
            GeoPoint(30.768578, 76.574595),
            GeoPoint(30.768639, 76.573639),
            GeoPoint(30.769573, 76.573647),
            GeoPoint(30.769643, 76.572580),
            GeoPoint(30.769803, 76.572588)
        )
    )
    val gate1toC3 = Route(
        id="gate1_c3",
        name="Gate 1 → C3",
        points = listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992), // A2

            GeoPoint(30.769484, 76.578896),
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.767690, 76.576344),
            GeoPoint(30.767670, 76.575001),
            GeoPoint(30.767500, 76.574944) // C3
        )
    )
    val gate1toB4=Route(
        id="gate1_B4",
        name="Gate 1 → B4",
        points = listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),// A1-A2 transition turn
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992), // A2
            GeoPoint(30.769505902489716, 76.57839137246361),
            GeoPoint(30.768964380924213, 76.57834958878301),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768578, 76.574595) // B4
        )
    )
    val a2ToB4 = Route(
        id = "a2_b4",
        name = "A2 → B4",
        points = listOf(
            GeoPoint(30.769931, 76.578992), // A2
            GeoPoint(30.769505902489716, 76.57839137246361),
            GeoPoint(30.768964380924213, 76.57834958878301),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768578, 76.574595) // B4
        )
    )
    val gate1toc1=Route(
        id="gate1_c1",
        name="Gate 1 → C1",
        points = listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),// A1-A2 transition turn
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992), // A2
            GeoPoint(30.769498, 76.578552),
            GeoPoint(30.76896564231378, 76.57833612533807),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.76768422930929, 76.57633556323496),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766892, 76.575894) // C1
        )
    )
    val a2ToC1 = Route(
        id = "a2_c1",
        name = "A2 → C1",
        points = listOf(
            GeoPoint(30.769931, 76.578992), // A2
            GeoPoint(30.769498, 76.578552),
            GeoPoint(30.76896564231378, 76.57833612533807),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.76768422930929, 76.57633556323496),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766892, 76.575894) // C1
        )
    )
    val gate1toC2=Route(
        id="gate1_C2",
        name="Gate 1 → C2",
        points = listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),// A1-A2 transition turn
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992), // A2
            GeoPoint(30.769498, 76.578552),
            GeoPoint(30.768963661930645, 76.57833904885511),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.76769452733911, 76.57632809427488),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870) // C2
        )
    )
    val a2ToC2 = Route(
        id = "a2_c2",
        name = "A2 → C2",
        points = listOf(
            GeoPoint(30.769931, 76.578992), // A2
            GeoPoint(30.769498, 76.578552),
            GeoPoint(30.768963661930645, 76.57833904885511),
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.76769452733911, 76.57632809427488),
            GeoPoint(30.767691, 76.575926),
            GeoPoint(30.766130, 76.575870) // C2
        )
    )


    val a3ToB1 = Route(
        id = "a3_b1",
        name = "A3 → B1",
        points = listOf(
            GeoPoint(30.768968, 76.578338), // A3
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.769923, 76.576318) // B1
        )
    )
    val gate1toB2=Route(
        id="gate1_b2",
        name="Gate 1 → B2",
        points = listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),// A1-A2 transition turn
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769484, 76.578896), // A2 Start
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338), // A3
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.769225299555668, 76.57644037926552),
            GeoPoint(30.769278, 76.575853) // B2
        )
    )
    val a2ToB2 = Route(
        id = "a2_b2",
        name = "A2 → B2",
        points = listOf(
            GeoPoint(30.769484, 76.578896), // A2 Start
            GeoPoint(30.769513, 76.578400),
            GeoPoint(30.768968, 76.578338), // A3
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.769225299555668, 76.57644037926552),
            GeoPoint(30.769278, 76.575853) // B2
        )
    )
    val a3ToB2 = Route(
        id = "a3_b2",
        name = "A3 → B2",
        points = listOf(
            GeoPoint(30.768968, 76.578338), // A3
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.769225299555668, 76.57644037926552),
            GeoPoint(30.769278, 76.575853) // B2
        )
    )

    val a3ToB3 = Route(
        id = "a3_b3",
        name = "A3 → B3",
        points = listOf(
            GeoPoint(30.768968, 76.578338), // A3
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768536, 76.575860) // B3
        )
    )

    val a3ToB4 = Route(
        id = "a3_b4",
        name = "A3 → B4",
        points = listOf(
            GeoPoint(30.768968, 76.578338), // A3
            GeoPoint(30.769067, 76.576413),
            GeoPoint(30.768506, 76.576372),
            GeoPoint(30.768578, 76.574595) // B4
        )
    )
    val gate1toa2=Route(
        id="gate1_a2",
        name="Gate 1 → A2",
        points = listOf(
            GeoPoint(30.772036106412582, 76.57987637336583),
            GeoPoint(30.771237850796037, 76.57965721764894),
            GeoPoint(30.77121086673674, 76.57966979506266),
            GeoPoint(30.771172840833646, 76.5796657717494),
            GeoPoint(30.77113366261386, 76.57963224413508),
            GeoPoint(30.771126172658366, 76.57959134044803),
            GeoPoint(30.770854598707523, 76.57951299373609),// A1-A2 transition turn
            GeoPoint(30.770280, 76.579368),
            GeoPoint(30.769931, 76.578992)
        )
    )


    val allRoutes = listOf(
        gate1toa2,
        vipgateToC1,
        b4ToDaca,
        VIPtoa1,
        gate2ToC3,
        b2ToD4GATE,
        gate2ToC1,
        gate2ToB3,
        gate2ToB4,
        gate2ToA3,
        gate2ToA2,
        A1ToC3,
        gate2ToDaca,
        gate3ToD1,
        d1ToGate3,
        d1ToC3,
        d1ToC2,
        d1ToC1,
        d1ToDaca,
        d1ToB3,
        d4gateToC3,
        d4gateToC2,
        d4gateToC1,
        d4gateToDaca,
        d4gateToB3,
        gate3ToC3,
        gate3ToC2,
        gate3ToC1,
        gate3ToDaca,
        gate3ToB3,
        d2ToC3,
        d2ToC2,
        d2ToC1,
        d2ToDaca,
        d2ToB3,
        d3ToC3,
        d3ToC2,
        d3ToC1,
        d3ToDaca,
        d3ToB3,
        d4ToC3,
        d4ToC2,
        d4ToC1,
        d4ToDaca,
        d4ToB3,
        a1ToA3,
        a1ToA2,
        a1ToB1,
        gate2ToC2,
        gate2ToB1,
        gate2ToB2,
        d4gateToB4,
        vipgateToC3,
        d4gateToA3,
        d4gateToB1,
        gate3ToB4,
        gate3ToA3,
        gate3ToB1,
        d1ToB4,
        d1ToA3,
        d1ToB1,
        d2ToB4,
        d2ToA3,
        d2ToB1,
        d3ToB4,
        d3ToA3,
        d3ToB1,
        d4ToB4,
        d4ToA3,
        d4ToB1,
        dacaToc1,
        d4gateToA1,
        d2ToA1,
        d3ToA1,
        d4ToA1,
        dacaToc3,
        dacaToc2,
        dacaToA3,
        a3toc3,
        b4ToC1,
        a3toc2,
        a3toc1,
        d4GateToD4,
        d4GateToD3,
        d4GateToD2,
        gate3ToD2,
        gate3ToD3,
        gate3ToD4,
        d1ToD2,
        d1ToD3,
        d1ToD4,
        VIPtoA2,
        VIPtoA3,
        VIPtoB4,
        dacaToB1,
        VIPtoB2,
        A2ToC3,
        a2ToB3,
        dacaToA2,
        a1ToB2,
        a1ToB3,
        a1ToB4,
        a1ToC1,
        a1ToC2,
        a1ToDaca,
        a1ToD4,
        a1ToD3,
        b1ToB3,
        VIPtoB1,
        b1ToB4,
        b2ToB4,
        b2ToD2,
        b2ToD3,
        b2ToD4,
        b3ToB4,
        b1ToA3,
        b1ToC3,
        b1ToC2,
        b2ToC3,
        b2ToC2,
        b2ToC1,
        b2ToDaca,
        b3ToC3,
        b3ToC2,
        b3ToDaca,
        b4ToC3,
        b4ToC2,
        vipgateToGate2,
        vipgateToB3,
        vipgateToC2,
        vipgateToDaca,
        vipgateToD4Gate,
        vipgateToD2,
        vipgateToD3,
        vipgateToD4,
        a3ToB4,
        a3ToB3,
        a3ToB2,
        a3ToB1,
        c1ToB3,
        c1ToDACA,
        a1ToGate2,
        a1ToD4Gate,
        a1ToD2,
        d4gateToA2,
        gate3ToA2,
        d1ToA2,
        d2ToA2,
        d3ToA2,
        d4ToA2,
        b1ToDaca,
        b1ToD4,
        b3ToD4,
        b4ToGate2,
        b4ToD4,
        c1ToB1,
        c3ToDACA,
        a2ToB1,
        a2ToB4,
        a2ToC1,
        a2ToC2,
        a2ToB2,
        gate1toa2,
        gate1toB1,
        gate1toB2,
        gate1toB3,
        gate1toB4,
        gate1toc1,
        gate1toC2,
        gate1toC3,
        gate1ToGate2,
        gate1ToA1,
        gate1ToD4Gate,
        gate3ToA1,
        d1ToA1,
        gate1ToD2,
        gate1ToD3,
        gate1ToD4,
        gate1ToDaca,
    ).let { original ->
        original + original.map { reverseRoute(it) }
    }
}

fun reverseRoute(route: Route): Route {
    val parts = route.name.split("→", "->")

    val reversedName = if (parts.size == 2) {
        "${parts[1].trim()} → ${parts[0].trim()}"
    } else {
        route.name + " (Reverse)"
    }

    return Route(
        id = route.id + "_rev",
        name = reversedName,
        points = route.points
            .asReversed()
            .map { it.copy(instruction = null) }
    )
}
