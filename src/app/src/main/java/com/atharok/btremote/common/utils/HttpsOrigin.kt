// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.common.utils

import java.net.URI
import java.util.Locale

/** An explicitly selected service origin, never a redirect, credential or API path. */
fun httpsOrigin(input: String): String {
    val uri = URI(input.trim())
    require(input.length <= 2048 && uri.scheme.equals("https", ignoreCase = true) &&
        !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null &&
        uri.rawFragment == null && !uri.rawAuthority.endsWith(":") && uri.rawPath in listOf("", "/") &&
        (uri.port == -1 || uri.port in 1..65535)) { "请输入完整 HTTPS 网站地址，可含端口，不含路径、账号或参数" }
    return URI("https", null, uri.host.lowercase(Locale.ROOT), if (uri.port == 443) -1 else uri.port,
        null, null, null).toASCIIString()
}
