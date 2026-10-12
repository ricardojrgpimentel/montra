package dev.montra.data

import dev.montra.data.model.IndexFile
import dev.montra.data.model.IndexJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsModelTest {

    private fun index(appJson: String) = IndexJson.decodeFromString(
        IndexFile.serializer(),
        """{"schemaVersion":1,"generatedAt":"2026-01-01T00:00:00Z","apps":[$appJson]}""",
    )

    private val base =
        """"id":"a","name":"A","summary":"s","packageName":"dev.a","license":"MIT","sourceCode":"https://example.com""""

    @Test fun `um indice anterior a este campo continua a descodificar`() {
        assertTrue(index("{$base}").apps.single().permissions.isEmpty())
    }

    @Test fun `as permissoes descodificam como o indice as publica`() {
        val permissions = index(
            "{$base,\"permissions\":[\"android.permission.CAMERA\",\"android.permission.INTERNET\"]}",
        ).apps.single().permissions

        assertEquals(listOf("android.permission.CAMERA", "android.permission.INTERNET"), permissions)
    }

    @Test fun `uma lista vazia e o mesmo que nenhuma`() {
        assertTrue(index("{$base,\"permissions\":[]}").apps.single().permissions.isEmpty())
    }
}
