package com.whattoeat.global.exception

class DuplicateRestaurantListItemException(id: Long) :
    RuntimeException("이미 리스트에 추가된 식당입니다. : $id")
