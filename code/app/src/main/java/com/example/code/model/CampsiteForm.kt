// Checks what an owner typed in the register campsite form and turns it into a Campsite.
// AI use: Claude (Anthropic) suggested moving the form checks out of the screen into this function so they can be unit tested.
package com.example.code.model

/**
 * Builds a campsite from the register form, or returns null if something is missing or invalid.
 *
 * @param name the campsite name, required
 * @param location where the campsite is, required
 * @param description the description and amenities, required
 * @param maxOccupancy the maximum number of guests, must be a whole number above 0
 * @param latitude the map latitude, must be between -90 and 90
 * @param longitude the map longitude, must be between -180 and 180
 * @param tags comma-separated tags, optional
 * @param imageUrl a link to a promotional image, optional
 * @return the campsite to save, or null if the form is not valid
 */
fun campsiteFromForm(
    name: String,
    location: String,
    description: String,
    maxOccupancy: String,
    latitude: String,
    longitude: String,
    tags: String,
    imageUrl: String,
): Campsite? {
    val occupancyValue = maxOccupancy.toIntOrNull()
    val latitudeValue = latitude.toDoubleOrNull()
    val longitudeValue = longitude.toDoubleOrNull()

    if (
        name.isBlank() ||
        location.isBlank() ||
        description.isBlank() ||
        occupancyValue == null ||
        occupancyValue <= 0 ||
        latitudeValue == null ||
        longitudeValue == null ||
        latitudeValue !in -90.0..90.0 || // latitude must be a real place
        longitudeValue !in -180.0..180.0 // longitude must be a real place
    ) {
        return null
    }

    return Campsite(
        name = name.trim(),
        location = location.trim(),
        description = description.trim(),
        maxOccupancy = occupancyValue,
        latitude = latitudeValue,
        longitude = longitudeValue,
        tags = tags
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        // Leave it empty if no image was given
        imageUrl = imageUrl.trim().ifBlank { null },
    )
}

/**
 * Adds the optional details of the campsite form to [base]: price per
 * night, rules, cancellation policy and the availability schedule.
 * AI use: written with Claude (Anthropic), Nov 2026; reviewed and tested by Aalok Mishra.
 *
 * @param price price per night in CAD, or blank for none
 * @param availableFrom first available date as YYYY-MM-DD, or blank
 * @param availableTo last available date as YYYY-MM-DD, or blank
 * @param weekdays weekdays open inside those dates (1 = Sunday to 7 = Saturday); empty means every day
 * @return [base] with the details filled in
 * @throws IllegalArgumentException with a message for the owner if a value is not valid
 */
fun campsiteDetailsFromForm(
    base: Campsite,
    price: String,
    rules: String,
    cancellationPolicy: String,
    availableFrom: String,
    availableTo: String,
    weekdays: List<Int>,
): Campsite {
    val priceValue = if (price.isBlank()) {
        null
    } else {
        val number = price.trim().removePrefix("$").toDoubleOrNull()
        require(number != null && number.isFinite() && number >= 0) { "Enter the price per night as a number, like 65." }
        number
    }
    val (from, to) = Availability.validate(availableFrom, availableTo, weekdays)
    return base.copy(
        price = priceValue,
        currency = if (priceValue == null) null else "CAD",
        rules = rules.trim(),
        cancellationPolicy = cancellationPolicy.trim(),
        availableFrom = from,
        availableTo = to,
        availableWeekdays = if (from.isEmpty()) emptyList() else weekdays.distinct().sorted(),
    )
}
