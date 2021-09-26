package de.passbutler.app

object TestConstants {
    const val TEST_SERVERURL = "example.passbutler.de"
    const val TEST_USERNAME = "alice.mustermann@passbutler.de"
    const val TEST_MASTER_PASSWORD = "1234567890abcd"
}

object TestItems {
    val amazon = TestItem(
        title = "Amazon (Prime Video)",
        username = "family.mustermann@passbutler.de",
        password = "12345678",
        url = "https://www.amazon.com"
    )

    val googleMail = TestItem(
        title = "Google Mail",
        username = "alice.mustermann@passbutler.de",
        password = "12345678",
        url = "https://accounts.google.com/signin/v2/"
    )

    val netflix = TestItem(
        title = "Netflix (Family share)",
        username = "family.mustermann@passbutler.de",
        password = "12345678",
        url = "https://www.netflix.com"
    )
}

data class TestItem(
    val title: String,
    val username: String,
    val password: String,
    val url: String
)
