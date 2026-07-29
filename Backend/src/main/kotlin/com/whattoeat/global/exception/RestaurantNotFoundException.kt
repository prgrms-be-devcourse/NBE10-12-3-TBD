package com.whattoeat.global.exception

class RestaurantNotFoundException : RuntimeException {
    constructor(id: Long) : super("식당을 찾을 수 없습니다: $id")
    constructor(message: String) : super(message)
}
