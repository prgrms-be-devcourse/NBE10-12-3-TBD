package com.whattoeat.global.exception

class FeedNotFoundException(id: Long) : RuntimeException("Feed not found: $id")
