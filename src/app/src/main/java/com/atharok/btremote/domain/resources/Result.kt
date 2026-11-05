package com.atharok.btremote.domain.resources

data class Result<T> (
    val value: T,
    val message: String
)