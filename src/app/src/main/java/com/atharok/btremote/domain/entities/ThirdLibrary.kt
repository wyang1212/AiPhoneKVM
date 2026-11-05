// AiPhoneKVM modifications, 2026-11-06. Original notices retained; see NOTICE.md.
package com.atharok.btremote.domain.entities

import androidx.annotation.StringRes
import com.atharok.btremote.R

enum class ThirdLibrary(
    @param:StringRes val title: Int,
    @param:StringRes val id: Int,
    @param:StringRes val codeHost: Int,
    @param:StringRes val codeUrl: Int,
    @param:StringRes val license: Int,
    @param:StringRes val licenseUrl: Int,
) {
    AIPHONEKVM_SOURCE(
        title = R.string.oss_aiphonekvm_source_name,
        id = R.string.oss_aiphonekvm_source_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_aiphonekvm_source_url,
        license = R.string.oss_gpl3_name,
        licenseUrl = R.string.oss_gpl3_url
    ),

    BTREMOTE_SOURCE(
        title = R.string.oss_btremote_source_name,
        id = R.string.oss_btremote_source_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_btremote_source_url,
        license = R.string.oss_gpl3_name,
        licenseUrl = R.string.oss_gpl3_url
    ),

    ANDROID_BT_REMOTE_SOURCE(
        title = R.string.oss_android_bt_remote_source_name,
        id = R.string.oss_android_bt_remote_source_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_android_bt_remote_source_url,
        license = R.string.oss_gpl3_name,
        licenseUrl = R.string.oss_gpl3_url
    ),

    BLUEHIDFLOW_REFERENCE(
        title = R.string.oss_bluehidflow_reference_name,
        id = R.string.oss_bluehidflow_reference_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_bluehidflow_reference_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    REORDERABLE(
        title = R.string.oss_reorderable_name,
        id = R.string.oss_reorderable_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_reorderable_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    ZXING_EMBEDDED(
        title = R.string.oss_zxing_embedded_name,
        id = R.string.oss_zxing_embedded_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_zxing_embedded_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    ZXING_CORE(
        title = R.string.oss_zxing_core_name,
        id = R.string.oss_zxing_core_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_zxing_core_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    PAHO_MQTT(
        title = R.string.oss_paho_mqtt_name,
        id = R.string.oss_paho_mqtt_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_paho_mqtt_url,
        license = R.string.oss_edl_name,
        licenseUrl = R.string.oss_edl_url
    ),

    NANO_HTTPD(
        title = R.string.oss_nano_httpd_name,
        id = R.string.oss_nano_httpd_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_nano_httpd_url,
        license = R.string.oss_bsd_name,
        licenseUrl = R.string.oss_bsd_url
    ),

    KOTLIN_RUNTIME(
        title = R.string.oss_kotlin_runtime_name,
        id = R.string.oss_kotlin_runtime_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_kotlin_runtime_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    KOTLIN_COROUTINES(
        title = R.string.oss_kotlin_coroutines_name,
        id = R.string.oss_kotlin_coroutines_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_kotlin_coroutines_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    GLANCE(
        title = R.string.oss_glance_name,
        id = R.string.oss_glance_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_glance_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    WORK_MANAGER(
        title = R.string.oss_work_manager_name,
        id = R.string.oss_work_manager_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_work_manager_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    OKIO(
        title = R.string.oss_okio_name,
        id = R.string.oss_okio_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_okio_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    STATELY(
        title = R.string.oss_stately_name,
        id = R.string.oss_stately_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_stately_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    JSPECIFY(
        title = R.string.oss_jspecify_name,
        id = R.string.oss_jspecify_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_jspecify_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    GUAVA_FUTURE(
        title = R.string.oss_guava_future_name,
        id = R.string.oss_guava_future_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_guava_future_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    JAVA_ANNOTATIONS(
        title = R.string.oss_java_annotations_name,
        id = R.string.oss_java_annotations_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_java_annotations_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    ANDROIDX_PROTOBUF(
        title = R.string.oss_protobuf_name,
        id = R.string.oss_protobuf_id,
        codeHost = R.string.oss_source_host,
        codeUrl = R.string.oss_protobuf_url,
        license = R.string.oss_bsd_name,
        licenseUrl = R.string.oss_protobuf_license_url
    ),

    CORE_KTX(
        title = R.string.androidx_core_ktx_name,
        id = R.string.androidx_core_ktx_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    ACTIVITY_COMPOSE(
        title = R.string.androidx_activity_compose_name,
        id = R.string.androidx_activity_compose_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    COMPOSE_BOM(
        title = R.string.androidx_compose_bom_name,
        id = R.string.androidx_compose_bom_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    COMPOSE_UI(
        title = R.string.androidx_compose_ui_name,
        id = R.string.androidx_compose_ui_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    COMPOSE_UI_GRAPHICS(
        title = R.string.androidx_compose_ui_graphics_name,
        id = R.string.androidx_compose_ui_graphics_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    COMPOSE_MATERIAL_3(
        title = R.string.androidx_compose_material3_name,
        id = R.string.androidx_compose_material3_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    COMPOSE_MATERIAL_ICONS_EXTENDED(
        title = R.string.androidx_compose_material_icons_extended_name,
        id = R.string.androidx_compose_material_icons_extended_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    LIFECYCLE_RUNTIME_COMPOSE(
        title = R.string.androidx_lifecycle_runtime_compose_name,
        id = R.string.androidx_lifecycle_runtime_compose_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    NAVIGATION_COMPOSE(
        title = R.string.androidx_navigation_compose_name,
        id = R.string.androidx_navigation_compose_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    DATA_STORE_PREFERENCES(
        title = R.string.androidx_data_store_preferences_name,
        id = R.string.androidx_data_store_preferences_id,
        codeHost = R.string.github,
        codeUrl = R.string.androidx_jetpack_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    KOIN_ANDROIDX_COMPOSE(
        title = R.string.koin_name,
        id = R.string.koin_id,
        codeHost = R.string.github,
        codeUrl = R.string.koin_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    ),

    KOTLIN_SERIALIZATION(
        title = R.string.kotlin_serialization_name,
        id = R.string.kotlin_serialization_id,
        codeHost = R.string.github,
        codeUrl = R.string.kotlin_serialization_url,
        license = R.string.apache_license_2_0,
        licenseUrl = R.string.apache_license_2_0_url
    )
}