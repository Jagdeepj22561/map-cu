package com.example.shared

enum class PlaceCategory(val displayLabel: String) {
    BLOCK("Blocks"),
    GATE("Gates"),
    PARK("Parks"),
    HOSTEL("Hostels"),
    BANK("Bank"),
    ATM("ATM"),
    CAFETERIA("Cafeteria / Food"),
    PARKING("Parking"),
    LIBRARY("Library"),
    LAB("Labs"),
    OTHER("Other")
}

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: PlaceCategory = PlaceCategory.OTHER
)

val campusPlaces = listOf(
    Place("VIP Gate", 30.772596, 76.577559, PlaceCategory.GATE),
    Place("Gate 1", 30.772036106412582, 76.57987637336583, PlaceCategory.GATE),
    Place("Gate 2", 30.772817, 76.576464, PlaceCategory.GATE),
    Place("Gate 3", 30.773800369487486, 76.5720582836731, PlaceCategory.GATE),
    Place("D4 Gate", 30.769819, 76.570414, PlaceCategory.GATE),
    Place("A1", 30.771450, 76.578228, PlaceCategory.BLOCK),
    Place("A2", 30.769931, 76.578992, PlaceCategory.BLOCK),
    Place("A3", 30.769136, 76.578354, PlaceCategory.BLOCK),
    Place("B1", 30.769595, 76.575869, PlaceCategory.BLOCK),
    Place("B2", 30.769278, 76.575853, PlaceCategory.BLOCK),
    Place("B3", 30.768525, 76.575919, PlaceCategory.BLOCK),
    Place("B4", 30.768578, 76.574595, PlaceCategory.BLOCK),
    Place("C1", 30.766892, 76.575894, PlaceCategory.BLOCK),
    Place("C2", 30.766130, 76.575870, PlaceCategory.BLOCK),
    Place("C3", 30.767500, 76.574944, PlaceCategory.BLOCK),
    Place("D1", 30.7716117893048, 76.57074814499073, PlaceCategory.BLOCK),
    Place("D2", 30.770919, 76.571232, PlaceCategory.BLOCK),
    Place("D3", 30.770529, 76.570911, PlaceCategory.BLOCK),
    Place("D4", 30.770258, 76.570707, PlaceCategory.BLOCK),
    Place("DACA", 30.769803, 76.572588, PlaceCategory.BLOCK),
    Place("cu main ground", 30.767066452484283, 76.57547404904999, PlaceCategory.PARK),
    Place("fountain", 30.769460490927187, 76.57746865247331, PlaceCategory.PARK),
    Place("b1 park", 30.76960138884334, 76.57611001199182, PlaceCategory.PARK),
    Place("park cu", 30.76794139804214, 76.57580369304374, PlaceCategory.PARK),
    Place("cu front garden", 30.772230274856696, 76.57774479174435, PlaceCategory.PARK),
    Place("j and k bank", 30.766541950899168, 76.5748079884156, PlaceCategory.BANK),
    Place("sbi atm", 30.767695983967048, 76.57529723744993, PlaceCategory.ATM),
    Place("tagore girls hostel", 30.76579249851006, 76.57578490280444, PlaceCategory.HOSTEL)
)
