package com.whattoeat.global.exception

class RestaurantListItemNotFoundException(id: Long) :
    RuntimeException("식당 리스트 아이템을 찾을 수 없습니다: $id")
