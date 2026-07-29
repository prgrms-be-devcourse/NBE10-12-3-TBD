package com.whattoeat.global.exception

class UserNotFoundException(id: Long) : RuntimeException("User not found: $id")
