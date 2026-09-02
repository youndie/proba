package dev.youndie.proba.checks

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import java.io.File
import java.net.URI

/**
 * Fetches a published file over http, or off the disk for a local repository.
 *
 * Both, because the first repository anyone wants to check is the one they just published into, and
 * a `file:` url is what that repository is addressed by.
 */
fun httpArtefacts(client: HttpClient): ArtefactSource =
    ArtefactSource { url ->
        if (url.startsWith("file:")) {
            runCatching { File(URI(url)).takeIf { it.isFile }?.readBytes() }.getOrNull()
        } else {
            runCatching {
                val response = client.get(url)
                if (response.status.value == 200) response.readRawBytes() else null
            }.getOrNull()
        }
    }
