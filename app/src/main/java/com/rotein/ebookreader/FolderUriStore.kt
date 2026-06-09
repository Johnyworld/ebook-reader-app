package com.rotein.ebookreader

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * SAF로 선택한 폴더 URI를 SharedPreferences에 저장/로드한다.
 * takePersistableUriPermission으로 앱 재시작 후에도 접근을 유지한다.
 */
object FolderUriStore {
    private const val PREF_NAME = "folder_uris"
    private const val KEY_URIS = "selected_uris"

    /** 저장된 폴더 URI 목록 반환 (유효한 권한이 있는 URI만 반환) */
    fun load(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_URIS, emptySet()) ?: emptySet()
        // 시스템에 유효한 persistable 권한이 남아있는 URI만 반환
        val persisted = context.contentResolver.persistedUriPermissions
            .map { it.uri.toString() }.toSet()
        val valid = raw.filter { it in persisted }
        // 무효한 URI가 있으면 정리
        if (valid.size < raw.size) {
            prefs.edit().putStringSet(KEY_URIS, valid.toSet()).apply()
        }
        return valid.mapNotNull { Uri.parse(it) }
    }

    /** 새 폴더 URI 추가 + persistable 권한 취득 */
    fun add(context: Context, uri: Uri) {
        // 영구 접근 권한 취득
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_URIS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(uri.toString())
        prefs.edit().putStringSet(KEY_URIS, current).apply()
    }

    /** 폴더 URI 제거 + persistable 권한 해제 */
    fun remove(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_URIS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(uri.toString())
        prefs.edit().putStringSet(KEY_URIS, current).apply()
    }

    /** 선택된 폴더가 있는지 확인 */
    fun hasAny(context: Context): Boolean = load(context).isNotEmpty()
}
