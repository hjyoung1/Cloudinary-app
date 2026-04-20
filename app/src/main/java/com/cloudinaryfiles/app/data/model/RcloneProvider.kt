package com.cloudinaryfiles.app.data.model

enum class ProviderAuthType {
    CLOUDINARY,
    S3_COMPATIBLE,   // AWS SigV4 — covers S3, Wasabi, R2, MinIO, B2, DO Spaces, etc.
    OAUTH_GOOGLE,
    OAUTH_DROPBOX,
    OAUTH_ONEDRIVE,
    OAUTH_BOX,
    BASIC_WEBDAV     // Nextcloud, OwnCloud, generic WebDAV — URL + user + pass
}

data class ProviderDef(
    val key: String,
    val label: String,
    val emoji: String,
    val hint: String,
    val authType: ProviderAuthType,
    val s3Endpoint: String = "",    // default endpoint for S3-compatible providers
    val s3Region: String = "us-east-1"
)

object Providers {
    val all: List<ProviderDef> = listOf(
        // ── Cloudinary ────────────────────────────────────────────────────
        ProviderDef("cloudinary",  "Cloudinary",               "☁️", "Native Cloudinary API",               ProviderAuthType.CLOUDINARY),
        // ── S3-compatible ─────────────────────────────────────────────────
        ProviderDef("s3",          "Amazon S3",                "🪣", "AWS S3 object storage",               ProviderAuthType.S3_COMPATIBLE, "s3.amazonaws.com"),
        ProviderDef("wasabi",      "Wasabi",                   "🌶️","Hot cloud storage",                    ProviderAuthType.S3_COMPATIBLE, "s3.wasabisys.com", "us-east-1"),
        ProviderDef("r2",          "Cloudflare R2",            "🔶", "Zero-egress S3-compatible",           ProviderAuthType.S3_COMPATIBLE, "<accountid>.r2.cloudflarestorage.com"),
        ProviderDef("minio",       "MinIO",                    "🏠", "Self-hosted S3-compatible",           ProviderAuthType.S3_COMPATIBLE),
        ProviderDef("b2s3",        "Backblaze B2 (S3)",        "🔷", "Backblaze B2 S3-compatible API",      ProviderAuthType.S3_COMPATIBLE, "s3.us-west-004.backblazeb2.com"),
        ProviderDef("spaces",      "DigitalOcean Spaces",      "🌊", "DO Spaces object storage",            ProviderAuthType.S3_COMPATIBLE, "<region>.digitaloceanspaces.com"),
        ProviderDef("linode",      "Linode Object Storage",    "🟢", "Linode / Akamai object storage",      ProviderAuthType.S3_COMPATIBLE, "<cluster>.linodeobjects.com"),
        ProviderDef("scaleway",    "Scaleway Object Storage",  "⚡", "Scaleway S3-compatible",              ProviderAuthType.S3_COMPATIBLE, "s3.<region>.scw.cloud"),
        ProviderDef("idrive",      "IDrive e2",                "💾", "IDrive e2 S3-compatible storage",     ProviderAuthType.S3_COMPATIBLE, "<region>.idrivee2.com"),
        ProviderDef("storj",       "Storj (S3 gateway)",       "🛸", "Decentralised cloud storage",         ProviderAuthType.S3_COMPATIBLE, "gateway.storjshare.io"),
        // ── OAuth2 providers ──────────────────────────────────────────────
        ProviderDef("gdrive",      "Google Drive",             "📁", "Google Drive — requires OAuth app",   ProviderAuthType.OAUTH_GOOGLE),
        ProviderDef("dropbox",     "Dropbox",                  "📦", "Dropbox — requires OAuth app",        ProviderAuthType.OAUTH_DROPBOX),
        ProviderDef("onedrive",    "OneDrive",                 "🗂️","Microsoft OneDrive / SharePoint",      ProviderAuthType.OAUTH_ONEDRIVE),
        ProviderDef("box",         "Box",                      "📫", "Box cloud storage",                   ProviderAuthType.OAUTH_BOX),
        // ── WebDAV / basic auth ───────────────────────────────────────────
        ProviderDef("nextcloud",   "Nextcloud",                "☁️", "Self-hosted Nextcloud",               ProviderAuthType.BASIC_WEBDAV),
        ProviderDef("owncloud",    "OwnCloud",                 "🔓", "Self-hosted OwnCloud",                ProviderAuthType.BASIC_WEBDAV),
        ProviderDef("webdav",      "WebDAV (generic)",         "🌐", "Any WebDAV server",                   ProviderAuthType.BASIC_WEBDAV),
        ProviderDef("pcloud",      "pCloud",                   "🌥️","pCloud — WebDAV access",               ProviderAuthType.BASIC_WEBDAV, "webdav.pcloud.com"),
        ProviderDef("yandex",      "Yandex Disk",              "🇷🇺","Yandex Disk WebDAV",                  ProviderAuthType.BASIC_WEBDAV, "webdav.yandex.com"),
        ProviderDef("koofr",       "Koofr",                    "🗃️","Koofr cloud storage",                  ProviderAuthType.BASIC_WEBDAV, "app.koofr.net"),
        ProviderDef("hetzner",     "Hetzner Storage Box",      "🟠", "Hetzner Storage Box",                 ProviderAuthType.BASIC_WEBDAV)
    )

    fun find(key: String): ProviderDef = all.firstOrNull { it.key == key } ?: all.first()

    val byAuthType: Map<ProviderAuthType, List<ProviderDef>> by lazy { all.groupBy { it.authType } }
}
