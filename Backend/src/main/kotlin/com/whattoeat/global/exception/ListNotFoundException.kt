package com.whattoeat.global.exception

class ListNotFoundException(id: Long) : RuntimeException("리스트를 찾을 수 없습니다: $id")
